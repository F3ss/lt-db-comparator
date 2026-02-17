package com.example.demo;

import com.example.demo.dto.LoadRequest;
import com.example.demo.service.DataGeneratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Тест REST-эндпоинтов — генерация и чтение данных через HTTP.
 */
class CustomerControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DataGeneratorService generatorService;

    @BeforeEach
    void generateData() {
        if (generatorService.getStatus().getTotalRecords() == 0) {
            LoadRequest request = LoadRequest.builder()
                    .batchSize(10)
                    .batchesPerSecond(1)
                    .durationMinutes(1)
                    .build();
            generatorService.start(request);

            await().atMost(10, SECONDS).untilAsserted(
                    () -> assertThat(generatorService.getStatus().getBatchesCompleted()).isGreaterThanOrEqualTo(1));
            generatorService.stop();
        }
    }

    @Test
    @DisplayName("GET /api/customers/{id} — возвращает клиента со связями")
    void shouldReturnCustomerWithDetails() {
        // when
        ResponseEntity<String> response = restTemplate.getForEntity("/api/customers/1", String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).contains("firstName");
        assertThat(body).contains("profile");
        assertThat(body).contains("orders");
    }

    @Test
    @DisplayName("GET /api/customers — страничная выдача")
    void shouldReturnPagedCustomers() {
        // when
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/customers?page=0&size=5", String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).contains("\"totalElements\"");
        assertThat(body).contains("\"content\"");
    }

    @Test
    @DisplayName("GET /api/customers/{id} — 500 для несуществующего клиента")
    void shouldReturn500ForNonExistentCustomer() {
        // when
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/customers/999999999", String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("POST /api/generator/start + GET /api/generator/status через REST")
    void shouldStartAndReportStatusViaRest() {
        // stop any running generator first
        restTemplate.postForEntity("/api/generator/stop", null, String.class);

        // given
        LoadRequest request = LoadRequest.builder()
                .batchSize(5)
                .batchesPerSecond(1)
                .durationMinutes(1)
                .build();

        // when — start
        ResponseEntity<String> startResponse = restTemplate.postForEntity(
                "/api/generator/start", request, String.class);
        assertThat(startResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // then — status
        await().atMost(5, SECONDS).untilAsserted(() -> {
            ResponseEntity<String> statusResponse = restTemplate.getForEntity(
                    "/api/generator/status", String.class);
            assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(statusResponse.getBody()).contains("\"running\":true");
        });

        // cleanup
        restTemplate.postForEntity("/api/generator/stop", null, String.class);
    }

    @Test
    @DisplayName("Actuator /actuator/prometheus доступен и содержит метрики")
    void shouldExposePrometheusMetrics() {
        // when
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/actuator/prometheus", String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("generator_batches_submitted_total");
    }

    @Test
    @DisplayName("Swagger UI доступен")
    void shouldExposeSwaggerUi() {
        // when
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/swagger-ui/index.html", String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
