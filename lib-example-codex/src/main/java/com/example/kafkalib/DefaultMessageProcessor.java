package com.example.kafkalib;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class DefaultMessageProcessor implements MessageProcessor {

    @Override
    public ProcessedMessage process(String key, String value, Map<String, String> metadata) {
        Objects.requireNonNull(key, "message key must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");

        String normalizedValue = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        String metadataValue = metadata.get(key);

        String mergedValue;
        if (metadataValue == null || metadataValue.isEmpty()) {
            mergedValue = normalizedValue;
        } else {
            mergedValue = normalizedValue + "|" + metadataValue;
        }

        byte[] encodedBytes = Base64.getEncoder()
                .encode(mergedValue.getBytes(StandardCharsets.UTF_8));

        return new ProcessedMessage(key, encodedBytes);
    }
}
