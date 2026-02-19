package com.lt.dbcomparator.service;

import com.lt.dbcomparator.dto.LoadRequest;
import com.lt.dbcomparator.dto.LoadStatusResponse;
import com.lt.dbcomparator.entity.*;
import com.lt.dbcomparator.repository.ProductRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Сервис генерации тестовых данных для MongoDB.
 * <p>
 * Управляется через REST: start(LoadRequest) / stop() / getStatus().
 * Использует {@link MongoTemplate} bulkOps для максимальной скорости записи
 * документов.
 * <p>
 * Логика:
 * 1. Формируется полный документ {@link Customer} (с вложенными Profile и
 * Orders).
 * 2. В {@link OrderItem} делается SNAPSHOT (копия) данных товара (Product) для
 * быстрого чтения.
 * 3. Документы пачками (batchSize) отправляются в Mongo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataGeneratorService {

    private final MongoTemplate mongoTemplate;
    private final ProductRepository productRepository;
    private final MeterRegistry meterRegistry;

    // ── Состояние ──
    private volatile boolean running = false;
    private ScheduledExecutorService scheduler;
    private ExecutorService workerPool;
    private Semaphore inflightPermits;
    private LoadRequest currentConfig;
    private Instant startedAt;

    // ── Счётчики ──
    private final AtomicLong totalRecords = new AtomicLong(0);
    private final AtomicLong submittedCount = new AtomicLong(0);
    private final AtomicLong completedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);

    // ── Метрики (Micrometer) ──
    private Counter batchesSubmittedCounter;
    private Counter batchesCompletedCounter;
    private Counter batchesFailedCounter;
    private Counter recordsTotalCounter;
    private Timer batchDurationTimer;

    // ── Пул продуктов (предзаполняется один раз) ──
    private List<Product> cachedProducts;

    // ── Оценка пропускной способности ──
    // Mongo обычно быстрее на запись сложных структур, но overhead на сериализацию
    // выше.
    private static final double FIXED_OVERHEAD_MS = 5.0;
    private static final double MS_PER_CUSTOMER_GRAPH = 0.15;

    // ── Справочные данные для генерации ──
    private static final String[] FIRST_NAMES = {
            "Alexander", "Maria", "Dmitry", "Elena", "Sergey",
            "Anna", "Ivan", "Olga", "Andrey", "Natalia",
            "Mikhail", "Tatiana", "Pavel", "Ekaterina", "Viktor"
    };
    private static final String[] LAST_NAMES = {
            "Ivanov", "Petrov", "Sidorov", "Kozlov", "Novikov",
            "Morozov", "Volkov", "Sokolov", "Lebedev", "Popov"
    };
    private static final String[] COUNTRIES = { "RU", "US", "DE", "FR", "GB", "JP", "CN", "BR", "IN", "KR" };
    private static final String[] CITIES = {
            "Moscow", "London", "Berlin", "Paris", "Tokyo",
            "New York", "Shanghai", "Sao Paulo", "Mumbai", "Seoul"
    };
    private static final String[] STATUSES = { "ACTIVE", "INACTIVE", "BLOCKED" };
    private static final String[] ORDER_STATUSES = { "NEW", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED" };
    private static final String[] CURRENCIES = { "RUB", "USD", "EUR" };
    private static final String[] CATEGORIES = {
            "Electronics", "Books", "Clothing", "Food", "Sports",
            "Home", "Beauty", "Toys", "Auto", "Garden"
    };
    private static final String[] LANGUAGES = { "ru", "en", "de", "fr", "ja" };

    private static final int PRODUCT_POOL_SIZE = 200;

    @PostConstruct
    void initMetrics() {
        batchesSubmittedCounter = Counter.builder("generator.batches.submitted")
                .description("Батчей отправлено на запись")
                .register(meterRegistry);
        batchesCompletedCounter = Counter.builder("generator.batches.completed")
                .description("Батчей успешно записано")
                .register(meterRegistry);
        batchesFailedCounter = Counter.builder("generator.batches.failed")
                .description("Батчей упало с ошибкой")
                .register(meterRegistry);
        recordsTotalCounter = Counter.builder("generator.records.total")
                .description("Всего документов (Customer) сгенерировано")
                .register(meterRegistry);
        batchDurationTimer = Timer.builder("generator.batch.duration")
                .description("Время выполнения одного батча")
                .register(meterRegistry);
    }

    // ═══════════════════════════════════════════
    // Публичное API
    // ═══════════════════════════════════════════

    public synchronized void start(LoadRequest request) {
        if (running) {
            throw new IllegalStateException("Генератор уже запущен. Сначала вызовите /stop.");
        }

        validate(request);

        int workers = request.getWorkerThreads() > 0
                ? request.getWorkerThreads()
                : Math.max(2, Runtime.getRuntime().availableProcessors());
        request.setWorkerThreads(workers);

        int maxRate = estimateMaxBatchesPerSecond(request.getBatchSize(), workers);
        if (request.getBatchesPerSecond() > maxRate) {
            log.warn("Запрошено {} батчей/сек, что может быть выше возможностей системы (max ~{}).",
                    request.getBatchesPerSecond(), maxRate);
        }

        this.currentConfig = request;
        this.running = true;
        this.startedAt = Instant.now();
        this.totalRecords.set(0);
        this.submittedCount.set(0);
        this.completedCount.set(0);
        this.failedCount.set(0);

        ensureProductsExist();

        this.workerPool = Executors.newFixedThreadPool(workers, r -> {
            Thread t = new Thread(r, "gen-worker");
            t.setDaemon(true);
            return t;
        });
        this.inflightPermits = new Semaphore(workers * 2);

        long periodMs = Math.max(1, 1000L / request.getBatchesPerSecond());
        this.scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::executeTick, 0, periodMs, TimeUnit.MILLISECONDS);

        log.info("Mongo генератор запущен: batchSize={}, batchesPerSecond={}, workers={}",
                request.getBatchSize(), request.getBatchesPerSecond(), workers);
    }

    public synchronized void stop() {
        if (!running)
            return;
        running = false;
        if (scheduler != null)
            scheduler.shutdown();
        if (workerPool != null) {
            workerPool.shutdown();
            try {
                if (!workerPool.awaitTermination(30, TimeUnit.SECONDS))
                    workerPool.shutdownNow();
            } catch (InterruptedException e) {
                workerPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("Генератор остановлен. Всего Document(Customer) записано: {}", totalRecords.get());
    }

    public LoadStatusResponse getStatus() {
        return LoadStatusResponse.builder()
                .running(running)
                .config(currentConfig)
                .totalRecords(totalRecords.get())
                .batchesSubmitted(submittedCount.get())
                .batchesCompleted(completedCount.get())
                .batchesFailed(failedCount.get())
                .elapsedMinutes(startedAt != null
                        ? java.time.Duration.between(startedAt, Instant.now()).toMinutes()
                        : 0)
                .build();
    }

    // ═══════════════════════════════════════════
    // Внутренняя механика
    // ═══════════════════════════════════════════

    private void executeTick() {
        if (!running)
            return;

        if (currentConfig.getDurationMinutes() > 0) {
            long elapsedMin = java.time.Duration.between(startedAt, Instant.now()).toMinutes();
            if (elapsedMin >= currentConfig.getDurationMinutes()) {
                stop();
                return;
            }
        }

        if (!inflightPermits.tryAcquire()) {
            return;
        }

        submittedCount.incrementAndGet();
        batchesSubmittedCounter.increment();

        workerPool.submit(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                generateBatch(currentConfig.getBatchSize());
                completedCount.incrementAndGet();
                batchesCompletedCounter.increment();
            } catch (Exception e) {
                failedCount.incrementAndGet();
                batchesFailedCounter.increment();
                log.error("Ошибка при записи батча: {}", e.getMessage());
            } finally {
                sample.stop(batchDurationTimer);
                inflightPermits.release();
            }
        });
    }

    private void generateBatch(int batchSize) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        LocalDateTime now = LocalDateTime.now();
        List<Customer> customers = new ArrayList<>(batchSize);

        for (int i = 0; i < batchSize; i++) {
            customers.add(createRandomCustomer(rng, now));
        }

        // Mongo Bulk Insert для максимальной производительности
        mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Customer.class)
                .insert(customers)
                .execute();

        totalRecords.addAndGet(batchSize);
        recordsTotalCounter.increment(batchSize);
    }

    private Customer createRandomCustomer(ThreadLocalRandom r, LocalDateTime now) {
        String fn = pick(FIRST_NAMES, r);
        String ln = pick(LAST_NAMES, r);
        String email = fn.toLowerCase() + "." + ln.toLowerCase() + UUID.randomUUID().toString().substring(0, 8)
                + "@test.com";

        Customer customer = Customer.builder()
                .firstName(fn)
                .lastName(ln)
                .email(email)
                .phone("+7" + (9000000000L + r.nextLong(999999999L)))
                .dateOfBirth(LocalDate.of(1970 + r.nextInt(40), 1 + r.nextInt(12), 1 + r.nextInt(28)))
                .registeredAt(now)
                .status(pick(STATUSES, r))
                .loyaltyPoints(r.nextInt(10000))
                .country(pick(COUNTRIES, r))
                .build();

        // 1. Profile (Embedded)
        customer.setProfile(CustomerProfile.builder()
                .avatarUrl("https://avatar.example.com/" + r.nextInt(1000) + ".png")
                .bio("Bio info...")
                .preferredLanguage(pick(LANGUAGES, r))
                .notificationsEnabled(r.nextBoolean())
                .address("Street " + r.nextInt(200) + ", apt " + r.nextInt(100))
                .city(pick(CITIES, r))
                .zipCode(String.valueOf(100000 + r.nextInt(899999)))
                .build());

        // 2. Orders (Embedded List)
        int orderCount = 1 + r.nextInt(5);
        List<Order> orders = new ArrayList<>(orderCount);
        for (int j = 0; j < orderCount; j++) {
            orders.add(createRandomOrder(r, now));
        }
        customer.setOrders(orders);

        return customer;
    }

    private Order createRandomOrder(ThreadLocalRandom r, LocalDateTime now) {
        Order order = Order.builder()
                .orderNumber("ORD-" + UUID.randomUUID())
                .orderDate(now.minusDays(r.nextInt(365)))
                .status(pick(ORDER_STATUSES, r))
                .totalAmount(BigDecimal.valueOf(r.nextDouble(10, 10000)).setScale(2, RoundingMode.HALF_UP))
                .currency(pick(CURRENCIES, r))
                .shippingAddress(pick(CITIES, r) + ", Street " + r.nextInt(200))
                .notes(r.nextBoolean() ? "Express" : null)
                .expectedDelivery(LocalDate.now().plusDays(r.nextInt(30)))
                .build();

        // 3. Order Items (Embedded)
        int itemCount = 2 + r.nextInt(6);
        List<OrderItem> items = new ArrayList<>(itemCount);
        for (int k = 0; k < itemCount; k++) {
            items.add(createRandomOrderItem(r, now));
        }
        order.setItems(items);

        return order;
    }

    private OrderItem createRandomOrderItem(ThreadLocalRandom r, LocalDateTime now) {
        // Берем случайный продукт из кэша
        Product product = cachedProducts.get(r.nextInt(cachedProducts.size()));
        int qty = 1 + r.nextInt(10);
        BigDecimal total = product.getPrice().multiply(BigDecimal.valueOf(qty));

        return OrderItem.builder()
                .productId(product.getId())
                // SNAPSHOTTING: Копируем данные товара в момент покупки
                .productName(product.getName())
                .productSku(product.getSku())
                .productCategory(product.getCategory())
                .quantity(qty)
                .unitPrice(product.getPrice())
                .totalPrice(total)
                .discount(BigDecimal.ZERO)
                .createdAt(now)
                .build();
    }

    private void ensureProductsExist() {
        long count = productRepository.count();

        if (count == 0) {
            log.info("Продукты не найдены в БД (count=0). Генерация {} продуктов...", PRODUCT_POOL_SIZE);
            generateProducts();
            cachedProducts = productRepository.findAll();
        } else if (cachedProducts == null || cachedProducts.isEmpty()) {
            log.info("Продукты есть в БД, загружаем в кэш...");
            cachedProducts = productRepository.findAll();
        }

        if (cachedProducts != null) {
            log.info("Генератор готов. Продуктов в кэше: {}", cachedProducts.size());
        }
    }

    private void generateProducts() {
        List<Product> products = new ArrayList<>(PRODUCT_POOL_SIZE);
        LocalDateTime now = LocalDateTime.now();
        ThreadLocalRandom r = ThreadLocalRandom.current();

        for (int i = 0; i < PRODUCT_POOL_SIZE; i++) {
            String cat = pick(CATEGORIES, r);
            products.add(Product.builder()
                    .name(cat + " Item #" + i)
                    .sku("SKU-" + UUID.randomUUID().toString().substring(0, 8))
                    .description("Description for " + cat)
                    .price(BigDecimal.valueOf(r.nextDouble(0.5, 9999)).setScale(2, RoundingMode.HALF_UP))
                    .category(cat)
                    .weight(Math.round(r.nextDouble(0.01, 50.0) * 100.0) / 100.0)
                    .inStock(true)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        }
        productRepository.saveAll(products);
    }

    private void validate(LoadRequest req) {
        if (req.getBatchSize() <= 0)
            throw new IllegalArgumentException("batchSize > 0");
        if (req.getBatchesPerSecond() <= 0)
            throw new IllegalArgumentException("batchesPerSecond > 0");
        if (req.getDurationMinutes() <= 0)
            throw new IllegalArgumentException("durationMinutes > 0");
    }

    private static int estimateMaxBatchesPerSecond(int batchSize, int workerThreads) {
        double estimatedBatchMs = FIXED_OVERHEAD_MS + batchSize * MS_PER_CUSTOMER_GRAPH;
        return Math.max(1, (int) (workerThreads * (1000.0 / estimatedBatchMs)));
    }

    private static String pick(String[] arr, ThreadLocalRandom rng) {
        return arr[rng.nextInt(arr.length)];
    }
}
