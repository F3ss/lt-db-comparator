package com.example.kafkalib.processing;

import java.util.Objects;

public final class ProcessedMessage {

    private final String key;
    private final byte[] payload;

    public ProcessedMessage(String key, byte[] payload) {
        this.key = Objects.requireNonNull(key, "key must not be null");
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
    }

    public String getKey() {
        return key;
    }

    public byte[] getPayload() {
        return payload;
    }
}
