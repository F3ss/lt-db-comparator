package com.example.kafkalib;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaMetaJournalServiceTest {

    @Test
    void shouldRefreshMetadataAndSendInBatches() {
        SwitchingMetadataLoader loader = new SwitchingMetadataLoader();
        loader.setMetadata(Collections.singletonMap("k1", "meta-value"));

        CapturingBatchSender sender = new CapturingBatchSender();
        KafkaLibraryConfig config = KafkaLibraryConfig.builder()
                .sendBatchSize(2)
                .build();

        KafkaMetaJournalService service = new KafkaMetaJournalService(
                config,
                loader,
                new InMemoryMetadataStore(),
                new DefaultMessageProcessor(),
                sender,
                false
        );

        service.refreshMetadata();

        Map<String, String> input = new LinkedHashMap<>();
        input.put("k1", " hello ");
        input.put("k2", "world");
        input.put(null, "ignored");
        input.put("k3", "java");

        service.processAndSend(input);

        assertEquals(2, sender.batches.size());
        assertEquals(2, sender.batches.get(0).size());
        assertEquals(1, sender.batches.get(1).size());
        assertEquals("journal", sender.topic);

        assertEquals("HELLO|meta-value", decode(sender.batches.get(0).get(0).getPayload()));
        assertEquals("WORLD", decode(sender.batches.get(0).get(1).getPayload()));
        assertEquals("JAVA", decode(sender.batches.get(1).get(0).getPayload()));
        assertEquals(1, sender.flushCallCount);
    }

    private static String decode(byte[] payload) {
        return new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8);
    }

    private static final class SwitchingMetadataLoader implements MetadataLoader {
        private Map<String, String> metadata = Collections.emptyMap();

        @Override
        public Map<String, String> load() {
            return metadata;
        }

        void setMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
        }
    }

    private static final class CapturingBatchSender implements BatchMessageSender {
        private final List<List<ProcessedMessage>> batches = new ArrayList<>();
        private String topic;
        private int flushCallCount;

        @Override
        public void sendBatch(String topic, List<ProcessedMessage> messages) {
            this.topic = topic;
            this.batches.add(new ArrayList<>(messages));
        }

        @Override
        public void flush() {
            flushCallCount++;
        }

        @Override
        public void close() {
            // no-op
        }
    }
}
