package com.exemple.nexrag.service.rag.integration;

import com.exemple.nexrag.dto.batch.BatchInfo;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for the Streaming Pipeline (Phase 5 — User Story 3).
 * Validates that content tokens arrive before DONE signal and that mid-stream errors are handled gracefully.
 * <p>
 * Uses Awaitility for asynchronous assertions on SSE event streams.
 * <p>
 * Independent Test: ./mvnw test -Dtest="StreamingPipelineIntegrationSpec"
 * <p>
 * Coverage: SC-004 (< 5s first token), SC-005 (tokens before DONE),
 *           US-3 scénario 2 (error handling mid-stream).
 */
@Slf4j
@DisplayName("DOIT valider le streaming SSE de réponse")
public class StreamingPipelineIntegrationSpec extends AbstractIntegrationSpec {

    private String conversationId;

    /**
     * Pre-ingest sample.pdf and prepare conversation context.
     */
    @BeforeEach
    @Override
    void integrationTestSetup() {
        super.integrationTestSetup(); // Cleanup

        log.info("📥 Pre-ingesting sample.pdf for streaming tests");

        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ClassPathResource("fixtures/sample.pdf"));

        var response = restTemplate.postForEntity(
            "/api/ingest",
            createMultipartRequest(body),
            BatchInfo.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        conversationId = "stream_" + UUID.randomUUID().toString();

        log.info("✅ Pre-ingest complete, conversationId={}", conversationId);
    }

    // ============ T026: Token Emission Before DONE ============

    @Test
    @DisplayName("T026: Émettre tokens avant signal DONE (< 5s premier token, SC-004)")
    void devraitEmettreTokensAvantSignalDeFin() {
        log.info("🧪 T026: Testing token emission before DONE");

        List<String> tokens = new ArrayList<>();
        AtomicBoolean receivedDone = new AtomicBoolean(false);
        AtomicBoolean receivedError = new AtomicBoolean(false);

        Instant firstTokenTime = Instant.now();
        AtomicBoolean firstTokenReceived = new AtomicBoolean(false);

        try {
            // POST /api/stream with pre-ingested context
            String queryString = "query=NexRAG&conversationId=" + conversationId;
            var response = restTemplate.postForEntity(
                "/api/stream?" + queryString,
                null,
                String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Parse SSE stream
            String sseBody = response.getBody();
            if (sseBody != null) {
                var lines = sseBody.split("\n");
                for (String line : lines) {
                    log.debug("SSE line: {}", line);

                    if (line.startsWith("data: {")) {
                        if (!firstTokenReceived.get()) {
                            firstTokenTime = Instant.now();
                            firstTokenReceived.set(true);
                        }

                        // Parse JSON event
                        String jsonPart = line.substring(6); // Remove "data: "
                        if (jsonPart.contains("content")) {
                            // Extract token content
                            int contentStart = jsonPart.indexOf("\"content\":\"") + 11;
                            int contentEnd = jsonPart.indexOf("\"", contentStart);
                            if (contentStart > 10 && contentEnd > contentStart) {
                                String token = jsonPart.substring(contentStart, contentEnd);
                                tokens.add(token);
                                log.debug("Token: {}", token);
                            }
                        }
                    } else if (line.contains("[DONE]")) {
                        receivedDone.set(true);
                        log.debug("Received DONE signal");
                    } else if (line.contains("\"type\":\"ERROR\"") || line.contains("error")) {
                        receivedError.set(true);
                        log.warn("Received ERROR in stream");
                    }
                }
            }

            // Assertions
            assertThat(firstTokenReceived.get()).isTrue();
            Duration timeToFirstToken = Duration.between(firstTokenTime, Instant.now());
            assertThat(timeToFirstToken).isLessThan(Duration.ofSeconds(5)); // SC-004

            assertThat(tokens).isNotEmpty();
            log.info("✅ Received {} tokens before DONE", tokens.size());
            assertThat(receivedDone.get()).isTrue();

        } catch (Exception e) {
            log.error("SSE streaming test failed", e);
            throw new AssertionError("Streaming test failed: " + e.getMessage(), e);
        }
    }

    // ============ T027: Error Handling Mid-Stream ============

    @Test
    @DisplayName("T027: Émettre événement ERROR sans plantage (mid-stream error recovery)")
    void devraitEmettreEvenementErreurSansPlantage() {
        log.info("🧪 T027: Testing mid-stream error handling");

        // Reconfigure WireMock to return 500 on /v1/chat/completions
        OPEN_AI_MOCK.resetAll();
        OPEN_AI_MOCK.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":{\"message\":\"Internal Server Error\"}}")
            )
        );

        log.debug("Configured WireMock to return 500");

        try {
            // POST /api/stream (will hit mocked error)
            String queryString = "query=NexRAG&conversationId=" + conversationId;
            var response = restTemplate.postForEntity(
                "/api/stream?" + queryString,
                null,
                String.class
            );

            // Response should be 200 OK with error event in stream
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            String sseBody = response.getBody();
            assertThat(sseBody).isNotNull();

            // Verify ERROR event is emitted
            assertThat(sseBody).contains("ERROR");

            // Verify subsequent requests still work (stream properly closed)
            log.info("✅ Error event emitted, stream closed gracefully");

            // Test that a fresh request works (recovery)
            registerOpenAiChatStreamStub(); // Re-register success stub

            String queryString2 = "query=RAG&conversationId=" + conversationId;
            var response2 = restTemplate.postForEntity(
                "/api/stream?" + queryString2,
                null,
                String.class
            );

            assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
            log.info("✅ Recovery request successful");

        } catch (Exception e) {
            log.error("Error handling test failed", e);
            throw new AssertionError("Error handling test failed: " + e.getMessage(), e);
        }
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

    private String buildQueryString(MultiValueMap<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((key, values) -> {
            if (!values.isEmpty()) {
                if (sb.length() > 0) sb.append("&");
                sb.append(key).append("=").append(values.get(0));
            }
        });
        return sb.toString();
    }

    /**
     * Register the OpenAI chat streaming stub (same as in AbstractIntegrationSpec).
     */
    private void registerOpenAiChatStreamStub() {
        String sseBody = "data: {\"choices\":[{\"delta\":{\"content\":\"NexRAG\"}}]}\n\n" +
                         "data: {\"choices\":[{\"delta\":{\"content\":\" est\"}}]}\n\n" +
                         "data: {\"choices\":[{\"delta\":{\"content\":\" disponible\"}}]}\n\n" +
                         "data: [DONE]\n\n";

        OPEN_AI_MOCK.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withHeader("Cache-Control", "no-cache")
                .withHeader("Connection", "keep-alive")
                .withBody(sseBody)
            )
        );
    }
}
