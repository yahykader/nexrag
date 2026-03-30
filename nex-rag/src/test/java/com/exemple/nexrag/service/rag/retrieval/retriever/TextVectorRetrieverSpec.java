package com.exemple.nexrag.service.rag.retrieval.retriever;

import com.exemple.nexrag.service.rag.retrieval.RetrievalTestHelper;
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
 * Spec : TextVectorRetriever — Recherche vectorielle texte
 *
 * AC couverts : FR-006 (top-20), AC-9.3 (tri décroissant)
 */
@DisplayName("Spec : TextVectorRetriever — Recherche vectorielle sur le store texte")
@ExtendWith(MockitoExtension.class)
class TextVectorRetrieverSpec {

    @Mock
    private EmbeddingStore<TextSegment> textStore;

    @Mock
    private EmbeddingModel embeddingModel;

    private TextVectorRetriever service;

    private static final Embedding MOCK_EMBEDDING = Embedding.from(new float[]{0.1f, 0.2f, 0.3f});

    @BeforeEach
    void setUp() {
        service = new TextVectorRetriever(textStore, embeddingModel);
        when(embeddingModel.embed(any(String.class)))
            .thenReturn(Response.from(MOCK_EMBEDDING));
    }

    private EmbeddingMatch<TextSegment> buildMatch(String id, String content, double score) {
        TextSegment segment = TextSegment.from(content);
        return new EmbeddingMatch<>(score, id, MOCK_EMBEDDING, segment);
    }

    // =========================================================================
    // FR-006 — Retourne au maximum topK résultats
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner exactement topK résultats quand le store en retourne topK (FR-006)")
    void doitRetournerTopKResultatsQuandStoreRetourneTopK() {
        // Given — le store retourne 20 chunks (topK=20)
        List<EmbeddingMatch<TextSegment>> matches = List.of(
            buildMatch("c1", "contenu 1", 0.95),
            buildMatch("c2", "contenu 2", 0.90),
            buildMatch("c3", "contenu 3", 0.85),
            buildMatch("c4", "contenu 4", 0.80),
            buildMatch("c5", "contenu 5", 0.75)
        );
        when(textStore.findRelevant(any(Embedding.class), anyInt())).thenReturn(matches);

        // When
        RetrievalResult result = service.retrieveAsync(List.of("query test"), 20).join();

        // Then
        assertThat(result.getChunks()).hasSize(5);
        assertThat(result.getTotalFound()).isEqualTo(5);
        assertThat(result.getRetrieverName()).isEqualTo("text");
    }

    // =========================================================================
    // AC-9.3 — Résultats triés par score décroissant
    // =========================================================================

    @Test
    @DisplayName("DOIT trier les résultats par score décroissant (AC-9.3)")
    void doitTrierResultatsParScoreDecroissant() {
        // Given — chunks dans un ordre quelconque
        List<EmbeddingMatch<TextSegment>> matches = List.of(
            buildMatch("c1", "premier", 0.70),
            buildMatch("c2", "deuxième", 0.95),
            buildMatch("c3", "troisième", 0.80)
        );
        when(textStore.findRelevant(any(Embedding.class), anyInt())).thenReturn(matches);

        // When
        RetrievalResult result = service.retrieveAsync(List.of("query"), 20).join();

        // Then — triés par score décroissant
        List<ScoredChunk> chunks = result.getChunks();
        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).getScore()).isEqualTo(0.95);
        assertThat(chunks.get(1).getScore()).isEqualTo(0.80);
        assertThat(chunks.get(2).getScore()).isEqualTo(0.70);
        assertThat(result.getTopScore()).isEqualTo(0.95);
    }

    // =========================================================================
    // Déduplication par ID
    // =========================================================================

    @Test
    @DisplayName("DOIT dédupliquer les chunks avec le même ID en retenant le score le plus élevé")
    void doitDedupliquerChunksAvecMemeIdEnRetentantMeilleurScore() {
        // Given — deux queries qui retournent le même chunk avec des scores différents
        List<EmbeddingMatch<TextSegment>> matchesQuery1 = List.of(
            buildMatch("chunk-dup", "contenu dupliqué", 0.95)
        );
        List<EmbeddingMatch<TextSegment>> matchesQuery2 = List.of(
            buildMatch("chunk-dup", "contenu dupliqué", 0.70),
            buildMatch("chunk-unique", "autre contenu", 0.80)
        );
        when(textStore.findRelevant(any(Embedding.class), anyInt()))
            .thenReturn(matchesQuery1)
            .thenReturn(matchesQuery2);

        // When — deux queries distinctes
        RetrievalResult result = service.retrieveAsync(List.of("query1", "query2"), 20).join();

        // Then — chunk-dup dédupliqué, score le plus élevé retenu
        assertThat(result.getChunks()).hasSize(2);
        ScoredChunk dupChunk = result.getChunks().stream()
            .filter(c -> c.getId().equals("chunk-dup"))
            .findFirst()
            .orElseThrow();
        assertThat(dupChunk.getScore()).isEqualTo(0.95);
    }

    // =========================================================================
    // Résultat vide
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner un résultat vide sans erreur quand le store ne retourne rien")
    void doitRetournerResultatVideSansErreurQuandStoreVide() {
        // Given
        when(textStore.findRelevant(any(Embedding.class), anyInt())).thenReturn(List.of());

        // When
        RetrievalResult result = service.retrieveAsync(List.of("query"), 20).join();

        // Then
        assertThat(result.getChunks()).isEmpty();
        assertThat(result.getTotalFound()).isEqualTo(0);
        assertThat(result.getTopScore()).isEqualTo(0.0);
        assertThat(result.getRetrieverName()).isEqualTo("text");
    }
}
