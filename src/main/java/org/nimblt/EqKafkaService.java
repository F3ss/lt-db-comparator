package org.nimblt;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.nimblt.config.KafkaConfig;
import org.nimblt.kafka.ConsumerWorker;
import org.nimblt.kafka.ProducerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Фасад библиотеки eq-kafka-sender.
 * <p>
 * Предоставляет два основных метода:
 * <ul>
 * <li>{@link #consume(BatchMessageHandler)} — чтение из Kafka батчами</li>
 * <li>{@link #produce(List)} — отправка батча записей в Kafka</li>
 * </ul>
 * <p>
 * Реализует {@link AutoCloseable} для корректного освобождения ресурсов.
 */
public class EqKafkaService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EqKafkaService.class);

    private final KafkaConfig config;
    private final ProducerService producerService;
    private final List<ConsumerWorker> activeWorkers = new ArrayList<>();

    public EqKafkaService(KafkaConfig config) {
        this.config = config;
        this.producerService = new ProducerService(config.buildProducerProperties());
        log.info("EqKafkaService инициализирован. " +
                "Чтение из '{}', отправка в '{}', потоков: {}, батчей/поток: {}",
                config.getMetaTopicName(), config.getTopicName(),
                config.getThreadCount(), config.getBatchesPerThread());
    }

    /**
     * Запустить чтение из Kafka-топика {@code metaTopicName}.
     * <p>
     * Создаёт {@code threadCount} consumer-потоков (thread-per-consumer).
     * Каждый поток выполняет до {@code batchesPerThread} poll-циклов,
     * передавая каждый батч целиком в {@code handler.handleBatch()}.
     * <p>
     * Метод <b>блокируется</b> до завершения всех потоков.
     *
     * @param handler callback для обработки каждого батча
     */
    public void consume(BatchMessageHandler handler) {
        int threadCount = config.getThreadCount();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        log.info("Запуск {} consumer-потоков для топика '{}'",
                threadCount, config.getMetaTopicName());

        for (int i = 0; i < threadCount; i++) {
            ConsumerWorker worker = new ConsumerWorker(
                    i,
                    config.getMetaTopicName(),
                    config.getBatchesPerThread(),
                    config.buildConsumerProperties(),
                    handler);
            activeWorkers.add(worker);
            executor.submit(worker);
        }

        executor.shutdown();

        try {
            // Ожидаем завершения всех consumer-потоков (без жёсткого таймаута)
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Ожидание consumer-потоков прервано");
        }

        log.info("Все consumer-потоки завершены");
    }

    /**
     * Отправить батч записей в Kafka-топик {@code topicName}.
     * <p>
     * Записи отправляются асинхронно, затем вызывается {@code flush()} —
     * метод вернёт управление только после подтверждения отправки всего батча.
     *
     * @param records список записей для отправки
     */
    public void produce(List<ProducerRecord<String, byte[]>> records) {
        producerService.sendBatch(records);
    }

    /**
     * Корректно остановить все consumer-потоки и закрыть producer.
     */
    @Override
    public void close() {
        log.info("Закрытие EqKafkaService...");

        // Остановка всех активных consumer-потоков через wakeup()
        for (ConsumerWorker worker : activeWorkers) {
            worker.shutdown();
        }
        activeWorkers.clear();

        producerService.close();
        log.info("EqKafkaService закрыт");
    }
}
