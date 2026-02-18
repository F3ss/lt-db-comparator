package com.lt.dbcomparator.service;

import com.lt.dbcomparator.dto.LoadRequest;
import com.lt.dbcomparator.dto.LoadStatusResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Сервис генерации тестовых данных.
 * <p>
 * Управляется через REST: start(LoadRequest) / stop() / getStatus().
 * Использует {@link JdbcTemplate} batch-insert для максимальной скорости
 * записи.
 * Регистрирует кастомные Micrometer-метрики для мониторинга пропускной
 * способности.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataGeneratorService {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
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
    private List<Long> productIds;

    // ── Оценка пропускной способности ──
    // Каждый батч = 8 SQL round-trips (4× nextval + 4× INSERT).
    // FIXED_OVERHEAD_MS — базовая стоимость round-trips, не зависящая от размера
    // батча.
    // MS_PER_CUSTOMER_GRAPH — стоимость одного Customer-графа внутри batch INSERT
    // (1 customer + 1 profile + ~3 orders + ~13.5 items = ~17.5 строк).
    // estimatedBatchMs = FIXED_OVERHEAD_MS + batchSize × MS_PER_CUSTOMER_GRAPH
    private static final double FIXED_OVERHEAD_MS = 10.0;
    private static final double MS_PER_CUSTOMER_GRAPH = 0.2;

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
                .description("Всего записей сгенерировано (все таблицы)")
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

        // ── Определяем кол-во воркеров ──
        int workers = request.getWorkerThreads() > 0
                ? request.getWorkerThreads()
                : Math.max(2, Runtime.getRuntime().availableProcessors());
        request.setWorkerThreads(workers); // сохраняем фактическое значение

        // ── Проверка реалистичности запроса ──
        int maxRate = estimateMaxBatchesPerSecond(request.getBatchSize(), workers);
        if (request.getBatchesPerSecond() > maxRate) {
            throw new IllegalArgumentException(String.format(
                    "Запрошено %d батчей/сек, но при batchSize=%d и %d воркерах " +
                            "максимально возможная нагрузка ≈ %d батчей/сек. " +
                            "Уменьшите batchesPerSecond до %d, уменьшите batchSize, " +
                            "увеличьте workerThreads или используйте несколько реплик.",
                    request.getBatchesPerSecond(), request.getBatchSize(),
                    workers, maxRate, maxRate));
        }

        this.currentConfig = request;
        this.running = true;
        this.startedAt = Instant.now();
        this.totalRecords.set(0);
        this.submittedCount.set(0);
        this.completedCount.set(0);
        this.failedCount.set(0);

        ensureProductsExist();

        // ── Worker pool: выполняет generateBatch параллельно ──
        this.workerPool = Executors.newFixedThreadPool(workers, r -> {
            Thread t = new Thread(r, "gen-worker");
            t.setDaemon(true);
            return t;
        });
        // Семафор ограничивает кол-во одновременно выполняемых батчей (2× воркеров)
        this.inflightPermits = new Semaphore(workers * 2);

        // ── Ticker: отправляет задачи в worker pool с заданной частотой ──
        long periodMs = Math.max(1, 1000L / request.getBatchesPerSecond());
        this.scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::executeTick, 0, periodMs, TimeUnit.MILLISECONDS);

        log.info("Генератор запущен: batchSize={}, batchesPerSecond={}, workers={}, maxRate={}, duration={}min",
                request.getBatchSize(), request.getBatchesPerSecond(), workers, maxRate,
                request.getDurationMinutes());
    }

    public synchronized void stop() {
        if (!running)
            return;
        running = false;
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (workerPool != null) {
            workerPool.shutdown();
            try {
                if (!workerPool.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("Worker pool не завершился за 30с, принудительная остановка");
                    workerPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                workerPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("Генератор остановлен. Всего записей: {}", totalRecords.get());
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

        // Авто-стоп по истечении времени
        if (currentConfig.getDurationMinutes() > 0) {
            long elapsedMin = java.time.Duration.between(startedAt, Instant.now()).toMinutes();
            if (elapsedMin >= currentConfig.getDurationMinutes()) {
                stop();
                return;
            }
        }

        // Backpressure: если все воркеры заняты — пропускаем тик
        if (!inflightPermits.tryAcquire()) {
            log.warn("Worker pool перегружен, батч пропущен");
            return;
        }

        submittedCount.incrementAndGet();
        batchesSubmittedCounter.increment();

        workerPool.submit(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                transactionTemplate.executeWithoutResult(
                        status -> generateBatch(currentConfig.getBatchSize()));
                completedCount.incrementAndGet();
                batchesCompletedCounter.increment();
            } catch (Exception e) {
                failedCount.incrementAndGet();
                batchesFailedCounter.increment();
                log.error("Ошибка при записи батча: {}", e.getMessage(), e);
            } finally {
                sample.stop(batchDurationTimer);
                inflightPermits.release();
            }
        });
    }

    /**
     * Генерирует один батч: N клиентов → N профилей → ~3N заказов → ~13.5N позиций.
     * Все вставки через JdbcTemplate.batchUpdate.
     */
    private void generateBatch(int customerCount) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        LocalDateTime now = LocalDateTime.now();
        int recordCount = 0;

        // 1. Pre-allocate customer IDs
        List<Long> customerIds = jdbcTemplate.queryForList(
                "SELECT nextval(pg_get_serial_sequence('customers','id')) FROM generate_series(1,?)",
                Long.class, customerCount);

        // 2. Insert customers
        jdbcTemplate.batchUpdate(
                "INSERT INTO customers (id, first_name, last_name, email, phone, date_of_birth, " +
                        "registered_at, status, loyalty_points, country) VALUES (?,?,?,?,?,?,?,?,?,?)",
                customerIds, customerCount,
                (PreparedStatement ps, Long custId) -> {
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    String fn = pick(FIRST_NAMES, r);
                    String ln = pick(LAST_NAMES, r);
                    ps.setLong(1, custId);
                    ps.setString(2, fn);
                    ps.setString(3, ln);
                    ps.setString(4, fn.toLowerCase() + "." + ln.toLowerCase() + custId + "@test.com");
                    ps.setString(5, "+7" + (9000000000L + r.nextLong(999999999L)));
                    ps.setObject(6, LocalDate.of(1970 + r.nextInt(40), 1 + r.nextInt(12), 1 + r.nextInt(28)));
                    ps.setTimestamp(7, Timestamp.valueOf(now));
                    ps.setString(8, pick(STATUSES, r));
                    ps.setInt(9, r.nextInt(10000));
                    ps.setString(10, pick(COUNTRIES, r));
                });
        recordCount += customerCount;

        // 3. Insert profiles (1:1 с customer)
        List<Long> profileIds = jdbcTemplate.queryForList(
                "SELECT nextval(pg_get_serial_sequence('customer_profiles','id')) FROM generate_series(1,?)",
                Long.class, customerCount);

        jdbcTemplate.batchUpdate(
                "INSERT INTO customer_profiles (id, customer_id, avatar_url, bio, preferred_language, " +
                        "notifications_enabled, address, city, zip_code) VALUES (?,?,?,?,?,?,?,?,?)",
                profileIds, customerCount,
                (PreparedStatement ps, Long profId) -> {
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    int idx = profileIds.indexOf(profId);
                    ps.setLong(1, profId);
                    ps.setLong(2, customerIds.get(idx));
                    ps.setString(3, "https://avatar.example.com/" + profId + ".png");
                    ps.setString(4, "Bio for customer " + customerIds.get(idx));
                    ps.setString(5, pick(LANGUAGES, r));
                    ps.setBoolean(6, r.nextBoolean());
                    ps.setString(7, "Street " + r.nextInt(200) + ", apt " + r.nextInt(100));
                    ps.setString(8, pick(CITIES, r));
                    ps.setString(9, String.valueOf(100000 + r.nextInt(899999)));
                });
        recordCount += customerCount;

        // 4. Insert orders (1–5 per customer)
        // Сначала собираем пары (orderId, customerId)
        int totalOrders = 0;
        long[][] orderCustomerPairs = new long[customerCount * 5][2]; // max
        for (Long custId : customerIds) {
            int orderCount = 1 + rng.nextInt(5);
            for (int i = 0; i < orderCount; i++) {
                orderCustomerPairs[totalOrders][1] = custId;
                totalOrders++;
            }
        }

        if (totalOrders > 0) {
            List<Long> orderIds = jdbcTemplate.queryForList(
                    "SELECT nextval(pg_get_serial_sequence('orders','id')) FROM generate_series(1,?)",
                    Long.class, totalOrders);

            for (int i = 0; i < totalOrders; i++) {
                orderCustomerPairs[i][0] = orderIds.get(i);
            }

            final int orderCount = totalOrders;
            jdbcTemplate.batchUpdate(
                    "INSERT INTO orders (id, customer_id, order_number, order_date, status, " +
                            "total_amount, currency, shipping_address, notes, expected_delivery) " +
                            "VALUES (?,?,?,?,?,?,?,?,?,?)",
                    orderIds, orderCount,
                    (PreparedStatement ps, Long ordId) -> {
                        ThreadLocalRandom r = ThreadLocalRandom.current();
                        int idx = orderIds.indexOf(ordId);
                        ps.setLong(1, ordId);
                        ps.setLong(2, orderCustomerPairs[idx][1]);
                        ps.setString(3, "ORD-" + ordId);
                        ps.setTimestamp(4, Timestamp.valueOf(now.minusDays(r.nextInt(365))));
                        ps.setString(5, pick(ORDER_STATUSES, r));
                        ps.setBigDecimal(6,
                                BigDecimal.valueOf(r.nextDouble(10, 10000)).setScale(2, RoundingMode.HALF_UP));
                        ps.setString(7, pick(CURRENCIES, r));
                        ps.setString(8, pick(CITIES, r) + ", Street " + r.nextInt(200));
                        ps.setString(9, r.nextBoolean() ? "Express delivery" : null);
                        ps.setObject(10, LocalDate.now().plusDays(r.nextInt(30)));
                    });
            recordCount += orderCount;

            // 5. Insert order items (2–7 per order)
            int totalItems = 0;
            long[][] itemMeta = new long[orderCount * 7][2]; // [orderId, productId]
            for (int i = 0; i < orderCount; i++) {
                int items = 2 + rng.nextInt(6);
                for (int j = 0; j < items; j++) {
                    itemMeta[totalItems][0] = orderIds.get(i);
                    itemMeta[totalItems][1] = productIds.get(rng.nextInt(productIds.size()));
                    totalItems++;
                }
            }

            if (totalItems > 0) {
                List<Long> itemIds = jdbcTemplate.queryForList(
                        "SELECT nextval(pg_get_serial_sequence('order_items','id')) FROM generate_series(1,?)",
                        Long.class, totalItems);

                final int itemCount = totalItems;
                jdbcTemplate.batchUpdate(
                        "INSERT INTO order_items (id, order_id, product_id, quantity, " +
                                "unit_price, total_price, discount, created_at) VALUES (?,?,?,?,?,?,?,?)",
                        itemIds, itemCount,
                        (PreparedStatement ps, Long itemId) -> {
                            ThreadLocalRandom r = ThreadLocalRandom.current();
                            int idx = itemIds.indexOf(itemId);
                            int qty = 1 + r.nextInt(10);
                            BigDecimal unitPr = BigDecimal.valueOf(r.nextDouble(1, 500)).setScale(2,
                                    RoundingMode.HALF_UP);
                            BigDecimal disc = BigDecimal.valueOf(r.nextDouble(0, 50)).setScale(2, RoundingMode.HALF_UP);
                            ps.setLong(1, itemId);
                            ps.setLong(2, itemMeta[idx][0]);
                            ps.setLong(3, itemMeta[idx][1]);
                            ps.setInt(4, qty);
                            ps.setBigDecimal(5, unitPr);
                            ps.setBigDecimal(6, unitPr.multiply(BigDecimal.valueOf(qty)));
                            ps.setBigDecimal(7, disc);
                            ps.setTimestamp(8, Timestamp.valueOf(now));
                        });
                recordCount += itemCount;
            }
        }

        totalRecords.addAndGet(recordCount);
        recordsTotalCounter.increment(recordCount);
    }

    // ═══════════════════════════════════════════
    // Утилиты
    // ═══════════════════════════════════════════

    /**
     * Оценивает максимально возможное кол-во батчей/сек для данного инстанса.
     * <p>
     * Формула: estimatedBatchMs = FIXED_OVERHEAD_MS + batchSize ×
     * MS_PER_CUSTOMER_GRAPH
     * <br>
     * maxRate = workerThreads × (1000 / estimatedBatchMs)
     * <p>
     * Примеры (4 воркера):
     * <ul>
     * <li>batchSize=50 → ~20ms/batch → max ~200 batch/sec</li>
     * <li>batchSize=100 → ~30ms/batch → max ~133 batch/sec</li>
     * <li>batchSize=500 → ~110ms/batch → max ~36 batch/sec</li>
     * </ul>
     */
    static int estimateMaxBatchesPerSecond(int batchSize, int workerThreads) {
        double estimatedBatchMs = FIXED_OVERHEAD_MS + batchSize * MS_PER_CUSTOMER_GRAPH;
        return Math.max(1, (int) (workerThreads * (1000.0 / estimatedBatchMs)));
    }

    /**
     * Инициализация справочника продуктов.
     * pg_advisory_xact_lock гарантирует, что при одновременном старте нескольких
     * реплик только одна выполнит INSERT; остальные подождут и увидят данные.
     */
    private void ensureProductsExist() {
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SELECT pg_advisory_xact_lock(1000042)");
            Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM products", Long.class);
            if (count == null || count == 0) {
                log.info("Предзаполнение {} продуктов...", PRODUCT_POOL_SIZE);
                generateProducts();
            }
        }); // lock автоматически освобождается при commit
        productIds = jdbcTemplate.queryForList("SELECT id FROM products", Long.class);
        log.info("Пул продуктов: {} шт.", productIds.size());
    }

    private void generateProducts() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        LocalDateTime now = LocalDateTime.now();

        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT nextval(pg_get_serial_sequence('products','id')) FROM generate_series(1,?)",
                Long.class, PRODUCT_POOL_SIZE);

        jdbcTemplate.batchUpdate(
                "INSERT INTO products (id, name, sku, description, price, category, " +
                        "weight, in_stock, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                ids, PRODUCT_POOL_SIZE,
                (PreparedStatement ps, Long prodId) -> {
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    String cat = pick(CATEGORIES, r);
                    ps.setLong(1, prodId);
                    ps.setString(2, cat + " Item #" + prodId);
                    ps.setString(3, "SKU-" + String.format("%06d", prodId));
                    ps.setString(4, "Description for " + cat + " product #" + prodId);
                    ps.setBigDecimal(5, BigDecimal.valueOf(r.nextDouble(0.5, 9999)).setScale(2, RoundingMode.HALF_UP));
                    ps.setString(6, cat);
                    ps.setDouble(7, Math.round(r.nextDouble(0.01, 50.0) * 100.0) / 100.0);
                    ps.setBoolean(8, r.nextBoolean());
                    ps.setTimestamp(9, Timestamp.valueOf(now));
                    ps.setTimestamp(10, Timestamp.valueOf(now));
                });
    }

    private void validate(LoadRequest req) {
        if (req.getBatchSize() <= 0)
            throw new IllegalArgumentException("batchSize должен быть > 0");
        if (req.getBatchesPerSecond() <= 0)
            throw new IllegalArgumentException("batchesPerSecond должен быть > 0");
        if (req.getDurationMinutes() <= 0)
            throw new IllegalArgumentException("durationMinutes должен быть > 0");
        if (req.getWorkerThreads() < 0)
            throw new IllegalArgumentException("workerThreads должен быть >= 0 (0 = авто)");
    }

    private static String pick(String[] arr, ThreadLocalRandom rng) {
        return arr[rng.nextInt(arr.length)];
    }
}
