package com.exemple.nexrag.service.rag.facade;

import com.exemple.nexrag.dto.DuplicateSummary;
import com.exemple.nexrag.service.rag.ingestion.deduplication.file.DeduplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Spec : DuplicateChecker — détection de doublons avant ingestion.
 *
 * Principe SRP : unique responsabilité → analyser une liste de fichiers
 *                et retourner un résumé des doublons.
 * Principe DIP : DeduplicationService injecté via @Mock.
 *
 * ⚠ Couverture requise : 100 % branches (chemin safety-critical — Constitution Principe IV).
 */
@DisplayName("Spec : DuplicateChecker — détection de doublons avant ingestion")
@ExtendWith(MockitoExtension.class)
class DuplicateCheckerSpec {

    @Mock private DeduplicationService deduplicationService;

    @InjectMocks private DuplicateChecker checker;

    // -------------------------------------------------------------------------
    // AC-dup-known — Fichier déjà ingéré → détecté comme doublon dans le résumé
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT détecter un doublon et retourner DuplicateSummary avec count=1 et le batchId existant")
    void shouldDetectDuplicateFileAndReturnSummaryWithExistingBatchId() throws Exception {
        var file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", "contenu".getBytes());
        when(deduplicationService.computeHash(any(byte[].class))).thenReturn("hash-abc");
        when(deduplicationService.isDuplicateByHash("hash-abc")).thenReturn(true);
        when(deduplicationService.getExistingBatchId("hash-abc")).thenReturn("batch-existant");

        DuplicateSummary summary = checker.check(List.of(file));

        assertThat(summary.getCount()).isEqualTo(1);
        assertThat(summary.getFilenames()).containsExactly("rapport.pdf");
        assertThat(summary.getExistingBatchIds()).containsEntry("rapport.pdf", "batch-existant");
        assertThat(summary.hasNone()).isFalse();
    }

    // -------------------------------------------------------------------------
    // AC-dup-unknown — Fichier non ingéré → résumé vide (aucun doublon)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner un DuplicateSummary vide quand le fichier n'est pas un doublon")
    void shouldReturnEmptySummaryWhenFileIsNotADuplicate() throws Exception {
        var file = new MockMultipartFile("file", "nouveau.pdf", "application/pdf", "nouveau".getBytes());
        when(deduplicationService.computeHash(any(byte[].class))).thenReturn("hash-xyz");
        when(deduplicationService.isDuplicateByHash("hash-xyz")).thenReturn(false);

        DuplicateSummary summary = checker.check(List.of(file));

        assertThat(summary.getCount()).isEqualTo(0);
        assertThat(summary.hasNone()).isTrue();
        assertThat(summary.getFilenames()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // AC-dup-store-unreachable — Erreur de store → traitement non bloquant,
    //   fichier ignoré silencieusement (comportement non-bloquant par conception)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT continuer sans lever d'exception et ignorer le fichier quand la vérification de doublon échoue")
    void shouldContinueProcessingWithoutExceptionWhenDeduplicationStoreThrows() throws Exception {
        var file = new MockMultipartFile("file", "erreur.pdf", "application/pdf", "data".getBytes());
        when(deduplicationService.computeHash(any(byte[].class)))
            .thenThrow(new RuntimeException("Store de déduplication inaccessible"));

        assertThatCode(() -> {
            DuplicateSummary summary = checker.check(List.of(file));
            assertThat(summary.getCount()).isEqualTo(0);
            assertThat(summary.hasNone()).isTrue();
        }).doesNotThrowAnyException();
    }
}
