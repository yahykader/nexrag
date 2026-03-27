package com.exemple.nexrag.service.rag.ingestion.tracker;

import com.exemple.nexrag.dto.batch.BatchInfo;
import com.exemple.nexrag.dto.batch.TrackerStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Spec : IngestionTracker — Coordination rollback + tracking embeddings + métadonnées batch.
 */
@DisplayName("Spec : IngestionTracker — Coordination des registres embedding et info avec rollback")
@ExtendWith(MockitoExtension.class)
class IngestionTrackerSpec {

    @Mock private BatchEmbeddingRegistry embeddingRegistry;
    @Mock private BatchInfoRegistry      infoRegistry;
    @Mock private RollbackExecutor       rollbackExecutor;

    @InjectMocks
    private IngestionTracker tracker;

    private static final String BATCH_ID    = "batch-001";
    private static final String FILENAME    = "doc.pdf";
    private static final String MIME_TYPE   = "application/pdf";
    private static final String EMBED_ID    = "embed-001";

    // -------------------------------------------------------------------------
    // trackBatch — délégation à infoRegistry
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT déléguer trackBatch() à infoRegistry.register()")
    void shouldDelegateTrackBatchToInfoRegistry() {
        tracker.trackBatch(BATCH_ID, FILENAME, MIME_TYPE);

        verify(infoRegistry).register(BATCH_ID, FILENAME, MIME_TYPE);
    }

    // -------------------------------------------------------------------------
    // addTextEmbeddingId — batchId ou embeddingId vide → ignoré silencieusement
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT ignorer silencieusement addTextEmbeddingId() pour un batchId null")
    void shouldIgnoreAddTextEmbeddingIdWhenBatchIdIsNull() {
        tracker.addTextEmbeddingId(null, EMBED_ID);

        verifyNoInteractions(embeddingRegistry, infoRegistry);
    }

    @Test
    @DisplayName("DOIT ignorer silencieusement addTextEmbeddingId() pour un batchId vide")
    void shouldIgnoreAddTextEmbeddingIdWhenBatchIdIsBlank() {
        tracker.addTextEmbeddingId("   ", EMBED_ID);

        verifyNoInteractions(embeddingRegistry, infoRegistry);
    }

    @Test
    @DisplayName("DOIT ignorer silencieusement addTextEmbeddingId() pour un embeddingId vide")
    void shouldIgnoreAddTextEmbeddingIdWhenEmbeddingIdIsBlank() {
        tracker.addTextEmbeddingId(BATCH_ID, "");

        verifyNoInteractions(embeddingRegistry, infoRegistry);
    }

    @Test
    @DisplayName("DOIT déléguer addTextEmbeddingId() aux deux registres quand les paramètres sont valides")
    void shouldDelegateAddTextEmbeddingIdToBothRegistries() {
        tracker.addTextEmbeddingId(BATCH_ID, EMBED_ID);

        verify(embeddingRegistry).addTextEmbedding(BATCH_ID, EMBED_ID);
        verify(infoRegistry).addTextEmbeddingId(BATCH_ID, EMBED_ID);
    }

    // -------------------------------------------------------------------------
    // addImageEmbeddingId — même comportement que addTextEmbeddingId
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT ignorer silencieusement addImageEmbeddingId() pour un embeddingId null")
    void shouldIgnoreAddImageEmbeddingIdWhenEmbeddingIdIsNull() {
        tracker.addImageEmbeddingId(BATCH_ID, null);

        verifyNoInteractions(embeddingRegistry, infoRegistry);
    }

    @Test
    @DisplayName("DOIT déléguer addImageEmbeddingId() aux deux registres quand les paramètres sont valides")
    void shouldDelegateAddImageEmbeddingIdToBothRegistries() {
        tracker.addImageEmbeddingId(BATCH_ID, "img-001");

        verify(embeddingRegistry).addImageEmbedding(BATCH_ID, "img-001");
        verify(infoRegistry).addImageEmbeddingId(BATCH_ID, "img-001");
    }

    // -------------------------------------------------------------------------
    // rollbackBatch — batch inexistant → retourne 0
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner 0 pour rollbackBatch() sur un batchId inexistant")
    void shouldReturn0ForRollbackBatchWhenBatchNotFound() {
        when(embeddingRegistry.get(BATCH_ID)).thenReturn(null);

        int result = tracker.rollbackBatch(BATCH_ID);

        assertThat(result).isZero();
        verifyNoInteractions(rollbackExecutor);
    }

    @Test
    @DisplayName("DOIT appeler rollbackExecutor.rollback() puis removeBatch() sur un batch existant")
    void shouldCallRollbackExecutorAndRemoveBatchForExistingBatch() {
        BatchEmbeddings embeddings = new BatchEmbeddings();
        embeddings.addTextEmbedding("t1");
        when(embeddingRegistry.get(BATCH_ID)).thenReturn(embeddings);
        when(rollbackExecutor.rollback(BATCH_ID, embeddings)).thenReturn(1);

        int result = tracker.rollbackBatch(BATCH_ID);

        assertThat(result).isEqualTo(1);
        verify(rollbackExecutor).rollback(BATCH_ID, embeddings);
        verify(embeddingRegistry).remove(BATCH_ID);
        verify(infoRegistry).remove(BATCH_ID);
    }

    @Test
    @DisplayName("DOIT exécuter rollback dans l'ordre : rollbackExecutor PUIS removeBatch")
    void shouldExecuteRollbackThenRemoveBatchInOrder() {
        BatchEmbeddings embeddings = new BatchEmbeddings();
        when(embeddingRegistry.get(BATCH_ID)).thenReturn(embeddings);
        when(rollbackExecutor.rollback(BATCH_ID, embeddings)).thenReturn(0);

        var ordered = inOrder(rollbackExecutor, embeddingRegistry, infoRegistry);

        tracker.rollbackBatch(BATCH_ID);

        ordered.verify(rollbackExecutor).rollback(BATCH_ID, embeddings);
        ordered.verify(embeddingRegistry).remove(BATCH_ID);
        ordered.verify(infoRegistry).remove(BATCH_ID);
    }

    // -------------------------------------------------------------------------
    // removeBatch — délégation aux deux registres
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT supprimer un batch des deux registres via removeBatch()")
    void shouldRemoveBatchFromBothRegistries() {
        tracker.removeBatch(BATCH_ID);

        verify(embeddingRegistry).remove(BATCH_ID);
        verify(infoRegistry).remove(BATCH_ID);
    }

    // -------------------------------------------------------------------------
    // getBatchInfo — délégation à infoRegistry
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT déléguer getBatchInfo() à infoRegistry.get()")
    void shouldDelegateGetBatchInfoToInfoRegistry() {
        BatchInfo info = new BatchInfo(BATCH_ID, FILENAME, MIME_TYPE,
            LocalDateTime.now(), new ArrayList<>(), new ArrayList<>());
        when(infoRegistry.get(BATCH_ID)).thenReturn(Optional.of(info));

        Optional<BatchInfo> result = tracker.getBatchInfo(BATCH_ID);

        assertThat(result).isPresent();
        assertThat(result.get().batchId()).isEqualTo(BATCH_ID);
    }

    // -------------------------------------------------------------------------
    // batchExists — OR entre les deux registres
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner true si le batch existe dans infoRegistry")
    void shouldReturnTrueIfBatchExistsInInfoRegistry() {
        when(infoRegistry.contains(BATCH_ID)).thenReturn(true);

        assertThat(tracker.batchExists(BATCH_ID)).isTrue();
    }

    @Test
    @DisplayName("DOIT retourner true si le batch existe dans embeddingRegistry")
    void shouldReturnTrueIfBatchExistsInEmbeddingRegistry() {
        when(infoRegistry.contains(BATCH_ID)).thenReturn(false);
        when(embeddingRegistry.contains(BATCH_ID)).thenReturn(true);

        assertThat(tracker.batchExists(BATCH_ID)).isTrue();
    }

    @Test
    @DisplayName("DOIT retourner false si le batch est absent des deux registres")
    void shouldReturnFalseIfBatchAbsentFromBothRegistries() {
        when(infoRegistry.contains(BATCH_ID)).thenReturn(false);
        when(embeddingRegistry.contains(BATCH_ID)).thenReturn(false);

        assertThat(tracker.batchExists(BATCH_ID)).isFalse();
    }

    // -------------------------------------------------------------------------
    // getTextEmbeddingIds / getImageEmbeddingIds — délégation à embeddingRegistry
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT déléguer getTextEmbeddingIds() à embeddingRegistry.getTextIds()")
    void shouldDelegateGetTextEmbeddingIdsToEmbeddingRegistry() {
        when(embeddingRegistry.getTextIds(BATCH_ID)).thenReturn(List.of("t1", "t2"));

        List<String> ids = tracker.getTextEmbeddingIds(BATCH_ID);

        assertThat(ids).containsExactly("t1", "t2");
    }

    @Test
    @DisplayName("DOIT déléguer getImageEmbeddingIds() à embeddingRegistry.getImageIds()")
    void shouldDelegateGetImageEmbeddingIdsToEmbeddingRegistry() {
        when(embeddingRegistry.getImageIds(BATCH_ID)).thenReturn(List.of("img-1"));

        List<String> ids = tracker.getImageEmbeddingIds(BATCH_ID);

        assertThat(ids).containsExactly("img-1");
    }

    // -------------------------------------------------------------------------
    // getAllBatches — délégation à infoRegistry
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT déléguer getAllBatches() à infoRegistry.getAll()")
    void shouldDelegateGetAllBatchesToInfoRegistry() {
        BatchInfo info = new BatchInfo(BATCH_ID, FILENAME, MIME_TYPE,
            LocalDateTime.now(), new ArrayList<>(), new ArrayList<>());
        when(infoRegistry.getAll()).thenReturn(Map.of(BATCH_ID, info));

        Map<String, BatchInfo> result = tracker.getAllBatches();

        assertThat(result).containsKey(BATCH_ID);
    }

    // -------------------------------------------------------------------------
    // clearBatch — supprime les embeddings mais pas les métadonnées
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT supprimer les embeddings via embeddingRegistry.remove() lors de clearBatch()")
    void shouldRemoveEmbeddingsOnClearBatch() {
        when(embeddingRegistry.get(BATCH_ID)).thenReturn(new BatchEmbeddings());

        tracker.clearBatch(BATCH_ID);

        verify(embeddingRegistry).remove(BATCH_ID);
        verifyNoInteractions(infoRegistry);
    }

    @Test
    @DisplayName("DOIT appeler embeddingRegistry.remove() même si le batch est null dans clearBatch()")
    void shouldCallRemoveEvenIfBatchIsNullInClearBatch() {
        when(embeddingRegistry.get(BATCH_ID)).thenReturn(null);

        tracker.clearBatch(BATCH_ID);

        verify(embeddingRegistry).remove(BATCH_ID);
    }

    // -------------------------------------------------------------------------
    // clearAll — supprime tous les registres
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT déléguer clearAll() aux deux registres")
    void shouldDelegateClearAllToBothRegistries() {
        tracker.clearAll();

        verify(embeddingRegistry).clear();
        verify(infoRegistry).clear();
    }

    // -------------------------------------------------------------------------
    // getStats / getBatchCount / getTotalEmbeddings
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT déléguer getStats() à embeddingRegistry.getStats()")
    void shouldDelegateGetStatsToEmbeddingRegistry() {
        TrackerStats expected = new TrackerStats(2, 5, 1, 6);
        when(embeddingRegistry.getStats()).thenReturn(expected);

        TrackerStats result = tracker.getStats();

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("DOIT déléguer getBatchCount() à infoRegistry.size()")
    void shouldDelegateGetBatchCountToInfoRegistry() {
        when(infoRegistry.size()).thenReturn(3);

        assertThat(tracker.getBatchCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("DOIT déléguer getTotalEmbeddings() à infoRegistry.totalEmbeddings()")
    void shouldDelegateGetTotalEmbeddingsToInfoRegistry() {
        when(infoRegistry.totalEmbeddings()).thenReturn(42);

        assertThat(tracker.getTotalEmbeddings()).isEqualTo(42);
    }

    // -------------------------------------------------------------------------
    // rollbackBatch — exception dans rollbackExecutor → IllegalStateException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT lever IllegalStateException quand rollbackExecutor.rollback() échoue")
    void shouldThrowIllegalStateExceptionWhenRollbackExecutorFails() {
        BatchEmbeddings embeddings = new BatchEmbeddings();
        when(embeddingRegistry.get(BATCH_ID)).thenReturn(embeddings);
        when(rollbackExecutor.rollback(BATCH_ID, embeddings))
            .thenThrow(new RuntimeException("pgvector down"));

        assertThatThrownBy(() -> tracker.rollbackBatch(BATCH_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(BATCH_ID);
    }
}
