# lt-db-comparator — Заметки по проекту

## Общее описание

PostgreSQL Load Testing Tool — приложение для генерации синтетической нагрузки на PostgreSQL
и тестирования производительности чтения/записи.

**Группа**: `com.lt`, **Артефакт**: `demo`, **Версия**: `0.0.1-SNAPSHOT`

---

## Технологический стек

| Технология              | Версия / Детали                           |
|-------------------------|-------------------------------------------|
| Java                    | 21                                        |
| Spring Boot             | 3.5.10                                    |
| Build                   | Gradle (Groovy DSL)                       |
| Database                | PostgreSQL 16 (Docker) / Pangolin         |
| ORM                     | Spring Data JPA / Hibernate               |
| Batch Writes            | `JdbcTemplate.batchUpdate` (не JPA)       |
| Metrics                 | Micrometer + Prometheus                   |
| API Docs                | SpringDoc OpenAPI (Swagger UI)            |
| Tests                   | JUnit 5, Testcontainers (PostgreSQL)      |
| Lombok                  | Используется повсеместно                  |

---

## Структура пакетов

```
com.lt.dbcomparator
├── Application.java                     — @SpringBootApplication
├── config/
│   └── OpenApiConfig.java               — Swagger/OpenAPI конфигурация
├── controller/
│   ├── GeneratorController.java          — POST /api/generator/start, stop, GET status
│   └── CustomerController.java          — GET /api/customers, /api/customers/{id}
├── dto/
│   ├── LoadRequest.java                 — Параметры генерации (batchSize, batchesPerSecond, durationMinutes, workerThreads)
│   ├── LoadStatusResponse.java          — Статус генератора (running, totalRecords, batches*, elapsedMinutes)
│   └── CustomerResponse.java           — Record с вложенными DTO: ProfileResponse, OrderResponse, ItemResponse, ProductResponse
├── entity/
│   ├── Customer.java                    — Ключевая сущность (OneToOne → Profile, OneToMany → Order)
│   ├── CustomerProfile.java            — Профиль клиента (OneToOne → Customer)
│   ├── Order.java                       — Заказ (ManyToOne → Customer, OneToMany → OrderItem)
│   ├── OrderItem.java                   — Позиция заказа (ManyToOne → Order, ManyToOne → Product)
│   └── Product.java                     — Товар (справочник)
├── repository/
│   ├── CustomerRepository.java          — JPA repo + @EntityGraph для полной загрузки
│   ├── CustomerProfileRepository.java
│   ├── OrderRepository.java
│   ├── OrderItemRepository.java
│   └── ProductRepository.java
└── service/
    ├── DataGeneratorService.java        — Многопоточная генерация (Worker Pool + JdbcTemplate), Advisory Locks, Rate Limiting
    └── CustomerService.java             — Чтение клиентов через JPA (41 строка)
```

---

## Модель данных (ER)

```
customers (1) ───── (1) customer_profiles
    │
    │ 1:N
    ▼
  orders (N) ───── (N) order_items
                         │
                         │ N:1
                         ▼
                      products
```

### Таблицы и ключевые поля

- **customers**: id, first_name, last_name, email, phone, date_of_birth, registered_at, status, loyalty_points, country
  - Индексы: `idx_customer_email`, `idx_customer_status`
- **customer_profiles**: id, customer_id (FK UNIQUE), avatar_url, bio, preferred_language, notifications_enabled, address, city, zip_code
- **products**: id, name, sku (UNIQUE), description, price, category, weight, in_stock, created_at, updated_at
  - Индексы: `idx_product_sku` (unique), `idx_product_category`
- **orders**: id, customer_id (FK), order_number (UNIQUE), order_date, status, total_amount, currency, shipping_address, notes, expected_delivery
  - Индексы: `idx_order_customer`, `idx_order_status`, `idx_order_date`
- **order_items**: id, order_id (FK), product_id (FK), quantity, unit_price, total_price, discount, created_at
  - Индексы: `idx_item_order`, `idx_item_product`

Схема определена явно в `schema.sql` (CREATE IF NOT EXISTS) + Hibernate `ddl-auto: update`.

---

## REST API

### Generator Controller (`/api/generator`)

| Метод   | Путь                     | Описание                                           |
|---------|--------------------------|----------------------------------------------------|
| `POST`  | `/api/generator/start`   | Запуск генерации. Body: `LoadRequest`               |
| `POST`  | `/api/generator/stop`    | Остановка генерации                                 |
| `GET`   | `/api/generator/status`  | Текущий статус и счётчики → `LoadStatusResponse`    |

**LoadRequest**: `{ batchSize, batchesPerSecond, durationMinutes, workerThreads }`
- `workerThreads` (опционально): кол-во потоков генерации. 0 = авто (CPU cores).

**Rate Limiting**:
Автоматическая проверка реалистичности нагрузки. Максимальный rate рассчитывается по формуле:
`maxRate = workerThreads * (1000 / (10 + batchSize * 0.2))`
При превышении возвращается **400 Bad Request**.

**Error Handling**:
- Повторный старт → **409 Conflict**
- Невалидные параметры → **400 Bad Request**

Каждый батч создаёт: N клиентов + N профилей + ~3N заказов + ~13.5N позиций.

### Customer Controller (`/api/customers`)

| Метод  | Путь                   | Описание                                        |
|--------|------------------------|-------------------------------------------------|
| `GET`  | `/api/customers/{id}`  | Полный граф: Customer → Profile → Orders → Items → Products |
| `GET`  | `/api/customers`       | Страничная выдача (без связей). Params: page, size (default 20) |

---

## Ключевые архитектурные решения

1. **Запись — JdbcTemplate.batchUpdate**, не JPA `saveAll`. Это критично для производительности вставки больших объёмов данных.
2. **Многопоточность (Worker Pool)**:
   - `ExecutorService` с фиксированным пулом потоков (по кол-ву CPU).
   - `Semaphore` для backpressure (защита от переполнения очереди задач).
   - Ticker (ScheduledExecutorService) отправляет задачи в пул с заданной частотой.
3. **Advisory Locks (`pg_advisory_xact_lock`)**:
   - Используется для безопасной инициализации таблицы `products` при одновременном старте нескольких реплик.
   - Гарантирует, что только одна нода выполнит вставку, остальные подождут завершения транзакции.
4. **Чтение — Optimizations**:
   - `getById`: **JdbcTemplate + PostgreSQL JSON** (`json_build_object`). Сборка графа объектов на стороне БД, zero-overhead десериализация. Исключает JPA.
   - `getAll`: **JdbcTemplate + RowMapper**. Плоский `SELECT ... LIMIT ... OFFSET`. Исключает N+1 и создание прокси.
5. **JPA + @EntityGraph** — оставлено как fallback или для админки (если потребуется).
6. **Hibernate batch_size: 50**, `order_inserts: true` — оптимизация Hibernate batching (для JPA-записи, если используется).
7. **HikariCP pool**: max 20, min idle 5.
8. **open-in-view: false** — отключено.
9. **DTO — Java records** (`CustomerResponse` и вложенные).

---

## Метрики (Prometheus)

Кастомные метрики генератора:

| Метрика                        | Тип     | Описание                          |
|--------------------------------|---------|-----------------------------------|
| `generator.batches.submitted`  | Counter | Батчей отправлено                 |
| `generator.batches.completed`  | Counter | Батчей успешно записано           |
| `generator.batches.failed`     | Counter | Батчей с ошибками                 |
| `generator.records.total`      | Counter | Общее кол-во записей              |
| `generator.batch.duration`     | Timer   | Время записи одного батча         |

Эндпоинт: `GET /actuator/prometheus`

---

## Инфраструктура

### Docker
- **docker-compose.yml**: PostgreSQL 16 Alpine, порт 5432, БД `demo`, user/pass: `postgres/postgres`
- Volume: `pgdata`

### Тесты
- **Testcontainers** с PostgreSQL
- `AbstractIntegrationTest.java` — базовый класс для интеграционных тестов
- `ApplicationTests.java` — проверка контекста
- `CustomerControllerIntegrationTest.java` — тесты контроллера клиентов
- `DataGeneratorIntegrationTest.java` — тесты генератора данных

---

## Запуск

```bash
# 1. Поднять PostgreSQL
docker-compose up -d

# 2. Запустить приложение
./gradlew bootRun
# Порт: 8080

# 3. Swagger UI
# http://localhost:8080/swagger-ui.html

# 4. Тесты
./gradlew test
```

---

## Заметки / TODO

- `DataGeneratorService` — самый большой файл (403 строки), основная логика генерации данных
- `CustomerResponse` использует вложенные records (ProfileResponse, OrderResponse, ItemResponse, ProductResponse) для маппинга всего графа
- Стратегия генерации ID: `GenerationType.IDENTITY` (BIGSERIAL) — не позволяет Hibernate batch insert через JPA, поэтому используется JdbcTemplate
- Все связи LAZY по умолчанию, `@EntityGraph` используется для eager-загрузки при чтении
