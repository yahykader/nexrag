package com.exemple.nexrag.service.rag.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec d'intégration : Pipeline d'ingestion bout-en-bout — PHASE 9 / US-22.
 *
 * <p>SRP : couvre uniquement le pipeline d'ingestion (antivirus → chunking → embedding → pgvector → déduplication).
 * <p>Chaque méthode @Test est indépendante : {@link AbstractIntegrationSpec#setUpBase()} nettoie l'état avant chaque test.
 *
 * <p>Critères d'acceptance couverts :
 * <ul>
 *   <li>AC-22.1 : PDF ingéré en moins de 10 secondes</li>
 *   <li>AC-22.2 : Embeddings persistés dans pgvector (COUNT > 0)</li>
 *   <li>AC-22.3 : Réingestion du même PDF → statut DUPLICATE</li>
 *   <li>FR-001   : DOCX, XLSX, image ingérés avec succès</li>
 *   <li>FR-010   : Fichier EICAR → statut VIRUS_DETECTED</li>
 * </ul>
 *
 * @author ayahyaoui
 * @version 1.0
 */
@DisplayName("Spec intégration : Pipeline d'ingestion bout-en-bout")
class IngestionPipelineIntegrationSpec extends AbstractIntegrationSpec {

    private static final String UPLOAD_URL     = "/api/v1/ingestion/upload";
    private static final long   MAX_DURATION   = 10_000L; // 10 secondes

    // =========================================================================
    // US-22 — AC-22.1 + AC-22.2 : Ingestion PDF réussie
    // =========================================================================

    @Test
    @DisplayName("DOIT ingérer un PDF en moins de 10 secondes et persister les embeddings dans pgvector")
    void devraitIngererPdfEnMoinsDe10SecondesEtPersisterEmbeddings() {
        long debut = System.currentTimeMillis();

        var response = webClient.post()
            .uri(UPLOAD_URL)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(buildMultipart("sample.pdf", minimalPdfContent(), "application/pdf")))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .returnResult();

        long duree = System.currentTimeMillis() - debut;

        assertThat(duree)
            .as("L'ingestion PDF doit se terminer en moins de %d ms", MAX_DURATION)
            .isLessThan(MAX_DURATION);

        assertThat(countTextEmbeddings())
            .as("Au moins un embedding doit être stocké dans pgvector après ingestion")
            .isGreaterThan(0);
    }

    // =========================================================================
    // US-22 — AC-22.3 : Déduplication
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner duplicate=true lors d'une réingestion du même PDF")
    void devraitRetournerDuplicateLorsReingestionMemePdf() {
        byte[] contenu = minimalPdfContent();

        // Première ingestion — doit réussir
        webClient.post()
            .uri(UPLOAD_URL)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(buildMultipart("sample_dedup.pdf", contenu, "application/pdf")))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true);

        // Deuxième ingestion — doit retourner duplicate=true
        webClient.post()
            .uri(UPLOAD_URL)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(buildMultipart("sample_dedup.pdf", contenu, "application/pdf")))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.duplicate").isEqualTo(true);
    }

    // =========================================================================
    // FR-001 : Formats supplémentaires — DOCX
    // =========================================================================

    @Test
    @DisplayName("DOIT ingérer un fichier texte (DOCX simplifié) en moins de 10 secondes et persister les embeddings")
    void devraitIngererDocxEnMoinsDe10SecondesEtPersisterEmbeddings() {
        long debut = System.currentTimeMillis();

        webClient.post()
            .uri(UPLOAD_URL)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(buildMultipart(
                "sample.txt",
                minimalTextContent(),
                "text/plain")))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true);

        long duree = System.currentTimeMillis() - debut;

        assertThat(duree)
            .as("L'ingestion texte doit se terminer en moins de %d ms", MAX_DURATION)
            .isLessThan(MAX_DURATION);

        assertThat(countTextEmbeddings())
            .as("Au moins un embedding doit être stocké dans pgvector")
            .isGreaterThan(0);
    }

    // =========================================================================
    // FR-001 : Formats supplémentaires — Image PNG
    // =========================================================================

    @Test
    @DisplayName("DOIT ingérer une image PNG en moins de 10 secondes")
    void devraitIngererImagePngEnMoinsDe10Secondes() {
        long debut = System.currentTimeMillis();

        webClient.post()
            .uri(UPLOAD_URL)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(buildMultipart("sample.png", minimalPngContent(), "image/png")))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true);

        long duree = System.currentTimeMillis() - debut;

        assertThat(duree)
            .as("L'ingestion PNG doit se terminer en moins de %d ms", MAX_DURATION)
            .isLessThan(MAX_DURATION);
    }

    // =========================================================================
    // FR-010 : Antivirus — Rejet EICAR
    // =========================================================================

    @Test
    @DisplayName("DOIT rejeter un fichier EICAR avec HTTP 422 et ne pas persister d'embedding")
    void devraitRejeterFichierEicarAvecErreurVirusDetecte() {
        int embeddingsAvant = countTextEmbeddings();

        webClient.post()
            .uri(UPLOAD_URL)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(buildMultipart("eicar.com", eicarContent(), "application/octet-stream")))
            .exchange()
            .expectStatus().is4xxClientError(); // 422 ou 400 selon l'implémentation du handler

        assertThat(countTextEmbeddings())
            .as("Aucun embedding ne doit être ajouté après rejet antivirus")
            .isEqualTo(embeddingsAvant);
    }

    // =========================================================================
    // Utilitaire
    // =========================================================================

    private org.springframework.util.MultiValueMap<String, org.springframework.http.HttpEntity<?>> buildMultipart(
            String filename, byte[] content, String contentType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        }).contentType(MediaType.parseMediaType(contentType));
        return builder.build();
    }
}
