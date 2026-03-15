package com.exemple.nexrag.service.rag.ingestion.tracker;

import com.exemple.nexrag.service.rag.ingestion.tracker.BatchEmbeddings;
import com.exemple.nexrag.dto.batch.TrackerStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registre des embeddings créés par batch — utilisé pour le rollback.
 *
 * Principe SRP : unique responsabilité → maintenir les IDs d'embeddings
 *                permettant d'annuler une ingestion en cas d'erreur.
 * Clean code   : extrait la première map ({@code batchMap}) de
 *                {@link IngestionTracker} pour isoler la logique rollback.
 *
 * @author ayahyaoui
 * @version 1.0
 */
@Slf4j
@Component
public class BatchEmbeddingRegistry {

    private final Map<String, BatchEmbeddings> registry = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Enregistrement
    // -------------------------------------------------------------------------

    public void addTextEmbedding(String batchId, String embeddingId) {
        getOrCreate(batchId).addTextEmbedding(embeddingId);
        log.debug("📝 [Registry] Text ajouté : batch={}, id={}", batchId, embeddingId);
    }

    public void addImageEmbedding(String batchId, String embeddingId) {
        getOrCreate(batchId).addImageEmbedding(embeddingId);
        log.debug("🖼️ [Registry] Image ajouté : batch={}, id={}", batchId, embeddingId);
    }

    // -------------------------------------------------------------------------
    // Lecture
    // -------------------------------------------------------------------------

    public BatchEmbeddings get(String batchId) {
        return registry.get(batchId);
    }

    public List<String> getTextIds(String batchId) {
        BatchEmbeddings batch = registry.get(batchId);
        return batch != null ? batch.getTextEmbeddingIds() : List.of();
    }

    public List<String> getImageIds(String batchId) {
        BatchEmbeddings batch = registry.get(batchId);
        return batch != null ? batch.getImageEmbeddingIds() : List.of();
    }

    public boolean contains(String batchId) {
        return registry.containsKey(batchId);
    }

    // -------------------------------------------------------------------------
    // Nettoyage
    // -------------------------------------------------------------------------

    public void remove(String batchId) {
        registry.remove(batchId);
    }

    public void clear() {
        int count = registry.size();
        registry.clear();
        log.warn("⚠️ [Registry] {} batch(es) supprimé(s)", count);
    }

    // -------------------------------------------------------------------------
    // Statistiques
    // -------------------------------------------------------------------------

    public TrackerStats getStats() {
        int text   = registry.values().stream().mapToInt(BatchEmbeddings::getTextEmbeddingCount).sum();
        int images = registry.values().stream().mapToInt(BatchEmbeddings::getImageEmbeddingCount).sum();
        return new TrackerStats(registry.size(), text, images, text + images);
    }

    // -------------------------------------------------------------------------
    // Privé
    // -------------------------------------------------------------------------

    private BatchEmbeddings getOrCreate(String batchId) {
        return registry.computeIfAbsent(batchId, k -> new BatchEmbeddings());
    }
}