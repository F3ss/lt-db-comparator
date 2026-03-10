package com.example.kafkalib.metadata;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class InMemoryMetadataStore implements MetadataStore {

    private final AtomicReference<Map<String, String>> state =
            new AtomicReference<>(Collections.emptyMap());

    @Override
    public void replaceAll(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            state.set(Collections.emptyMap());
            return;
        }
        state.set(Collections.unmodifiableMap(new HashMap<>(metadata)));
    }

    @Override
    public Map<String, String> snapshot() {
        return state.get();
    }
}
