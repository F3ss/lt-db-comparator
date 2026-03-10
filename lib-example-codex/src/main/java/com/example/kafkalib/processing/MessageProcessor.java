package com.example.kafkalib.processing;

import java.util.Map;

public interface MessageProcessor {

    ProcessedMessage process(String key, String value, Map<String, String> metadata);
}
