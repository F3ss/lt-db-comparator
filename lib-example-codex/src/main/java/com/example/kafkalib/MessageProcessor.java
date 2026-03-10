package com.example.kafkalib;

import java.util.Map;

interface MessageProcessor {

    ProcessedMessage process(String key, String value, Map<String, String> metadata);
}
