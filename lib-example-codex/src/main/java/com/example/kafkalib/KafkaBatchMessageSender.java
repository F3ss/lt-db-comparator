package com.example.kafkalib;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

final class KafkaBatchMessageSender implements BatchMessageSender {

    private final KafkaProducer<String, byte[]> producer;

    KafkaBatchMessageSender(KafkaLibraryConfig config) {
        this.producer = new KafkaProducer<>(producerProperties(config));
    }

    @Override
    public void sendBatch(String topic, List<ProcessedMessage> messages) {
        List<Future<RecordMetadata>> futures = new ArrayList<>(messages.size());
        for (ProcessedMessage message : messages) {
            ProducerRecord<String, byte[]> record =
                    new ProducerRecord<>(topic, message.getKey(), message.getPayload());
            futures.add(producer.send(record));
        }
        waitForAcknowledgement(futures);
    }

    @Override
    public void flush() {
        producer.flush();
    }

    @Override
    public void close() {
        producer.close();
    }

    private static Properties producerProperties(KafkaLibraryConfig config) {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "1");
        properties.put(ProducerConfig.LINGER_MS_CONFIG, Integer.toString(config.getProducerLingerMs()));
        properties.put(ProducerConfig.BATCH_SIZE_CONFIG, Integer.toString(config.getProducerBatchBytes()));
        properties.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, config.getProducerCompressionType());
        return properties;
    }

    private static void waitForAcknowledgement(List<Future<RecordMetadata>> futures) {
        for (Future<RecordMetadata> future : futures) {
            try {
                future.get();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Kafka send interrupted", ex);
            } catch (ExecutionException ex) {
                throw new IllegalStateException("Kafka send failed", ex.getCause());
            }
        }
    }
}
