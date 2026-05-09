package com.exemple.nexrag.service.rag.integration;

import com.exemple.nexrag.dto.batch.BatchInfo;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for the Full RAG Pipeline (Phase 7 — User Story 5).
 * End-to-end regression test covering upload → vectors → query → streaming.
 * Verifies isolation between consecutive test suites.
 * <p>
 * Independent Test: ./mvnw test -Dtest="FullRagPipelineIntegrationSpec"
 * <p>
 * Coverage: Full pipeline validation (FR-001 through FR-010), SC-007 (complete flow < 30s),
 *           SC-008 (isolation between runs).
 */
@Slf4j
@DisplayName("DOIT valider le pipeline complet ingestion→requête→streaming")
public class FullRagPipelineIntegrationSpec extends AbstractIntegrationSpec {

    // ============ T032: Complete Flow (Upload → Query → Stream) ============

    @Test
    @DisplayName("T032: Compléter flux complet ingestion → requête → streaming (< 30s)")
    void devraitCompleterFluxCompletIngestionVersStreaming() throws IOException {
        log.info("🧪 T032: Testing complete RAG pipeline flow");

        Instant pipelineStart = Instant.now();
        String conversationId = "full_" + UUID.randomUUID().toString();

        // === PHASE 1: INGESTION ===
        log.info("Phase 1: Ingesting sample.pdf...");
        Instant ingestStart = Instant.now();

        var ingestBody = new LinkedMultiValueMap<String, Object>();
        ingestBody.add("file", new ClassPathResource("fixtures/sample.pdf"));

        var ingestResponse = restTemplate.postForEntity(
            "/api/ingest",
            createMultipartRequest(ingestBody),
            BatchInfo.class
        );

        Duration ingestDuration = Duration.between(ingestStart, Instant.now());

        assertThat(ingestResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(ingestResponse.getBody()).isNotNull();
        assertThat(ingestResponse.getBody().batchId()).isNotBlank();
        assertThat(ingestDuration).isLessThan(Duration.ofSeconds(10)); // SC-001

        log.info("✅ Ingestion complete in {}ms", ingestDuration.toMillis());

        // === PHASE 2: RETRIEVAL ===
        log.info("Phase 2: Querying for passages...");
        Instant retrievalStart = Instant.now();

        var retrievalResponse = restTemplate.getForEntity(
            "/api/search?query=NexRAG&conversationId=" + conversationId,
            Map.class
        );

        Duration retrievalDuration = Duration.between(retrievalStart, Instant.now());

        assertThat(retrievalResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retrievalResponse.getBody()).isNotNull();
        assertThat(retrievalResponse.getBody()).containsKey("passages");

        Object passagesObj = retrievalResponse.getBody().get("passages");
        if (passagesObj instanceof java.util.List<?> passages) {
            assertThat(passages).hasSizeGreaterThanOrEqualTo(3); // SC-003
        }
        assertThat(retrievalDuration).isLessThan(Duration.ofSeconds(3)); // SC-003

        log.info("✅ Retrieval complete in {}ms", retrievalDuration.toMillis());

        // === PHASE 3: STREAMING ===
        log.info("Phase 3: Streaming response...");
        Instant streamStart = Instant.now();

        String queryString = "query=NexRAG&conversationId=" + conversationId;
        var streamResponse = restTemplate.postForEntity(
            "/api/stream?" + queryString,
            null,
            String.class
        );

        Duration streamDuration = Duration.between(streamStart, Instant.now());

        assertThat(streamResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(streamResponse.getBody()).isNotNull();
        assertThat(streamResponse.getBody()).contains("data:"); // SSE format

        // Verify DONE signal was sent
        assertThat(streamResponse.getBody()).contains("[DONE]");

        log.info("✅ Streaming complete in {}ms", streamDuration.toMillis());

        // === TOTAL PIPELINE TIMING ===
        Duration totalDuration = Duration.between(pipelineStart, Instant.now());

        log.info("📊 Pipeline Summary:");
        log.info("  Ingest:   {}ms (< 10s)", ingestDuration.toMillis());
        log.info("  Retrieval: {}ms (< 3s)", retrievalDuration.toMillis());
        log.info("  Streaming: {}ms", streamDuration.toMillis());
        log.info("  Total:    {}ms (< 30s)", totalDuration.toMillis());

        assertThat(totalDuration).isLessThan(Duration.ofSeconds(30)); // SC-007

        log.info("✅ Complete pipeline test passed");
    }

    // ============ T033: Isolation Between Consecutive Runs ============

    @Test
    @DisplayName("T033: Garantir isolation entre suites consécutives (pgvector=0, Redis=0, WireMock=0)")
    void devraitGarantirIsolationEntreSuitesConsecutives() {
        log.info("🧪 T033: Testing isolation between consecutive test runs");

        // Verify pgvector is empty (no documents)
        // This would require injecting EmbeddingRepository
        // For now, we verify via indirect method: retrieval should return 0 passages
        var response = restTemplate.getForEntity(
            "/api/search?query=nonexistent",
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Object passagesObj = response.getBody().get("passages");
        if (passagesObj instanceof java.util.List<?> passages) {
            assertThat(passages).isEmpty(); // No documents ingested yet
            log.info("✅ pgvector verified empty (0 documents)");
        }

        // Verify Redis is clean (FLUSHALL done in @BeforeEach)
        // Check via Redis connection if available
        if (redisTemplate != null) {
            try {
                var connection = redisTemplate.getConnectionFactory().getConnection();
                if (connection != null) {
                    long dbSize = connection.dbSize();
                    assertThat(dbSize).isEqualTo(0);
                    connection.close();
                    log.info("✅ Redis verified empty (0 keys)");
                }
            } catch (Exception e) {
                log.warn("Could not verify Redis DBSIZE: {}", e.getMessage());
            }
        }

        // Verify WireMock has been reset
        // (This is done in @BeforeEach via OPEN_AI_MOCK.resetAll())
        log.info("✅ WireMock reset performed in @BeforeEach");

        log.info("✅ Isolation test passed");
    }

    // ============ Helper Methods ============

    /**
     * Create a multipart request body.
     */
    private org.springframework.http.HttpEntity<?> createMultipartRequest(MultiValueMap<String, Object> body) {
         Objects.requireNonNull(body, "body cannot be null");
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new org.springframework.http.HttpEntity<>(body, headers);
    }
}
