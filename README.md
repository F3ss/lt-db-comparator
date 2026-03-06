# eq-kafka-sender

Java-библиотека для чтения и отправки сообщений в Apache Kafka батчами.

## Возможности

- **Чтение (consume)** — многопоточное потребление из Kafka-топика с batch-callback'ом  
- **Отправка (produce)** — батчевая отправка сообщений с гарантией доставки (flush)
- **Ручной коммит** — оффсеты фиксируются только после успешной обработки батча  
- **Graceful shutdown** — корректная остановка consumer-потоков через `wakeup()`  
- **Конфигурация через JSON** — все параметры Kafka и потоков во внешнем файле

## Быстрый старт

### 1. Конфигурация

Создайте файл `config.json` (или используйте встроенный в resources):

```json
{
  "metaTopicName": "EQ_PARSED_META",
  "topicName": "EQ_JOURNAL",
  "batchesPerThread": 64139,
  "threadCount": 2,
  "kafkaSettings": {
    "bootstrap.servers": "localhost:33193",
    "group.id": "test4",
    "enable.auto.commit": "false",
    "max.request.size": "1000000",
    "auto.commit.interval.ms": "1000",
    "session.timeout.ms": "30000",
    "key.deserializer": "org.apache.kafka.common.serialization.StringDeserializer",
    "value.deserializer": "org.apache.kafka.common.serialization.StringDeserializer",
    "key.serializer": "org.apache.kafka.common.serialization.StringSerializer",
    "value.serializer": "org.apache.kafka.common.serialization.ByteArraySerializer",
    "compression.type": "snappy"
  }
}
```

| Поле              | Описание                                                   |
|-------------------|------------------------------------------------------------|
| `metaTopicName`   | Топик для **чтения** (consumer)                            |
| `topicName`       | Топик для **отправки** (producer)                          |
| `batchesPerThread`| Макс. кол-во poll-циклов на каждый consumer-поток          |
| `threadCount`     | Кол-во параллельных consumer-потоков                       |
| `kafkaSettings`   | Настройки Kafka (bootstrap, serializers, compression и пр.)|

### 2. Использование как библиотеки

#### Чтение из Kafka

```java
KafkaConfig config = KafkaConfig.fromFile(Path.of("config.json"));

try (EqKafkaService service = new EqKafkaService(config)) {
    // consume() блокируется до завершения всех потоков
    service.consume(records -> {
        records.forEach(record -> {
            System.out.println("key=" + record.key() + ", value=" + record.value());
        });
    });
}
```

#### Отправка в Kafka

```java
KafkaConfig config = KafkaConfig.fromResource("config.json");

try (EqKafkaService service = new EqKafkaService(config)) {
    List<ProducerRecord<String, byte[]>> batch = List.of(
        new ProducerRecord<>("EQ_JOURNAL", "key1", "payload1".getBytes()),
        new ProducerRecord<>("EQ_JOURNAL", "key2", "payload2".getBytes())
    );
    service.produce(batch);
}
```

#### Чтение + пересылка (consume → produce)

```java
try (EqKafkaService service = new EqKafkaService(config)) {
    service.consume(records -> {
        List<ProducerRecord<String, byte[]>> batch = new ArrayList<>();
        records.forEach(r -> batch.add(
            new ProducerRecord<>(config.getTopicName(), r.key(),
                                 r.value().getBytes(StandardCharsets.UTF_8))
        ));
        service.produce(batch);
    });
}
```

### 3. Запуск демо

```bash
./gradlew run
```

Приложение загрузит `config.json` из classpath и запустит consume → produce пайплайн.

## Архитектура

```
EqKafkaService (фасад)
 ├── consume(BatchMessageHandler)
 │    └── ConsumerWorker × threadCount
 │         ├── KafkaConsumer (thread-per-consumer)
 │         ├── poll() → handler.handleBatch(records) → commitSync()
 │         └── Остановка: по batchesPerThread или wakeup()
 └── produce(List<ProducerRecord>)
      └── ProducerService
           ├── send() каждой записи асинхронно
           └── flush() — блокировка до подтверждения
```

### Ключевые решения

- **At-least-once** — `enable.auto.commit=false` + `commitSync()` после `handleBatch()`. Оффсеты не потеряются при падении.
- **Thread-per-consumer** — `KafkaConsumer` не потокобезопасен, каждый поток имеет свой экземпляр.
- **Batch callback** — `BatchMessageHandler.handleBatch(ConsumerRecords)` передаёт весь батч, давая контроль над итерацией.

## Сборка

```bash
./gradlew build
```

## Требования

- Java 17+
- Apache Kafka (доступный по адресу из `bootstrap.servers`)
