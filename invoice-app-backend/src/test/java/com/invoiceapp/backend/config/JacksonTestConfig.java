package com.invoiceapp.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class JacksonTestConfig {

    @Bean
    public ObjectMapper testObjectMapper() {
        return new ObjectMapper();
    }
}