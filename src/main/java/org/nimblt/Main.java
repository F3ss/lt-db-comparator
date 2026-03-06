package org.nimblt;

import org.nimblt.config.KafkaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Демонстрационный пример использования библиотеки eq-kafka-sender.
 * <p>
 * Показывает, как инициализировать {@link EqKafkaService} из config.json,
 * получить мета-данные и обновить их.
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws IOException {
        KafkaConfig config = KafkaConfig.fromResource("config.json");

        try (EqKafkaService service = new EqKafkaService(config)) {

            // Мета уже загружена при создании сервиса
            Map<String, String> meta = service.getMeta();
            log.info("Загружено {} мета-записей:", meta.size());
            meta.forEach((key, value) -> log.info("  key='{}', value='{}'", key, value));

            // Пример: перечитать мету по необходимости
            service.refreshMeta();
            log.info("Мета обновлена, записей: {}", service.getMeta().size());
        }

        log.info("Работа завершена");
    }
}