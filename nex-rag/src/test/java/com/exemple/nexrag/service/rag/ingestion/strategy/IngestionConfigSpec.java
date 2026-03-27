package com.exemple.nexrag.service.rag.ingestion.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Spec : IngestionConfig — Tri et sélection des stratégies d'ingestion.
 */
@DisplayName("Spec : IngestionConfig — Tri des stratégies par priorité croissante (OCP)")
class IngestionConfigSpec {

    private final IngestionConfig config = new IngestionConfig();

    private IngestionStrategy mockStrategy(String name, int priority, boolean handles) {
        IngestionStrategy s = mock(IngestionStrategy.class);
        when(s.getName()).thenReturn(name);
        when(s.getPriority()).thenReturn(priority);
        return s;
    }

    // -------------------------------------------------------------------------
    // Tri par priorité
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT trier les stratégies par priorité croissante")
    void shouldSortStrategiesByAscendingPriority() {
        IngestionStrategy tika = mockStrategy("TIKA", 10, true);
        IngestionStrategy text = mockStrategy("TEXT", 8,  true);
        IngestionStrategy pdf  = mockStrategy("PDF",  1,  true);
        IngestionStrategy docx = mockStrategy("DOCX", 2,  true);

        List<IngestionStrategy> sorted = config.ingestionStrategies(List.of(tika, text, pdf, docx));

        assertThat(sorted).extracting(IngestionStrategy::getPriority)
            .containsExactly(1, 2, 8, 10);
    }

    @Test
    @DisplayName("DOIT placer la stratégie PDF (priorité 1) avant TIKA (priorité 10)")
    void shouldPlacePdfBeforeTika() {
        IngestionStrategy tika = mockStrategy("TIKA", 10, true);
        IngestionStrategy pdf  = mockStrategy("PDF",  1,  true);

        List<IngestionStrategy> sorted = config.ingestionStrategies(List.of(tika, pdf));

        assertThat(sorted.get(0).getName()).isEqualTo("PDF");
        assertThat(sorted.get(1).getName()).isEqualTo("TIKA");
    }

    @Test
    @DisplayName("DOIT conserver toutes les stratégies après le tri")
    void shouldPreserveAllStrategiesAfterSorting() {
        List<IngestionStrategy> strategies = List.of(
            mockStrategy("PDF",   1,  true),
            mockStrategy("DOCX",  2,  true),
            mockStrategy("XLSX",  3,  true),
            mockStrategy("IMAGE", 4,  true),
            mockStrategy("TEXT",  8,  true),
            mockStrategy("TIKA",  10, true)
        );

        List<IngestionStrategy> sorted = config.ingestionStrategies(strategies);

        assertThat(sorted).hasSize(6);
    }

    @Test
    @DisplayName("DOIT retourner une liste vide si aucune stratégie n'est enregistrée")
    void shouldReturnEmptyListWhenNoStrategiesRegistered() {
        List<IngestionStrategy> sorted = config.ingestionStrategies(List.of());
        assertThat(sorted).isEmpty();
    }

    // -------------------------------------------------------------------------
    // OCP — ajout d'une nouvelle stratégie sans modifier IngestionConfig
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT intégrer une nouvelle stratégie sans modifier IngestionConfig (OCP)")
    void shouldIntegrateNewStrategyWithoutModifyingConfig() {
        IngestionStrategy pdf      = mockStrategy("PDF",  1,  true);
        IngestionStrategy tika     = mockStrategy("TIKA", 10, true);
        IngestionStrategy nouvelle = mockStrategy("CUSTOM", 5, true);

        List<IngestionStrategy> sorted = config.ingestionStrategies(List.of(tika, pdf, nouvelle));

        assertThat(sorted).extracting(IngestionStrategy::getName)
            .containsExactly("PDF", "CUSTOM", "TIKA");
    }

    @Test
    @DisplayName("DOIT être le premier candidat pour 'pdf' grâce au tri (stratégie PDF priorité 1)")
    void shouldSelectPdfStrategyFirstForPdfFile() {
        IngestionStrategy pdf  = mockStrategy("PDF",  1, true);
        IngestionStrategy tika = mockStrategy("TIKA", 10, true);

        when(pdf.canHandle(null, "pdf")).thenReturn(true);
        when(tika.canHandle(null, "pdf")).thenReturn(true);

        List<IngestionStrategy> sorted = config.ingestionStrategies(List.of(tika, pdf));

        // La première stratégie qui canHandle("pdf") doit être PDF
        IngestionStrategy selected = sorted.stream()
            .filter(s -> s.canHandle(null, "pdf"))
            .findFirst()
            .orElseThrow();

        assertThat(selected.getName()).isEqualTo("PDF");
    }
}
