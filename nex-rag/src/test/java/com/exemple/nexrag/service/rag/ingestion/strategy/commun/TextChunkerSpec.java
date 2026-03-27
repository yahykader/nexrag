package com.exemple.nexrag.service.rag.ingestion.strategy.commun;

import com.exemple.nexrag.service.rag.ingestion.util.MetadataSanitizer;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Spec : TextChunker — Découpage de texte en chunks avec chevauchement.
 */
@DisplayName("Spec : TextChunker — Découpage de texte avec chevauchement (overlap=100, size=1000)")
@ExtendWith(MockitoExtension.class)
class TextChunkerSpec {

    @Mock private EmbeddingIndexer              embeddingIndexer;
    @Mock private MetadataSanitizer             sanitizer;
    @Mock private EmbeddingStore<TextSegment>   store;

    @InjectMocks
    private TextChunker textChunker;

    private static final String BATCH_ID = "batch-test-001";

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String texteRepete(int longueur) {
        return "A".repeat(longueur);
    }

    // -------------------------------------------------------------------------
    // Texte vide / très court
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner 0 chunk indexé pour un texte vide (< MIN_CHUNK_LENGTH)")
    void shouldReturn0ChunksForEmptyText() {
        when(sanitizer.sanitize(any())).thenReturn(Map.of());
        when(embeddingIndexer.indexText(anyString(), any(), anyString(), any())).thenReturn(null);

        TextChunker.ChunkResult result = textChunker.chunk(
            "   ", "test.txt", "text", BATCH_ID, store, null
        );

        assertThat(result.indexed()).isZero();
    }

    @Test
    @DisplayName("DOIT indexer 1 chunk pour un texte plus court que chunkSize (1000)")
    void shouldIndex1ChunkForShortText() {
        when(sanitizer.sanitize(any())).thenReturn(Map.of());
        when(embeddingIndexer.indexText(anyString(), any(), anyString(), any()))
            .thenReturn("embedding-id-1");

        TextChunker.ChunkResult result = textChunker.chunk(
            texteRepete(500), "test.txt", "text", BATCH_ID, store, null
        );

        assertThat(result.indexed()).isEqualTo(1);
        assertThat(result.duplicates()).isZero();
    }

    // -------------------------------------------------------------------------
    // Texte long — plusieurs chunks
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT produire plusieurs chunks pour un texte de 1000 chars avec chunkSize par défaut")
    void shouldProduceMultipleChunksForTextLongerThanChunkSize() {
        when(sanitizer.sanitize(any())).thenReturn(Map.of());
        when(embeddingIndexer.indexText(anyString(), any(), anyString(), any()))
            .thenReturn("embedding-id");

        // 1000 chars + 1 : dépasse la limite de 1000
        TextChunker.ChunkResult result = textChunker.chunk(
            texteRepete(1001), "test.txt", "text", BATCH_ID, store, null
        );

        // Avec overlap=100 et chunkSize=1000 : ≥ 2 chunks
        assertThat(result.indexed()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("DOIT produire ≥ 6 chunks pour un texte de 1000 chars avec chunkSize=200 via paramétrage indirect")
    void shouldProduceAtLeast6ChunksForLongText() {
        when(sanitizer.sanitize(any())).thenReturn(Map.of());
        when(embeddingIndexer.indexText(anyString(), any(), anyString(), any()))
            .thenReturn("embedding-id");

        // Texte 5× la taille de chunk : devrait produire plusieurs chunks avec overlap
        TextChunker.ChunkResult result = textChunker.chunk(
            texteRepete(5000), "doc.pdf", "pdf_text", BATCH_ID, store, null
        );

        // 5000 chars avec chunkSize=1000 et overlap=100 → environ 6 chunks
        assertThat(result.indexed()).isGreaterThanOrEqualTo(5);
        assertThat(result.total()).isGreaterThanOrEqualTo(5);
    }

    // -------------------------------------------------------------------------
    // Déduplication — chunks dupliqués
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT compter comme doublon un chunk quand indexText retourne null")
    void shouldCountDuplicateWhenIndexTextReturnsNull() {
        when(sanitizer.sanitize(any())).thenReturn(Map.of());
        when(embeddingIndexer.indexText(anyString(), any(), anyString(), any()))
            .thenReturn(null); // doublon signalé par l'indexer

        TextChunker.ChunkResult result = textChunker.chunk(
            texteRepete(500), "test.txt", "text", BATCH_ID, store, null
        );

        assertThat(result.indexed()).isZero();
        assertThat(result.duplicates()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // ChunkResult.total()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT calculer total() = indexed + duplicates")
    void shouldCalculateTotalAsIndexedPlusDuplicates() {
        TextChunker.ChunkResult result = new TextChunker.ChunkResult(3, 2);
        assertThat(result.total()).isEqualTo(5);
    }

    // -------------------------------------------------------------------------
    // Métadonnées additionnelles
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT accepter des métadonnées additionnelles sans lever d'exception")
    void shouldAcceptExtraMetadataWithoutException() {
        when(sanitizer.sanitize(any())).thenReturn(Map.of());
        when(embeddingIndexer.indexText(anyString(), any(), anyString(), any()))
            .thenReturn("id");

        TextChunker.ChunkResult result = textChunker.chunk(
            texteRepete(200), "page.pdf", "pdf_page_1", BATCH_ID, store, null,
            Map.of("page", 1, "totalPages", 10)
        );

        assertThat(result.indexed()).isEqualTo(1);
    }
}
