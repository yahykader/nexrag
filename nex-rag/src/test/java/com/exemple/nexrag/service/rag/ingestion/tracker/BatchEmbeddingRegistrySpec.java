package com.exemple.nexrag.service.rag.ingestion.tracker;

import com.exemple.nexrag.dto.batch.TrackerStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec : BatchEmbeddingRegistry — Registre des embeddings créés par batch.
 */
@DisplayName("Spec : BatchEmbeddingRegistry — Enregistrement, lecture et suppression des embeddings par batch")
class BatchEmbeddingRegistrySpec {

    private BatchEmbeddingRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new BatchEmbeddingRegistry();
    }

    private static final String BATCH_ID = "batch-001";

    // -------------------------------------------------------------------------
    // addTextEmbedding / addImageEmbedding
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT créer le batch et y ajouter un embedding texte")
    void shouldCreateBatchAndAddTextEmbedding() {
        registry.addTextEmbedding(BATCH_ID, "t1");

        assertThat(registry.getTextIds(BATCH_ID)).containsExactly("t1");
    }

    @Test
    @DisplayName("DOIT créer le batch et y ajouter un embedding image")
    void shouldCreateBatchAndAddImageEmbedding() {
        registry.addImageEmbedding(BATCH_ID, "img-1");

        assertThat(registry.getImageIds(BATCH_ID)).containsExactly("img-1");
    }

    @Test
    @DisplayName("DOIT accumuler plusieurs embeddings texte pour le même batch")
    void shouldAccumulateMultipleTextEmbeddingsForSameBatch() {
        registry.addTextEmbedding(BATCH_ID, "t1");
        registry.addTextEmbedding(BATCH_ID, "t2");
        registry.addTextEmbedding(BATCH_ID, "t3");

        assertThat(registry.getTextIds(BATCH_ID)).containsExactlyInAnyOrder("t1", "t2", "t3");
    }

    // -------------------------------------------------------------------------
    // get
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner le BatchEmbeddings pour un batch existant")
    void shouldReturnBatchEmbeddingsForExistingBatch() {
        registry.addTextEmbedding(BATCH_ID, "t1");

        BatchEmbeddings batch = registry.get(BATCH_ID);

        assertThat(batch).isNotNull();
        assertThat(batch.getTextEmbeddingIds()).containsExactly("t1");
    }

    @Test
    @DisplayName("DOIT retourner null pour un batch inexistant")
    void shouldReturnNullForUnknownBatch() {
        assertThat(registry.get("inconnu")).isNull();
    }

    // -------------------------------------------------------------------------
    // getTextIds / getImageIds — batch inexistant
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner une liste vide pour getTextIds() sur batch inconnu")
    void shouldReturnEmptyListForTextIdsOnUnknownBatch() {
        assertThat(registry.getTextIds("inconnu")).isEmpty();
    }

    @Test
    @DisplayName("DOIT retourner une liste vide pour getImageIds() sur batch inconnu")
    void shouldReturnEmptyListForImageIdsOnUnknownBatch() {
        assertThat(registry.getImageIds("inconnu")).isEmpty();
    }

    // -------------------------------------------------------------------------
    // contains
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner true après ajout d'un embedding")
    void shouldReturnTrueAfterAddingEmbedding() {
        registry.addTextEmbedding(BATCH_ID, "t1");

        assertThat(registry.contains(BATCH_ID)).isTrue();
    }

    @Test
    @DisplayName("DOIT retourner false pour un batch inexistant")
    void shouldReturnFalseForUnknownBatch() {
        assertThat(registry.contains("inconnu")).isFalse();
    }

    // -------------------------------------------------------------------------
    // remove
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT supprimer le batch et retourner false pour contains() après suppression")
    void shouldRemoveBatchAndReturnFalseForContains() {
        registry.addTextEmbedding(BATCH_ID, "t1");
        registry.remove(BATCH_ID);

        assertThat(registry.contains(BATCH_ID)).isFalse();
    }

    @Test
    @DisplayName("DOIT être idempotent : remove() sur batch inexistant ne lève pas d'exception")
    void shouldBeIdempotentRemoveOnUnknownBatch() {
        registry.remove("inexistant"); // no exception
    }

    // -------------------------------------------------------------------------
    // clear
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT supprimer tous les batches lors d'un clear()")
    void shouldClearAllBatches() {
        registry.addTextEmbedding("b1", "t1");
        registry.addImageEmbedding("b2", "img-1");

        registry.clear();

        assertThat(registry.contains("b1")).isFalse();
        assertThat(registry.contains("b2")).isFalse();
    }

    // -------------------------------------------------------------------------
    // getStats
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner des stats vides pour un registre vide")
    void shouldReturnEmptyStatsForEmptyRegistry() {
        TrackerStats stats = registry.getStats();

        assertThat(stats.activeBatches()).isZero();
        assertThat(stats.textEmbeddings()).isZero();
        assertThat(stats.imageEmbeddings()).isZero();
        assertThat(stats.totalEmbeddings()).isZero();
    }

    @Test
    @DisplayName("DOIT comptabiliser correctement les embeddings dans les stats")
    void shouldCountEmbeddingsCorrectlyInStats() {
        registry.addTextEmbedding("b1", "t1");
        registry.addTextEmbedding("b1", "t2");
        registry.addImageEmbedding("b2", "img-1");

        TrackerStats stats = registry.getStats();

        assertThat(stats.activeBatches()).isEqualTo(2);
        assertThat(stats.textEmbeddings()).isEqualTo(2);
        assertThat(stats.imageEmbeddings()).isEqualTo(1);
        assertThat(stats.totalEmbeddings()).isEqualTo(3);
    }
}
