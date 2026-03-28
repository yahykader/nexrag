package com.exemple.nexrag.service.rag.ingestion.tracker;

import com.exemple.nexrag.dto.batch.BatchInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec : BatchInfoRegistry — Registre des métadonnées de batch.
 */
@DisplayName("Spec : BatchInfoRegistry — Enregistrement, lecture et suppression des métadonnées de batch")
class BatchInfoRegistrySpec {

    private BatchInfoRegistry registry;

    private static final String BATCH_ID  = "batch-001";
    private static final String FILENAME  = "document.pdf";
    private static final String MIME_TYPE = "application/pdf";

    @BeforeEach
    void setUp() {
        registry = new BatchInfoRegistry();
    }

    // -------------------------------------------------------------------------
    // register — enregistrement d'un nouveau batch
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT enregistrer un batch et le rendre disponible via get()")
    void shouldRegisterBatchAndMakeItAvailableViaGet() {
        registry.register(BATCH_ID, FILENAME, MIME_TYPE);

        Optional<BatchInfo> result = registry.get(BATCH_ID);

        assertThat(result).isPresent();
        assertThat(result.get().batchId()).isEqualTo(BATCH_ID);
        assertThat(result.get().filename()).isEqualTo(FILENAME);
        assertThat(result.get().mimeType()).isEqualTo(MIME_TYPE);
    }

    @Test
    @DisplayName("DOIT enregistrer plusieurs batches avec des IDs distincts")
    void shouldRegisterMultipleBatchesWithDistinctIds() {
        registry.register("batch-A", "fichier-a.pdf", "application/pdf");
        registry.register("batch-B", "fichier-b.docx", "application/msword");

        assertThat(registry.size()).isEqualTo(2);
        assertThat(registry.contains("batch-A")).isTrue();
        assertThat(registry.contains("batch-B")).isTrue();
    }

    // -------------------------------------------------------------------------
    // get — présent / absent
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner Optional.empty() pour un batchId inconnu")
    void shouldReturnEmptyOptionalForUnknownBatchId() {
        Optional<BatchInfo> result = registry.get("batch-inexistant");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("DOIT retourner Optional présent pour un batchId enregistré")
    void shouldReturnPresentOptionalForRegisteredBatchId() {
        registry.register(BATCH_ID, FILENAME, MIME_TYPE);

        assertThat(registry.get(BATCH_ID)).isPresent();
    }

    // -------------------------------------------------------------------------
    // addTextEmbeddingId
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT ajouter un ID d'embedding texte au batch correspondant")
    void shouldAddTextEmbeddingIdToRegisteredBatch() {
        registry.register(BATCH_ID, FILENAME, MIME_TYPE);

        registry.addTextEmbeddingId(BATCH_ID, "embed-text-001");

        BatchInfo info = registry.get(BATCH_ID).orElseThrow();
        assertThat(info.textEmbeddings()).containsExactly("embed-text-001");
    }

    @Test
    @DisplayName("DOIT ignorer silencieusement un addTextEmbeddingId pour un batchId inconnu")
    void shouldIgnoreSilentlyAddTextEmbeddingIdForUnknownBatch() {
        // Ne doit pas lever d'exception
        registry.addTextEmbeddingId("batch-inexistant", "embed-001");

        assertThat(registry.size()).isZero();
    }

    // -------------------------------------------------------------------------
    // addImageEmbeddingId
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT ajouter un ID d'embedding image au batch correspondant")
    void shouldAddImageEmbeddingIdToRegisteredBatch() {
        registry.register(BATCH_ID, FILENAME, MIME_TYPE);

        registry.addImageEmbeddingId(BATCH_ID, "embed-img-001");

        BatchInfo info = registry.get(BATCH_ID).orElseThrow();
        assertThat(info.imageEmbeddings()).containsExactly("embed-img-001");
    }

    @Test
    @DisplayName("DOIT ignorer silencieusement un addImageEmbeddingId pour un batchId inconnu")
    void shouldIgnoreSilentlyAddImageEmbeddingIdForUnknownBatch() {
        registry.addImageEmbeddingId("batch-inexistant", "img-001");

        assertThat(registry.size()).isZero();
    }

    // -------------------------------------------------------------------------
    // totalEmbeddings
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner 0 embeddings pour un registre vide")
    void shouldReturn0EmbeddingsForEmptyRegistry() {
        assertThat(registry.totalEmbeddings()).isZero();
    }

    @Test
    @DisplayName("DOIT calculer le total des embeddings texte + image sur tous les batches")
    void shouldCalculateTotalEmbeddingsAcrossAllBatches() {
        registry.register("batch-A", "a.pdf", "application/pdf");
        registry.register("batch-B", "b.pdf", "application/pdf");

        registry.addTextEmbeddingId("batch-A", "t1");
        registry.addTextEmbeddingId("batch-A", "t2");
        registry.addImageEmbeddingId("batch-A", "img1");
        registry.addTextEmbeddingId("batch-B", "t3");

        assertThat(registry.totalEmbeddings()).isEqualTo(4);
    }

    // -------------------------------------------------------------------------
    // remove
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT supprimer un batch enregistré et le rendre absent de get()")
    void shouldRemoveRegisteredBatchAndMakeItAbsent() {
        registry.register(BATCH_ID, FILENAME, MIME_TYPE);

        registry.remove(BATCH_ID);

        assertThat(registry.get(BATCH_ID)).isEmpty();
        assertThat(registry.contains(BATCH_ID)).isFalse();
        assertThat(registry.size()).isZero();
    }

    @Test
    @DisplayName("DOIT être idempotent : un second remove ne lève pas d'exception")
    void shouldBeIdempotentOnSecondRemove() {
        registry.register(BATCH_ID, FILENAME, MIME_TYPE);

        registry.remove(BATCH_ID);
        registry.remove(BATCH_ID); // ne doit pas lever d'exception

        assertThat(registry.size()).isZero();
    }

    // -------------------------------------------------------------------------
    // contains / size
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner false pour contains() avant enregistrement")
    void shouldReturnFalseForContainsBeforeRegistration() {
        assertThat(registry.contains(BATCH_ID)).isFalse();
    }

    @Test
    @DisplayName("DOIT retourner true pour contains() après enregistrement")
    void shouldReturnTrueForContainsAfterRegistration() {
        registry.register(BATCH_ID, FILENAME, MIME_TYPE);

        assertThat(registry.contains(BATCH_ID)).isTrue();
    }

    @Test
    @DisplayName("DOIT retourner la taille exacte du registre")
    void shouldReturnExactSizeOfRegistry() {
        assertThat(registry.size()).isZero();

        registry.register("b1", "f1.pdf", "application/pdf");
        assertThat(registry.size()).isEqualTo(1);

        registry.register("b2", "f2.pdf", "application/pdf");
        assertThat(registry.size()).isEqualTo(2);
    }
}
