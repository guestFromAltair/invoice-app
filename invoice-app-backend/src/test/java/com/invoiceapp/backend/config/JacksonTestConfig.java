package com.invoiceapp.backend.config;

import tools.jackson.databind.json.JsonMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class JacksonTestConfig {

    @Bean
    public JsonMapper testJsonMapper() {
        return new JsonMapper();
    }
}