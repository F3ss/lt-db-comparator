package com.lt.dbcomparator.config;

import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class MongoConfig {

    @Primary
    @Bean
    @ConfigurationProperties(prefix = "domain.settings.push.mongodb")
    public MongoProperties mongoProperties() {
        return new MongoProperties();
    }
}
