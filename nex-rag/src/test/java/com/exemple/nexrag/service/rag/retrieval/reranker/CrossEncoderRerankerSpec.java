package com.exemple.nexrag.service.rag.retrieval.reranker;

import com.exemple.nexrag.config.RetrievalConfig;
import com.exemple.nexrag.service.rag.metrics.RAGMetrics;
import com.exemple.nexrag.service.rag.retrieval.RetrievalTestHelper;
import com.exemple.nexrag.service.rag.retrieval.model.AggregatedContext.SelectedChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;

/**
 * Spec : CrossEncoderReranker — Reranking sémantique simulé
 *
 * AC couverts : AC-10.1 (meilleur chunk en tête après reranking)
 *
 * Note : CrossEncoderReranker est instancié directement (bypass @ConditionalOnProperty)
 * conformément à la décision 5 du research.md.
 */
@DisplayName("Spec : CrossEncoderReranker — Reranking et enregistrement métriques")
@ExtendWith(MockitoExtension.class)
class CrossEncoderRerankerSpec {

    @Mock
    private RAGMetrics ragMetrics;

    private RetrievalConfig config;
    private CrossEncoderReranker reranker;

    @BeforeEach
    void setUp() {
        config = RetrievalTestHelper.buildTestConfig();
        config.getReranker().setEnabled(true);
        // Construction directe : bypass @ConditionalOnProperty
        reranker = new CrossEncoderReranker(config, ragMetrics);
    }

    // =========================================================================
    // AC-10.1 — Le plus pertinent en tête après reranking
    // =========================================================================

    @Test
    @DisplayName("DOIT réordonner les passages pour que le plus pertinent soit en tête (AC-10.1)")
    void doitReordonnerPassagesPourQuePlusPertinentSoitEnTete() {
        // Given — query avec mots-clés spécifiques
        String query = "résultats ventes performance annuel";

        // Chunk pertinent : son contenu contient les mots de la query
        SelectedChunk pertinent = RetrievalTestHelper.buildSelectedChunk(
            "c-pertinent",
            "les résultats des ventes ont montré une bonne performance annuelle",
            0.01,  // score RRF bas — le reranker doit le promouvoir
            "text"
        );

        // Chunks moins pertinents
        SelectedChunk moinsPertinent1 = RetrievalTestHelper.buildSelectedChunk(
            "c-autre1", "document sur la politique de l'entreprise", 0.02, "text"
        );
        SelectedChunk moinsPertinent2 = RetrievalTestHelper.buildSelectedChunk(
            "c-autre2", "présentation générale de l'organisation", 0.015, "text"
        );
        SelectedChunk moinsPertinent3 = RetrievalTestHelper.buildSelectedChunk(
            "c-autre3", "rapport annuel de gouvernance", 0.012, "text"
        );
        SelectedChunk moinsPertinent4 = RetrievalTestHelper.buildSelectedChunk(
            "c-autre4", "stratégie d'investissement à long terme", 0.011, "text"
        );

        List<SelectedChunk> chunks = List.of(
            moinsPertinent1, moinsPertinent2, pertinent, moinsPertinent3, moinsPertinent4
        );

        // When
        List<SelectedChunk> reranked = reranker.rerank(query, chunks);

        // Then — le chunk pertinent doit être en première position
        assertThat(reranked).isNotEmpty();
        assertThat(reranked.get(0).getId()).isEqualTo("c-pertinent");
    }

    // =========================================================================
    // Enregistrement des métriques
    // =========================================================================

    @Test
    @DisplayName("DOIT enregistrer les métriques recordReranking() via RAGMetrics")
    void doitEnregistrerMetriquesRecordRerankingViaRAGMetrics() {
        // Given
        String query = "analyse financière";
        List<SelectedChunk> chunks = List.of(
            RetrievalTestHelper.buildSelectedChunk("c1", "analyse des résultats financiers", 0.02, "text"),
            RetrievalTestHelper.buildSelectedChunk("c2", "bilan comptable annuel", 0.015, "text"),
            RetrievalTestHelper.buildSelectedChunk("c3", "revue stratégique", 0.01, "text")
        );

        // When
        reranker.rerank(query, chunks);

        // Then — recordReranking doit être appelé avec la durée et le nombre de chunks
        verify(ragMetrics).recordReranking(anyLong(), anyInt());
    }

    // =========================================================================
    // Cas limite — un seul passage
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner la liste inchangée sans erreur si un seul passage est fourni")
    void doitRetournerListeInchangeeSansErreurSiUnSeulPassage() {
        // Given
        String query = "query quelconque";
        SelectedChunk seul = RetrievalTestHelper.buildSelectedChunk(
            "c-seul", "contenu unique", 0.05, "text"
        );

        // When
        List<SelectedChunk> result = reranker.rerank(query, List.of(seul));

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("c-seul");
    }
}
