# kafka-meta-journal-lib

Java 11 Gradle library for:
- loading metadata from Kafka topic (default `meta`) into in-memory map
- processing map messages and sending batches to another topic (default `journal`)

## Public API

`KafkaMetaJournalService` exposes 2 core methods:
- `refreshMetadata()` - re-read metadata topic
- `processAndSend(Map<String, String> messages)` - process and send to journal topic

Metadata is loaded automatically in constructor.

## Config

Use `Properties` constructor or `KafkaLibraryConfig`.

Supported keys:
- `kafka.bootstrap.servers` (default `localhost:9092`)
- `kafka.metadata.topic` (default `meta`)
- `kafka.journal.topic` (default `journal`)
- `kafka.metadata.group-id` (default `kafka-meta-journal-lib-metadata-loader`)
- `kafka.metadata.poll-ms` (default `200`)
- `kafka.metadata.empty-polls` (default `3`)
- `kafka.send.batch-size` (default `1000`)
- `kafka.producer.linger-ms` (default `20`)
- `kafka.producer.batch-bytes` (default `65536`)
- `kafka.producer.compression-type` (default `lz4`)

## Usage

```java
Properties props = new Properties();
props.setProperty("kafka.bootstrap.servers", "localhost:9092");

try (KafkaMetaJournalService service = new KafkaMetaJournalService(props)) {
    service.refreshMetadata();

    Map<String, String> messages = new HashMap<>();
    messages.put("order-1", "created");
    messages.put("order-2", "updated");

    service.processAndSend(messages);
}
```
