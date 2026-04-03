package com.exemple.nexrag.service.rag.controller;

import com.exemple.nexrag.config.TestWebConfig;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Spec : MetricsController — endpoints métriques Prometheus et health
 *
 * Principe SRP : valide le scrape Prometheus (text/plain), le health check
 * et le résumé des métriques. Utilise une @TestConfiguration pour fournir
 * un PrometheusMeterRegistry pré-chargé — aucun @MockBean nécessaire.
 */
@DisplayName("Spec : MetricsController — endpoints métriques Prometheus et health")
@WebMvcTest(MetricsController.class)
@Import({ MetricsControllerSpec.TestMetricsConfig.class, TestWebConfig.class })
class MetricsControllerSpec {

    @Autowired
    private MockMvc mockMvc;

    // =========================================================================
    // @TestConfiguration — fournit un PrometheusMeterRegistry pré-chargé
    // =========================================================================

    @TestConfiguration
    static class TestMetricsConfig {

        @Bean
        PrometheusMeterRegistry prometheusMeterRegistry() {
            PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

            // Meters requis par MetricsController.metricsSummary()
            registry.timer("rag.query.duration");
            registry.counter("rag.queries.total");
            registry.counter("rag.queries.success");

            AtomicLong activeConns = new AtomicLong(0);
            registry.gauge("rag.connections.active", activeConns, AtomicLong::get);

            return registry;
        }
    }

    // =========================================================================
    // Scrape Prometheus — US-5 / AC-1
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner text/plain non vide pour le scrape Prometheus")
    void shouldReturnPlainTextForPrometheusEndpoint() throws Exception {
        mockMvc.perform(get("/api/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(not(emptyString())));
    }

    // =========================================================================
    // Health — US-5 / AC-2
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 200 avec les champs status et application pour le health")
    void shouldReturn200ForHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.details.application").value("rag-assistant"));
    }

    // =========================================================================
    // Résumé métriques — US-5 / AC-3
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 200 avec queries, performance et connections pour le résumé")
    void shouldReturn200ForMetricsSummaryWithNonNullValues() throws Exception {
        mockMvc.perform(get("/api/actuator/metrics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queries").exists())
                .andExpect(jsonPath("$.performance").exists())
                .andExpect(jsonPath("$.connections").exists())
                .andExpect(jsonPath("$.queries.total").isNumber())
                .andExpect(jsonPath("$.connections.active").isNumber());
    }
}
