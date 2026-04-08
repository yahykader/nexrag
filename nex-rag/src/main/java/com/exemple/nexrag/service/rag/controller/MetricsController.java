package com.exemple.nexrag.service.rag.controller;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Controller pour métriques et health checks.
 *
 * {@code Optional<PrometheusMeterRegistry>} permet au bean de démarrer même
 * si Prometheus n'est pas disponible (profil integration-test avec observabilité
 * désactivée par Spring Boot Test). Spring injecte Optional.empty() dans ce cas.
 */
@Slf4j
@RestController
@RequestMapping("/api/actuator")
public class MetricsController {

    private final Optional<PrometheusMeterRegistry> prometheusRegistry;
    private final MeterRegistry                     meterRegistry;

    public MetricsController(Optional<PrometheusMeterRegistry> prometheusRegistry,
                              MeterRegistry meterRegistry) {
        this.prometheusRegistry = prometheusRegistry;
        this.meterRegistry      = meterRegistry;
        log.info("✅ MetricsController initialisé (prometheus={})",
            prometheusRegistry.map(r -> r.getMeters().size() + " métriques")
                               .orElse("absent"));
    }

    // -------------------------------------------------------------------------
    // Prometheus scrape
    // -------------------------------------------------------------------------

    @GetMapping(value = "/prometheus", produces = "text/plain")
    public String prometheus() {
        return prometheusRegistry
            .map(r -> {
                String scrape = r.scrape();
                log.debug("📊 /prometheus — {} bytes", scrape.length());
                return scrape;
            })
            .orElse("# Prometheus registry not available\n");
    }

    // -------------------------------------------------------------------------
    // Health check
    // -------------------------------------------------------------------------

    @GetMapping("/health")
    public ResponseEntity<Health> health() {
        Health health = Health.up()
            .withDetail("status",      "UP")
            .withDetail("application", "rag-assistant")
            .build();
        return ResponseEntity.ok(health);
    }

    // -------------------------------------------------------------------------
    // Métriques lisibles
    // -------------------------------------------------------------------------

    @GetMapping("/metrics/summary")
    public ResponseEntity<Map<String, Object>> metricsSummary() {
        double avgDurationMs  = safeTimer("rag.query.duration");
        long   queriesTotal   = safeCounter("rag.queries.total");
        long   queriesSuccess = safeCounter("rag.queries.success");
        long   activeConns    = safeGauge("rag.connections.active");

        Map<String, Object> summary = Map.of(
            "queries", Map.of(
                "total",        queriesTotal,
                "success",      queriesSuccess,
                "success_rate", queriesTotal > 0
                    ? (double) queriesSuccess / queriesTotal : 0.0
            ),
            "performance", Map.of(
                "avg_duration_ms", avgDurationMs
            ),
            "connections", Map.of(
                "active", activeConns
            )
        );

        return ResponseEntity.ok(summary);
    }

    // -------------------------------------------------------------------------
    // Helpers — évite les exceptions si une métrique n'existe pas encore
    // -------------------------------------------------------------------------

    private double safeTimer(String name) {
        try { return meterRegistry.get(name).timer().mean(TimeUnit.MILLISECONDS); }
        catch (Exception e) { return 0.0; }
    }

    private long safeCounter(String name) {
        try { return (long) meterRegistry.get(name).counter().count(); }
        catch (Exception e) { return 0L; }
    }

    private long safeGauge(String name) {
        try { return (long) meterRegistry.get(name).gauge().value(); }
        catch (Exception e) { return 0L; }
    }
}