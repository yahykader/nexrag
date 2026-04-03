package com.exemple.nexrag.service.rag.controller;

import com.exemple.nexrag.config.TestWebConfig;
import com.exemple.nexrag.advice.IngestionExceptionHandler;
import com.exemple.nexrag.dto.*;
import com.exemple.nexrag.exception.DuplicateFileException;
import com.exemple.nexrag.exception.ResourceNotFoundException;
import com.exemple.nexrag.service.rag.facade.IngestionFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Spec : MultimodalIngestionController — API d'ingestion REST
 *
 * Principe SRP : valide uniquement le routage HTTP et le mapping des exceptions.
 * Aucune logique métier testée ici — tout est délégué à IngestionFacade (mocké).
 */
@DisplayName("Spec : MultimodalIngestionController — API d'ingestion REST")
@WebMvcTest(MultimodalIngestionController.class)
@Import({ IngestionExceptionHandler.class, TestWebConfig.class })
class MultimodalIngestionControllerSpec {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IngestionFacade ingestionFacade;

    // =========================================================================
    // Upload synchrone — US-1 / AC-1
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 200 pour un upload synchrone valide")
    void shouldReturn200ForSyncUpload() throws Exception {
        when(ingestionFacade.uploadSync(any(), any()))
                .thenReturn(IngestionResponse.builder().success(true).batchId("batch-1").build());

        mockMvc.perform(multipart("/api/v1/ingestion/upload")
                        .file(new MockMultipartFile("file", "doc.pdf", "application/pdf", "data".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // =========================================================================
    // Upload asynchrone — US-1 / AC-2 & 3
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 202 pour un upload asynchrone sans doublon")
    void shouldReturn202ForAsyncUpload() throws Exception {
        when(ingestionFacade.uploadAsync(any(), any()))
                .thenReturn(AsyncResponse.builder().accepted(true).duplicate(false).batchId("batch-2").build());

        mockMvc.perform(multipart("/api/v1/ingestion/upload/async")
                        .file(new MockMultipartFile("file", "doc.pdf", "application/pdf", "data".getBytes())))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("DOIT retourner 409 pour un upload asynchrone avec doublon détecté")
    void shouldReturn409ForAsyncDuplicate() throws Exception {
        when(ingestionFacade.uploadAsync(any(), any()))
                .thenReturn(AsyncResponse.builder().accepted(false).duplicate(true).existingBatchId("batch-0").build());

        mockMvc.perform(multipart("/api/v1/ingestion/upload/async")
                        .file(new MockMultipartFile("file", "dup.pdf", "application/pdf", "data".getBytes())))
                .andExpect(status().isConflict());
    }

    // =========================================================================
    // Upload batch — US-1 / AC-4
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 202 pour un upload batch valide")
    void shouldReturn202ForBatchUpload() throws Exception {
        when(ingestionFacade.uploadBatch(anyList(), any()))
                .thenReturn(BatchResponse.builder().build());

        mockMvc.perform(multipart("/api/v1/ingestion/upload/batch")
                        .file(new MockMultipartFile("files", "a.pdf", "application/pdf", "data".getBytes())))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("DOIT retourner 202 pour un upload batch avec détails par fichier")
    void shouldReturn202ForBatchDetailedUpload() throws Exception {
        when(ingestionFacade.uploadBatchDetailed(anyList(), any()))
                .thenReturn(BatchDetailedResponse.builder().build());

        mockMvc.perform(multipart("/api/v1/ingestion/upload/batch/detailed")
                        .file(new MockMultipartFile("files", "b.pdf", "application/pdf", "data".getBytes())))
                .andExpect(status().isAccepted());
    }

    // =========================================================================
    // Suivi — US-1 / AC-5 & 6
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 200 avec le statut pour un batchId valide")
    void shouldReturn200ForStatus() throws Exception {
        when(ingestionFacade.getStatus("batch-123"))
                .thenReturn(StatusResponse.builder().batchId("batch-123").build());

        mockMvc.perform(get("/api/v1/ingestion/status/batch-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value("batch-123"));
    }

    @Test
    @DisplayName("DOIT retourner 200 avec le résultat du rollback")
    void shouldReturn200ForRollback() throws Exception {
        when(ingestionFacade.rollback("batch-123"))
                .thenReturn(RollbackResponse.builder().batchId("batch-123").build());

        mockMvc.perform(delete("/api/v1/ingestion/rollback/batch-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value("batch-123"));
    }

    // =========================================================================
    // Monitoring — US-1 / AC-10-12
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 200 avec la liste des ingestions actives")
    void shouldReturn200ForActiveIngestions() throws Exception {
        when(ingestionFacade.getActiveIngestions())
                .thenReturn(ActiveIngestionsResponse.builder().build());

        mockMvc.perform(get("/api/v1/ingestion/active"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DOIT retourner 200 avec les statistiques globales")
    void shouldReturn200ForStats() throws Exception {
        when(ingestionFacade.getStats())
                .thenReturn(StatsResponse.builder().build());

        mockMvc.perform(get("/api/v1/ingestion/stats"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DOIT retourner 200 avec la liste des stratégies disponibles")
    void shouldReturn200ForStrategies() throws Exception {
        when(ingestionFacade.getStrategies())
                .thenReturn(StrategiesResponse.builder().build());

        mockMvc.perform(get("/api/v1/ingestion/strategies"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // Health — US-1 / AC-7, 8, 9
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 200 quand le health détaillé rapporte un état sain")
    void shouldReturn200ForDetailedHealthWhenHealthy() throws Exception {
        when(ingestionFacade.getDetailedHealth())
                .thenReturn(DetailedHealthResponse.builder().status("HEALTHY").build());

        mockMvc.perform(get("/api/v1/ingestion/health/detailed"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DOIT retourner 503 quand le health détaillé rapporte un état dégradé")
    void shouldReturn503ForDetailedHealthWhenUnhealthy() throws Exception {
        when(ingestionFacade.getDetailedHealth())
                .thenReturn(DetailedHealthResponse.builder().status("DEGRADED").build());

        mockMvc.perform(get("/api/v1/ingestion/health/detailed"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("DOIT retourner 200 avec les champs statiques pour le health basique")
    void shouldReturn200ForBasicHealth() throws Exception {
        mockMvc.perform(get("/api/v1/ingestion/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("ingestion"))
                .andExpect(jsonPath("$.streaming").value(true))
                .andExpect(jsonPath("$.duplicateDetection").value(true))
                .andExpect(jsonPath("$.websocketProgress").value(true));
    }

    // =========================================================================
    // @ControllerAdvice — exception mapping (US-1 / AC-14-16)
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 409 quand IngestionFacade lève une DuplicateFileException")
    void shouldReturn409WhenDuplicateFileExceptionThrown() throws Exception {
        when(ingestionFacade.uploadSync(any(), any()))
                .thenThrow(new DuplicateFileException("Doublon détecté", "batch-0"));

        mockMvc.perform(multipart("/api/v1/ingestion/upload")
                        .file(new MockMultipartFile("file", "dup.pdf", "application/pdf", "data".getBytes())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.duplicate").value(true));
    }

    @Test
    @DisplayName("DOIT retourner 404 quand IngestionFacade lève une ResourceNotFoundException")
    void shouldReturn404WhenResourceNotFoundExceptionThrown() throws Exception {
        when(ingestionFacade.getStatus("unknown"))
                .thenThrow(new ResourceNotFoundException("Batch introuvable"));

        mockMvc.perform(get("/api/v1/ingestion/status/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DOIT retourner 400 quand le paramètre file est absent")
    void shouldReturn400WhenFileMissing() throws Exception {
        mockMvc.perform(multipart("/api/v1/ingestion/upload"))
                .andExpect(status().isBadRequest());
    }
}
