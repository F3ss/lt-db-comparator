package com.lt.dbcomparator;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;

/**
 * Базовый класс для интеграционных тестов.
 * Один контейнер PostgreSQL на весь тестовый прогон (singleton pattern).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    static final MongoDBContainer MONGO;

    static {
        MONGO = new MongoDBContainer("mongo:7.0");
        MONGO.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }
}
