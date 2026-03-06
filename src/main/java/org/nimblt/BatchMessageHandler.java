package org.nimblt;

import org.apache.kafka.clients.consumer.ConsumerRecords;

/**
 * Callback-интерфейс для обработки батча записей, полученных из Kafka.
 * <p>
 * Реализуйте этот интерфейс и передайте в
 * {@link EqKafkaService#consume(BatchMessageHandler)}.
 * Метод {@code handleBatch} вызывается для каждого результата {@code poll()},
 * т.е. получает сразу все записи батча — итерация на стороне пользователя.
 */
@FunctionalInterface
public interface BatchMessageHandler {

    /**
     * Обработать батч записей из Kafka.
     *
     * @param records записи, полученные одним вызовом {@code poll()}
     */
    void handleBatch(ConsumerRecords<String, String> records);
}
