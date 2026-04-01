package com.example.metakafkacp;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class KafkaReplicator {
    private static final Logger log = LoggerFactory.getLogger(KafkaReplicator.class);

    long replicate(AppConfig config) {
        Properties consumerProperties = config.consumerProperties();
        Properties producerProperties = config.producerProperties();

        try (KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(consumerProperties);
                KafkaProducer<byte[], byte[]> producer = new KafkaProducer<>(producerProperties)) {

            List<TopicPartition> partitions = loadPartitions(consumer, config.sourceTopic());
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);

            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);
            log.info("Source topic {} partitions: {}", config.sourceTopic(), partitions.size());
            log.info("Target topic: {}", config.targetTopic());

            long copiedRecords = 0L;
            long lastProgressAt = System.currentTimeMillis();

            while (true) {
                ConsumerRecords<byte[], byte[]> records = consumer.poll(Duration.ofMillis(config.pollTimeoutMs()));
                if (!records.isEmpty()) {
                    for (ConsumerRecord<byte[], byte[]> record : records) {
                        ProducerRecord<byte[], byte[]> producerRecord = toProducerRecord(config, record);
                        producer.send(producerRecord).get();
                        copiedRecords++;
                    }
                    producer.flush();
                    lastProgressAt = System.currentTimeMillis();
                    log.info("Copied {} records so far", copiedRecords);
                }

                if (reachedEnd(consumer, endOffsets) && System.currentTimeMillis() - lastProgressAt >= config.idleTimeoutMs()) {
                    producer.flush();
                    return copiedRecords;
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Kafka replication failed", exception);
        }
    }

    private static List<TopicPartition> loadPartitions(KafkaConsumer<byte[], byte[]> consumer, String topic) {
        var partitionsInfo = consumer.partitionsFor(topic);
        if (partitionsInfo == null || partitionsInfo.isEmpty()) {
            throw new IllegalStateException("Topic has no partitions or does not exist: " + topic);
        }

        List<TopicPartition> partitions = new ArrayList<>(partitionsInfo.size());
        for (var partitionInfo : partitionsInfo) {
            partitions.add(new TopicPartition(topic, partitionInfo.partition()));
        }
        return partitions;
    }

    private static boolean reachedEnd(KafkaConsumer<byte[], byte[]> consumer, Map<TopicPartition, Long> endOffsets) {
        for (var entry : endOffsets.entrySet()) {
            if (consumer.position(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static ProducerRecord<byte[], byte[]> toProducerRecord(AppConfig config, ConsumerRecord<byte[], byte[]> record) {
        Integer partition = config.preservePartition() ? record.partition() : null;
        ProducerRecord<byte[], byte[]> producerRecord =
                new ProducerRecord<>(config.targetTopic(), partition, record.timestamp(), record.key(), record.value(), record.headers());
        return producerRecord;
    }
}
