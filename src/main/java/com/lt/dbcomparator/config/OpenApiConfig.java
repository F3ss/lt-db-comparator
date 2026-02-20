package com.lt.dbcomparator.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

        @Bean
        public OpenAPI demoOpenAPI() {
                return new OpenAPI()
                                .info(new Info()
                                                .title("Demo — PostgreSQL Load Test API")
                                                .version("1.0")
                                                .description(
                                                                "Инструмент нагрузочного тестирования PostgreSQL.\n" +
                                                                                "\n" +
                                                                                "**Генерация данных** — запускается через POST /api/generator/start с параметрами нагрузки.\n"
                                                                                +
                                                                                "\n" +
                                                                                "**Чтение** — GET /api/customers/{id} возвращает клиента со связями (Profile, Orders, Items, Products).\n"
                                                                                +
                                                                                "\n" +
                                                                                "**Метрики** — /actuator/prometheus, /actuator/metrics\n")
                                                .contact(new Contact().name("Demo Team")));
        }
}
