package com.exemple.nexrag.service.rag.retrieval.retriever;

import com.exemple.nexrag.service.rag.retrieval.model.RetrievalResult;
import com.exemple.nexrag.service.rag.retrieval.model.RetrievalResult.ScoredChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Spec : BM25Retriever — Recherche full-text PostgreSQL
 *
 * AC couverts : FR-007 (top-10), isolation par rapport à TextVectorRetriever
 */
@DisplayName("Spec : BM25Retriever — Recherche BM25 via JdbcTemplate")
@ExtendWith(MockitoExtension.class)
class BM25RetrieverSpec {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private BM25Retriever service;

    @BeforeEach
    void setUp() {
        // BM25Retriever n'a besoin que de JdbcTemplate — aucun mock de TextVectorRetriever
        service = new BM25Retriever(jdbcTemplate);
    }

    private List<ScoredChunk> buildChunks(int count) {
        List<ScoredChunk> chunks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            chunks.add(ScoredChunk.builder()
                .id("bm25-" + i)
                .content("contenu bm25 " + i)
                .metadata(Map.of())
                .score(0.9 - (i * 0.05))
                .retrieverName("bm25")
                .rank(i)
                .build());
        }
        return chunks;
    }

    // =========================================================================
    // FR-007 — Retourne au maximum top-10 résultats
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner exactement top-10 résultats quand le store en retourne 10 (FR-007)")
    @SuppressWarnings("unchecked")
    void doitRetournerTop10ResultatsQuandStoreRetourne10() {
        // Given
        List<ScoredChunk> chunks = buildChunks(10);
        when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class)))
            .thenReturn(chunks);

        // When
        RetrievalResult result = service.retrieveAsync(List.of("ventes 2024"), 10).join();

        // Then
        assertThat(result.getChunks()).hasSize(10);
        assertThat(result.getTotalFound()).isEqualTo(10);
        assertThat(result.getRetrieverName()).isEqualTo("bm25");
    }

    // =========================================================================
    // Isolation — pas de dépendance à TextVectorRetriever
    // =========================================================================

    @Test
    @DisplayName("DOIT opérer indépendamment sans aucune dépendance sur TextVectorRetriever")
    @SuppressWarnings("unchecked")
    void doitOpererIndependammentSansTextVectorRetriever() {
        // Given — BM25Retriever instancié avec seulement JdbcTemplate
        when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class)))
            .thenReturn(buildChunks(3));

        // When
        RetrievalResult result = service.retrieveAsync(List.of("analyse performance"), 10).join();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getRetrieverName()).isEqualTo("bm25");
        assertThat(result.getChunks()).hasSize(3);
    }

    // =========================================================================
    // Résultat vide
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner un résultat vide sans erreur si aucun document ne correspond")
    @SuppressWarnings("unchecked")
    void doitRetournerResultatVideSansErreurQuandAucunDocumentCorrespond() {
        // Given
        when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class)))
            .thenReturn(Collections.emptyList());

        // When
        RetrievalResult result = service.retrieveAsync(List.of("requête sans correspondance"), 10).join();

        // Then
        assertThat(result.getChunks()).isEmpty();
        assertThat(result.getTotalFound()).isEqualTo(0);
        assertThat(result.getTopScore()).isEqualTo(0.0);
        assertThat(result.getRetrieverName()).isEqualTo("bm25");
    }
}
