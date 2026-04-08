package com.exemple.nexrag.service.rag.integration;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Classe de base pour tous les tests d'intégration NexRAG (PHASE 9).
 *
 * <p>SRP : centralise l'infrastructure de test partagée (conteneurs, WireMock, cleanup).
 * <p>OCP : chaque classe fille ajoute ses propres @Test sans modifier cette classe.
 * <p>DIP : les sous-classes reçoivent les beans Spring via injection — pas d'instanciation directe.
 *
 * <p>Infrastructure :
 * <ul>
 *   <li>PostgreSQL/pgvector:pg16 — EmbeddingStore</li>
 *   <li>Redis:7-alpine — cache embeddings et déduplication</li>
 *   <li>ClamAV — scan antivirus réel</li>
 *   <li>WireMock — stub des appels OpenAI (embeddings + chat)</li>
 * </ul>
 *
 * <p>Clean code : {@link #truncateEmbeddingTables()} purge Redis ET PostgreSQL avant chaque test.
 *                 Sans la purge Redis, le hash du fichier reste en cache de déduplication entre
 *                 les runs, ce qui déclenche un faux 409 au deuxième run du même contenu.
 *
 * @author ayahyaoui
 * @version 2.0
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
public abstract class AbstractIntegrationSpec {

    // =========================================================================
    // Conteneurs partagés (démarrés une seule fois pour toute la JVM de test)
    // =========================================================================

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("nexrag_test")
            .withUsername("test_user")
            .withPassword("test_pass")
            .withReuse(true)
            .withStartupTimeout(Duration.ofMinutes(2));

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withReuse(true)
            .withStartupTimeout(Duration.ofSeconds(30));

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> CLAMAV =
        new GenericContainer<>("clamav/clamav:latest")
            .withExposedPorts(3310)
            .waitingFor(Wait.forListeningPort())
            .withReuse(true) 
            .withStartupTimeout(Duration.ofMinutes(3));

    // =========================================================================
    // WireMock — stub des appels OpenAI
    // =========================================================================

    @RegisterExtension
    static final WireMockExtension OPEN_AI_MOCK = WireMockExtension.newInstance()
        .options(WireMockConfiguration.wireMockConfig().dynamicPort())
        .build();

    // =========================================================================
    // Injection dynamique des URLs des conteneurs dans le contexte Spring
    // =========================================================================

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL / pgvector
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",  POSTGRES::getUsername);
        registry.add("spring.datasource.password",  POSTGRES::getPassword);
        registry.add("pgvector.host",     POSTGRES::getHost);
        registry.add("pgvector.port",     () -> POSTGRES.getMappedPort(5432));
        registry.add("pgvector.database", () -> "nexrag_test");
        registry.add("pgvector.user",     POSTGRES::getUsername);
        registry.add("pgvector.password", POSTGRES::getPassword);

        // Redis
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.redis.host",      REDIS::getHost);
        registry.add("spring.redis.port",      () -> REDIS.getMappedPort(6379));

        // ClamAV
        registry.add("antivirus.host", CLAMAV::getHost);
        registry.add("antivirus.port", () -> CLAMAV.getMappedPort(3310));
        registry.add("antivirus.timeout",              () -> "1000");
        registry.add("antivirus.retry.attempts",       () -> "0");
        registry.add("antivirus.health-check.enabled", () -> "false");
        registry.add("antivirus.enabled", () -> "false");

        // OpenAI → redirigé vers WireMock
        registry.add("openai.base-url", OPEN_AI_MOCK::baseUrl);
    }

    // =========================================================================
    // Client HTTP pour les tests
    // =========================================================================

    @LocalServerPort
    protected int serverPort;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("redisTemplateJson")
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    private RedisTemplate<String, Object> redisTemplate;

    protected WebTestClient webClient;

    // =========================================================================
    // Setup / Cleanup
    // =========================================================================

    /**
     * Avant chaque méthode de test :
     * - Reconfigure WebTestClient sur le port du serveur courant.
     * - Enregistre les stubs WireMock pour OpenAI.
     * - Nettoie Redis ET les tables d'embeddings PostgreSQL.
     */
    @BeforeEach
    void setUpBase() {
        webClient = WebTestClient
            .bindToServer()
            .baseUrl("http://localhost:" + serverPort)
            .responseTimeout(Duration.ofSeconds(120))
            .build();

        configureOpenAiStubs();
        truncateEmbeddingTables();
    }

    // =========================================================================
    // Stubs WireMock (OpenAI)
    // =========================================================================

    /**
     * Configure les stubs WireMock pour intercepter les appels OpenAI.
     */
   protected void configureOpenAiStubs() {
        String embeddingVector = buildEmbeddingVector(1536, 0.1);

        // POST /embeddings → vecteur 1536 dimensions
        OPEN_AI_MOCK.stubFor(
            post(urlPathEqualTo("/embeddings"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                        "object": "list",
                        "data": [{"object": "embedding", "embedding": %s, "index": 0}],
                        "model": "text-embedding-3-small",
                        "usage": {"prompt_tokens": 10, "total_tokens": 10}
                        }
                        """.formatted(embeddingVector)))
        );

        // POST /chat/completions — mode STREAMING (Accept: text/event-stream)
        OPEN_AI_MOCK.stubFor(
            post(urlPathEqualTo("/chat/completions"))
                .withHeader("Accept", containing("text/event-stream"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(
                        "data: {\"id\":\"chatcmpl-test\",\"object\":\"chat.completion.chunk\"," +
                        "\"model\":\"gpt-4o\",\"choices\":[{\"index\":0,\"delta\":" +
                        "{\"role\":\"assistant\",\"content\":\"Réponse de test NexRAG.\"}," +
                        "\"finish_reason\":null}]}\n\n" +
                        "data: {\"id\":\"chatcmpl-test\",\"object\":\"chat.completion.chunk\"," +
                        "\"model\":\"gpt-4o\",\"choices\":[{\"index\":0,\"delta\":{}," +
                        "\"finish_reason\":\"stop\"}]}\n\n" +
                        "data: [DONE]\n\n"))
        );

        // POST /chat/completions — mode SYNC (QueryTransformer, reranking, etc.)
        OPEN_AI_MOCK.stubFor(
            post(urlPathEqualTo("/chat/completions"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                        "id": "chatcmpl-test-nexrag",
                        "object": "chat.completion",
                        "created": 1700000000,
                        "model": "gpt-4o",
                        "choices": [{
                            "index": 0,
                            "message": {
                            "role": "assistant",
                            "content": "Réponse de test NexRAG."
                            },
                            "finish_reason": "stop"
                        }],
                        "usage": {"prompt_tokens": 50, "completion_tokens": 20, "total_tokens": 70}
                        }
                        """))
        );
    }

    // =========================================================================
    // Nettoyage des données entre tests
    // =========================================================================

    /**
     * Vide Redis ET les tables d'embeddings pgvector avant chaque test.
     *
     * <p>Pourquoi purger Redis : le service de déduplication stocke les hashes en Redis.
     * Sans cette purge, un fichier ingéré lors d'un premier run est rejeté en 409
     * (doublon) lors du run suivant, même si PostgreSQL a été vidé. Les deux doivent
     * être purgés ensemble pour garantir l'isolation entre les tests.
     */
    protected void truncateEmbeddingTables() {
        // ✅ Purger Redis — hashes de déduplication, cache embeddings, buckets rate limit
        try {
            redisTemplate.getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushDb();
        } catch (Exception ignored) {
            // Redis peut ne pas être disponible au premier appel
        }

        // Vider les tables pgvector
        try {
            jdbcTemplate.execute("TRUNCATE TABLE text_embeddings RESTART IDENTITY CASCADE");
            jdbcTemplate.execute("TRUNCATE TABLE image_embeddings RESTART IDENTITY CASCADE");
        } catch (Exception ignored) {
            // Les tables sont créées par LangChain4j à l'initialisation — peuvent ne pas exister encore
        }
    }

    /**
     * Compte les embeddings dans la table text_embeddings.
     */
    protected int countTextEmbeddings() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM text_embeddings", Integer.class);
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    // =========================================================================
    // Utilitaires — contenu de fichiers pour les tests
    // =========================================================================

    /**
     * Contenu EICAR standard — reconnu par ClamAV comme virus de test.
     */
    protected static byte[] eicarContent() {
        return "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"
            .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    /**
     * PDF minimal valide avec contenu unique par run (évite les collisions de hash Redis).
     * Le timestamp nano garantit un hash différent à chaque exécution.
     */
    protected static byte[] minimalPdfContent() {
        String pdf = "%PDF-1.4\n" +
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n" +
            "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n" +
            "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842]\n" +
            "   /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n" +
            "4 0 obj\n<< /Length 88 >>\nstream\n" +
            "BT /F1 12 Tf 100 700 Td (NexRAG integration test document RAG pipeline page 1) Tj ET\n" +
            "endstream\nendobj\n" +
            "5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n" +
            "%% unique-run-id: " + System.nanoTime() + "\n" +   // ✅ hash unique par run
            "xref\n0 6\n0000000000 65535 f \n" +
            "trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n0\n%%EOF";
        return pdf.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    /**
     * Texte brut pour les tests DOCX/XLSX (parsé par Tika en fallback).
     */
    protected static byte[] minimalTextContent() {
        return ("NexRAG integration test document. " +
                "Ce document contient du texte pour les tests d'ingestion DOCX et XLSX. " +
                "run-" + System.nanoTime())
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * PNG 1×1 pixel blanc minimal valide.
     */
    protected static byte[] minimalPngContent() {
        return java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwADhQGAWjR9awAAAABJRU5ErkJggg=="
        );
    }

    // =========================================================================
    // Privé
    // =========================================================================

    private static String buildEmbeddingVector(int dim, double value) {
        return "[" +
            DoubleStream.generate(() -> value)
                .limit(dim)
                .mapToObj(Double::toString)
                .collect(Collectors.joining(",")) +
            "]";
    }
}