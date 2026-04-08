package com.exemple.nexrag.service.rag.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec d'intégration : Pipeline de retrieval — PHASE 9 / US-23 (retrieval).
 *
 * <p>SRP : couvre uniquement la validation du retrieval vectoriel (requête → passages).
 * <p>Prérequis : un document PDF est ingéré en @BeforeEach (une seule fois grâce au flag)
 *               après initialisation du webClient par AbstractIntegrationSpec.setUpBase().
 *
 * <p>Critères d'acceptance couverts :
 * <ul>
 *   <li>AC-23.1 : Requête pertinente retourne une réponse en moins de 2 secondes</li>
 *   <li>SC-004  : Retrieval < 2 000 ms</li>
 * </ul>
 *
 * @author ayahyaoui
 * @version 1.0
 */
@DisplayName("Spec intégration : Pipeline de retrieval vectoriel")
class RetrievalPipelineIntegrationSpec extends AbstractIntegrationSpec {

    private static final String UPLOAD_URL    = "/api/v1/ingestion/upload";
    private static final String STREAM_URL    = "/api/v1/assistant/stream";
    private static final long   MAX_RETRIEVAL = 2_000L; // ms

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
            public String getFilename() { return "reference.pdf"; }
        }).contentType(MediaType.APPLICATION_PDF);

        webClient.post()
            .uri(UPLOAD_URL)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(builder.build()))
            .exchange()
            .expectStatus().isOk();
    }

    // =========================================================================
    // AC-23.1 + SC-004 : Retrieval pertinent et rapide
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner une réponse via streaming en moins de 2 secondes pour une requête pertinente")
    void devraitRetournerReponseStreamingEnMoinsDe2Secondes() {
        long debut = System.currentTimeMillis();

        webClient.post()
            .uri(STREAM_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "query",     "NexRAG integration test document RAG pipeline",
                "streaming", false,
                "maxChunks", 10))
            .exchange()
            .expectStatus().isOk();

        long duree = System.currentTimeMillis() - debut;

        assertThat(duree)
            .as("Le retrieval + génération doit se terminer en moins de %d ms", MAX_RETRIEVAL)
            .isLessThan(MAX_RETRIEVAL);
    }

    @Test
    @DisplayName("DOIT retourner du contenu non-nul pour une requête liée au document ingéré")
    void devraitRetournerContenuPourRequetePertinente() {
        webClient.post()
            .uri(STREAM_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "query",     "NexRAG integration test document",
                "streaming", false))
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .value(body -> assertThat(body).isNotNull().isNotBlank());
    }
}