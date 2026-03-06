package org.nimblt.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.Future;

/**
 * Обёртка над {@link KafkaProducer} для батчевой отправки сообщений.
 * <p>
 * Все записи из батча отправляются асинхронно, после чего вызывается
 * {@code flush()},
 * что гарантирует доставку всего батча до возврата из метода
 * {@code sendBatch()}.
 */
public class ProducerService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ProducerService.class);

    private final KafkaProducer<String, byte[]> producer;

    public ProducerService(Properties properties) {
        this.producer = new KafkaProducer<>(properties);
        log.info("KafkaProducer создан. bootstrap.servers={}",
                properties.getProperty("bootstrap.servers"));
    }

    /**
     * Отправить батч записей в Kafka.
     * <p>
     * Каждая запись отправляется асинхронно с callback-логированием ошибок.
     * В конце вызывается {@code flush()} — метод блокируется до подтверждения
     * отправки всех записей батча.
     *
     * @param records список ProducerRecord для отправки
     */
    public void sendBatch(List<ProducerRecord<String, byte[]>> records) {
        log.debug("Отправка батча: {} записей", records.size());

        for (ProducerRecord<String, byte[]> record : records) {
            producer.send(record, (RecordMetadata metadata, Exception ex) -> {
                if (ex != null) {
                    log.error("Ошибка отправки в топик '{}', key='{}'",
                            record.topic(), record.key(), ex);
                } else {
                    log.trace("Отправлено: topic={}, partition={}, offset={}",
                            metadata.topic(), metadata.partition(), metadata.offset());
                }
            });
        }

        // flush гарантирует, что все записи батча ушли в Kafka
        producer.flush();
        log.debug("Батч из {} записей отправлен и подтверждён", records.size());
    }

    @Override
    public void close() {
        log.info("Закрытие KafkaProducer...");
        producer.close();
    }
}
