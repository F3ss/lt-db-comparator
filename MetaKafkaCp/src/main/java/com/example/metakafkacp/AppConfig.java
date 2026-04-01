package com.example.metakafkacp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

final class AppConfig {
    private final String sourceTopic;
    private final String targetTopic;
    private final boolean preservePartition;
    private final long pollTimeoutMs;
    private final long idleTimeoutMs;
    private final Properties consumerProperties;
    private final Properties producerProperties;

    private AppConfig(
            String sourceTopic,
            String targetTopic,
            boolean preservePartition,
            long pollTimeoutMs,
            long idleTimeoutMs,
            Properties consumerProperties,
            Properties producerProperties) {
        this.sourceTopic = sourceTopic;
        this.targetTopic = targetTopic;
        this.preservePartition = preservePartition;
        this.pollTimeoutMs = pollTimeoutMs;
        this.idleTimeoutMs = idleTimeoutMs;
        this.consumerProperties = consumerProperties;
        this.producerProperties = producerProperties;
    }

    static AppConfig load(Path path) throws IOException {
        Properties fileProperties = new Properties();
        try (InputStream inputStream = Files.newInputStream(path)) {
            fileProperties.load(inputStream);
        }

        String sourceTopic = required(fileProperties, "app.source.topic");
        String targetTopic = required(fileProperties, "app.target.topic");
        boolean preservePartition = Boolean.parseBoolean(
                fileProperties.getProperty("app.copy.preservePartition", "false"));
        long pollTimeoutMs = Long.parseLong(fileProperties.getProperty("app.copy.pollTimeoutMs", "1000"));
        long idleTimeoutMs = Long.parseLong(fileProperties.getProperty("app.copy.idleTimeoutMs", "5000"));

        Properties consumerProperties = new Properties();
        consumerProperties.putAll(extractWithPrefix(fileProperties, "app.source.consumer."));
        consumerProperties.putIfAbsent("bootstrap.servers", required(fileProperties, "app.source.bootstrapServers"));
        consumerProperties.putIfAbsent("group.id", fileProperties.getProperty("app.source.groupId", "meta-kafka-cp"));
        consumerProperties.putIfAbsent("client.id", fileProperties.getProperty("app.source.clientId", "meta-kafka-cp-consumer"));
        consumerProperties.put("enable.auto.commit", "false");
        consumerProperties.putIfAbsent("auto.offset.reset", "earliest");
        consumerProperties.putIfAbsent("key.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        consumerProperties.putIfAbsent("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");

        Properties producerProperties = new Properties();
        producerProperties.putAll(extractWithPrefix(fileProperties, "app.target.producer."));
        producerProperties.putIfAbsent("bootstrap.servers", required(fileProperties, "app.target.bootstrapServers"));
        producerProperties.putIfAbsent("client.id", fileProperties.getProperty("app.target.clientId", "meta-kafka-cp-producer"));
        producerProperties.putIfAbsent("acks", "all");
        producerProperties.putIfAbsent("key.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
        producerProperties.putIfAbsent("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");

        return new AppConfig(
                sourceTopic,
                targetTopic,
                preservePartition,
                pollTimeoutMs,
                idleTimeoutMs,
                consumerProperties,
                producerProperties);
    }

    String sourceTopic() {
        return sourceTopic;
    }

    String targetTopic() {
        return targetTopic;
    }

    boolean preservePartition() {
        return preservePartition;
    }

    long pollTimeoutMs() {
        return pollTimeoutMs;
    }

    long idleTimeoutMs() {
        return idleTimeoutMs;
    }

    Properties consumerProperties() {
        return consumerProperties;
    }

    Properties producerProperties() {
        return producerProperties;
    }

    private static Map<String, String> extractWithPrefix(Properties properties, String prefix) {
        Map<String, String> extracted = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith(prefix)) {
                extracted.put(name.substring(prefix.length()), properties.getProperty(name));
            }
        }
        return extracted;
    }

    private static String required(Properties properties, String key) {
        return Objects.requireNonNull(properties.getProperty(key), () -> "Missing required property: " + key);
    }
}
