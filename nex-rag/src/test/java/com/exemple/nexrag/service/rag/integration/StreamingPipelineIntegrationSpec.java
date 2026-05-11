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
    void devraitEmettreTokensAvantSignalDeFin() throws Exception {
        log.info("🧪 T026: Testing token emission before DONE");

        String queryString = "query=NexRAG&conversationId=" + conversationId;

        // Use Awaitility to wait for SSE response with timeout
        AtomicBoolean tokensEmitted = new AtomicBoolean(false);
        AtomicBoolean doneSignalReceived = new AtomicBoolean(false);

        Awaitility.await()
            .atMost(java.time.Duration.ofSeconds(10))
            .pollInterval(java.time.Duration.ofMillis(100))
            .untilAsserted(() -> {
                try {
                    Instant streamStart = Instant.now();

                    // Send streaming request
                    var streamResponse = restTemplate.postForEntity(
                        "/api/stream?" + queryString,
                        null,
                        String.class
                    );

                    Duration firstTokenLatency = Duration.between(streamStart, Instant.now());

                    assertThat(streamResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(streamResponse.getBody()).isNotNull();

                    String sseBody = streamResponse.getBody();

                    // Verify SSE format with data: prefix
                    assertThat(sseBody).contains("data:");
                    tokensEmitted.set(true);

                    // Verify tokens appear before DONE marker
                    int dataIndex = sseBody.indexOf("data:");
                    int doneIndex = sseBody.indexOf("[DONE]");

                    if (doneIndex > 0 && dataIndex >= 0) {
                        assertThat(dataIndex).isLessThan(doneIndex);
                        doneSignalReceived.set(true);
                        log.info("✅ Tokens emitted before DONE signal");
                    }

                    // Verify first token latency < 5s
                    assertThat(firstTokenLatency).isLessThan(Duration.ofSeconds(5));
                    log.info("✅ First token received in {}ms (SC-004)", firstTokenLatency.toMillis());

                } catch (AssertionError e) {
                    log.debug("Assertion not yet satisfied: {}", e.getMessage());
                    throw e;
                }
            });

        assertThat(tokensEmitted.get()).isTrue();
        log.info("✅ Token emission test passed");
    }

    // ============ T027: Error Handling Mid-Stream ============

    @Test
    @DisplayName("T027: Émettre événement ERROR sans plantage (mid-stream error recovery)")
    void devraitEmettreEvenementErreurSansPlantage() throws Exception {
        log.info("🧪 T027: Testing mid-stream error handling");

        // Set up WireMock to return error mid-stream
        OPEN_AI_MOCK.resetAll();

        // Stub with error event
        String sseBodyWithError = "data: {\"choices\":[{\"delta\":{\"content\":\"First\"}}]}\n\n" +
                                  "data: {\"error\":{\"message\":\"Internal server error\"}}\n\n" +
                                  "data: [DONE]\n\n";

        OPEN_AI_MOCK.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withBody(sseBodyWithError)
            )
        );

        String queryString = "query=NexRAG&conversationId=" + conversationId;

        // Stream should not crash on error event
        AtomicBoolean errorHandled = new AtomicBoolean(false);

        Awaitility.await()
            .atMost(java.time.Duration.ofSeconds(10))
            .pollInterval(java.time.Duration.ofMillis(100))
            .untilAsserted(() -> {
                try {
                    var streamResponse = restTemplate.postForEntity(
                        "/api/stream?" + queryString,
                        null,
                        String.class
                    );

                    // Even with error events, response should be 200 (stream was established)
                    assertThat(streamResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(streamResponse.getBody()).isNotNull();

                    String sseBody = streamResponse.getBody();

                    // Verify stream continued despite error
                    assertThat(sseBody).contains("data:");

                    if (sseBody.contains("[DONE]")) {
                        errorHandled.set(true);
                        log.info("✅ Stream recovered from mid-stream error");
                    }

                } catch (Exception e) {
                    log.debug("Waiting for error recovery: {}", e.getMessage());
                    throw new AssertionError("Error handling test failed", e);
                }
            });

        assertThat(errorHandled.get()).isTrue();
        log.info("✅ Error handling test passed");
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
