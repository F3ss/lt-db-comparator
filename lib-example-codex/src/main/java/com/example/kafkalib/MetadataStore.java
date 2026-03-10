package com.example.kafkalib;

import java.util.Map;

interface MetadataStore {

    void replaceAll(Map<String, String> metadata);

    Map<String, String> snapshot();
}
