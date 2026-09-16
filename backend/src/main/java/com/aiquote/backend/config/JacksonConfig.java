package com.aiquote.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4's autoconfigured ObjectMapper is Jackson 3 (tools.jackson.databind).
 * AiClient/service code here is written against the classic Jackson 2 API
 * (com.fasterxml.jackson.databind), so we provide that bean explicitly.
 */
@Configuration
public class JacksonConfig {

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
