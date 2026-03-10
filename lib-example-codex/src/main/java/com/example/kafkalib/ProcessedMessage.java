package com.example.kafkalib;

import java.util.Objects;

final class ProcessedMessage {

    private final String key;
    private final byte[] payload;

    ProcessedMessage(String key, byte[] payload) {
        this.key = Objects.requireNonNull(key, "key must not be null");
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
    }

    String getKey() {
        return key;
    }

    byte[] getPayload() {
        return payload;
    }
}
