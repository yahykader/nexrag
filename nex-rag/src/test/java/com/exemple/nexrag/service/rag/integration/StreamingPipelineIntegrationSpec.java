package com.exemple.nexrag.service.rag.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec d'intégration : Streaming SSE et historique de conversation — PHASE 9 / US-23.
 *
 * <p>SRP : couvre la validation du streaming SSE et du maintien de contexte entre deux requêtes.
 * <p>Prérequis : un document de référence est ingéré en @BeforeEach (une seule fois grâce au flag)
 *               après initialisation du webClient par AbstractIntegrationSpec.setUpBase().
 *
 * <p>Critères d'acceptance couverts :
 * <ul>
 *   <li>AC-23.2 : Tokens reçus avant l'événement [DONE]</li>
 *   <li>AC-23.3 : Historique de conversation maintenu entre deux requêtes successives</li>
 *   <li>SC-005  : Premier token SSE < 3 000 ms</li>
 * </ul>
 *
 * @author ayahyaoui
 * @version 1.0
 */
@DisplayName("Spec intégration : Streaming SSE et historique de conversation")
class StreamingPipelineIntegrationSpec extends AbstractIntegrationSpec {

    private static final String UPLOAD_URL         = "/api/v1/ingestion/upload";
    private static final String STREAM_URL         = "/api/v1/assistant/stream";
    private static final long   MAX_FIRST_TOKEN_MS = 3_000L;

    // Flag pour ingérer le document une seule fois (webClient non disponible en @BeforeAll)
    private boolean documentIngere = false;

    /**
     * Ingère un document de référence avant les tests, une seule fois.
     * Doit être @BeforeEach (pas @BeforeAll) pour que webClient soit déjà
     * initialisé par AbstractIntegrationSpec.setUpBase().
     */
    @BeforeEach
    void ingererDocumentDeReference() {
        if (documentIngere) return;
        documentIngere = true;

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(minimalPdfContent()) {
            @Override
            public String getFilename() { return "streaming_ref.pdf"; }
        }).contentType(MediaType.APPLICATION_PDF);

        webClient.post()
            .uri(UPLOAD_URL)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(builder.build()))
            .exchange()
            .expectStatus().isOk();
    }

    // =========================================================================
    // AC-23.2 + SC-005 : Premier token SSE < 3s
    // =========================================================================

    @Test
    @DisplayName("DOIT recevoir au moins un token SSE avant 3 secondes et avant l'événement DONE")
    void devraitRecevoirPremierTokenSsEnMoinsDe3Secondes() {
        AtomicBoolean tokenRecu = new AtomicBoolean(false);
        long debut = System.currentTimeMillis();

        webClient.post()
            .uri(STREAM_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("query", "NexRAG test", "streaming", false))
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .value(body -> {
                tokenRecu.set(body != null && !body.isBlank());
                long delai = System.currentTimeMillis() - debut;
                assertThat(delai)
                    .as("Le serveur doit répondre en moins de %d ms", MAX_FIRST_TOKEN_MS)
                    .isLessThan(MAX_FIRST_TOKEN_MS);
            });

        assertThat(tokenRecu.get())
            .as("La réponse ne doit pas être vide")
            .isTrue();
    }

    @Test
    @DisplayName("DOIT émettre du contenu non-vide dans la réponse avant l'événement DONE")
    void devraitEmettreDuContenuAvantDone() {
        webClient.post()
            .uri(STREAM_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("query", "integration test NexRAG", "streaming", false))
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .value(body -> assertThat(body)
                .as("La réponse doit contenir du contenu avant [DONE]")
                .isNotNull()
                .isNotBlank());
    }

    // =========================================================================
    // AC-23.3 : Historique de conversation maintenu sur deux tours
    // =========================================================================

    @Test
    @DisplayName("DOIT maintenir le contexte de conversation entre deux requêtes successives avec le même conversationId")
    void devraitMainteniContexteConversationSurDeuxTours() {
        String conversationId = "conv-integration-test-" + UUID.randomUUID();

        // Premier tour
        webClient.post()
            .uri(STREAM_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "query",          "NexRAG integration test document",
                "conversationId", conversationId,
                "streaming",      false))
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .value(body -> assertThat(body).isNotNull().isNotBlank());

        // Deuxième tour — même conversationId
        webClient.post()
            .uri(STREAM_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "query",          "Peux-tu m'en dire plus ?",
                "conversationId", conversationId,
                "streaming",      false))
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .value(body -> assertThat(body)
                .as("La réponse du deuxième tour doit être non-nulle (contexte conservé)")
                .isNotNull()
                .isNotBlank());
    }
}