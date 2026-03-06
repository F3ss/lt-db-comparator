package org.nimblt.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Простой consumer для чтения мета-данных из Kafka-топика в локальную Map.
 * <p>
 * При создании вычитывает все записи из топика с самого начала.
 * Метод {@link #refreshMeta()} перечитывает топик и обновляет Map.
 */
public class MetaConsumer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MetaConsumer.class);
    private static final Duration POLL_TIMEOUT = Duration.ofMillis(1000);

    private final KafkaConsumer<String, String> consumer;
    private final String topic;
    private final Map<String, String> metaMap = new ConcurrentHashMap<>();

    public MetaConsumer(Properties consumerProps, String topic) {
        this.topic = topic;
        this.consumer = new KafkaConsumer<>(consumerProps);
        log.info("MetaConsumer создан для топика '{}'", topic);
        readAll();
    }

    /**
     * Перечитать топик с начала и обновить локальную Map.
     */
    public void refreshMeta() {
        log.info("Обновление мета-данных из топика '{}'...", topic);
        readAll();
    }

    /**
     * Получить копию текущей Map с мета-данными.
     */
    public Map<String, String> getMeta() {
        return Collections.unmodifiableMap(metaMap);
    }

    @Override
    public void close() {
        log.info("Закрытие MetaConsumer...");
        consumer.close();
    }

    // -------- private --------

    private void readAll() {
        // Получаем все партиции топика и назначаем их вручную (без group.id)
        List<TopicPartition> partitions = consumer.partitionsFor(topic)
                .stream()
                .map(info -> new TopicPartition(info.topic(), info.partition()))
                .collect(Collectors.toList());

        consumer.assign(partitions);
        consumer.seekToBeginning(partitions);

        Map<String, String> freshMap = new ConcurrentHashMap<>();
        int totalRecords = 0;

        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(POLL_TIMEOUT);
            if (records.isEmpty()) {
                break;
            }
            for (ConsumerRecord<String, String> record : records) {
                freshMap.put(record.key(), record.value());
            }
            totalRecords += records.count();
        }

        metaMap.clear();
        metaMap.putAll(freshMap);
        log.info("Загружено {} записей из топика '{}', уникальных ключей: {}",
                totalRecords, topic, metaMap.size());
    }
}
