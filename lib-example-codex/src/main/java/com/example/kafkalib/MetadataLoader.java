package com.example.kafkalib;

import java.util.Map;

interface MetadataLoader {

    Map<String, String> load();
}
