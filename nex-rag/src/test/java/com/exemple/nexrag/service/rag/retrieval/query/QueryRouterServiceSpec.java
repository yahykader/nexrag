package com.exemple.nexrag.service.rag.retrieval.query;

import com.exemple.nexrag.config.RetrievalConfig;
import com.exemple.nexrag.service.rag.retrieval.RetrievalTestHelper;
import com.exemple.nexrag.service.rag.retrieval.model.RoutingDecision;
import com.exemple.nexrag.service.rag.retrieval.model.RoutingDecision.Strategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec : QueryRouterService — Routage intelligent de requêtes
 *
 * AC couverts : AC-8.2 (IMAGE_ONLY), AC-8.3 (HYBRID général), FR-002, FR-003
 */
@DisplayName("Spec : QueryRouterService — Routage de requêtes par stratégie")
@ExtendWith(MockitoExtension.class)
class QueryRouterServiceSpec {

    private RetrievalConfig config;
    private QueryRouterService service;

    @BeforeEach
    void setUp() {
        config = RetrievalTestHelper.buildTestConfig();
        service = new QueryRouterService(config);
    }

    // =========================================================================
    // AC-8.2 — IMAGE_ONLY pour queries avec mots-clés image
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner IMAGE_ONLY pour une query contenant 'graphique' sans mots-clés texte (AC-8.2)")
    void doitRetournerImageOnlyQuandQueryContientGraphique() {
        // Given
        String query = "montre-moi le graphique des ventes";

        // When
        RoutingDecision decision = service.route(query);

        // Then
        assertThat(decision.getStrategy()).isEqualTo(Strategy.IMAGE_ONLY);
        assertThat(decision.getConfidence()).isGreaterThanOrEqualTo(0.6);
        assertThat(decision.getRetrievers()).containsKey("image");
        assertThat(decision.getRetrievers().get("image").isEnabled()).isTrue();
        assertThat(decision.getRetrievers().get("image").getPriority())
            .isEqualTo(RoutingDecision.Priority.HIGH);
    }

    // =========================================================================
    // AC-8.3 — HYBRID pour query générale
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner HYBRID pour une query générale sans mots-clés spécifiques (AC-8.3)")
    void doitRetournerHybridPourQueryGenerale() {
        // Given — pas de mots-clés image/texte/structuré
        String query = "quelle est la stratégie commerciale de l'entreprise";

        // When
        RoutingDecision decision = service.route(query);

        // Then
        assertThat(decision.getStrategy()).isEqualTo(Strategy.HYBRID);
        assertThat(decision.isParallelExecution()).isTrue();
    }

    // =========================================================================
    // TEXT_ONLY pour query d'explication
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner TEXT_ONLY pour une query d'explication sans mots-clés image")
    void doitRetournerTextOnlyPourQueryExplication() {
        // Given — "pourquoi" est dans TEXT_PATTERNS (ASCII, word boundaries fonctionnent)
        String query = "pourquoi les ventes ont diminué cette année";

        // When
        RoutingDecision decision = service.route(query);

        // Then
        assertThat(decision.getStrategy()).isEqualTo(Strategy.TEXT_ONLY);
        assertThat(decision.getRetrievers().get("text").isEnabled()).isTrue();
        assertThat(decision.getRetrievers().get("image").isEnabled()).isFalse();
        assertThat(decision.getRetrievers().get("bm25").isEnabled()).isFalse();
    }

    // =========================================================================
    // STRUCTURED pour query avec données chiffrées
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner STRUCTURED pour une query avec données chiffrées")
    void doitRetournerStructuredPourQueryAvecDonneesChiffrees() {
        // Given — "données" est dans STRUCTURED_PATTERNS
        String query = "tableau des données financières";

        // When
        RoutingDecision decision = service.route(query);

        // Then
        assertThat(decision.getStrategy()).isEqualTo(Strategy.STRUCTURED);
        assertThat(decision.getRetrievers().get("bm25").isEnabled()).isTrue();
        assertThat(decision.getRetrievers().get("image").isEnabled()).isFalse();
    }

    // =========================================================================
    // FR-003 — Score de confiance toujours ≥ 0.6
    // =========================================================================

    @Test
    @DisplayName("DOIT avoir un score de confiance ≥ 0.6 pour toute stratégie (FR-003)")
    void doitAvoirScoreConfidencePositifPourTouteStrategie() {
        // Given — plusieurs queries différentes
        String[] queries = {
            "quelle est la performance",
            "montre le graphique",
            "explique le concept",
            "données du tableau",
            "analyse et compare"
        };

        for (String query : queries) {
            // When
            RoutingDecision decision = service.route(query);

            // Then
            assertThat(decision.getConfidence())
                .as("confidence pour : " + query)
                .isGreaterThanOrEqualTo(0.6);
        }
    }

    // =========================================================================
    // Désactivation — HYBRID par défaut
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner HYBRID par défaut quand router enabled=false")
    void doitRetournerHybridParDefautQuandRouterDesactive() {
        // Given
        config.getQueryRouter().setEnabled(false);
        String query = "montre le graphique image photo";

        // When
        RoutingDecision decision = service.route(query);

        // Then — stratégie par défaut même si la query serait IMAGE_ONLY
        assertThat(decision.getStrategy()).isEqualTo(Strategy.HYBRID);
        assertThat(decision.getConfidence()).isEqualTo(0.5);
    }

    // =========================================================================
    // Configuration des retrievers selon stratégie HYBRID
    // =========================================================================

    @Test
    @DisplayName("DOIT configurer les trois retrievers avec les topK corrects en mode HYBRID")
    void doitConfigurerTroisRetrieversAvecTopKCorrectsPourHybrid() {
        // Given
        String query = "quels sont les résultats 2024";

        // When
        RoutingDecision decision = service.route(query);

        // Then — en HYBRID, tous les retrievers sont configurés via la config
        assertThat(decision.getRetrievers()).containsKeys("text", "image", "bm25");
        assertThat(decision.getRetrievers().get("text").getTopK()).isEqualTo(20);
        assertThat(decision.getRetrievers().get("image").getTopK()).isEqualTo(5);
        assertThat(decision.getRetrievers().get("bm25").getTopK()).isEqualTo(10);
    }

    // =========================================================================
    // HYBRID avec image keywords → image priority = HIGH
    // =========================================================================

    @Test
    @DisplayName("DOIT attribuer la priorité HIGH à l'image retriever en HYBRID quand la query contient des mots-clés image ET texte")
    void doitAttribuerPrioriteHighImageEnHybridQuandMotsClesImageEtTexte() {
        // Given — image + texte → HYBRID (pas IMAGE_ONLY car TEXT présent)
        // "graphique" est un image keyword, "explique" est un text keyword → HYBRID
        String query = "explique le graphique des ventes";

        // When
        RoutingDecision decision = service.route(query);

        // Then — stratégie HYBRID, image priority = HIGH (car hasImageKeywords=true)
        assertThat(decision.getStrategy()).isEqualTo(Strategy.HYBRID);
        assertThat(decision.getRetrievers().get("image").getPriority())
            .isEqualTo(RoutingDecision.Priority.HIGH);
    }

    // =========================================================================
    // Détection isComparative — query avec "compare"
    // =========================================================================

    @Test
    @DisplayName("DOIT détecter une requête comparative quand la query contient 'compare'")
    void doitDetecterRequeteComparativeQuandQueryContientCompare() {
        // Given — "compare" déclenche isComparative=true → augmente le matchCount
        String query = "compare les resultats vs objectifs";

        // When
        RoutingDecision decision = service.route(query);

        // Then — décision valide avec confidence ≥ 0.6
        assertThat(decision).isNotNull();
        assertThat(decision.getConfidence()).isGreaterThanOrEqualTo(0.6);
    }

    // =========================================================================
    // Détection isQuestion — query terminant par '?'
    // =========================================================================

    @Test
    @DisplayName("DOIT traiter une query terminant par '?' sans erreur")
    void doitTraiterQueryTerminantParPointInterrogationSansErreur() {
        // Given — query se terminant par '?' → isQuestion = true
        String query = "quels sont les résultats du trimestre?";

        // When
        RoutingDecision decision = service.route(query);

        // Then — décision valide
        assertThat(decision).isNotNull();
        assertThat(decision.getStrategy()).isNotNull();
        assertThat(decision.getConfidence()).isGreaterThanOrEqualTo(0.6);
    }

    // =========================================================================
    // Mots-clés analytiques — confidence boostée
    // =========================================================================

    @Test
    @DisplayName("DOIT détecter les mots-clés analytiques et retourner HYBRID avec confidence > 0.7")
    void doitDetecterMotsClesAnalytiquesEtRetournerHybridAvecConfidenceElevee() {
        // Given — "progression" est dans ANALYTICAL_PATTERNS (ASCII, \b fonctionne)
        String query = "quelle est la progression des ventes";

        // When
        RoutingDecision decision = service.route(query);

        // Then — HYBRID car analytique seul ne déclenche pas TEXT_ONLY/IMAGE_ONLY
        assertThat(decision.getStrategy()).isEqualTo(Strategy.HYBRID);
        assertThat(decision.getConfidence()).isGreaterThan(0.7);
    }
}
