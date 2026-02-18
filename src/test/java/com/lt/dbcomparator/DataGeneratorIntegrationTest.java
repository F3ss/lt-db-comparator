package com.lt.dbcomparator;

import com.lt.dbcomparator.dto.LoadRequest;
import com.lt.dbcomparator.dto.LoadStatusResponse;
import com.lt.dbcomparator.service.DataGeneratorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Тест генератора данных — проверяет, что данные реально записываются в БД.
 */
class DataGeneratorIntegrationTest extends AbstractIntegrationTest {

        @Autowired
        private DataGeneratorService generatorService;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @AfterEach
        void cleanup() {
                generatorService.stop();
        }

        @Test
        @DisplayName("Генератор создаёт записи во всех 5 таблицах")
        void shouldGenerateDataInAllTables() {
                // given — запуск с минимальной нагрузкой
                LoadRequest request = LoadRequest.builder()
                                .batchSize(10)
                                .batchesPerSecond(1)
                                .durationMinutes(1)
                                .build();

                // when
                generatorService.start(request);

                // then — ждём, пока хотя бы 1 батч завершится
                await().atMost(10, SECONDS).untilAsserted(() -> {
                        LoadStatusResponse status = generatorService.getStatus();
                        assertThat(status.isRunning()).isTrue();
                        assertThat(status.getBatchesCompleted()).isGreaterThanOrEqualTo(1);
                });

                // Проверяем наличие данных во всех таблицах
                assertTableNotEmpty("products");
                assertTableNotEmpty("customers");
                assertTableNotEmpty("customer_profiles");
                assertTableNotEmpty("orders");
                assertTableNotEmpty("order_items");

                // Проверяем, что счётчик записей > 0
                LoadStatusResponse status = generatorService.getStatus();
                assertThat(status.getTotalRecords()).isGreaterThan(0);

                // stop — останавливаем
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
                assertThat(status.getElapsedMinutes()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Связи между сущностями корректны (FK)")
        void shouldMaintainForeignKeyIntegrity() {
                // given — генерируем данные
                LoadRequest request = LoadRequest.builder()
                                .batchSize(10)
                                .batchesPerSecond(1)
                                .durationMinutes(1)
                                .build();

                generatorService.start(request);

                await().atMost(10, SECONDS).untilAsserted(
                                () -> assertThat(generatorService.getStatus().getBatchesCompleted())
                                                .isGreaterThanOrEqualTo(1));
                generatorService.stop();

                // then — проверяем, что все FK корректны

                // Каждый профиль ссылается на существующего клиента
                Long orphanProfiles = jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM customer_profiles cp " +
                                                "LEFT JOIN customers c ON cp.customer_id = c.id WHERE c.id IS NULL",
                                Long.class);
                assertThat(orphanProfiles).isZero();

                // Каждый заказ ссылается на существующего клиента
                Long orphanOrders = jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM orders o " +
                                                "LEFT JOIN customers c ON o.customer_id = c.id WHERE c.id IS NULL",
                                Long.class);
                assertThat(orphanOrders).isZero();

                // Каждая позиция ссылается на существующий заказ и продукт
                Long orphanItems = jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM order_items oi " +
                                                "LEFT JOIN orders o ON oi.order_id = o.id " +
                                                "LEFT JOIN products p ON oi.product_id = p.id " +
                                                "WHERE o.id IS NULL OR p.id IS NULL",
                                Long.class);
                assertThat(orphanItems).isZero();
        }

        private void assertTableNotEmpty(String tableName) {
                Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM " + tableName, Long.class);
                assertThat(count).as("Table '%s' should not be empty", tableName).isGreaterThan(0);
        }
}
