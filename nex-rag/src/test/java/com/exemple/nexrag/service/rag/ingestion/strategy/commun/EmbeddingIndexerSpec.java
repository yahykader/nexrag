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
import dev.langchain4j.model.output.Response;
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
import static org.mockito.Mockito.*;

/**
 * Spec : EmbeddingIndexer — Pipeline dedup → cache → embed → store → track.
 */
@DisplayName("Spec : EmbeddingIndexer — Indexation avec cache L1/L2 et déduplication texte")
@ExtendWith(MockitoExtension.class)
class EmbeddingIndexerSpec {

    @Mock private EmbeddingModel           embeddingModel;
    @Mock private EmbeddingCache           embeddingCache;
    @Mock private TextDeduplicationService textDeduplicationService;
    @Mock private IngestionTracker         tracker;
    @Mock private MetadataSanitizer        sanitizer;
    @Mock private RAGMetrics               ragMetrics;
    @Mock private EmbeddingStore<TextSegment> store;

    @InjectMocks
    private EmbeddingIndexer embeddingIndexer;

    private static final String BATCH_ID   = "batch-001";
    private static final String TEXTE      = "Voici un texte de test pour l'embedding.";
    private static final String EMBED_ID   = "embed-id-001";

    private Response<Embedding> reponseEmbedding() {
        return Response.from(Embedding.from(new float[]{0.1f, 0.2f, 0.3f}));
    }

    // -------------------------------------------------------------------------
    // Cache miss — nouveau texte
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT appeler embeddingModel.embed() quand le cache est vide (cache miss)")
    void shouldCallEmbeddingModelOnCacheMiss() {
        when(textDeduplicationService.checkAndMark(TEXTE, BATCH_ID)).thenReturn(true);
        when(embeddingCache.getAndTrack(TEXTE, BATCH_ID)).thenReturn(null);
        when(embeddingModel.embed(TEXTE)).thenReturn(reponseEmbedding());
        when(store.add(any(Embedding.class), (TextSegment) any())).thenReturn(EMBED_ID);

        embeddingIndexer.indexText(TEXTE, new Metadata(), BATCH_ID, store);

        verify(embeddingModel).embed(TEXTE);
    }

    @Test
    @DisplayName("DOIT mettre en cache l'embedding après l'avoir calculé")
    void shouldCacheEmbeddingAfterCalculation() {
        when(textDeduplicationService.checkAndMark(TEXTE, BATCH_ID)).thenReturn(true);
        when(embeddingCache.getAndTrack(TEXTE, BATCH_ID)).thenReturn(null);
        when(embeddingModel.embed(TEXTE)).thenReturn(reponseEmbedding());
        when(store.add(any(Embedding.class), (TextSegment) any())).thenReturn(EMBED_ID);

        embeddingIndexer.indexText(TEXTE, new Metadata(), BATCH_ID, store);

        verify(embeddingCache).put(eq(TEXTE), any(Embedding.class), eq(BATCH_ID));
    }

    // -------------------------------------------------------------------------
    // Cache hit — texte déjà en cache
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT NE PAS appeler embeddingModel.embed() quand le cache retourne un embedding")
    void shouldNotCallEmbeddingModelOnCacheHit() {
        Embedding cached = Embedding.from(new float[]{0.5f, 0.6f});
        when(textDeduplicationService.checkAndMark(TEXTE, BATCH_ID)).thenReturn(true);
        when(embeddingCache.getAndTrack(TEXTE, BATCH_ID)).thenReturn(cached);
        when(store.add(any(Embedding.class), (TextSegment) any())).thenReturn(EMBED_ID);

        embeddingIndexer.indexText(TEXTE, new Metadata(), BATCH_ID, store);

        verify(embeddingModel, never()).embed(anyString());
    }

    // -------------------------------------------------------------------------
    // Déduplication texte — doublon ignoré
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner null et NE PAS appeler store.add() pour un texte dupliqué")
    void shouldReturnNullAndSkipStoreForDuplicateText() {
        when(textDeduplicationService.checkAndMark(TEXTE, BATCH_ID)).thenReturn(false);

        String result = embeddingIndexer.indexText(TEXTE, new Metadata(), BATCH_ID, store);

        assertThat(result).isNull();
        verify(store, never()).add(any(Embedding.class), (TextSegment) any());
        verify(embeddingModel, never()).embed(anyString());
    }

    // -------------------------------------------------------------------------
    // Tracking
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT enregistrer l'ID d'embedding dans le tracker après indexation")
    void shouldTrackEmbeddingIdAfterIndexation() {
        when(textDeduplicationService.checkAndMark(TEXTE, BATCH_ID)).thenReturn(true);
        when(embeddingCache.getAndTrack(TEXTE, BATCH_ID)).thenReturn(null);
        when(embeddingModel.embed(TEXTE)).thenReturn(reponseEmbedding());
        when(store.add(any(Embedding.class), (TextSegment) any())).thenReturn(EMBED_ID);

        embeddingIndexer.indexText(TEXTE, new Metadata(), BATCH_ID, store);

        verify(tracker).addTextEmbeddingId(BATCH_ID, EMBED_ID);
    }

    // -------------------------------------------------------------------------
    // Indexation image (sans déduplication texte)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT indexer une description image SANS déduplication texte")
    void shouldIndexImageDescriptionWithoutTextDeduplication() {
        when(embeddingCache.getAndTrack(anyString(), eq(BATCH_ID))).thenReturn(null);
        when(embeddingModel.embed(anyString())).thenReturn(reponseEmbedding());
        when(sanitizer.sanitize(any())).thenReturn(Map.of());
        when(store.add(any(Embedding.class), any(TextSegment.class))).thenReturn("img-id");

        embeddingIndexer.indexImageDescription("Description Vision AI", Map.of(), BATCH_ID, store);

        verify(textDeduplicationService, never()).checkAndMark(anyString(), anyString());
        verify(tracker).addImageEmbeddingId(BATCH_ID, "img-id");
    }

    @Test
    @DisplayName("DOIT retourner l'ID d'embedding créé pour une indexation réussie")
    void shouldReturnEmbeddingIdOnSuccess() {
        when(textDeduplicationService.checkAndMark(TEXTE, BATCH_ID)).thenReturn(true);
        when(embeddingCache.getAndTrack(TEXTE, BATCH_ID)).thenReturn(null);
        when(embeddingModel.embed(TEXTE)).thenReturn(reponseEmbedding());
        when(store.add(any(Embedding.class), (TextSegment) any())).thenReturn(EMBED_ID);

        String result = embeddingIndexer.indexText(TEXTE, new Metadata(), BATCH_ID, store);

        assertThat(result).isEqualTo(EMBED_ID);
    }
}
