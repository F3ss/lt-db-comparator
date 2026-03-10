package com.example.kafkalib;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

final class KafkaMetadataLoader implements MetadataLoader {

    private final KafkaLibraryConfig config;

    KafkaMetadataLoader(KafkaLibraryConfig config) {
        this.config = config;
    }

    @Override
    public Map<String, String> load() {
        Map<String, String> loadedMetadata = new HashMap<>();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties())) {
            consumer.subscribe(Collections.singletonList(config.getMetadataTopic()));
            waitForAssignment(consumer);

            Set<?> assignment = consumer.assignment();
            if (assignment.isEmpty()) {
                return loadedMetadata;
            }

            consumer.seekToBeginning(consumer.assignment());
            int emptyPolls = 0;

            while (emptyPolls < config.getMetadataEmptyPolls()) {
                ConsumerRecords<String, String> records =
                        consumer.poll(Duration.ofMillis(config.getMetadataPollMs()));

                if (records.isEmpty()) {
                    emptyPolls++;
                    continue;
                }

                emptyPolls = 0;
                for (ConsumerRecord<String, String> record : records) {
                    if (record.key() != null) {
                        loadedMetadata.put(record.key(), record.value());
                    }
                }
            }
        }

        return loadedMetadata;
    }

    private void waitForAssignment(KafkaConsumer<String, String> consumer) {
        int attempts = 0;
        while (consumer.assignment().isEmpty() && attempts < 10) {
            consumer.poll(Duration.ofMillis(config.getMetadataPollMs()));
            attempts++;
        }
    }

    private Properties consumerProperties() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, config.getMetadataGroupId());
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return properties;
    }
}
