package com.lt.dbcomparator;

import com.lt.dbcomparator.dto.LoadRequest;
import com.lt.dbcomparator.dto.LoadStatusResponse;
import com.lt.dbcomparator.repository.CustomerRepository;
import com.lt.dbcomparator.repository.ProductRepository;
import com.lt.dbcomparator.service.DataGeneratorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Тест генератора данных — проверяет, что данные реально записываются в БД
 * (MongoDB).
 */
class DataGeneratorIntegrationTest extends AbstractIntegrationTest {

        @Autowired
        private DataGeneratorService generatorService;

        @Autowired
        private MongoTemplate mongoTemplate;

        @Autowired
        private CustomerRepository customerRepository;

        @Autowired
        private ProductRepository productRepository;

        @AfterEach
        void cleanup() {
                generatorService.stop();
                customerRepository.deleteAll();
                productRepository.deleteAll();
        }

        @Test
        @DisplayName("Генератор создаёт записи (Customer и Product)")
        void shouldGenerateDataInAllCollections() {
                // given
                LoadRequest request = LoadRequest.builder()
                                .batchSize(10)
                                .batchesPerSecond(1)
                                .durationMinutes(1)
                                .build();

                // when
                generatorService.start(request);

                // then
                await().atMost(10, SECONDS).untilAsserted(() -> {
                        LoadStatusResponse status = generatorService.getStatus();
                        assertThat(status.isRunning()).isTrue();
                        assertThat(status.getBatchesCompleted()).isGreaterThanOrEqualTo(1);
                        // Also await until products are generated (async initialization)
                        assertThat(productRepository.count()).isGreaterThan(0);
                });

                // Проверяем наличие данных во всех таблицах
                assertThat(productRepository.count()).as("Products collection should not be empty").isGreaterThan(0);
                assertThat(customerRepository.count()).as("Customers collection should not be empty").isGreaterThan(0);

                // stop
                generatorService.stop();
                assertThat(generatorService.getStatus().isRunning()).isFalse();
        }

        @Test
        @DisplayName("Генератор корректно останавливается и отдаёт финальный статус")
        void shouldStopAndReportStatus() {
                // given
                LoadRequest request = LoadRequest.builder()
                                .batchSize(5)
                                .batchesPerSecond(2)
                                .durationMinutes(5)
                                .build();

                // when
                generatorService.start(request);

                await().atMost(5, SECONDS).untilAsserted(
                                () -> assertThat(generatorService.getStatus().getBatchesCompleted())
                                                .isGreaterThanOrEqualTo(1));

                generatorService.stop();

                // then
                LoadStatusResponse status = generatorService.getStatus();
                assertThat(status.isRunning()).isFalse();
                assertThat(status.getBatchesSubmitted()).isGreaterThanOrEqualTo(1);
                assertThat(status.getBatchesCompleted()).isGreaterThanOrEqualTo(1);
        }
}
