package com.example.kafkalib;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Public entry point of the library.
 *
 * <p>Core methods:
 * <ul>
 *     <li>{@link #refreshMetadata()} - re-reads metadata from topic</li>
 *     <li>{@link #processAndSend(Map)} - processes map payload and sends to journal topic</li>
 * </ul>
 */
public final class KafkaMetaJournalService implements AutoCloseable {

    private final KafkaLibraryConfig config;
    private final MetadataLoader metadataLoader;
    private final MetadataStore metadataStore;
    private final MessageProcessor messageProcessor;
    private final BatchMessageSender messageSender;

    /**
     * Creates service with default config. Startup immediately loads metadata topic.
     */
    public KafkaMetaJournalService() {
        this(KafkaLibraryConfig.builder().build());
    }

    /**
     * Creates service from properties. Startup immediately loads metadata topic.
     */
    public KafkaMetaJournalService(Properties properties) {
        this(KafkaLibraryConfig.fromProperties(properties));
    }

    /**
     * Creates service from explicit config. Startup immediately loads metadata topic.
     */
    public KafkaMetaJournalService(KafkaLibraryConfig config) {
        this(
                config,
                new KafkaMetadataLoader(config),
                new InMemoryMetadataStore(),
                new DefaultMessageProcessor(),
                new KafkaBatchMessageSender(config),
                true
        );
    }

    KafkaMetaJournalService(
            KafkaLibraryConfig config,
            MetadataLoader metadataLoader,
            MetadataStore metadataStore,
            MessageProcessor messageProcessor,
            BatchMessageSender messageSender,
            boolean loadMetadataOnStartup
    ) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.metadataLoader = Objects.requireNonNull(metadataLoader, "metadataLoader must not be null");
        this.metadataStore = Objects.requireNonNull(metadataStore, "metadataStore must not be null");
        this.messageProcessor = Objects.requireNonNull(messageProcessor, "messageProcessor must not be null");
        this.messageSender = Objects.requireNonNull(messageSender, "messageSender must not be null");

        if (loadMetadataOnStartup) {
            refreshMetadata();
        }
    }

    /**
     * Re-reads metadata topic and replaces local in-memory map.
     */
    public void refreshMetadata() {
        Map<String, String> loadedMetadata = metadataLoader.load();
        metadataStore.replaceAll(loadedMetadata);
    }

    /**
     * Accepts a key-value map, applies transformation, encodes payload into bytes and sends batches to Kafka.
     */
    public void processAndSend(Map<String, String> messages) {
        Objects.requireNonNull(messages, "messages must not be null");
        if (messages.isEmpty()) {
            return;
        }

        Map<String, String> metadataSnapshot = metadataStore.snapshot();
        int batchSize = config.getSendBatchSize();
        List<ProcessedMessage> batch = new ArrayList<>(batchSize);

        for (Map.Entry<String, String> entry : messages.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }

            batch.add(messageProcessor.process(entry.getKey(), entry.getValue(), metadataSnapshot));

            if (batch.size() >= batchSize) {
                sendCurrentBatch(batch);
            }
        }

        if (!batch.isEmpty()) {
            sendCurrentBatch(batch);
        }

        messageSender.flush();
    }

    @Override
    public void close() {
        messageSender.close();
    }

    private void sendCurrentBatch(List<ProcessedMessage> batch) {
        messageSender.sendBatch(config.getJournalTopic(), batch);
        batch.clear();
    }
}
