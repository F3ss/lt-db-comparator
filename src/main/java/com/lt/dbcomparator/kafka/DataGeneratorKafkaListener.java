package com.lt.dbcomparator.kafka;

import com.lt.dbcomparator.service.DataGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Listener for Kafka messages to trigger data generation.
 * This allows horizontal scaling of the data generation process.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataGeneratorKafkaListener {

    private final DataGeneratorService dataGeneratorService;

    @KafkaListener(topics = "lt-test", groupId = "lt-db-comparator-group")
    public void listen(List<String> messages) {
        log.info("Received Kafka batch of {} messages", messages.size());

        for (String message : messages) {
            try {
                int batchSize = Integer.parseInt(message.trim());
                if (batchSize > 0) {
                    log.debug("Generating batch of size {} from Kafka message", batchSize);
                    dataGeneratorService.generateFromKafka(batchSize);
                } else {
                    log.warn("Invalid batch size received from Kafka: {}", batchSize);
                }
            } catch (NumberFormatException e) {
                log.error("Failed to parse Kafka message as integer batch size: '{}'", message, e);
            } catch (Exception e) {
                log.error("Error generating data from Kafka message: {}", message, e);
            }
        }
    }
}
