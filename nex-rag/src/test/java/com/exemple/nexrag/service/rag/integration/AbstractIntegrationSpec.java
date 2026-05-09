package com.exemple.nexrag.service.rag.integration;

import com.exemple.nexrag.IntegrationTestConfiguration;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Base class for all integration tests in Phase 9.
 * Manages lifecycle of shared infrastructure containers (PostgreSQL, Redis, ClamAV, WireMock).
 * Provides @DynamicPropertySource to override Spring properties at runtime.
 * Implements @BeforeEach cleanup: DELETE /api/files, FLUSHALL Redis, resetAll WireMock, register stubs.
 * <p>
 * Architecture: Singleton Container Pattern (Testcontainers .withReuse(true)) shares containers
 * across 5 IntegrationSpec classes, reducing startup overhead from 4×30s to 1×30s.
 * <p>
 * Compliance: Follows NexRAG Test Constitution v1.1.0 — Principle II (SOLID DIP — tests call REST APIs,
 * not direct repositories), Principle III (separation of test/main source), Principle IV (80% coverage
 * enforced separately via JaCoCo Maven config).
 */
@Slf4j
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
@ActiveProfiles("integration-test")
@Import(IntegrationTestConfiguration.class)
public abstract class AbstractIntegrationSpec {

    // ============ Static Containers (Singleton Pattern with .withReuse) ============

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
        .withDatabaseName("nexrag_test")
        .withUsername("testuser")
        .withPassword("testpass")
        .withReuse(true);

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379)
        .withReuse(true);

    @Container
    static final GenericContainer<?> CLAMAV = new GenericContainer<>("clamav/clamav:latest")
        .withExposedPorts(3310)
        .withReuse(true);

    @RegisterExtension
    static final WireMockExtension OPEN_AI_MOCK = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    // ============ Injected Dependencies ============

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired(required = false)
    protected RedisTemplate<String, String> redisTemplate;

    // ============ Dynamic Property Override ============

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL datasource
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // Redis
        registry.add("spring.redis.host", REDIS::getHost);
        registry.add("spring.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.redis.password", () -> "");

        // ClamAV antivirus
        registry.add("antivirus.host", CLAMAV::getHost);
        registry.add("antivirus.port", () -> CLAMAV.getMappedPort(3310));

        // OpenAI (WireMock)
        registry.add("openai.base-url", OPEN_AI_MOCK::baseUrl);
        registry.add("openai.chat.api-url", () -> OPEN_AI_MOCK.baseUrl() + "/v1/chat/completions");
    }

    // ============ Cleanup & Stub Registration (@BeforeEach) ============

    @BeforeEach
    void integrationTestSetup() {
        log.info("📋 Starting @BeforeEach cleanup and WireMock stub registration");

        // Cache Redis vidé → chaque test démarre en cold cache (spec.md §Edge Cases: "cold start lors d'une requête d'embedding")
        // This implicit coverage of the "cold cache" edge case applies to all tests.

        // 1. DELETE /api/files via REST to clear all ingested documents
        try {
            log.debug("Calling DELETE /api/files to clear documents");
            restTemplate.delete("/api/files");
            log.debug("✅ DELETE /api/files succeeded");
        } catch (Exception e) {
            log.warn("⚠️ DELETE /api/files failed (expected if endpoint doesn't exist yet): {}", e.getMessage());
        }

        // 2. FLUSHALL Redis
        if (redisTemplate != null) {
            try {
                log.debug("Flushing Redis cache");
                var connection = redisTemplate.getConnectionFactory().getConnection();
                if (connection != null) {
                    connection.flushAll();
                    connection.close();
                    log.debug("✅ Redis FLUSHALL succeeded");
                }
            } catch (Exception e) {
                log.warn("⚠️ Redis FLUSHALL failed: {}", e.getMessage());
            }
        }

        // 3. Reset all WireMock stubs
        OPEN_AI_MOCK.resetAll();
        log.debug("✅ WireMock stubs reset");

        // 4. Register stubs for OpenAI embeddings and chat
        registerOpenAiEmbeddingsStub();
        registerOpenAiChatStreamStub();

        log.info("✅ @BeforeEach cleanup and stub registration complete");
    }

    // ============ WireMock Stub Helpers ============

    /**
     * Stub: POST /v1/embeddings → returns a 1536-dim vector with all values = 0.1
     * This ensures cosine similarity between documents and queries is > 0 (valid for retrieval).
     */
    private void registerOpenAiEmbeddingsStub() {
        // Build embedding response with 1536 floats set to 0.1
        StringBuilder embeddingJson = new StringBuilder("[");
        for (int i = 0; i < 1536; i++) {
            if (i > 0) embeddingJson.append(",");
            embeddingJson.append("0.1");
        }
        embeddingJson.append("]");

        String responseBody = String.format(
            "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":%s}],\"model\":\"text-embedding-3-small\",\"usage\":{\"prompt_tokens\":10,\"total_tokens\":10}}",
            embeddingJson
        );

        OPEN_AI_MOCK.stubFor(post(urlPathEqualTo("/v1/embeddings"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(responseBody)
            )
        );

        log.debug("✅ Registered WireMock stub: POST /v1/embeddings");
    }

    /**
     * Stub: POST /v1/chat/completions → returns Server-Sent Events (SSE) stream
     * with 3 tokens ("NexRAG", " est", " disponible") then [DONE].
     */
    private void registerOpenAiChatStreamStub() {
        String sseBody = buildOpenAiSseResponse();

        OPEN_AI_MOCK.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withHeader("Cache-Control", "no-cache")
                .withHeader("Connection", "keep-alive")
                .withBody(sseBody)
            )
        );

        log.debug("✅ Registered WireMock stub: POST /v1/chat/completions (SSE stream)");
    }

    /**
     * Build OpenAI SSE response format (streaming chat completion).
     * Format: data: {"choices":[{"delta":{"content":"..."}}]}\n\ndata: [DONE]\n\n
     */
    private String buildOpenAiSseResponse() {
        return "data: {\"choices\":[{\"delta\":{\"content\":\"NexRAG\"}}]}\n\n" +
               "data: {\"choices\":[{\"delta\":{\"content\":\" est\"}}]}\n\n" +
               "data: {\"choices\":[{\"delta\":{\"content\":\" disponible\"}}]}\n\n" +
               "data: [DONE]\n\n";
    }
}
