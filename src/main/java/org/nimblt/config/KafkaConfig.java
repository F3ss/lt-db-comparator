package org.nimblt.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * Модель конфигурации приложения, десериализуемая из JSON.
 * <p>
 * Содержит имена топиков, параметры многопоточности и
 * общие настройки Kafka (bootstrap.servers, serializers и т.д.).
 */
public class KafkaConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonProperty("metaTopicName")
    private String metaTopicName;

    @JsonProperty("topicName")
    private String topicName;

    @JsonProperty("batchesPerThread")
    private int batchesPerThread;

    @JsonProperty("threadCount")
    private int threadCount;

    @JsonProperty("kafkaSettings")
    private Map<String, String> kafkaSettings;

    // --- Фабричные методы ---

    /**
     * Загрузить конфигурацию из файла на диске.
     */
    public static KafkaConfig fromFile(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            return fromInputStream(is);
        }
    }

    /**
     * Загрузить конфигурацию из classpath-ресурса.
     */
    public static KafkaConfig fromResource(String resourceName) throws IOException {
        try (InputStream is = KafkaConfig.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) {
                throw new IOException("Ресурс не найден: " + resourceName);
            }
            return fromInputStream(is);
        }
    }

    public static KafkaConfig fromInputStream(InputStream is) throws IOException {
        return MAPPER.readValue(is, KafkaConfig.class);
    }

    // --- Сборка Properties для Consumer и Producer ---

    /**
     * Собирает Properties для KafkaConsumer на основе kafkaSettings.
     * Используются только параметры, относящиеся к consumer:
     * десериализаторы, group.id, auto.commit и т.д.
     */
    public Properties buildConsumerProperties() {
        Properties props = new Properties();
        kafkaSettings.forEach((key, value) -> {
            // Пропускаем serializer/compression — они нужны только producer'у
            if (!key.startsWith("key.serializer")
                    && !key.startsWith("value.serializer")
                    && !key.equals("compression.type")
                    && !key.equals("max.request.size")) {
                props.put(key, value);
            }
        });
        return props;
    }

    /**
     * Собирает Properties для KafkaProducer на основе kafkaSettings.
     * Используются только параметры, относящиеся к producer:
     * сериализаторы, compression, max.request.size и т.д.
     */
    public Properties buildProducerProperties() {
        Properties props = new Properties();
        kafkaSettings.forEach((key, value) -> {
            // Пропускаем deserializer/consumer-only параметры
            if (!key.startsWith("key.deserializer")
                    && !key.startsWith("value.deserializer")
                    && !key.equals("group.id")
                    && !key.equals("enable.auto.commit")
                    && !key.equals("auto.commit.interval.ms")
                    && !key.equals("session.timeout.ms")) {
                props.put(key, value);
            }
        });
        return props;
    }

    // --- Геттеры ---

    public String getMetaTopicName() {
        return metaTopicName;
    }

    public String getTopicName() {
        return topicName;
    }

    public int getBatchesPerThread() {
        return batchesPerThread;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public Map<String, String> getKafkaSettings() {
        return kafkaSettings;
    }
}
