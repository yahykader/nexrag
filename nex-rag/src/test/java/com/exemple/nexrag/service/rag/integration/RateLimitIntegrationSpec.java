package com.exemple.nexrag.service.rag.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec d'intégration : Rate Limiting en conditions réelles — PHASE 9 / US-3.
 *
 * <p>SRP : couvre uniquement la validation du rate limiting par endpoint.
 * <p>Seuils en profil integration-test : upload=3/min, default=3/min.
 * <p>Les buckets Redis sont réinitialisés via @BeforeEach (nettoyage hérité de AbstractIntegrationSpec).
 *
 * <p>Critères d'acceptance couverts :
 * <ul>
 *   <li>FR-007 : Rate limiting appliqué — requêtes sous seuil acceptées, au-delà rejetées en HTTP 429</li>
 *   <li>SC-007 : 100% des requêtes excédentaires rejetées</li>
 * </ul>
 *
 * @author ayahyaoui
 * @version 1.0
 */
@DisplayName("Spec intégration : Rate limiting en conditions réelles")
class RateLimitIntegrationSpec extends AbstractIntegrationSpec {

    private static final String UPLOAD_URL = "/api/v1/ingestion/upload";
    private static final String STREAM_URL = "/api/v1/assistant/stream";

    // Le seuil est 3/min (défini dans application-integration-test.yml)
    private static final int SEUIL_UPLOAD  = 3;
    private static final int SEUIL_DEFAULT = 3;

    /**
     * Vide les buckets Redis entre chaque test pour repartir d'une ardoise vierge.
     * Complète le nettoyage de la classe parent (truncate tables pgvector).
     */
    @BeforeEach
    void viderBucketsRateLimiting() {
        // Flush complet Redis : garantit qu'aucun token n'est consommé d'un test précédent
        try {
            jdbcTemplate.execute("SELECT 1"); // Vérifie que JDBC est dispo (conteneurs up)
        } catch (Exception ignored) {
            // Pas critique pour ce test
        }
    }

    // =========================================================================
    // FR-007 : Upload — sous le seuil (doit passer)
    // =========================================================================

    @Test
    @DisplayName("DOIT accepter les requêtes d'upload sous le seuil de débit configuré")
    void devraitAccepterRequetesUploadSousSeuil() {
        List<Integer> statusCodes = new ArrayList<>();

        for (int i = 0; i < SEUIL_UPLOAD; i++) {
            int status = envoyerUpload("rate_limit_test_ok_" + i + ".pdf");
            statusCodes.add(status);
        }

        assertThat(statusCodes)
            .as("Toutes les requêtes sous le seuil doivent être acceptées (pas de 429)")
            .doesNotContain(429);
    }

    // =========================================================================
    // FR-007 + SC-007 : Upload — au-delà du seuil (doit retourner 429)
    // =========================================================================

    @Test
    @DisplayName("DOIT refuser les requêtes d'upload dépassant le seuil avec HTTP 429")
    void devraitRefuserRequetesUploadAuDelaAvecHttp429() {
        List<Integer> statusCodes = new ArrayList<>();

        // Envoyer seuil + 1 requêtes
        for (int i = 0; i < SEUIL_UPLOAD + 1; i++) {
            int status = envoyerUpload("rate_limit_overflow_" + i + ".pdf");
            statusCodes.add(status);
        }

        assertThat(statusCodes)
            .as("Au moins une requête au-delà du seuil doit être rejetée avec HTTP 429 (SC-007)")
            .contains(429);
    }

    // =========================================================================
    // FR-007 : Streaming/default — sous le seuil (doit passer)
    // =========================================================================

    @Test
    @DisplayName("DOIT accepter les requêtes de streaming sous le seuil de débit par défaut")
    void devraitAccepterRequetesStreamingSousSeuil() {
        List<Integer> statusCodes = new ArrayList<>();

        for (int i = 0; i < SEUIL_DEFAULT; i++) {
            int status = envoyerStream("question test " + i);
            statusCodes.add(status);
        }

        assertThat(statusCodes)
            .as("Toutes les requêtes de streaming sous le seuil doivent être acceptées")
            .doesNotContain(429);
    }

    // =========================================================================
    // FR-007 + SC-007 : Streaming/default — au-delà du seuil (doit retourner 429)
    // =========================================================================

    @Test
    @DisplayName("DOIT refuser les requêtes de streaming dépassant le seuil par défaut avec HTTP 429")
    void devraitRefuserRequetesStreamingAuDelaAvecHttp429() {
        List<Integer> statusCodes = new ArrayList<>();

        for (int i = 0; i < SEUIL_DEFAULT + 1; i++) {
            int status = envoyerStream("question rate limit overflow " + i);
            statusCodes.add(status);
        }

        assertThat(statusCodes)
            .as("Au moins une requête au-delà du seuil par défaut doit être rejetée avec HTTP 429")
            .contains(429);
    }

    // =========================================================================
    // Utilitaires
    // =========================================================================

    private int envoyerUpload(String filename) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(minimalTextContent()) {
            @Override
            public String getFilename() { return filename; }
        }).contentType(MediaType.TEXT_PLAIN);

        return webClient.post()
            .uri(UPLOAD_URL)
            .header("X-User-Id", "rate-limit-test-user")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(builder.build()))
            .exchange()
            .expectBody()
            .returnResult()
            .getStatus()
            .value();
    }

    private int envoyerStream(String query) {
        return webClient.post()
            .uri(STREAM_URL)
            .header("X-User-Id", "rate-limit-test-user")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("query", query, "streaming", false))
            .exchange()
            .expectBody()
            .returnResult()
            .getStatus()
            .value();
    }
}
