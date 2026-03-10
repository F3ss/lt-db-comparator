package com.example.kafkalib;

import java.util.List;

interface BatchMessageSender extends AutoCloseable {

    void sendBatch(String topic, List<ProcessedMessage> messages);

    void flush();

    @Override
    void close();
}
