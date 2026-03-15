package com.exemple.nexrag.service.rag.ingestion.tracker;

import com.exemple.nexrag.service.rag.ingestion.tracker.BatchEmbeddings;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Exécuteur de rollback pour une ingestion en erreur.
 *
 * Principe SRP : unique responsabilité → supprimer les embeddings
 *                d'un batch depuis les stores PgVector.
 * Clean code   : extrait la logique de suppression de {@link IngestionTracker}
 *                pour isoler le couplage avec les {@link EmbeddingStore}.
 *
 * @author ayahyaoui
 * @version 1.0
 */
@Slf4j
@Component
public class RollbackExecutor {

    private final EmbeddingStore<TextSegment> textStore;
    private final EmbeddingStore<TextSegment> imageStore;

    public RollbackExecutor(
            @Qualifier("textEmbeddingStore")  EmbeddingStore<TextSegment> textStore,
            @Qualifier("imageEmbeddingStore") EmbeddingStore<TextSegment> imageStore) {
        this.textStore  = textStore;
        this.imageStore = imageStore;
    }

    /**
     * Supprime tous les embeddings d'un batch depuis les stores.
     *
     * @param batchId        identifiant du batch
     * @param batchEmbeddings IDs des embeddings à supprimer
     * @return nombre d'embeddings effectivement supprimés
     */
    public int rollback(String batchId, BatchEmbeddings batchEmbeddings) {
        log.info("🔄 [Rollback] Démarrage : {}", batchId);

        int deleted = deleteAll(textStore,  batchEmbeddings.getTextEmbeddingIds(),  "text",  batchId)
                    + deleteAll(imageStore, batchEmbeddings.getImageEmbeddingIds(), "image", batchId);

        log.info("✅ [Rollback] Terminé : {} — {} embedding(s) supprimé(s)", batchId, deleted);
        return deleted;
    }

    // -------------------------------------------------------------------------
    // Privé
    // -------------------------------------------------------------------------

    private int deleteAll(
            EmbeddingStore<TextSegment> store,
            List<String> ids,
            String label,
            String batchId) {

        int deleted = 0;
        for (String id : ids) {
            try {
                store.remove(id);
                deleted++;
            } catch (Exception e) {
                log.warn("⚠️ [Rollback] Erreur suppression {} : {} — {}", label, id, e.getMessage());
            }
        }
        return deleted;
    }
}