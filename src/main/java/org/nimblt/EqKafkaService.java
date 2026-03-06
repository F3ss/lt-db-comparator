package org.nimblt;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.nimblt.config.KafkaConfig;
import org.nimblt.kafka.MetaConsumer;
import org.nimblt.kafka.ProducerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Фасад библиотеки eq-kafka-sender.
 * <p>
 * Предоставляет методы:
 * <ul>
 * <li>{@link #getMeta()} — получить текущую мета-информацию</li>
 * <li>{@link #refreshMeta()} — перечитать мета-топик и обновить данные</li>
 * <li>{@link #produce(List)} — отправка батча записей в Kafka</li>
 * </ul>
 * <p>
 * При создании автоматически вычитывает все записи из мета-топика.
 * Реализует {@link AutoCloseable} для корректного освобождения ресурсов.
 */
public class EqKafkaService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EqKafkaService.class);

    private final ProducerService producerService;
    private final MetaConsumer metaConsumer;

    public EqKafkaService(KafkaConfig config) {
        this.producerService = new ProducerService(config.buildProducerProperties());
        this.metaConsumer = new MetaConsumer(
                config.buildConsumerProperties(), config.getMetaTopicName());
        log.info("EqKafkaService инициализирован. Мета из '{}', отправка в '{}'",
                config.getMetaTopicName(), config.getTopicName());
    }

    /**
     * Получить текущую Map с мета-данными (неизменяемая копия).
     */
    public Map<String, String> getMeta() {
        return metaConsumer.getMeta();
    }

    /**
     * Перечитать мета-топик и обновить локальные данные.
     */
    public void refreshMeta() {
        metaConsumer.refreshMeta();
    }

    /**
     * Отправить батч записей в Kafka-топик.
     *
     * @param records список записей для отправки
     */
    public void produce(List<ProducerRecord<String, byte[]>> records) {
        producerService.sendBatch(records);
    }

    @Override
    public void close() {
        log.info("Закрытие EqKafkaService...");
        metaConsumer.close();
        producerService.close();
        log.info("EqKafkaService закрыт");
    }
}
