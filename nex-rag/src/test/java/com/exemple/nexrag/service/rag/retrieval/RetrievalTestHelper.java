package com.exemple.nexrag.service.rag.retrieval;

import com.exemple.nexrag.config.RetrievalConfig;
import com.exemple.nexrag.service.rag.retrieval.model.AggregatedContext;
import com.exemple.nexrag.service.rag.retrieval.model.AggregatedContext.SelectedChunk;
import com.exemple.nexrag.service.rag.retrieval.model.RetrievalResult;
import com.exemple.nexrag.service.rag.retrieval.model.RetrievalResult.ScoredChunk;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Helper partagé pour tous les specs de la Phase 3 — Retrieval
 *
 * Fournit :
 * - buildTestConfig()       : RetrievalConfig avec valeurs de test (parallelTimeout=200 ms)
 * - buildScoredChunk()      : ScoredChunk pour mocker les résultats de retriever
 * - buildRetrievalResult()  : RetrievalResult prêt à l'emploi
 * - buildEmptyResult()      : RetrievalResult vide
 * - completedResult()       : CompletableFuture<RetrievalResult> déjà complété
 * - buildSelectedChunk()    : SelectedChunk pour les tests d'agrégation
 * - buildAggregatedContext(): AggregatedContext pour les tests d'injection
 */
public final class RetrievalTestHelper {

    private RetrievalTestHelper() {
        // Utilitaire statique
    }

    // =========================================================================
    // CONFIGURATION
    // =========================================================================

    /**
     * Construit une RetrievalConfig avec des valeurs cohérentes avec application.yml
     * et un parallelTimeout court (200 ms) pour les tests de timeout.
     *
     * Valeurs testées :
     *   text.topK=20 | image.topK=5 | bm25.topK=10
     *   rrfK=60      | finalTopK=10 | maxTokens=200 000
     *   parallelTimeout=200 (ms) — court pour les tests
     */
    public static RetrievalConfig buildTestConfig() {
        RetrievalConfig config = new RetrievalConfig();

        // Query Transformer
        RetrievalConfig.QueryTransformer qt = new RetrievalConfig.QueryTransformer();
        qt.setEnabled(true);
        qt.setMethod("rule-based");
        qt.setMaxVariants(5);
        qt.setEnableSynonyms(true);
        qt.setEnableTemporalContext(true);
        config.setQueryTransformer(qt);

        // Query Router
        RetrievalConfig.QueryRouter qr = new RetrievalConfig.QueryRouter();
        qr.setEnabled(true);
        qr.setDefaultStrategy("HYBRID");
        qr.setConfidenceThreshold(0.7);
        config.setQueryRouter(qr);

        // Retrievers
        RetrievalConfig.Retrievers retrievers = new RetrievalConfig.Retrievers();
        retrievers.setParallelTimeout(200); // Court pour les tests de timeout

        RetrievalConfig.TextRetriever text = new RetrievalConfig.TextRetriever();
        text.setEnabled(true);
        text.setTopK(20);
        text.setSimilarityThreshold(0.7);
        retrievers.setText(text);

        RetrievalConfig.ImageRetriever image = new RetrievalConfig.ImageRetriever();
        image.setEnabled(true);
        image.setTopK(5);
        image.setSimilarityThreshold(0.6);
        retrievers.setImage(image);

        RetrievalConfig.BM25Retriever bm25 = new RetrievalConfig.BM25Retriever();
        bm25.setEnabled(true);
        bm25.setTopK(10);
        retrievers.setBm25(bm25);

        config.setRetrievers(retrievers);

        // Aggregator
        RetrievalConfig.Aggregator agg = new RetrievalConfig.Aggregator();
        agg.setRrfK(60);
        agg.setMaxCandidates(30);
        agg.setFinalTopK(10);
        config.setAggregator(agg);

        // Reranker — désactivé par défaut
        RetrievalConfig.Reranker reranker = new RetrievalConfig.Reranker();
        reranker.setEnabled(false);
        reranker.setTopK(10);
        config.setReranker(reranker);

        // ContentInjector
        RetrievalConfig.ContentInjector ci = new RetrievalConfig.ContentInjector();
        ci.setMaxTokens(200000);
        ci.setEnableCitations(true);
        config.setContentInjector(ci);

        return config;
    }

    // =========================================================================
    // SCORED CHUNK
    // =========================================================================

    public static ScoredChunk buildScoredChunk(String id, String content, double score, String retrieverName) {
        return ScoredChunk.builder()
            .id(id)
            .content(content)
            .metadata(Map.of("source", "test.pdf", "type", "text"))
            .score(score)
            .retrieverName(retrieverName)
            .rank(0)
            .build();
    }

    public static ScoredChunk buildScoredChunk(String id, String content, double score, String retrieverName, int rank) {
        return ScoredChunk.builder()
            .id(id)
            .content(content)
            .metadata(Map.of("source", "test.pdf", "type", "text"))
            .score(score)
            .retrieverName(retrieverName)
            .rank(rank)
            .build();
    }

    // =========================================================================
    // RETRIEVAL RESULT
    // =========================================================================

    public static RetrievalResult buildRetrievalResult(String retrieverName, List<ScoredChunk> chunks) {
        double topScore = chunks.isEmpty() ? 0.0 : chunks.get(0).getScore();
        return RetrievalResult.builder()
            .retrieverName(retrieverName)
            .chunks(new ArrayList<>(chunks))
            .totalFound(chunks.size())
            .topScore(topScore)
            .durationMs(10)
            .cacheHits(0)
            .cacheMisses(1)
            .build();
    }

    public static RetrievalResult buildEmptyResult(String retrieverName) {
        return buildRetrievalResult(retrieverName, Collections.emptyList());
    }

    public static CompletableFuture<RetrievalResult> completedResult(String retrieverName, List<ScoredChunk> chunks) {
        return CompletableFuture.completedFuture(buildRetrievalResult(retrieverName, chunks));
    }

    public static CompletableFuture<RetrievalResult> completedEmptyResult(String retrieverName) {
        return CompletableFuture.completedFuture(buildEmptyResult(retrieverName));
    }

    // =========================================================================
    // SELECTED CHUNK
    // =========================================================================

    public static SelectedChunk buildSelectedChunk(String id, String content, double score, String... retrievers) {
        return SelectedChunk.builder()
            .id(id)
            .content(content)
            .metadata(Map.of("source", "test.pdf", "type", "text"))
            .finalScore(score)
            .scoresByRetriever(Map.of())
            .retrieversUsed(new ArrayList<>(Arrays.asList(retrievers)))
            .build();
    }

    // =========================================================================
    // AGGREGATED CONTEXT
    // =========================================================================

    public static AggregatedContext buildAggregatedContext(List<SelectedChunk> chunks) {
        return AggregatedContext.builder()
            .chunks(new ArrayList<>(chunks))
            .inputChunks(chunks.size())
            .deduplicatedChunks(chunks.size())
            .rrfCandidates(chunks.size())
            .finalSelected(chunks.size())
            .fusionMethod("rrf")
            .durationMs(5)
            .chunksByRetriever(Map.of())
            .build();
    }

    public static AggregatedContext buildEmptyAggregatedContext() {
        return buildAggregatedContext(Collections.emptyList());
    }
}
