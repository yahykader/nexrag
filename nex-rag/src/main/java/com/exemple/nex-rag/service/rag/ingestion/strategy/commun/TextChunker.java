package com.exemple.nexrag.service.rag.ingestion.strategy.commun;

import com.exemple.nexrag.service.rag.ingestion.progress.ProgressNotifier;
import com.exemple.nexrag.service.rag.ingestion.util.MetadataSanitizer;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service de découpage et d'indexation de texte en chunks.
 *
 * Principe SRP  : unique responsabilité → découper un texte en chunks
 *                 et les indexer via {@link EmbeddingIndexer}.
 * Clean code    : élimine {@code chunkAndIndexText()} dupliqué
 *                 dans PDF, DOCX, XLSX et TEXT.
 *                 Le type de contenu ({@code pdf_text}, {@code docx_text}…)
 *                 est passé en paramètre — un seul algorithme.
 *
 * @author RAG Team
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TextChunker {

    private static final int DEFAULT_CHUNK_SIZE = 1000;
    private static final int DEFAULT_OVERLAP    = 100;
    private static final int MIN_CHUNK_LENGTH   = 10;

    private final EmbeddingIndexer  embeddingIndexer;
    private final MetadataSanitizer sanitizer;

    // -------------------------------------------------------------------------
    // API publique
    // -------------------------------------------------------------------------

    /**
     * Découpe un texte en chunks et indexe chaque chunk.
     *
     * @param text            texte complet à indexer
     * @param filename        nom du fichier source (métadonnée)
     * @param contentType     type de contenu (ex : {@code pdf_text}, {@code docx_text})
     * @param batchId         identifiant du batch courant
     * @param store           store cible
     * @param notifier        notificateur de progression (peut être null)
     * @param extraMetadata   métadonnées additionnelles (ex : numéro de page)
     * @return résultat : nombre de chunks indexés et nombre de doublons ignorés
     */
    public ChunkResult chunk(
            String text,
            String filename,
            String contentType,
            String batchId,
            EmbeddingStore<TextSegment> store,
            ProgressNotifier notifier,
            Map<String, Object> extraMetadata) {

        int chunkSize = DEFAULT_CHUNK_SIZE;
        int overlap   = DEFAULT_OVERLAP;
        int indexed   = 0;
        int duplicates = 0;
        int chunkIndex = 0;

        int estimatedChunks = text.length() <= chunkSize ? 1
            : (int) Math.ceil(text.length() / (double)(chunkSize - overlap));

        if (text.length() <= chunkSize) {
            String embeddingId = indexChunk(
                text.trim(), filename, contentType, 0, batchId, store, extraMetadata
            );
            if (embeddingId != null) {
                notifyProgress(notifier, batchId, filename, 1, 1);
                return new ChunkResult(1, 0);
            }
            return new ChunkResult(0, 1);
        }

        int start = 0;
        while (start < text.length()) {
            int    end   = Math.min(start + chunkSize, text.length());
            String chunk = text.substring(start, end).trim();

            if (chunk.length() > MIN_CHUNK_LENGTH) {
                String embeddingId = indexChunk(
                    chunk, filename, contentType, chunkIndex, batchId, store, extraMetadata
                );

                if (embeddingId != null) {
                    indexed++;
                    if (indexed % 10 == 0 || indexed == estimatedChunks) {
                        notifyProgress(notifier, batchId, filename, indexed, estimatedChunks);
                    }
                } else {
                    duplicates++;
                }
                chunkIndex++;
            }

            start += Math.max(1, chunkSize - overlap);
        }

        log.info("✅ [Chunker] {} chunks indexés ({} doublons ignorés) — {}",
            indexed, duplicates, filename);

        return new ChunkResult(indexed, duplicates);
    }

    /**
     * Surcharge sans métadonnées additionnelles.
     */
    public ChunkResult chunk(
            String text,
            String filename,
            String contentType,
            String batchId,
            EmbeddingStore<TextSegment> store,
            ProgressNotifier notifier) {

        return chunk(text, filename, contentType, batchId, store, notifier, Map.of());
    }

    // -------------------------------------------------------------------------
    // Privé
    // -------------------------------------------------------------------------

    private String indexChunk(
            String text,
            String filename,
            String contentType,
            int chunkIndex,
            String batchId,
            EmbeddingStore<TextSegment> store,
            Map<String, Object> extraMetadata) {

        Map<String, Object> meta = new HashMap<>(extraMetadata);
        meta.put("source",     filename);
        meta.put("type",       contentType);
        meta.put("chunkIndex", chunkIndex);
        meta.put("batchId",    batchId);

        Metadata metadata = Metadata.from(sanitizer.sanitize(meta));
        return embeddingIndexer.indexText(text, metadata, batchId, store);
    }

    private void notifyProgress(
            ProgressNotifier notifier,
            String batchId,
            String filename,
            int current,
            int total) {

        if (notifier != null) {
            notifier.embeddingProgress(batchId, filename, current, total);
        }
    }

    // -------------------------------------------------------------------------
    // Record résultat
    // -------------------------------------------------------------------------

    /**
     * Résultat d'un découpage : chunks indexés + doublons ignorés.
     */
    public record ChunkResult(int indexed, int duplicates) {
        public int total() { return indexed + duplicates; }
    }
}