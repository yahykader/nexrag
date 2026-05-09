package com.exemple.nexrag;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration providing minimal beans needed for integration tests.
 * Provides a simple MeterRegistry to satisfy MetricsService dependencies,
 * and mocks PrometheusMeterRegistry to prevent eager instantiation of Prometheus components.
 */
@TestConfiguration
public class IntegrationTestConfiguration {

    @Bean
    @Primary
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    public Clock clock() {
        return Clock.SYSTEM;
    }

    @MockBean
    private PrometheusMeterRegistry prometheusMeterRegistry;
}
