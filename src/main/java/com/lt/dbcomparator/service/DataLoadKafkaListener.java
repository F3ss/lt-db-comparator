package com.lt.dbcomparator.service;

import com.lt.dbcomparator.dto.KafkaLoadMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Слушатель Kafka для управления генерацией нагрузки.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataLoadKafkaListener {

    private final DataGeneratorService dataGeneratorService;

    @KafkaListener(topics = "lt-test")
    public void listenBatches(List<KafkaLoadMessage> messages) {
        log.info("Получен батч из Kafka: {} сообщений", messages.size());
        for (KafkaLoadMessage message : messages) {
            if (message != null && message.getBatchSize() > 0) {
                try {
                    log.info("Генерация батча размером {} из Kafka", message.getBatchSize());
                    dataGeneratorService.generateAndSaveBatch(message.getBatchSize());
                } catch (Exception e) {
                    log.error("Ошибка при генерации батча из Kafka (размер: {})", message.getBatchSize(), e);
                }
            } else {
                log.warn("Получено некорректное сообщение из Kafka: {}", message);
            }
        }
    }
}
