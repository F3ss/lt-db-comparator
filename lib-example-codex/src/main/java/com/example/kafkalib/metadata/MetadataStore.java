package com.example.kafkalib.metadata;

import java.util.Map;

public interface MetadataStore {

    void replaceAll(Map<String, String> metadata);

    Map<String, String> snapshot();
}
