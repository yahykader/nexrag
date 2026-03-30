package com.exemple.nexrag.service.rag.retrieval.injector;

import com.exemple.nexrag.config.RetrievalConfig;
import com.exemple.nexrag.service.rag.retrieval.RetrievalTestHelper;
import com.exemple.nexrag.service.rag.retrieval.model.AggregatedContext;
import com.exemple.nexrag.service.rag.retrieval.model.AggregatedContext.SelectedChunk;
import com.exemple.nexrag.service.rag.retrieval.model.InjectedPrompt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Spec : ContentInjectorService — Injection de contexte et respect du budget tokens
 *
 * AC couverts : AC-10.3 (budget tokens maxTokens=200 000), FR-012 (contenu + citations),
 *               FR-013 (budget tokens), FR-014 (citations sources)
 */
@DisplayName("Spec : ContentInjectorService — Injection de contexte avec budget tokens")
@ExtendWith(MockitoExtension.class)
class ContentInjectorServiceSpec {

    private RetrievalConfig config;
    private ContentInjectorService service;

    @BeforeEach
    void setUp() {
        config = RetrievalTestHelper.buildTestConfig();
        service = new ContentInjectorService(config);
    }

    // =========================================================================
    // AC-10.3 / FR-013 — Budget tokens ne dépasse jamais maxTokens=200 000
    // =========================================================================

    @Test
    @DisplayName("DOIT ne jamais dépasser maxTokens=200 000 pour des chunks de taille raisonnable (AC-10.3)")
    void doitNePasDepasserMaxTokensAvecChunksRaisonnables() {
        // Given — 5 chunks avec du contenu court
        List<SelectedChunk> chunks = List.of(
            RetrievalTestHelper.buildSelectedChunk("c1", "Analyse des résultats du trimestre.", 0.05, "text"),
            RetrievalTestHelper.buildSelectedChunk("c2", "Performance des ventes en hausse.", 0.04, "text"),
            RetrievalTestHelper.buildSelectedChunk("c3", "Objectifs atteints pour l'année.", 0.03, "text"),
            RetrievalTestHelper.buildSelectedChunk("c4", "Stratégie commerciale définie.", 0.02, "text"),
            RetrievalTestHelper.buildSelectedChunk("c5", "Bilan positif de l'exercice.", 0.01, "text")
        );
        AggregatedContext context = RetrievalTestHelper.buildAggregatedContext(chunks);
        String query = "quels sont les résultats ?";

        // When
        InjectedPrompt prompt = service.injectContext(context, query);

        // Then
        assertThat(prompt.getStructure().getTotalTokens()).isLessThanOrEqualTo(200000);
        assertThat(prompt.getContextUsagePercent()).isLessThanOrEqualTo(100.0);
    }

    // =========================================================================
    // FR-012 — Le prompt contient la query et le contexte des passages
    // =========================================================================

    @Test
    @DisplayName("DOIT inclure la query utilisateur et le contexte des passages dans le prompt final (FR-012)")
    void doitInclureQueryEtContextePassagesDansPromptFinal() {
        // Given
        SelectedChunk chunk = RetrievalTestHelper.buildSelectedChunk(
            "c1", "Le chiffre d'affaires a augmenté de 15% cette année.", 0.05, "text"
        );
        AggregatedContext context = RetrievalTestHelper.buildAggregatedContext(List.of(chunk));
        String query = "quelle est la croissance du chiffre d'affaires ?";

        // When
        InjectedPrompt prompt = service.injectContext(context, query);

        // Then — query et contenu du chunk présents dans le prompt
        assertThat(prompt.getFullPrompt()).contains(query);
        assertThat(prompt.getFullPrompt()).contains("Le chiffre d'affaires a augmenté de 15%");
        assertThat(prompt.getStructure().getUserQuery()).isEqualTo(query);
    }

    // =========================================================================
    // Calcul de contextUsagePercent
    // =========================================================================

    @Test
    @DisplayName("DOIT calculer contextUsagePercent = (totalTokens / maxTokens) * 100")
    void doitCalculerContextUsagePercentCorrectement() {
        // Given
        SelectedChunk chunk = RetrievalTestHelper.buildSelectedChunk(
            "c1", "Contenu de test pour vérifier le calcul des tokens.", 0.05, "text"
        );
        AggregatedContext context = RetrievalTestHelper.buildAggregatedContext(List.of(chunk));
        String query = "test calcul tokens";

        // When
        InjectedPrompt prompt = service.injectContext(context, query);

        // Then — formula : contextUsagePercent = (totalTokens / maxTokens) * 100
        int totalTokens = prompt.getStructure().getTotalTokens();
        double expectedPercent = (totalTokens / 200000.0) * 100;
        assertThat(prompt.getContextUsagePercent()).isCloseTo(expectedPercent, within(1e-9));
    }

    // =========================================================================
    // Cas limite — AggregatedContext vide (zéro chunks)
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner un prompt valide sans erreur quand AggregatedContext est vide")
    void doitRetournerPromptValideSansErreurQuandContexteVide() {
        // Given
        AggregatedContext emptyContext = RetrievalTestHelper.buildEmptyAggregatedContext();
        String query = "question sans contexte";

        // When
        InjectedPrompt prompt = service.injectContext(emptyContext, query);

        // Then — prompt non nul, non vide, sources vides
        assertThat(prompt).isNotNull();
        assertThat(prompt.getFullPrompt()).isNotBlank();
        assertThat(prompt.getFullPrompt()).contains(query);
        assertThat(prompt.getSources()).isEmpty();
        assertThat(prompt.getStructure().getTotalTokens()).isGreaterThan(0);
    }

    // =========================================================================
    // Chunk avec métadonnée 'page'
    // =========================================================================

    @Test
    @DisplayName("DOIT inclure la balise <page> dans le prompt quand la métadonnée 'page' est présente")
    void doitInclurePageDansPromptQuandMetadonneePagePresente() {
        // Given — chunk avec page=5 dans les métadonnées
        SelectedChunk chunkAvecPage = SelectedChunk.builder()
            .id("c-page")
            .content("Contenu de la page 5.")
            .metadata(Map.of("source", "rapport.pdf", "type", "text", "page", 5))
            .finalScore(0.05)
            .scoresByRetriever(Map.of())
            .retrieversUsed(new java.util.ArrayList<>(List.of("text")))
            .build();
        AggregatedContext context = RetrievalTestHelper.buildAggregatedContext(List.of(chunkAvecPage));

        // When
        InjectedPrompt prompt = service.injectContext(context, "test page");

        // Then — balise <page> présente dans le prompt
        assertThat(prompt.getFullPrompt()).contains("<page>");
        assertThat(prompt.getSources().get(0).getPage()).isNotNull();
    }

    // =========================================================================
    // Chunk avec métadonnée 'slide' (sans 'page')
    // =========================================================================

    @Test
    @DisplayName("DOIT inclure la balise <slide> dans le prompt quand la métadonnée 'slide' est présente")
    void doitInclureSlideDansPromptQuandMetadonneeSlidePresente() {
        // Given — chunk avec slide=3, sans page (pour tester le fallback slide dans extractSources)
        SelectedChunk chunkAvecSlide = SelectedChunk.builder()
            .id("c-slide")
            .content("Contenu de la diapositive 3.")
            .metadata(Map.of("source", "presentation.pptx", "type", "image", "slide", 3))
            .finalScore(0.04)
            .scoresByRetriever(Map.of())
            .retrieversUsed(new java.util.ArrayList<>(List.of("image")))
            .build();
        AggregatedContext context = RetrievalTestHelper.buildAggregatedContext(List.of(chunkAvecSlide));

        // When
        InjectedPrompt prompt = service.injectContext(context, "test slide");

        // Then — balise <slide> présente dans le prompt, source contient le numéro de diapo
        assertThat(prompt.getFullPrompt()).contains("<slide>");
        assertThat(prompt.getSources().get(0).getPage()).isEqualTo(3);
    }

    // =========================================================================
    // Citations désactivées
    // =========================================================================

    @Test
    @DisplayName("DOIT omettre la directive de citation quand enableCitations=false")
    void doitOmettreDirectiveCitationQuandCitationsDesactivees() {
        // Given — citations désactivées
        config.getContentInjector().setEnableCitations(false);
        SelectedChunk chunk = RetrievalTestHelper.buildSelectedChunk(
            "c1", "Contenu de test.", 0.05, "text"
        );
        AggregatedContext context = RetrievalTestHelper.buildAggregatedContext(List.of(chunk));

        // When
        InjectedPrompt prompt = service.injectContext(context, "query sans citations");

        // Then — format de citation absent des instructions
        assertThat(prompt.getFullPrompt()).doesNotContain("Cite systématiquement avec:");
    }

    // =========================================================================
    // Utilisation > 80 % du budget tokens
    // =========================================================================

    @Test
    @DisplayName("DOIT tolérer une utilisation > 80 % sans lever d'exception")
    void doitTolererUtilisationSuperieure80PourcentSansException() {
        // Given — maxTokens très petit pour forcer usage > 80 %
        config.getContentInjector().setMaxTokens(10);
        SelectedChunk chunk = RetrievalTestHelper.buildSelectedChunk(
            "c1", "Un texte suffisamment long pour dépasser largement 80 % du budget tokens configuré.", 0.05, "text"
        );
        AggregatedContext context = RetrievalTestHelper.buildAggregatedContext(List.of(chunk));

        // When — pas d'exception même si budget dépassé
        InjectedPrompt prompt = service.injectContext(context, "query haute densité");

        // Then — prompt retourné, contextUsagePercent > 80
        assertThat(prompt).isNotNull();
        assertThat(prompt.getContextUsagePercent()).isGreaterThan(80.0);
    }
}
