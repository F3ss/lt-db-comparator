package com.example.kafkalib;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaLibraryConfigTest {

    @Test
    void shouldApplyRequiredDefaults() {
        KafkaLibraryConfig config = KafkaLibraryConfig.builder().build();

        assertEquals("localhost:9092", config.getBootstrapServers());
        assertEquals("meta", config.getMetadataTopic());
        assertEquals("journal", config.getJournalTopic());
    }

    @Test
    void shouldReadProperties() {
        Properties properties = new Properties();
        properties.setProperty("kafka.bootstrap.servers", "localhost:19092");
        properties.setProperty("kafka.metadata.topic", "meta-topic");
        properties.setProperty("kafka.journal.topic", "journal-topic");
        properties.setProperty("kafka.send.batch-size", "50");

        KafkaLibraryConfig config = KafkaLibraryConfig.fromProperties(properties);

        assertEquals("localhost:19092", config.getBootstrapServers());
        assertEquals("meta-topic", config.getMetadataTopic());
        assertEquals("journal-topic", config.getJournalTopic());
        assertEquals(50, config.getSendBatchSize());
    }
}
