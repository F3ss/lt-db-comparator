package org.nimblt.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.nimblt.BatchMessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consumer-поток: читает из Kafka-топика батчами и передаёт
 * каждый батч в {@link BatchMessageHandler}.
 * <p>
 * Каждый экземпляр создаёт собственный {@link KafkaConsumer}
 * (паттерн thread-per-consumer — KafkaConsumer не потокобезопасен).
 * <p>
 * После обработки каждого батча выполняется {@code commitSync()} —
 * оффсеты коммитятся только после успешной обработки (at-least-once гарантия).
 */
public class ConsumerWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ConsumerWorker.class);
    private static final Duration POLL_TIMEOUT = Duration.ofMillis(1000);

    private final int workerId;
    private final String topic;
    private final int maxBatches;
    private final Properties consumerProps;
    private final BatchMessageHandler handler;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private KafkaConsumer<String, String> consumer;

    public ConsumerWorker(int workerId,
            String topic,
            int maxBatches,
            Properties consumerProps,
            BatchMessageHandler handler) {
        this.workerId = workerId;
        this.topic = topic;
        this.maxBatches = maxBatches;
        this.consumerProps = consumerProps;
        this.handler = handler;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("consumer-worker-" + workerId);
        log.info("[Worker-{}] Запуск. Топик='{}', maxBatches={}", workerId, topic, maxBatches);

        consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(Collections.singletonList(topic));

        int batchCount = 0;

        try {
            while (running.get() && batchCount < maxBatches) {
                ConsumerRecords<String, String> records = consumer.poll(POLL_TIMEOUT);

                if (records.isEmpty()) {
                    continue;
                }

                log.debug("[Worker-{}] Получен батч #{}: {} записей",
                        workerId, batchCount + 1, records.count());

                // Передаём весь батч в обработчик; коммит — только после успешной обработки
                handler.handleBatch(records);
                consumer.commitSync();

                batchCount++;
            }

            log.info("[Worker-{}] Завершён. Обработано батчей: {}", workerId, batchCount);

        } catch (WakeupException e) {
            // wakeup() вызывается из shutdown() для корректной остановки
            if (running.get()) {
                throw e; // неожиданный wakeup — пробрасываем
            }
            log.info("[Worker-{}] Остановлен по wakeup. Обработано батчей: {}",
                    workerId, batchCount);
        } catch (Exception e) {
            log.error("[Worker-{}] Ошибка при обработке", workerId, e);
        } finally {
            consumer.close();
            log.debug("[Worker-{}] KafkaConsumer закрыт", workerId);
        }
    }

    /**
     * Корректная остановка consumer-потока.
     * <p>
     * Метод потокобезопасен: {@code wakeup()} — единственный метод KafkaConsumer,
     * безопасный для вызова из другого потока. Он заставляет текущий/следующий
     * {@code poll()} выбросить {@link WakeupException}.
     */
    public void shutdown() {
        log.info("[Worker-{}] Запрошена остановка", workerId);
        running.set(false);
        if (consumer != null) {
            consumer.wakeup();
        }
    }
}
