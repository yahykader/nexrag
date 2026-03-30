package com.exemple.nexrag.service.rag.retrieval.query;

import com.exemple.nexrag.config.RetrievalConfig;
import com.exemple.nexrag.service.rag.retrieval.RetrievalTestHelper;
import com.exemple.nexrag.service.rag.retrieval.model.QueryTransformResult;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Spec : QueryTransformerService — Transformation de requêtes par règles et LLM
 *
 * AC couverts : AC-8.1 (expansion synonymes), FR-001 (exactement 5 variantes max)
 */
@DisplayName("Spec : QueryTransformerService — Transformation de requêtes")
@ExtendWith(MockitoExtension.class)
class QueryTransformerServiceSpec {

    @Mock
    private ChatLanguageModel chatModel;

    private RetrievalConfig config;
    private QueryTransformerService service;

    @BeforeEach
    void setUp() {
        config = RetrievalTestHelper.buildTestConfig();
        service = new QueryTransformerService(chatModel, config);
    }

    // =========================================================================
    // AC-8.1 — Expansion de synonymes
    // =========================================================================

    @Test
    @DisplayName("DOIT générer des variantes synonymes quand la query contient 'analyse' en mode rule-based (AC-8.1)")
    void doitGenererVariantesSynonymesQuandQueryContientAnalyse() {
        // Given — "analyse" est dans le dictionnaire SYNONYMS
        String query = "analyse des résultats";

        // When
        QueryTransformResult result = service.transform(query);

        // Then
        assertThat(result.getOriginalQuery()).isEqualTo(query);
        assertThat(result.getVariants()).contains(query);
        assertThat(result.getVariants().size()).isGreaterThan(1);
        assertThat(result.getMethod()).isEqualTo("rule-based");
    }

    // =========================================================================
    // FR-001 — Développement des acronymes
    // =========================================================================

    @Test
    @DisplayName("DOIT développer l'acronyme 'CA' en 'chiffre d''affaires' dans les variantes")
    void doitDevelopperAcronymeCAQuandQueryContientCA() {
        // Given — "ca" est dans le dictionnaire ACRONYMS
        String query = "quel est le CA du trimestre";

        // When
        QueryTransformResult result = service.transform(query);

        // Then — au moins une variante doit contenir l'expansion
        assertThat(result.getVariants()).isNotEmpty();
        assertThat(result.getVariants())
            .anyMatch(v -> v.contains("chiffre d'affaires") || v.contains("chiffre affaires"));
    }

    // =========================================================================
    // FR-001 — Limite maxVariants
    // =========================================================================

    @Test
    @DisplayName("DOIT limiter les variantes à maxVariants=5 même si plus sont générées (FR-001)")
    void doitLimiterVariantesAMaxVariantsQuandBeaucoupSontGenerees() {
        // Given — query avec synonymes ET contexte temporel → génère > 5 variantes brutes
        // "ventes" a des synonymes ET "q1" déclenche le contexte temporel
        String query = "ventes q1";
        config.getQueryTransformer().setMaxVariants(5);

        // When
        QueryTransformResult result = service.transform(query);

        // Then
        assertThat(result.getVariants()).hasSizeLessThanOrEqualTo(5);
    }

    // =========================================================================
    // FR-001 — Query originale toujours présente
    // =========================================================================

    @Test
    @DisplayName("DOIT toujours inclure la query originale dans les variantes")
    void doitToujoursInclureQueryOriginaleQuandTransformation() {
        // Given
        String query = "chiffre d'affaires annuel";

        // When
        QueryTransformResult result = service.transform(query);

        // Then
        assertThat(result.getVariants()).contains(query);
        assertThat(result.getOriginalQuery()).isEqualTo(query);
    }

    // =========================================================================
    // Désactivation
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner seulement la query originale quand transformer enabled=false")
    void doitRetournerQuerySeuleQuandTransformerDesactive() {
        // Given
        config.getQueryTransformer().setEnabled(false);
        String query = "analyse ventes 2024";

        // When
        QueryTransformResult result = service.transform(query);

        // Then
        assertThat(result.getVariants()).containsExactly(query);
        assertThat(result.getMethod()).isEqualTo("disabled");
        assertThat(result.getConfidence()).isEqualTo(1.0);
    }

    // =========================================================================
    // Fallback rule-based quand LLM échoue
    // =========================================================================

    @Test
    @DisplayName("DOIT utiliser le fallback rule-based quand le LLM lance une exception")
    void doitUtiliserFallbackRuleBasedQuandLLMEchoue() {
        // Given — LLM configuré mais qui plante
        config.getQueryTransformer().setMethod("llm");
        when(chatModel.generate(anyString())).thenThrow(new RuntimeException("Timeout LLM"));
        String query = "analyse ventes";

        // When
        QueryTransformResult result = service.transform(query);

        // Then — fallback activé, toujours des variantes valides
        assertThat(result.getMethod()).isEqualTo("rule-based-fallback");
        assertThat(result.getVariants()).isNotEmpty();
        assertThat(result.getVariants()).contains(query);
    }

    // =========================================================================
    // Case LLM — réponse JSON valide parsée
    // =========================================================================

    @Test
    @DisplayName("DOIT parser la réponse JSON du LLM et retourner les variantes (case llm)")
    void doitParserReponseJsonLLMEtRetournerVariantes() {
        // Given — LLM retourne un JSON array valide
        config.getQueryTransformer().setMethod("llm");
        when(chatModel.generate(anyString()))
            .thenReturn("[\"résultats trimestriels\", \"performance Q1\", \"bilan financier\"]");
        String query = "résultats du trimestre";

        // When
        QueryTransformResult result = service.transform(query);

        // Then — variantes parsées depuis JSON, méthode = "llm"
        assertThat(result.getMethod()).isEqualTo("llm");
        assertThat(result.getVariants()).isNotEmpty();
        assertThat(result.getVariants()).hasSizeLessThanOrEqualTo(5);
    }

    // =========================================================================
    // Case LLM — réponse non-JSON (ligne par ligne)
    // =========================================================================

    @Test
    @DisplayName("DOIT parser la réponse ligne par ligne quand le LLM ne retourne pas de JSON array")
    void doitParserReponseNonJsonLigneParligne() {
        // Given — LLM retourne du texte brut (pas un array JSON)
        config.getQueryTransformer().setMethod("llm");
        when(chatModel.generate(anyString()))
            .thenReturn("résultats trimestriels\nperformance Q1\nbilan financier");
        String query = "résultats du trimestre";

        // When
        QueryTransformResult result = service.transform(query);

        // Then — fallback ligne-par-ligne, pas d'exception
        assertThat(result.getMethod()).isEqualTo("llm");
        assertThat(result.getVariants()).isNotEmpty();
    }

    // =========================================================================
    // Case HYBRID — fusion LLM + rules
    // =========================================================================

    @Test
    @DisplayName("DOIT fusionner les variantes LLM et rule-based en mode hybrid")
    void doitFusionnerVariantesLLMEtRulesEnModeHybrid() {
        // Given — méthode hybrid, LLM retourne un JSON valide
        config.getQueryTransformer().setMethod("hybrid");
        when(chatModel.generate(anyString()))
            .thenReturn("[\"variante LLM 1\", \"variante LLM 2\"]");
        String query = "analyse ventes";

        // When
        QueryTransformResult result = service.transform(query);

        // Then — variantes fusionnées (LLM + rules), limitées à maxVariants
        assertThat(result.getMethod()).isEqualTo("hybrid");
        assertThat(result.getVariants()).hasSizeLessThanOrEqualTo(5);
        assertThat(result.getVariants()).isNotEmpty();
    }

    // =========================================================================
    // Case default — méthode inconnue
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner uniquement la query originale pour une méthode inconnue")
    void doitRetournerQuerySeulePourMethodeInconnue() {
        // Given — méthode non reconnue → default case du switch
        config.getQueryTransformer().setMethod("unknown-method");
        String query = "analyse des ventes";

        // When
        QueryTransformResult result = service.transform(query);

        // Then — seule la query originale dans les variantes
        assertThat(result.getVariants()).containsExactly(query);
    }

    // =========================================================================
    // Expansion temporelle — query avec trimestre Q1
    // =========================================================================

    @Test
    @DisplayName("DOIT ajouter des variantes temporelles quand la query contient 'q1'")
    void doitAjouterVariantesTemporellesQuandQueryContientQ1() {
        // Given — "q1" déclenche l'expansion temporelle
        String query = "résultats q1";

        // When
        QueryTransformResult result = service.transform(query);

        // Then — au moins une variante avec contexte temporel
        assertThat(result.getVariants()).hasSizeGreaterThan(1);
        assertThat(result.getVariants())
            .anyMatch(v -> v.contains("premier trimestre") || v.contains("T1") || v.contains("q1"));
    }

    // =========================================================================
    // Synonymes désactivés — branch false de isEnableSynonyms
    // =========================================================================

    @Test
    @DisplayName("DOIT ne générer aucune variante synonyme quand enableSynonyms=false")
    void doitNeGenererAucuneVarianteSynonymeQuandSynonymesDesactives() {
        // Given — synonymes désactivés, contexte temporel désactivé
        config.getQueryTransformer().setEnableSynonyms(false);
        config.getQueryTransformer().setEnableTemporalContext(false);
        String query = "analyse ventes";

        // When
        QueryTransformResult result = service.transform(query);

        // Then — seule la query originale (pas d'expansion)
        assertThat(result.getVariants()).containsExactly(query);
    }

    // =========================================================================
    // Contexte temporel désactivé — branch false de isEnableTemporalContext
    // =========================================================================

    @Test
    @DisplayName("DOIT ne générer aucune variante temporelle quand enableTemporalContext=false")
    void doitNeGenererAucuneVarianteTemporelleQuandContexteTemporelDesactive() {
        // Given — contexte temporel désactivé
        config.getQueryTransformer().setEnableTemporalContext(false);
        String query = "ventes q1";

        // When
        QueryTransformResult result = service.transform(query);

        // Then — aucune variante avec "premier trimestre" ou "T1"
        assertThat(result.getVariants())
            .noneMatch(v -> v.contains("premier trimestre") || v.contains("T1"));
    }
}
