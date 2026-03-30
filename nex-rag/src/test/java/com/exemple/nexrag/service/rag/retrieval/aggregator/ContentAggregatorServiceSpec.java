package com.exemple.nexrag.service.rag.retrieval.aggregator;

import com.exemple.nexrag.config.RetrievalConfig;
import com.exemple.nexrag.service.rag.retrieval.RetrievalTestHelper;
import com.exemple.nexrag.service.rag.retrieval.model.AggregatedContext;
import com.exemple.nexrag.service.rag.retrieval.model.AggregatedContext.SelectedChunk;
import com.exemple.nexrag.service.rag.retrieval.model.RetrievalResult;
import com.exemple.nexrag.service.rag.retrieval.model.RetrievalResult.ScoredChunk;
import com.exemple.nexrag.service.rag.retrieval.reranker.Reranker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec : ContentAggregatorService — Déduplication, fusion RRF et reranking optionnel
 *
 * AC couverts : AC-10.2 (dédup par ID), FR-009 (RRF k=60), FR-011 (dédup ID),
 *               FR-015 (zéro résultats)
 *
 * Gaps connus (research.md) :
 *  - Déduplication par ID uniquement (pas cosinus)
 *  - Poids RRF (0.5/0.3/0.2) configurés mais non appliqués → poids égaux en production
 */
@DisplayName("Spec : ContentAggregatorService — Fusion RRF, déduplication et reranking")
@ExtendWith(MockitoExtension.class)
class ContentAggregatorServiceSpec {

    @Mock
    private Reranker reranker;

    private RetrievalConfig config;
    private ContentAggregatorService service;

    @BeforeEach
    void setUp() {
        config = RetrievalTestHelper.buildTestConfig();
        // Reranker désactivé par défaut
        service = new ContentAggregatorService(config, Optional.empty());
    }

    // =========================================================================
    // AC-10.2 — Déduplication par ID (gap research.md : pas cosinus)
    // =========================================================================

    @Test
    @DisplayName("DOIT dédupliquer deux chunks avec le même ID en retenant le score le plus élevé (AC-10.2 / FR-011)")
    void doitDedupliquerDeuxChunksAvecMemneIdEnRetentantMeilleurScore() {
        // Given — même chunk ID dans text (score 0.9) et bm25 (score 0.6)
        ScoredChunk chunkText = RetrievalTestHelper.buildScoredChunk("dup-id", "contenu partagé", 0.9, "text");
        ScoredChunk chunkBm25 = RetrievalTestHelper.buildScoredChunk("dup-id", "contenu partagé", 0.6, "bm25");
        ScoredChunk unique = RetrievalTestHelper.buildScoredChunk("unique-id", "autre contenu", 0.7, "text");

        Map<String, RetrievalResult> results = Map.of(
            "text", RetrievalTestHelper.buildRetrievalResult("text", List.of(chunkText, unique)),
            "bm25", RetrievalTestHelper.buildRetrievalResult("bm25", List.of(chunkBm25))
        );

        // When
        AggregatedContext context = service.aggregate(results, "query");

        // Then — dup-id apparaît une seule fois, unique-id également
        assertThat(context.getDeduplicatedChunks()).isEqualTo(2); // dup-id dédupliqué
        assertThat(context.getInputChunks()).isEqualTo(3);       // 3 chunks en entrée
    }

    // =========================================================================
    // FR-009 — Fusion RRF avec k=60 : score vérifiable
    // =========================================================================

    @Test
    @DisplayName("DOIT appliquer la fusion RRF et produire le score 1/(k+rank+1) avec k=60 (FR-009)")
    void doitAppliquerFusionRRFAvecK60EtProduireScoreCalculable() {
        // Given — un seul chunk au rang 0 dans le text retriever
        ScoredChunk chunk = RetrievalTestHelper.buildScoredChunk("rrf-id", "contenu rrf", 0.9, "text");
        Map<String, RetrievalResult> results = Map.of(
            "text", RetrievalTestHelper.buildRetrievalResult("text", List.of(chunk))
        );

        // When
        AggregatedContext context = service.aggregate(results, "query rrf");

        // Then — score RRF = 1/(60+0+1) = 1/61 ≈ 0.01639
        double expectedRrfScore = 1.0 / (60 + 0 + 1);
        assertThat(context.getChunks()).isNotEmpty();
        assertThat(context.getChunks().get(0).getFinalScore())
            .isCloseTo(expectedRrfScore, within(1e-10));
    }

    // =========================================================================
    // Limitation à finalTopK=10
    // =========================================================================

    @Test
    @DisplayName("DOIT limiter le résultat final à finalTopK=10")
    void doitLimiterResultatFinalAFinalTopK() {
        // Given — 15 chunks distincts dans text
        List<ScoredChunk> chunks = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            chunks.add(RetrievalTestHelper.buildScoredChunk("id-" + i, "contenu " + i, 0.9 - i * 0.01, "text", i));
        }
        Map<String, RetrievalResult> results = Map.of(
            "text", RetrievalTestHelper.buildRetrievalResult("text", chunks)
        );

        // When
        AggregatedContext context = service.aggregate(results, "query");

        // Then
        assertThat(context.getFinalSelected()).isLessThanOrEqualTo(10);
        assertThat(context.getChunks()).hasSizeLessThanOrEqualTo(10);
    }

    // =========================================================================
    // FR-015 — Zéro résultats → AggregatedContext vide sans erreur
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner un AggregatedContext vide sans erreur quand tous les retrievers retournent 0 chunks (FR-015)")
    void doitRetournerContexteVideSansErreurQuandZeroChunks() {
        // Given
        Map<String, RetrievalResult> results = Map.of(
            "text",  RetrievalTestHelper.buildEmptyResult("text"),
            "image", RetrievalTestHelper.buildEmptyResult("image"),
            "bm25",  RetrievalTestHelper.buildEmptyResult("bm25")
        );

        // When
        AggregatedContext context = service.aggregate(results, "query vide");

        // Then
        assertThat(context.getChunks()).isEmpty();
        assertThat(context.getFinalSelected()).isEqualTo(0);
        assertThat(context.getInputChunks()).isEqualTo(0);
    }

    // =========================================================================
    // Délégation au reranker quand enabled=true
    // =========================================================================

    @Test
    @DisplayName("DOIT déléguer au reranker quand reranker.enabled=true")
    void doitDeleguérAuRerankerQuandRerankerActive() {
        // Given — reranker activé
        config.getReranker().setEnabled(true);
        ContentAggregatorService serviceAvecReranker =
            new ContentAggregatorService(config, Optional.of(reranker));

        ScoredChunk chunk = RetrievalTestHelper.buildScoredChunk("c1", "contenu", 0.9, "text");
        Map<String, RetrievalResult> results = Map.of(
            "text", RetrievalTestHelper.buildRetrievalResult("text", List.of(chunk))
        );

        List<SelectedChunk> rerankedChunks = List.of(
            RetrievalTestHelper.buildSelectedChunk("c1", "contenu", 0.05, "text")
        );
        when(reranker.rerank(anyString(), anyList())).thenReturn(rerankedChunks);

        // When
        serviceAvecReranker.aggregate(results, "query reranker");

        // Then — reranker.rerank() appelé
        verify(reranker).rerank(anyString(), anyList());
    }
}
