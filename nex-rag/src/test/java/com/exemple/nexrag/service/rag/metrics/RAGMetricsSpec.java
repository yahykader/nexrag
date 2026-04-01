package com.exemple.nexrag.service.rag.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Spec : RAGMetrics — Métriques centralisées du pipeline RAG")
class RAGMetricsSpec {

    private MeterRegistry registry;
    private RAGMetrics    metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics  = new RAGMetrics(registry);
    }

    // US-2 / AC-14.1
    @Test
    @DisplayName("DOIT incrémenter le counter de succès après une ingestion réussie")
    void devraitIncrementerCompteurSuccesApresIngestionReussie() {
        metrics.recordIngestionSuccess("pdf", 100, 5);

        double count = registry.counter(
            "rag_ingestion_files_total",
            "strategy", "pdf", "status", "success"
        ).count();

        assertThat(count).isEqualTo(1.0);
    }

    // US-2 / AC-14.1
    @Test
    @DisplayName("DOIT incrémenter totalFilesProcessed après une ingestion réussie")
    void devraitIncrementerTotalFichiersTraites() {
        metrics.recordIngestionSuccess("pdf", 100, 5);

        assertThat(metrics.getTotalFilesProcessed()).isEqualTo(1L);
    }

    // US-2 / FR-007 / FR-008
    @Test
    @DisplayName("DOIT incrémenter le counter d'erreur sans toucher au counter de succès")
    void devraitEnregistrerErreurIngestionSansToucherSucces() {
        metrics.recordIngestionError("pdf", "IO");

        double errorCount = registry.counter(
            "rag_ingestion_errors_total",
            "strategy", "pdf", "error_type", "IO"
        ).count();
        assertThat(errorCount).isEqualTo(1.0);

        // Le counter de succès ne doit pas avoir été créé
        assertThat(registry.find("rag_ingestion_files_total")
            .tags("status", "success").counter()).isNull();
    }

    // US-2 / FR-014 (cache de meters)
    @Test
    @DisplayName("DOIT réutiliser le même meter sans re-créer à chaque appel")
    void devraitNePasRecreeLeMeterSiDejaEnregistre() {
        metrics.recordIngestionSuccess("pdf", 100, 5);
        metrics.recordIngestionSuccess("pdf", 200, 3);

        double count = registry.counter(
            "rag_ingestion_files_total",
            "strategy", "pdf", "status", "success"
        ).count();

        assertThat(count).isEqualTo(2.0);
    }

    // US-2 / AC-14.2
    @Test
    @DisplayName("DOIT suivre les ingestions actives via startIngestion et endIngestion")
    void devraitSuivreIngestionsActivesViaStartEtEnd() {
        metrics.startIngestion();
        assertThat(metrics.getActiveIngestions()).isEqualTo(1);

        metrics.endIngestion();
        assertThat(metrics.getActiveIngestions()).isEqualTo(0);
    }

    // US-2 / FR-009
    @Test
    @DisplayName("DOIT suivre les queries actives via startQuery et endQuery")
    void devraitSuivreQueriesActivesViaStartEtEnd() {
        metrics.startQuery();
        assertThat(metrics.getActiveQueries()).isEqualTo(1);

        metrics.endQuery();
        assertThat(metrics.getActiveQueries()).isEqualTo(0);
        assertThat(metrics.getTotalQueriesProcessed()).isEqualTo(1L);
    }

    // US-2 / AC-14.3
    @Test
    @DisplayName("DOIT incrémenter uniquement le counter hit sans affecter le counter miss")
    void devraitIncrementerSeulementLeCompteurHitSansAffecterMiss() {
        metrics.recordCacheHit("embedding");

        double hitCount = registry.counter("rag_cache_hits_total", "cache", "embedding").count();
        assertThat(hitCount).isEqualTo(1.0);

        // Le counter miss ne doit pas exister
        assertThat(registry.find("rag_cache_misses_total").counter()).isNull();
    }

    // US-2 / AC-14.3
    @Test
    @DisplayName("DOIT incrémenter uniquement le counter miss sans affecter le counter hit")
    void devraitIncrementerSeulementLeCompteurMissSansAffecterHit() {
        metrics.recordCacheMiss("embedding");

        double missCount = registry.counter("rag_cache_misses_total", "cache", "embedding").count();
        assertThat(missCount).isEqualTo(1.0);

        // Le counter hit ne doit pas exister
        assertThat(registry.find("rag_cache_hits_total").counter()).isNull();
    }

    // US-2 / AC-14.5
    @Test
    @DisplayName("DOIT accumuler correctement les tokens générés sur plusieurs appels")
    void devraitAccumulerTokensGeneresCorrectementSurPlusieursAppels() {
        metrics.recordGeneration(100, 80);
        metrics.recordGeneration(200, 70);

        assertThat(metrics.getTotalTokensGenerated()).isEqualTo(150L);
    }

    // US-2 / FR-013
    @Test
    @DisplayName("DOIT enregistrer le counter de tokens sur le registre Micrometer")
    void devraitEnregistrerCompteurTokensSurLeRegistre() {
        metrics.recordGeneration(100, 50);

        double tokenCount = registry.counter("rag_tokens_generated_total").count();
        assertThat(tokenCount).isEqualTo(50.0);
    }
}
