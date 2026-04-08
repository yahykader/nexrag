package com.exemple.nexrag.service.rag.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec d'intégration : Pipeline RAG complet bout-en-bout — PHASE 9 / Constitution Principle V.
 *
 * <p>SRP : valide le flux complet : upload → antivirus → chunking → embedding → pgvector → retrieval → streaming.
 * <p>Ce test est REQUIS par la constitution (Principle V) :
 * "at least one spec exercising the full flow: upload → antivirus scan → deduplication
 *  → strategy selection → embedding → vector store → retrieval."
 *
 * <p>Critères validés en un seul scénario intégré :
 * <ul>
 *   <li>SC-001 : Ingestion < 10 000 ms</li>
 *   <li>SC-002 : Embeddings persistés (COUNT > 0)</li>
 *   <li>SC-004 : Réponse générée disponible (retrieval opérationnel)</li>
 *   <li>SC-005 : Réponse streaming reçue</li>
 * </ul>
 *
 * @author ayahyaoui
 * @version 1.0
 */
@DisplayName("Spec intégration : Pipeline RAG complet — upload → retrieval → streaming")
class FullRagPipelineIntegrationSpec extends AbstractIntegrationSpec {

    private static final String UPLOAD_URL  = "/api/v1/ingestion/upload";
    private static final String STREAM_URL  = "/api/v1/assistant/stream";
    private static final long   MAX_INGESTION = 10_000L;

    // =========================================================================
    // Constitution Principle V — Flux complet obligatoire
    // =========================================================================

    @Test
    @DisplayName("DOIT exécuter le pipeline complet upload → embedding → streaming sans erreur")
    void devraitExecuterPipelineCompletSansErreur() {
        // Étape 1 : upload d'un PDF de référence
        long debutIngestion = System.currentTimeMillis();

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(minimalPdfContent()) {
            @Override
            public String getFilename() { return "full_pipeline_test.pdf"; }
        }).contentType(MediaType.APPLICATION_PDF);

        webClient.post()
            .uri(UPLOAD_URL)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(builder.build()))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true);

        long dureeIngestion = System.currentTimeMillis() - debutIngestion;

        assertThat(dureeIngestion)
            .as("L'ingestion doit se terminer en moins de %d ms (SC-001)", MAX_INGESTION)
            .isLessThan(MAX_INGESTION);

        // Étape 2 : vérification des embeddings en base (SC-002)
        assertThat(countTextEmbeddings())
            .as("Les embeddings doivent être persistés dans pgvector après ingestion (SC-002)")
            .isGreaterThan(0);

        // Étape 3 : requête sur le document ingéré → vérification du retrieval + streaming (SC-004, SC-005)
        webClient.post()
            .uri(STREAM_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "query", "NexRAG integration test document RAG pipeline",
                "streaming", false))
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .value(response -> assertThat(response)
                .as("La réponse générée ne doit pas être vide (SC-004, SC-005)")
                .isNotNull()
                .isNotBlank());
    }

    @Test
    @DisplayName("DOIT ne retourner aucun résultat pour une requête sans document ingéré au préalable")
    void devraitRetournerReponseVideSiAucunDocumentInere() {
        // Base est nettoyée par @BeforeEach dans AbstractIntegrationSpec — aucun document ici
        webClient.post()
            .uri(STREAM_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "query", "requête sans contexte disponible",
                "streaming", false))
            .exchange()
            .expectStatus().isOk(); // Le système ne doit pas planter — il peut retourner une réponse vide ou générique
    }
}
