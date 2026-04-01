package com.example.metakafkacp;

import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KafkaCopyApplication {
    private static final Logger log = LoggerFactory.getLogger(KafkaCopyApplication.class);
    private static final Path DEFAULT_CONFIG = Path.of("config", "application.properties");

    private KafkaCopyApplication() {
    }

    public static void main(String[] args) throws Exception {
        Path configPath = args.length > 0 ? Path.of(args[0]) : DEFAULT_CONFIG;
        if (!Files.exists(configPath)) {
            throw new IllegalArgumentException("Config file not found: " + configPath.toAbsolutePath());
        }

        AppConfig config = AppConfig.load(configPath);
        KafkaReplicator replicator = new KafkaReplicator();

        log.info("Starting Kafka copy using config {}", configPath.toAbsolutePath());
        long copiedRecords = replicator.replicate(config);
        log.info("Finished. Total copied records: {}", copiedRecords);
    }
}
