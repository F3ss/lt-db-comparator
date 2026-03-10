package com.example.kafkalib.kafka.sender;

import com.example.kafkalib.processing.ProcessedMessage;

import java.util.List;

public interface BatchMessageSender extends AutoCloseable {

    void sendBatch(String topic, List<ProcessedMessage> messages);

    void flush();

    @Override
    void close();
}
