package org.nimblt;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.nimblt.config.KafkaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Демонстрационный пример использования библиотеки eq-kafka-sender.
 * <p>
 * Показывает, как инициализировать {@link EqKafkaService} из config.json,
 * выполнить чтение из одного топика и отправку в другой.
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws IOException {
        // Загружаем конфигурацию из classpath-ресурса
        KafkaConfig config = KafkaConfig.fromResource("config.json");

        // Создаём сервис и регистрируем shutdown hook для корректной остановки по
        // Ctrl+C
        EqKafkaService service = new EqKafkaService(config);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Получен сигнал завершения, останавливаем сервис...");
            service.close();
        }));

        // --- Пример: чтение из metaTopicName и пересылка в topicName ---
        service.consume(records -> {
            List<ProducerRecord<String, byte[]>> batch = new ArrayList<>();

            records.forEach(record -> {
                log.info("Получено: key='{}', value='{}'", record.key(), record.value());

                // Пример трансформации: пересылаем value как byte[] в целевой топик
                byte[] payload = record.value().getBytes(StandardCharsets.UTF_8);
                batch.add(new ProducerRecord<>(
                        config.getTopicName(), record.key(), payload));
            });

            if (!batch.isEmpty()) {
                service.produce(batch);
                log.info("Переслано {} записей в '{}'", batch.size(), config.getTopicName());
            }
        });

        log.info("Работа завершена");
    }
}