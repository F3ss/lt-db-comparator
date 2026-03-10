package com.example.kafkalib.metadata;

import java.util.Map;

public interface MetadataLoader {

    Map<String, String> load();
}
