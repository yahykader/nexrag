package com.exemple.nexrag.service.rag.ingestion.strategy.commun;

import com.exemple.nexrag.service.rag.ingestion.cache.EmbeddingCache;
import com.exemple.nexrag.service.rag.ingestion.deduplication.text.TextDeduplicationService;
import com.exemple.nexrag.service.rag.ingestion.tracker.IngestionTracker;
import com.exemple.nexrag.service.rag.ingestion.util.MetadataSanitizer;
import com.exemple.nexrag.service.rag.metrics.RAGMetrics;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service d'indexation d'embeddings texte et image.
 *
 * Principe SRP  : unique responsabilité → orchestrer le pipeline
 *                 dedup → cache → embed → store → track → métrique.
 * Clean code    : élimine les méthodes {@code indexText()} et
 *                 {@code analyzeAndIndexImageWithRetry()} dupliquées
 *                 dans PDF, DOCX, XLSX et TEXT.
 *
 * @author RAG Team
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingIndexer {

    private final EmbeddingModel              embeddingModel;
    private final EmbeddingCache              embeddingCache;
    private final TextDeduplicationService    textDeduplicationService;
    private final IngestionTracker            tracker;
    private final MetadataSanitizer           sanitizer;
    private final RAGMetrics                  ragMetrics;

    // -------------------------------------------------------------------------
    // Indexation texte
    // -------------------------------------------------------------------------

    /**
     * Indexe un segment texte dans le store donné.
     *
     * <p>Pipeline : déduplication → cache → embed → store → track → métrique.
     *
     * @param text     texte à indexer
     * @param metadata métadonnées du segment
     * @param batchId  identifiant du batch courant
     * @param store    store cible (text ou image)
     * @return identifiant de l'embedding créé, ou {@code null} si doublon
     */
    public String indexText(
            String text,
            Metadata metadata,
            String batchId,
            EmbeddingStore<TextSegment> store) {

        if (!textDeduplicationService.checkAndMark(text, batchId)) {
            log.debug("⏭️ [Dedup] Doublon texte ignoré : {}", truncate(text));
            return null;
        }

        log.debug("✅ [Dedup] Nouveau texte indexé : {}", truncate(text));

        TextSegment segment   = TextSegment.from(text, metadata);
        Embedding   embedding = resolveEmbedding(text, batchId);

        long   storeStart = System.currentTimeMillis();
        String embeddingId = store.add(embedding, segment);
        ragMetrics.recordVectorStoreOperation("insert",
            System.currentTimeMillis() - storeStart, 1);

        tracker.addTextEmbeddingId(batchId, embeddingId);
        return embeddingId;
    }

    // -------------------------------------------------------------------------
    // Indexation image (description Vision AI déjà calculée)
    // -------------------------------------------------------------------------

    /**
     * Indexe une description d'image dans le store donné.
     *
     * <p>Le texte est la description produite par Vision AI.
     * Le pipeline est identique à {@link #indexText} mais sans déduplication texte
     * (chaque image est unique par définition).
     *
     * @param description description Vision AI de l'image
     * @param metadata    métadonnées enrichies de l'image
     * @param batchId     identifiant du batch courant
     * @param store       store cible (image)
     * @return identifiant de l'embedding créé
     */
    public String indexImageDescription(
            String description,
            Map<String, Object> rawMetadata,
            String batchId,
            EmbeddingStore<TextSegment> store) {

        Map<String, Object> sanitized = sanitizer.sanitize(rawMetadata);
        TextSegment segment   = TextSegment.from(description, Metadata.from(sanitized));
        Embedding   embedding = resolveEmbedding(description, batchId);

        long   storeStart  = System.currentTimeMillis();
        String embeddingId = store.add(embedding, segment);
        ragMetrics.recordVectorStoreOperation("insert",
            System.currentTimeMillis() - storeStart, 1);

        tracker.addImageEmbeddingId(batchId, embeddingId);
        return embeddingId;
    }

    // -------------------------------------------------------------------------
    // Privé
    // -------------------------------------------------------------------------

    /**
     * Retourne l'embedding depuis le cache ou le calcule via OpenAI.
     */
    private Embedding resolveEmbedding(String text, String batchId) {
        Embedding cached = embeddingCache.getAndTrack(text, batchId);
        if (cached != null) return cached;

        long      apiStart  = System.currentTimeMillis();
        Embedding embedding = embeddingModel.embed(text).content();
        ragMetrics.recordApiCall("embed_text", System.currentTimeMillis() - apiStart);

        embeddingCache.put(text, embedding, batchId);
        return embedding;
    }

    private String truncate(String text) {
        return text != null && text.length() > 50
            ? text.substring(0, 50) + "..."
            : text;
    }
}