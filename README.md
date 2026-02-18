# Demo Project — PostgreSQL Load Testing Tool

Приложение для генерации синтетической нагрузки на PostgreSQL и тестирования производительности чтения/записи.

## 🛠 Технологический стек
*   **Java 21**
*   **Spring Boot 3.5.10** (Snapshot)
*   **Database**: PostgreSQL / Pangolin
*   **ORM**: Spring Data JPA / Hibernate
*   **Metrics**: Micrometer + Prometheus
*   **Docs**: SpringDoc OpenAPI (Swagger)
*   **Tests**: Testcontainers

## 🚀 Запуск
1.  Поднять базу данных:
    ```bash
    docker-compose up -d
    ```
2.  Запустить приложение:
    ```bash
    ./gradlew bootRun
    ```
    *По умолчанию приложение слушает порт 8080.*

## 📖 API Documentation
Swagger UI доступен по адресу:
[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

### Основные эндопоинты

#### Генератор (Load Generator)
Позволяет генерировать свзязанные данные (Customer → Profile → Orders → Items) в многопоточном режиме.

*   `POST /api/generator/start` — Запуск генерации.
    *   body: `{"batchSize": 100, "batchesPerSecond": 5, "durationMinutes": 10}`
*   `POST /api/generator/stop` — Остановка.
*   `GET /api/generator/status` — Текущий статус и статистика.

#### Клиенты (Customers)
*   `GET /api/customers` — Получить список клиентов (Pageable).
*   `GET /api/customers/{id}` — Получить полный граф объектов клиента (с заказами и товарами).

## 📊 Метрики (Monitorng)
Приложение экспортирует метрики в формате Prometheus по адресу:
[http://localhost:8080/actuator/prometheus](http://localhost:8080/actuator/prometheus)

### Кастомные метрики генератора
| Метрика | Тип | Описание |
|---|---|---|
| `generator.batches.submitted` | Counter | Количество отправленных на обработку батчей |
| `generator.batches.completed` | Counter | Количество успешно записанных батчей |
| `generator.batches.failed` | Counter | Количество батчей с ошибками |
| `generator.records.total` | Counter | Общее количество созданных записей (во всех таблицах) |
| `generator.batch.duration` | Timer | Время выполнения записи одного батча |
