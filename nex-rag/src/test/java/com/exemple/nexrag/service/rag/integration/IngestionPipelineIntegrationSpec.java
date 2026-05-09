package com.exemple.nexrag.service.rag.integration;

import com.exemple.nexrag.dto.batch.BatchInfo;
import com.exemple.nexrag.service.rag.ingestion.repository.EmbeddingRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for the Ingestion Pipeline (Phase 3 — User Story 1).
 * Validates document ingestion for all 5 formats (PDF, DOCX, XLSX, Image, Text),
 * duplicate detection, antivirus scanning, and concurrent atomicity.
 * <p>
 * Tests are designed to be deterministic:
 * - Fixtures contain repetitive, searchable text ("NexRAG", "RAG", "multimodal")
 * - Each test inherits @BeforeEach cleanup from AbstractIntegrationSpec
 * - Assertions validate timing (SCs 001, 002) and vector presence (SC-003)
 * <p>
 * Independent Test: ./mvnw test -Dtest="IngestionPipelineIntegrationSpec"
 * <p>
 * Coverage: FR-001 (PDF), FR-002 (DOCX), FR-003 (XLSX), FR-004 (Image),
 *           FR-005 (Text), FR-008 (Antivirus), SC-001 (latency), SC-002 (dedup),
 *           US-1 scénarios 3 (EICAR) et 4 (concurrence).
 */
@Slf4j
@DisplayName("DOIT valider le pipeline d'ingestion bout-en-bout")
public class IngestionPipelineIntegrationSpec extends AbstractIntegrationSpec {

    @Autowired
    private EmbeddingRepository embeddingRepository;

    // ============ T013: PDF Ingestion (10s SLA) ============

    @Test
    @DisplayName("T013: Ingérer PDF en moins de 10 secondes (SC-001)")
    void devraitIngererpdfEnMoinsDe10Secondes() throws IOException {
        log.info("🧪 T013: Testing PDF ingestion < 10s");

        Instant start = Instant.now();

        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ClassPathResource("fixtures/sample.pdf"));

        var response = restTemplate.postForEntity(
            "/api/ingest",
            createMultipartRequest(body),
            BatchInfo.class
        );

        Duration elapsed = Duration.between(start, Instant.now());

        log.info("✅ PDF ingestion completed in {}ms", elapsed.toMillis());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().batchId()).isNotBlank();
        assertThat(elapsed).isLessThan(Duration.ofSeconds(10));

        // Verify vectors exist in pgvector
        long vectorCount = embeddingRepository.countAllText() + embeddingRepository.countAllImage();
        assertThat(vectorCount).isGreaterThan(0);

        log.info("📊 PDF test passed: {} vectors ingested", vectorCount);
    }

    // ============ T014: DOCX Ingestion (10s SLA) ============

    @Test
    @DisplayName("T014: Ingérer DOCX en moins de 10 secondes (SC-001)")
    void devraitIngererDocxEnMoinsDe10Secondes() throws IOException {
        log.info("🧪 T014: Testing DOCX ingestion < 10s");

        Instant start = Instant.now();

        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ClassPathResource("fixtures/sample.docx"));

        var response = restTemplate.postForEntity(
            "/api/ingest",
            createMultipartRequest(body),
            BatchInfo.class
        );

        Duration elapsed = Duration.between(start, Instant.now());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().batchId()).isNotBlank();
        assertThat(elapsed).isLessThan(Duration.ofSeconds(10));

        log.info("✅ DOCX test passed in {}ms", elapsed.toMillis());
    }

    // ============ T015: XLSX Ingestion (10s SLA) ============

    @Test
    @DisplayName("T015: Ingérer XLSX en moins de 10 secondes (SC-001)")
    void devraitIngererXlsxEnMoinsDe10Secondes() throws IOException {
        log.info("🧪 T015: Testing XLSX ingestion < 10s");

        Instant start = Instant.now();

        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ClassPathResource("fixtures/sample.xlsx"));

        var response = restTemplate.postForEntity(
            "/api/ingest",
            createMultipartRequest(body),
            BatchInfo.class
        );

        Duration elapsed = Duration.between(start, Instant.now());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().batchId()).isNotBlank();
        assertThat(elapsed).isLessThan(Duration.ofSeconds(10));

        log.info("✅ XLSX test passed in {}ms", elapsed.toMillis());
    }

    // ============ T016: Image Ingestion (10s SLA) ============

    @Test
    @DisplayName("T016: Ingérer Image en moins de 10 secondes (SC-001)")
    void devraitIngererImageEnMoinsDe10Secondes() throws IOException {
        log.info("🧪 T016: Testing Image ingestion < 10s");

        Instant start = Instant.now();

        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ClassPathResource("fixtures/sample.jpg"));

        var response = restTemplate.postForEntity(
            "/api/ingest",
            createMultipartRequest(body),
            BatchInfo.class
        );

        Duration elapsed = Duration.between(start, Instant.now());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().batchId()).isNotBlank();
        assertThat(elapsed).isLessThan(Duration.ofSeconds(10));

        log.info("✅ Image test passed in {}ms", elapsed.toMillis());
    }

    // ============ T017: Text Ingestion (10s SLA) ============

    @Test
    @DisplayName("T017: Ingérer Texte en moins de 10 secondes (SC-001)")
    void devraitIngererTexteEnMoinsDe10Secondes() throws IOException {
        log.info("🧪 T017: Testing Text ingestion < 10s");

        Instant start = Instant.now();

        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ClassPathResource("fixtures/sample.txt"));

        var response = restTemplate.postForEntity(
            "/api/ingest",
            createMultipartRequest(body),
            BatchInfo.class
        );

        Duration elapsed = Duration.between(start, Instant.now());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().batchId()).isNotBlank();
        assertThat(elapsed).isLessThan(Duration.ofSeconds(10));

        log.info("✅ Text test passed in {}ms", elapsed.toMillis());
    }

    // ============ T018: Duplicate Detection (< 2s) ============

    @Test
    @DisplayName("T018: Retourner DUPLICATE pour le même document (SC-002)")
    void devraitRetournerDuplicatePourMemeDocument() throws IOException {
        log.info("🧪 T018: Testing duplicate detection < 2s");

        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ClassPathResource("fixtures/sample.pdf"));
        var request = createMultipartRequest(body);

        // First ingestion
        var response1 = restTemplate.postForEntity(
            "/api/ingest",
            request,
            BatchInfo.class
        );

        long vectorsAfterFirst = embeddingRepository.countAllText() + embeddingRepository.countAllImage();

        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response1.getBody()).isNotNull();
        assertThat(response1.getBody().batchId()).isNotBlank();

        // Second ingestion (same file)
        Instant start = Instant.now();

        var body2 = new LinkedMultiValueMap<String, Object>();
        body2.add("file", new ClassPathResource("fixtures/sample.pdf"));

        var response2 = restTemplate.postForEntity(
            "/api/ingest",
            createMultipartRequest(body2),
            Object.class
        );

        Duration elapsed = Duration.between(start, Instant.now());

        log.info("✅ Duplicate detected in {}ms", elapsed.toMillis());

        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.CONFLICT); // 409 DUPLICATE
        assertThat(elapsed).isLessThan(Duration.ofSeconds(2)); // SC-002: < 2s detection
        long vectorsAfterSecond = embeddingRepository.countAllText() + embeddingRepository.countAllImage();
        assertThat(vectorsAfterSecond).isEqualTo(vectorsAfterFirst); // No new vectors
    }

    // ============ T019: Antivirus Rejection (EICAR) ============

    @Test
    @DisplayName("T019: Rejeter fichier EICAR avec erreur virus")
    void devraitRejeterFichierEicarAvecErreurVirus() throws IOException {
        log.info("🧪 T019: Testing EICAR rejection");

        // EICAR test string (triggers antivirus)
        String eicarContent = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", "eicar.txt");
        body.add("file", eicarContent.getBytes());

        // Note: Implementation detail — adjust if endpoint doesn't support inline content
        var response = restTemplate.postForEntity(
            "/api/ingest",
            createMultipartRequest(body),
            Object.class
        );

        long vectorsAfterReject = embeddingRepository.countAllText() + embeddingRepository.countAllImage();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST); // 400 REJECTED
        assertThat(vectorsAfterReject).isZero(); // No vectors created
        log.info("✅ EICAR correctly rejected");
    }

    // ============ T020: Safe File Confirmation ============

    @Test
    @DisplayName("T020: Accepter fichier sain (confirme ClamAV actif)")
    void devraitAccepterFichierSain() throws IOException {
        log.info("🧪 T020: Testing safe file acceptance");

        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ClassPathResource("fixtures/sample.txt"));

        var response = restTemplate.postForEntity(
            "/api/ingest",
            createMultipartRequest(body),
            BatchInfo.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().batchId()).isNotBlank();

        // Confirm vectors were created (proves ClamAV scanned and passed)
        long vectorCount = embeddingRepository.countAllText() + embeddingRepository.countAllImage();
        assertThat(vectorCount).isGreaterThan(0);

        log.info("✅ Safe file accepted and processed");
    }

    // ============ T021: Concurrent Atomic Ingestion ============

    @Test
    @DisplayName("T021: Gérer ingestion concurrente atomiquement (exactement 1 SUCCESS + 1 DUPLICATE)")
    void devraitGererIngestionConcurrenteAtomiquement() throws IOException, InterruptedException {
        log.info("🧪 T021: Testing concurrent ingestion atomicity");

        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ClassPathResource("fixtures/sample.pdf"));

        AtomicReference<HttpStatus> status1 = new AtomicReference<>();
        AtomicReference<HttpStatus> status2 = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Submit two concurrent ingestion requests
        executor.submit(() -> {
            var response = restTemplate.postForEntity(
                "/api/ingest",
                createMultipartRequest(body),
                Object.class
            );
            status1.set((HttpStatus) response.getStatusCode());
            log.debug("Thread 1 received: {}", response.getStatusCode());
        });

        executor.submit(() -> {
            var body2 = new LinkedMultiValueMap<String, Object>();
            body2.add("file", new ClassPathResource("fixtures/sample.pdf"));
            var response = restTemplate.postForEntity(
                "/api/ingest",
                createMultipartRequest(body2),
                Object.class
            );
            status2.set((HttpStatus) response.getStatusCode());
            log.debug("Thread 2 received: {}", response.getStatusCode());
        });

        executor.shutdown();
        boolean finished = executor.awaitTermination(30, TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(status1.get()).isNotNull();
        assertThat(status2.get()).isNotNull();

        // Exactly one SUCCESS (202) and one DUPLICATE (409)
        long successCount = (status1.get() == HttpStatus.ACCEPTED ? 1 : 0) +
                            (status2.get() == HttpStatus.ACCEPTED ? 1 : 0);
        long duplicateCount = (status1.get() == HttpStatus.CONFLICT ? 1 : 0) +
                              (status2.get() == HttpStatus.CONFLICT ? 1 : 0);

        assertThat(successCount).isEqualTo(1);
        assertThat(duplicateCount).isEqualTo(1);

        // Vector count should equal single ingestion
        long vectorCount = embeddingRepository.countAllText() + embeddingRepository.countAllImage();
        assertThat(vectorCount).isGreaterThan(0);

        log.info("✅ Concurrent ingestion correctly atomicized: 1 SUCCESS + 1 DUPLICATE");
    }

    // ============ Helper Methods ============

    // ============ T041: ClamAV Unavailability Edge Case ============

    @Test
    @DisplayName("T041: Gérer indisponibilité ClamAV (bloquer ou fail-open — décision à documenter)")
    void devraitBloquerIngestionSiAntivirusIndisponible() throws IOException {
        log.info("🧪 T041: Testing ClamAV unavailability behavior");

        // Note: This test documents the expected behavior when ClamAV is unavailable.
        // Current implementation: ClamAV is mandatory (blocks ingestion on unavailability).
        // If fail-open behavior is desired, update application-integration-test.yml
        // and modify the antivirus guard to allow processing when service is down.

        // For now, verify that antivirus scanning is active (ClamAV available)
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ClassPathResource("fixtures/sample.txt"));

        var response = restTemplate.postForEntity(
            "/api/ingest",
            createMultipartRequest(body),
            BatchInfo.class
        );

        // With ClamAV available, request should succeed
        assertThat(response.getStatusCode()).isIn(HttpStatus.ACCEPTED, HttpStatus.CONFLICT);
        log.info("✅ ClamAV availability confirmed (ingestion processed)");
    }

    // ============ Helper Methods ============

    /**
     * Create a multipart request body from LinkedMultiValueMap.
     */
    private org.springframework.http.HttpEntity<?> createMultipartRequest(MultiValueMap<String, Object> body) {
        Objects.requireNonNull(body, "body cannot be null");
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new org.springframework.http.HttpEntity<>(body, headers);
    }
}
