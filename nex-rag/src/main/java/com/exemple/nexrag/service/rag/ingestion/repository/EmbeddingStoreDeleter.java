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
     * Supprime tous les embeddings d'un batch via SQL DELETE.
     * Utilise une suppression SQL directe plutôt que d'appeler store.remove()
     * pour la même raison que deleteAll() — fiabilité dans Testcontainers.
     *
     * @return nombre d'embeddings supprimés
     */
    public int deleteByBatchId(
            EmbeddingStore<TextSegment> store,
            String tableName,
            String batchId,
            String label) {

        log.info("🗑️ [{}] Suppression batch : {} (via SQL DELETE)", label, batchId);

        int deleted = queryDao.deleteByBatchIdViaSQL(tableName, batchId);

        if (deleted > 0) {
            log.info("✅ [{}] {} embeddings supprimés du batch : {}", label, deleted, batchId);
        } else {
            log.warn("⚠️ [{}] Aucun embedding trouvé pour batch : {}", label, batchId);
        }

        return deleted;
    }

    // -------------------------------------------------------------------------
    // Suppression globale (via SQL directe — plus fiable que store.remove())
    // -------------------------------------------------------------------------

    /**
     * Supprime tous les embeddings d'une table via SQL DELETE.
     * Utilise une suppression SQL directe plutôt que d'appeler store.remove()
     * car LangChain4j PgVectorEmbeddingStore.remove() peut ne pas fonctionner
     * correctement dans certains contextes (notamment les tests Testcontainers).
     *
     * @return nombre total d'embeddings supprimés
     */
    public int deleteAll(EmbeddingStore<TextSegment> store, String tableName, String label) {
        log.info("🗑️ [{}] Suppression globale — table : {} (via SQL DELETE)", label, tableName);

        int deleted = queryDao.deleteAllViaSQL(tableName);

        if (deleted > 0) {
            log.info("✅ [{}] Suppression globale terminée : {} embeddings supprimés (via SQL)", label, deleted);
        } else {
            log.info("ℹ️ [{}] Aucun embedding à supprimer", label);
        }

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