package com.exemple.nexrag.config;

import com.exemple.nexrag.service.rag.interceptor.RateLimitService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestWebConfig {

    @Bean
    public RateLimitService rateLimitService() {
        return Mockito.mock(RateLimitService.class);
    }
}