package com.exemple.nexrag.service.rag.retrieval.retriever;

import com.exemple.nexrag.service.rag.retrieval.model.RetrievalResult;
import com.exemple.nexrag.service.rag.retrieval.model.RetrievalResult.ScoredChunk;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Spec : ImageVectorRetriever — Recherche vectorielle images
 *
 * AC couverts : FR-008 (top-5), seuil similarité image 0.6
 */
@DisplayName("Spec : ImageVectorRetriever — Recherche vectorielle sur le store image")
@ExtendWith(MockitoExtension.class)
class ImageVectorRetrieverSpec {

    @Mock
    private EmbeddingStore<TextSegment> imageStore;

    @Mock
    private EmbeddingModel embeddingModel;

    private ImageVectorRetriever service;

    private static final Embedding MOCK_EMBEDDING = Embedding.from(new float[]{0.1f, 0.2f, 0.3f});

    @BeforeEach
    void setUp() {
        service = new ImageVectorRetriever(imageStore, embeddingModel);
        when(embeddingModel.embed(any(String.class)))
            .thenReturn(Response.from(MOCK_EMBEDDING));
    }

    private EmbeddingMatch<TextSegment> buildImageMatch(String id, String content, double score) {
        TextSegment segment = TextSegment.from(content);
        return new EmbeddingMatch<>(score, id, MOCK_EMBEDDING, segment);
    }

    // =========================================================================
    // FR-008 — Retourne au maximum top-5 résultats
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner au maximum top-5 résultats images (FR-008)")
    void doitRetournerTop5ResultatsImages() {
        // Given — store retourne exactement 5 images
        List<EmbeddingMatch<TextSegment>> matches = List.of(
            buildImageMatch("img-1", "image 1", 0.92),
            buildImageMatch("img-2", "image 2", 0.88),
            buildImageMatch("img-3", "image 3", 0.85),
            buildImageMatch("img-4", "image 4", 0.80),
            buildImageMatch("img-5", "image 5", 0.75)
        );
        when(imageStore.findRelevant(any(Embedding.class), anyInt())).thenReturn(matches);

        // When — topK=5 (topK image par défaut)
        RetrievalResult result = service.retrieveAsync(List.of("graphique ventes"), 5).join();

        // Then
        assertThat(result.getChunks()).hasSize(5);
        assertThat(result.getTotalFound()).isEqualTo(5);
        assertThat(result.getRetrieverName()).isEqualTo("image");
    }

    // =========================================================================
    // Tri par score — les chunks image doivent être triés
    // =========================================================================

    @Test
    @DisplayName("DOIT trier les résultats image par score décroissant")
    void doitTrierResultatsImageParScoreDecroissant() {
        // Given — chunks dans un ordre quelconque
        List<EmbeddingMatch<TextSegment>> matches = List.of(
            buildImageMatch("img-a", "image a", 0.62),
            buildImageMatch("img-b", "image b", 0.90),
            buildImageMatch("img-c", "image c", 0.75)
        );
        when(imageStore.findRelevant(any(Embedding.class), anyInt())).thenReturn(matches);

        // When
        RetrievalResult result = service.retrieveAsync(List.of("diagramme"), 5).join();

        // Then — triés par score décroissant
        List<ScoredChunk> chunks = result.getChunks();
        assertThat(chunks.get(0).getScore()).isEqualTo(0.90);
        assertThat(chunks.get(1).getScore()).isEqualTo(0.75);
        assertThat(chunks.get(2).getScore()).isEqualTo(0.62);
    }

    // =========================================================================
    // Résultat vide quand le store image est vide
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner un résultat vide sans erreur si le store image est vide")
    void doitRetournerResultatVideSansErreurQuandStoreImageVide() {
        // Given
        when(imageStore.findRelevant(any(Embedding.class), anyInt())).thenReturn(List.of());

        // When
        RetrievalResult result = service.retrieveAsync(List.of("image non trouvée"), 5).join();

        // Then
        assertThat(result.getChunks()).isEmpty();
        assertThat(result.getTotalFound()).isEqualTo(0);
        assertThat(result.getTopScore()).isEqualTo(0.0);
        assertThat(result.getRetrieverName()).isEqualTo("image");
    }
}
