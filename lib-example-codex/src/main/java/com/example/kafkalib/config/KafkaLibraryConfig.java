package com.example.kafkalib.config;

import java.util.Objects;
import java.util.Properties;

/**
 * Immutable configuration for the library.
 */
public final class KafkaLibraryConfig {

    private static final String DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String DEFAULT_METADATA_TOPIC = "meta";
    private static final String DEFAULT_JOURNAL_TOPIC = "journal";
    private static final String DEFAULT_METADATA_GROUP_ID = "kafka-meta-journal-lib-metadata-loader";
    private static final int DEFAULT_METADATA_POLL_MS = 200;
    private static final int DEFAULT_METADATA_EMPTY_POLLS = 3;
    private static final int DEFAULT_SEND_BATCH_SIZE = 1_000;
    private static final int DEFAULT_PRODUCER_LINGER_MS = 20;
    private static final int DEFAULT_PRODUCER_BATCH_BYTES = 65_536;
    private static final String DEFAULT_PRODUCER_COMPRESSION_TYPE = "lz4";

    private final String bootstrapServers;
    private final String metadataTopic;
    private final String journalTopic;
    private final String metadataGroupId;
    private final int metadataPollMs;
    private final int metadataEmptyPolls;
    private final int sendBatchSize;
    private final int producerLingerMs;
    private final int producerBatchBytes;
    private final String producerCompressionType;

    private KafkaLibraryConfig(Builder builder) {
        this.bootstrapServers = requireText(builder.bootstrapServers, "bootstrapServers");
        this.metadataTopic = requireText(builder.metadataTopic, "metadataTopic");
        this.journalTopic = requireText(builder.journalTopic, "journalTopic");
        this.metadataGroupId = requireText(builder.metadataGroupId, "metadataGroupId");
        this.metadataPollMs = positive(builder.metadataPollMs, "metadataPollMs");
        this.metadataEmptyPolls = positive(builder.metadataEmptyPolls, "metadataEmptyPolls");
        this.sendBatchSize = positive(builder.sendBatchSize, "sendBatchSize");
        this.producerLingerMs = nonNegative(builder.producerLingerMs, "producerLingerMs");
        this.producerBatchBytes = positive(builder.producerBatchBytes, "producerBatchBytes");
        this.producerCompressionType = requireText(builder.producerCompressionType, "producerCompressionType");
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates config from properties with defaults.
     */
    public static KafkaLibraryConfig fromProperties(Properties properties) {
        Objects.requireNonNull(properties, "properties must not be null");

        return KafkaLibraryConfig.builder()
                .bootstrapServers(properties.getProperty("kafka.bootstrap.servers", DEFAULT_BOOTSTRAP_SERVERS))
                .metadataTopic(properties.getProperty("kafka.metadata.topic", DEFAULT_METADATA_TOPIC))
                .journalTopic(properties.getProperty("kafka.journal.topic", DEFAULT_JOURNAL_TOPIC))
                .metadataGroupId(properties.getProperty("kafka.metadata.group-id", DEFAULT_METADATA_GROUP_ID))
                .metadataPollMs(parseInt(properties, "kafka.metadata.poll-ms", DEFAULT_METADATA_POLL_MS))
                .metadataEmptyPolls(parseInt(properties, "kafka.metadata.empty-polls", DEFAULT_METADATA_EMPTY_POLLS))
                .sendBatchSize(parseInt(properties, "kafka.send.batch-size", DEFAULT_SEND_BATCH_SIZE))
                .producerLingerMs(parseInt(properties, "kafka.producer.linger-ms", DEFAULT_PRODUCER_LINGER_MS))
                .producerBatchBytes(parseInt(properties, "kafka.producer.batch-bytes", DEFAULT_PRODUCER_BATCH_BYTES))
                .producerCompressionType(properties.getProperty(
                        "kafka.producer.compression-type",
                        DEFAULT_PRODUCER_COMPRESSION_TYPE
                ))
                .build();
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public String getMetadataTopic() {
        return metadataTopic;
    }

    public String getJournalTopic() {
        return journalTopic;
    }

    public String getMetadataGroupId() {
        return metadataGroupId;
    }

    public int getMetadataPollMs() {
        return metadataPollMs;
    }

    public int getMetadataEmptyPolls() {
        return metadataEmptyPolls;
    }

    public int getSendBatchSize() {
        return sendBatchSize;
    }

    public int getProducerLingerMs() {
        return producerLingerMs;
    }

    public int getProducerBatchBytes() {
        return producerBatchBytes;
    }

    public String getProducerCompressionType() {
        return producerCompressionType;
    }

    private static int parseInt(Properties properties, String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid integer for key '" + key + "': " + value, ex);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static int positive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be > 0");
        }
        return value;
    }

    private static int nonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0");
        }
        return value;
    }

    public static final class Builder {
        private String bootstrapServers = DEFAULT_BOOTSTRAP_SERVERS;
        private String metadataTopic = DEFAULT_METADATA_TOPIC;
        private String journalTopic = DEFAULT_JOURNAL_TOPIC;
        private String metadataGroupId = DEFAULT_METADATA_GROUP_ID;
        private int metadataPollMs = DEFAULT_METADATA_POLL_MS;
        private int metadataEmptyPolls = DEFAULT_METADATA_EMPTY_POLLS;
        private int sendBatchSize = DEFAULT_SEND_BATCH_SIZE;
        private int producerLingerMs = DEFAULT_PRODUCER_LINGER_MS;
        private int producerBatchBytes = DEFAULT_PRODUCER_BATCH_BYTES;
        private String producerCompressionType = DEFAULT_PRODUCER_COMPRESSION_TYPE;

        private Builder() {
        }

        public Builder bootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
            return this;
        }

        public Builder metadataTopic(String metadataTopic) {
            this.metadataTopic = metadataTopic;
            return this;
        }

        public Builder journalTopic(String journalTopic) {
            this.journalTopic = journalTopic;
            return this;
        }

        public Builder metadataGroupId(String metadataGroupId) {
            this.metadataGroupId = metadataGroupId;
            return this;
        }

        public Builder metadataPollMs(int metadataPollMs) {
            this.metadataPollMs = metadataPollMs;
            return this;
        }

        public Builder metadataEmptyPolls(int metadataEmptyPolls) {
            this.metadataEmptyPolls = metadataEmptyPolls;
            return this;
        }

        public Builder sendBatchSize(int sendBatchSize) {
            this.sendBatchSize = sendBatchSize;
            return this;
        }

        public Builder producerLingerMs(int producerLingerMs) {
            this.producerLingerMs = producerLingerMs;
            return this;
        }

        public Builder producerBatchBytes(int producerBatchBytes) {
            this.producerBatchBytes = producerBatchBytes;
            return this;
        }

        public Builder producerCompressionType(String producerCompressionType) {
            this.producerCompressionType = producerCompressionType;
            return this;
        }

        public KafkaLibraryConfig build() {
            return new KafkaLibraryConfig(this);
        }
    }
}
