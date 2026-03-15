package com.exemple.nexrag.service.rag.ingestion.tracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IDs d'embeddings créés pendant un batch — utilisé pour le rollback.
 *
 * Principe SRP : unique responsabilité → porter les IDs d'un batch.
 * Clean code   : utilise {@link ConcurrentHashMap#newKeySet()} de manière
 *                cohérente — contrairement à {@code Collections.synchronizedList}
 *                qui nécessite des blocs {@code synchronized} manuels supplémentaires.
 *                {@code newKeySet()} est atomique sans synchronisation externe.
 */
public class BatchEmbeddings {

    private final Set<String> textEmbeddingIds  = ConcurrentHashMap.newKeySet();
    private final Set<String> imageEmbeddingIds = ConcurrentHashMap.newKeySet();

    // -------------------------------------------------------------------------
    // Ajout
    // -------------------------------------------------------------------------

    public void addTextEmbedding(String id) {
        if (id != null && !id.isBlank()) textEmbeddingIds.add(id);
    }

    public void addImageEmbedding(String id) {
        if (id != null && !id.isBlank()) imageEmbeddingIds.add(id);
    }

    // -------------------------------------------------------------------------
    // Lecture — copies défensives
    // -------------------------------------------------------------------------

    public List<String> getTextEmbeddingIds()  { return new ArrayList<>(textEmbeddingIds);  }
    public List<String> getImageEmbeddingIds() { return new ArrayList<>(imageEmbeddingIds); }

    public int getTextEmbeddingCount()  { return textEmbeddingIds.size();  }
    public int getImageEmbeddingCount() { return imageEmbeddingIds.size(); }
    public int getTotalCount()          { return getTextEmbeddingCount() + getImageEmbeddingCount(); }

    public boolean isEmpty() { return textEmbeddingIds.isEmpty() && imageEmbeddingIds.isEmpty(); }

    @Override
    public String toString() {
        return String.format("BatchEmbeddings[text=%d, images=%d]",
            getTextEmbeddingCount(), getImageEmbeddingCount());
    }
}