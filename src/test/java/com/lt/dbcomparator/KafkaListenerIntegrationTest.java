package com.lt.dbcomparator;

import com.lt.dbcomparator.dto.KafkaLoadMessage;
import com.lt.dbcomparator.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaListenerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private CustomerRepository customerRepository;

    @BeforeEach
    void setUp() {
        customerRepository.deleteAll();
    }

    @Test
    void shouldGenerateBatchOnKafkaMessage() throws InterruptedException {
        // Arrange
        int batchSize = 5;
        KafkaLoadMessage message = new KafkaLoadMessage(batchSize);

        // Act
        kafkaTemplate.send("lt-test", message);

        // Assert - wait for async processing
        boolean success = false;
        long expectedCount = batchSize; // 5

        for (int i = 0; i < 30; i++) {
            Thread.sleep(500);
            if (customerRepository.count() >= expectedCount) {
                success = true;
                break;
            }
        }

        assertTrue(success, "Customers were not generated in time from Kafka message");
    }
}
