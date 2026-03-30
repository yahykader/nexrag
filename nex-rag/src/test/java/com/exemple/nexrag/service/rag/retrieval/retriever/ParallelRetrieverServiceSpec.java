package com.exemple.nexrag.service.rag.retrieval.retriever;

import com.exemple.nexrag.config.RetrievalConfig;
import com.exemple.nexrag.service.rag.retrieval.RetrievalTestHelper;
import com.exemple.nexrag.service.rag.retrieval.model.RetrievalResult;
import com.exemple.nexrag.service.rag.retrieval.model.RetrievalResult.ScoredChunk;
import com.exemple.nexrag.service.rag.retrieval.model.RoutingDecision;
import com.exemple.nexrag.service.rag.retrieval.model.RoutingDecision.Priority;
import com.exemple.nexrag.service.rag.retrieval.model.RoutingDecision.RetrieverConfig;
import com.exemple.nexrag.service.rag.retrieval.model.RoutingDecision.Strategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Spec : ParallelRetrieverService — Retrieval parallèle avec dégradation gracieuse
 *
 * AC couverts : AC-9.1 (fusion résultats), AC-9.2 (timeout graceful), AC-9.3 (tri),
 *               FR-004, FR-005, FR-015 (zéro résultats)
 */
@DisplayName("Spec : ParallelRetrieverService — Retrieval parallèle et tolérance aux pannes")
@ExtendWith(MockitoExtension.class)
class ParallelRetrieverServiceSpec {

    @Mock
    private TextVectorRetriever textRetriever;

    @Mock
    private ImageVectorRetriever imageRetriever;

    @Mock
    private BM25Retriever bm25Retriever;

    private RetrievalConfig config;
    private ParallelRetrieverService service;

    @BeforeEach
    void setUp() {
        config = RetrievalTestHelper.buildTestConfig(); // parallelTimeout=200ms
        service = new ParallelRetrieverService(textRetriever, imageRetriever, bm25Retriever, config);
    }

    private RoutingDecision buildHybridDecision() {
        return RoutingDecision.builder()
            .strategy(Strategy.HYBRID)
            .confidence(0.7)
            .retrievers(Map.of(
                "text",  RetrieverConfig.builder().enabled(true).topK(20).priority(Priority.HIGH).estimatedLatencyMs(50).build(),
                "image", RetrieverConfig.builder().enabled(true).topK(5).priority(Priority.MEDIUM).estimatedLatencyMs(80).build(),
                "bm25",  RetrieverConfig.builder().enabled(true).topK(10).priority(Priority.HIGH).estimatedLatencyMs(40).build()
            ))
            .estimatedTotalDurationMs(80)
            .parallelExecution(true)
            .build();
    }

    private RoutingDecision buildTextOnlyDecision() {
        return RoutingDecision.builder()
            .strategy(Strategy.TEXT_ONLY)
            .confidence(0.8)
            .retrievers(Map.of(
                "text",  RetrieverConfig.builder().enabled(true).topK(20).priority(Priority.HIGH).estimatedLatencyMs(50).build(),
                "image", RetrieverConfig.builder().enabled(false).topK(0).priority(Priority.LOW).estimatedLatencyMs(0).build(),
                "bm25",  RetrieverConfig.builder().enabled(false).topK(0).priority(Priority.LOW).estimatedLatencyMs(0).build()
            ))
            .estimatedTotalDurationMs(50)
            .parallelExecution(true)
            .build();
    }

    // =========================================================================
    // AC-9.1 — Fusionne les résultats de tous les retrievers activés
    // =========================================================================

    @Test
    @DisplayName("DOIT fusionner les résultats des trois retrievers activés (AC-9.1 / FR-004)")
    void doitFusionnerResultatsTroisRetrieversActives() {
        // Given
        when(textRetriever.retrieveAsync(any(), anyInt()))
            .thenReturn(RetrievalTestHelper.completedResult("text",
                List.of(RetrievalTestHelper.buildScoredChunk("t1", "texte 1", 0.9, "text"))));
        when(imageRetriever.retrieveAsync(any(), anyInt()))
            .thenReturn(RetrievalTestHelper.completedResult("image",
                List.of(RetrievalTestHelper.buildScoredChunk("i1", "image 1", 0.85, "image"))));
        when(bm25Retriever.retrieveAsync(any(), anyInt()))
            .thenReturn(RetrievalTestHelper.completedResult("bm25",
                List.of(RetrievalTestHelper.buildScoredChunk("b1", "bm25 1", 0.80, "bm25"))));

        // When
        Map<String, RetrievalResult> results = service.retrieveParallel(
            List.of("query"), buildHybridDecision());

        // Then
        assertThat(results).containsKeys("text", "image", "bm25");
        assertThat(results.get("text").getChunks()).hasSize(1);
        assertThat(results.get("image").getChunks()).hasSize(1);
        assertThat(results.get("bm25").getChunks()).hasSize(1);
    }

    // =========================================================================
    // AC-9.2 — Retourne résultats partiels si un retriever dépasse le timeout
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner résultats partiels quand bm25 dépasse le timeout de 200 ms (AC-9.2 / FR-005)")
    void doitRetournerResultatsPartielsSiUnRetrieverDepaseeTimeout() {
        // Given — text et image rapides, bm25 lent (1000ms > timeout 200ms)
        when(textRetriever.retrieveAsync(any(), anyInt()))
            .thenReturn(RetrievalTestHelper.completedResult("text",
                List.of(RetrievalTestHelper.buildScoredChunk("t1", "texte 1", 0.9, "text"))));
        when(imageRetriever.retrieveAsync(any(), anyInt()))
            .thenReturn(RetrievalTestHelper.completedResult("image",
                List.of(RetrievalTestHelper.buildScoredChunk("i1", "image 1", 0.8, "image"))));
        when(bm25Retriever.retrieveAsync(any(), anyInt()))
            .thenAnswer(inv -> CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(1000); // Dépasse le timeout de 200ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return RetrievalTestHelper.buildEmptyResult("bm25");
            }));

        // When
        Map<String, RetrievalResult> results = service.retrieveParallel(
            List.of("query"), buildHybridDecision());

        // Then — les résultats rapides sont présents, le lent est absent
        assertThat(results).containsKey("text");
        assertThat(results).containsKey("image");
        assertThat(results).doesNotContainKey("bm25");
    }

    // =========================================================================
    // Désactivation des retrievers selon la RoutingDecision
    // =========================================================================

    @Test
    @DisplayName("DOIT ignorer les retrievers dont enabled=false dans la RoutingDecision")
    void doitIgnorerRetrieversDesactivesParRoutingDecision() {
        // Given — seul text est activé
        when(textRetriever.retrieveAsync(any(), anyInt()))
            .thenReturn(RetrievalTestHelper.completedResult("text",
                List.of(RetrievalTestHelper.buildScoredChunk("t1", "texte 1", 0.9, "text"))));

        // When
        Map<String, RetrievalResult> results = service.retrieveParallel(
            List.of("query"), buildTextOnlyDecision());

        // Then — seul "text" dans le résultat
        assertThat(results).containsOnlyKeys("text");
    }

    // =========================================================================
    // AC-9.3 — Résultats triés par score décroissant (via les retrievers individuels)
    // =========================================================================

    @Test
    @DisplayName("DOIT conserver l'ordre décroissant des chunks de chaque retriever (AC-9.3)")
    void doitConserverOrdreDecroissantChunksParRetriever() {
        // Given — text retourne chunks triés par score décroissant
        List<ScoredChunk> sortedChunks = List.of(
            RetrievalTestHelper.buildScoredChunk("t1", "texte 1", 0.95, "text", 0),
            RetrievalTestHelper.buildScoredChunk("t2", "texte 2", 0.85, "text", 1),
            RetrievalTestHelper.buildScoredChunk("t3", "texte 3", 0.75, "text", 2)
        );
        when(textRetriever.retrieveAsync(any(), anyInt()))
            .thenReturn(RetrievalTestHelper.completedResult("text", sortedChunks));
        when(imageRetriever.retrieveAsync(any(), anyInt()))
            .thenReturn(RetrievalTestHelper.completedEmptyResult("image"));
        when(bm25Retriever.retrieveAsync(any(), anyInt()))
            .thenReturn(RetrievalTestHelper.completedEmptyResult("bm25"));

        // When
        Map<String, RetrievalResult> results = service.retrieveParallel(
            List.of("query"), buildHybridDecision());

        // Then — l'ordre est préservé
        List<ScoredChunk> textChunks = results.get("text").getChunks();
        assertThat(textChunks.get(0).getScore()).isGreaterThan(textChunks.get(1).getScore());
        assertThat(textChunks.get(1).getScore()).isGreaterThan(textChunks.get(2).getScore());
    }

    // =========================================================================
    // FR-015 — Zéro résultats → map vide ou résultats vides, sans erreur
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner des résultats vides sans erreur quand tous les retrievers retournent 0 chunks (FR-015)")
    void doitRetournerResultatsVidesQuandTousRetrieversRetournentZeroChunks() {
        // Given — tous les retrievers retournent des résultats vides
        when(textRetriever.retrieveAsync(any(), anyInt()))
            .thenReturn(RetrievalTestHelper.completedEmptyResult("text"));
        when(imageRetriever.retrieveAsync(any(), anyInt()))
            .thenReturn(RetrievalTestHelper.completedEmptyResult("image"));
        when(bm25Retriever.retrieveAsync(any(), anyInt()))
            .thenReturn(RetrievalTestHelper.completedEmptyResult("bm25"));

        // When
        Map<String, RetrievalResult> results = service.retrieveParallel(
            List.of("query sans résultat"), buildHybridDecision());

        // Then — pas d'exception, tous les résultats présents mais vides
        assertThat(results).isNotNull();
        int totalChunks = results.values().stream()
            .mapToInt(RetrievalResult::getTotalFound)
            .sum();
        assertThat(totalChunks).isEqualTo(0);
    }
}
