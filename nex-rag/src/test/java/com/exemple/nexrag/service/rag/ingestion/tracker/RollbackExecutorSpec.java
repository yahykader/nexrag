package com.exemple.nexrag.service.rag.ingestion.tracker;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Spec : RollbackExecutor — Suppression best-effort des embeddings sur erreur d'ingestion.
 *
 * Couverture de branche : 100 % (boucle vide, succès, exception interceptée).
 */
@DisplayName("Spec : RollbackExecutor — Rollback best-effort avec 100% de couverture de branches")
@ExtendWith(MockitoExtension.class)
class RollbackExecutorSpec {

    @Mock private EmbeddingStore<TextSegment> textStore;
    @Mock private EmbeddingStore<TextSegment> imageStore;

    private RollbackExecutor rollbackExecutor;

    @BeforeEach
    void setUp() {
        // Injection manuelle car @Qualifier n'est pas supporté par @InjectMocks
        rollbackExecutor = new RollbackExecutor(textStore, imageStore);
    }

    // -------------------------------------------------------------------------
    // Batch vide — branche : boucle sans itération
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner 0 pour un batch sans embeddings")
    void shouldReturn0ForEmptyBatch() {
        BatchEmbeddings vide = new BatchEmbeddings();

        int deleted = rollbackExecutor.rollback("batch-vide", vide);

        assertThat(deleted).isZero();
        verifyNoInteractions(textStore, imageStore);
    }

    // -------------------------------------------------------------------------
    // Suppression réussie — branche : try sans exception
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT supprimer 5 embeddings texte + 2 images et retourner 7")
    void shouldDelete5TextAnd2ImageEmbeddingsAndReturn7() {
        BatchEmbeddings batch = new BatchEmbeddings();
        for (int i = 1; i <= 5; i++) batch.addTextEmbedding("text-" + i);
        batch.addImageEmbedding("img-1");
        batch.addImageEmbedding("img-2");

        // Mockito 5 appelle les méthodes default — stub explicite nécessaire
        doNothing().when(textStore).remove(anyString());
        doNothing().when(imageStore).remove(anyString());

        int deleted = rollbackExecutor.rollback("batch-001", batch);

        assertThat(deleted).isEqualTo(7);
        verify(textStore,  times(5)).remove(anyString());
        verify(imageStore, times(2)).remove(anyString());
    }

    @Test
    @DisplayName("DOIT supprimer uniquement des embeddings texte quand aucune image n'existe")
    void shouldDeleteOnlyTextEmbeddingsWhenNoImages() {
        BatchEmbeddings batch = new BatchEmbeddings();
        batch.addTextEmbedding("t1");
        batch.addTextEmbedding("t2");
        batch.addTextEmbedding("t3");

        doNothing().when(textStore).remove(anyString());

        int deleted = rollbackExecutor.rollback("batch-text-only", batch);

        assertThat(deleted).isEqualTo(3);
        verify(textStore,  times(3)).remove(anyString());
        verifyNoInteractions(imageStore);
    }

    @Test
    @DisplayName("DOIT supprimer uniquement des embeddings image quand aucun texte n'existe")
    void shouldDeleteOnlyImageEmbeddingsWhenNoText() {
        BatchEmbeddings batch = new BatchEmbeddings();
        batch.addImageEmbedding("img-1");

        doNothing().when(imageStore).remove(anyString());

        int deleted = rollbackExecutor.rollback("batch-img-only", batch);

        assertThat(deleted).isEqualTo(1);
        verifyNoInteractions(textStore);
        verify(imageStore, times(1)).remove(anyString());
    }

    // -------------------------------------------------------------------------
    // Exception interceptée — branche : catch (best-effort)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT continuer le rollback quand store.remove() lève une exception (best-effort)")
    void shouldContinueRollbackWhenStoreRemoveThrows() {
        BatchEmbeddings batch = new BatchEmbeddings();
        batch.addTextEmbedding("success");
        batch.addTextEmbedding("failure");

        // Stub explicite : success → rien, failure → exception
        doNothing().when(textStore).remove("success");
        doThrow(new RuntimeException("pgvector unavailable"))
            .when(textStore).remove("failure");

        int deleted = rollbackExecutor.rollback("batch-partial-fail", batch);

        // 1 réussie, 1 échouée interceptée
        assertThat(deleted).isEqualTo(1);
        verify(textStore, times(2)).remove(anyString());
    }

    @Test
    @DisplayName("DOIT retourner 0 quand toutes les suppressions échouent (best-effort)")
    void shouldReturn0WhenAllDeletionsFail() {
        BatchEmbeddings batch = new BatchEmbeddings();
        batch.addTextEmbedding("t1");
        batch.addImageEmbedding("img-1");

        doThrow(new RuntimeException("store unavailable")).when(textStore).remove(anyString());
        doThrow(new RuntimeException("store unavailable")).when(imageStore).remove(anyString());

        int deleted = rollbackExecutor.rollback("batch-all-fail", batch);

        assertThat(deleted).isZero();
        verify(textStore,  times(1)).remove("t1");
        verify(imageStore, times(1)).remove("img-1");
    }

    // -------------------------------------------------------------------------
    // Idempotence
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT être idempotent : un second rollback retourne 0 (batch vide)")
    void shouldBeIdempotentOnSecondRollback() {
        BatchEmbeddings batch = new BatchEmbeddings();
        batch.addTextEmbedding("t1");

        doNothing().when(textStore).remove(anyString());
        rollbackExecutor.rollback("batch-001", batch);

        // Un second rollback sur un batch vide → 0
        BatchEmbeddings vide = new BatchEmbeddings();
        int secondRollback = rollbackExecutor.rollback("batch-001", vide);

        assertThat(secondRollback).isZero();
    }
}
