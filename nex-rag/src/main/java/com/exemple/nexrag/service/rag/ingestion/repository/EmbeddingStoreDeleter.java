package com.exemple.nexrag.service.rag.ingestion.repository;

import com.exemple.nexrag.constant.EmbeddingRepositoryConstants;
import com.exemple.nexrag.service.rag.ingestion.repository.EmbeddingQueryDao;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Suppression d'embeddings depuis un {@link EmbeddingStore}.
 *
 * Principe SRP : unique responsabilité → supprimer des embeddings du store.
 * Clean code   : élimine la duplication entre les paires text/image
 *                ({@code deleteText}/{@code deleteImage},
 *                 {@code deleteTextBatch}/{@code deleteImageBatch},
 *                 {@code deleteAllText}/{@code deleteAllImage}).
 *                Un seul algorithme paramétré par le store et le nom de table.
 *
 * @author RAG Team
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingStoreDeleter {

    private final EmbeddingQueryDao queryDao;

    // -------------------------------------------------------------------------
    // Suppression individuelle
    // -------------------------------------------------------------------------

    /**
     * Supprime un embedding par ID depuis le store donné.
     *
     * @return {@code true} si supprimé, {@code false} en cas d'erreur
     */
    public boolean deleteById(EmbeddingStore<TextSegment> store, String embeddingId, String label) {
        try {
            store.remove(embeddingId);
            log.info("✅ [{}] Embedding supprimé : {}", label, embeddingId);
            return true;
        } catch (Exception e) {
            log.error("❌ [{}] Erreur suppression : {}", label, embeddingId, e);
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Suppression par liste d'IDs
    // -------------------------------------------------------------------------

    /**
     * Supprime une liste d'IDs depuis le store donné.
     *
     * @return nombre d'embeddings effectivement supprimés
     */
    public int deleteByIds(EmbeddingStore<TextSegment> store, List<String> ids, String label) {
        log.info("🗑️ [{}] Suppression de {} embeddings", label, ids.size());
        int deleted = 0;
        for (String id : ids) {
            if (deleteById(store, id, label)) deleted++;
        }
        log.info("✅ [{}] {}/{} supprimés", label, deleted, ids.size());
        return deleted;
    }

    // -------------------------------------------------------------------------
    // Suppression par batchId (via SQL)
    // -------------------------------------------------------------------------

    /**
     * Supprime tous les embeddings d'un batch depuis le store.
     *
     * @return nombre d'embeddings supprimés
     */
    public int deleteByBatchId(
            EmbeddingStore<TextSegment> store,
            String tableName,
            String batchId,
            String label) {

        log.info("🗑️ [{}] Suppression batch : {}", label, batchId);
        List<String> ids = queryDao.findIdsByBatchId(tableName, batchId);

        if (ids.isEmpty()) {
            log.warn("⚠️ [{}] Aucun embedding trouvé pour batch : {}", label, batchId);
            return 0;
        }

        int deleted = deleteByIds(store, ids, label);
        log.info("✅ [{}] {} embeddings supprimés du batch : {}", label, deleted, batchId);
        return deleted;
    }

    // -------------------------------------------------------------------------
    // Suppression globale (via SQL, par micro-batches)
    // -------------------------------------------------------------------------

    /**
     * Supprime tous les embeddings d'une table via des micro-batches.
     *
     * @return nombre total d'embeddings supprimés
     */
    public int deleteAll(EmbeddingStore<TextSegment> store, String tableName, String label) {
        log.info("🗑️ [{}] Suppression globale — table : {}", label, tableName);

        List<String> allIds = queryDao.findAllIds(tableName);
        if (allIds.isEmpty()) {
            log.info("ℹ️ [{}] Aucun embedding à supprimer", label);
            return 0;
        }

        log.info("📊 [{}] {} embeddings trouvés", label, allIds.size());

        int deleted = 0;
        int batchSize = EmbeddingRepositoryConstants.DELETE_BATCH_SIZE;

        for (int i = 0; i < allIds.size(); i += batchSize) {
            List<String> batch = allIds.subList(i, Math.min(i + batchSize, allIds.size()));
            for (String id : batch) {
                try {
                    store.remove(id);
                    deleted++;
                } catch (Exception e) {
                    log.warn("⚠️ [{}] Erreur suppression {} : {}", label, id, e.getMessage());
                }
            }
            logProgress(deleted, allIds.size(), label);
        }

        log.info("✅ [{}] Suppression globale terminée : {}/{}", label, deleted, allIds.size());
        return deleted;
    }

    // -------------------------------------------------------------------------
    // Privé
    // -------------------------------------------------------------------------

    private void logProgress(int deleted, int total, String label) {
        int interval = EmbeddingRepositoryConstants.PROGRESS_LOG_INTERVAL;
        if (deleted > 0 && deleted % interval == 0) {
            log.info("⏳ [{}] Progression : {}/{}", label, deleted, total);
        }
    }
}