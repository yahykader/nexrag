package com.exemple.nexrag.service.rag.facade;

import com.exemple.nexrag.dto.AsyncResponse;
import com.exemple.nexrag.dto.DuplicateSummary;
import com.exemple.nexrag.dto.IngestionResponse;
import com.exemple.nexrag.dto.IngestionResult;
import com.exemple.nexrag.exception.ResourceNotFoundException;
import com.exemple.nexrag.exception.VirusDetectedException;
import com.exemple.nexrag.service.rag.ingestion.IngestionOrchestrator;
import com.exemple.nexrag.service.rag.ingestion.deduplication.file.DeduplicationService;
import com.exemple.nexrag.service.rag.ingestion.progress.ProgressService;
import com.exemple.nexrag.service.rag.ingestion.tracker.IngestionTracker;
import com.exemple.nexrag.validation.FileValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec : IngestionFacadeImpl — orchestration d'ingestion multimodale.
 *
 * Principe SRP : chaque méthode de test couvre une responsabilité distincte.
 * Principe DIP : toutes les dépendances sont injectées via @Mock / @InjectMocks.
 */
@DisplayName("Spec : IngestionFacadeImpl — Facade d'ingestion multimodale")
@ExtendWith(MockitoExtension.class)
class IngestionFacadeImplSpec {

    @Mock private IngestionOrchestrator ingestionService;
    @Mock private IngestionTracker      tracker;
    @Mock private DeduplicationService  deduplicationService;
    @Mock private ProgressService       progressService;
    @Mock private FileValidator         fileValidator;
    @Mock private DuplicateChecker      duplicateChecker;

    @InjectMocks private IngestionFacadeImpl facade;

    // -------------------------------------------------------------------------
    // AC-15.1 — Fichier valide → réponse success
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner IngestionResponse success pour un fichier PDF valide non dupliqué")
    void shouldReturnSuccessResponseForValidFile() throws Exception {
        var file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "content".getBytes());
        when(ingestionService.ingestFile(any(), any()))
            .thenReturn(new IngestionResult(5, 2));

        IngestionResponse response = facade.uploadSync(file, "batch-001");

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getBatchId()).isEqualTo("batch-001");
        assertThat(response.getTextEmbeddings()).isEqualTo(5);
        assertThat(response.getImageEmbeddings()).isEqualTo(2);
        assertThat(response.getDuplicate()).isFalse();
        verify(fileValidator).validate(file);
        verify(ingestionService).ingestFile(eq(file), eq("batch-001"));
    }

    // -------------------------------------------------------------------------
    // AC-15.2 — Doublon pré-détecté → réponse duplicate, orchestrateur non appelé
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner AsyncResponse duplicate et ne pas lancer l'ingestion quand doublon pré-détecté")
    void shouldReturnDuplicateAsyncResponseAndSkipIngestionWhenDuplicateDetected() {
        var file = new MockMultipartFile("file", "dup.pdf", "application/pdf", "data".getBytes());
        var summary = DuplicateSummary.builder()
            .count(1)
            .filenames(List.of("dup.pdf"))
            .existingBatchIds(Map.of("dup.pdf", "batch-existing"))
            .build();
        when(duplicateChecker.check(anyList())).thenReturn(summary);

        AsyncResponse response = facade.uploadAsync(file, null);

        assertThat(response.getDuplicate()).isTrue();
        assertThat(response.getAccepted()).isFalse();
        assertThat(response.getExistingBatchId()).isEqualTo("batch-existing");
        verify(ingestionService, never()).ingestFileAsync(any(), any());
    }

    // -------------------------------------------------------------------------
    // AC-15.3 — Virus détecté → IllegalStateException encapsulant VirusDetectedException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT encapsuler la VirusDetectedException dans une IllegalStateException lors d'une ingestion sync")
    void shouldWrapVirusExceptionInIllegalStateExceptionOnSyncUpload() throws Exception {
        var file = new MockMultipartFile("file", "virus.pdf", "application/pdf", "evil".getBytes());
        when(ingestionService.ingestFile(any(), any()))
            .thenThrow(new VirusDetectedException("EICAR-Test-Signature"));

        assertThatThrownBy(() -> facade.uploadSync(file, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("EICAR-Test-Signature")
            .hasCauseInstanceOf(VirusDetectedException.class);

        verify(fileValidator).validate(file);
    }

    // -------------------------------------------------------------------------
    // AC-new — Suivi : batchId inconnu → ResourceNotFoundException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT lever ResourceNotFoundException quand le batchId est inconnu lors du suivi de statut")
    void shouldThrowResourceNotFoundExceptionForUnknownBatchId() {
        when(tracker.getTextEmbeddingIds("batch-inconnu")).thenReturn(List.of());
        when(tracker.getImageEmbeddingIds("batch-inconnu")).thenReturn(List.of());

        assertThatThrownBy(() -> facade.getStatus("batch-inconnu"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("batch-inconnu");
    }
}
