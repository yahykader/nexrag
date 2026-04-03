package com.exemple.nexrag.service.rag.controller;

import com.exemple.nexrag.service.rag.controller.MultimodalCrudController;
import com.exemple.nexrag.config.TestWebConfig;
import com.exemple.nexrag.advice.CrudExceptionHandler;
import com.exemple.nexrag.dto.*;
import com.exemple.nexrag.exception.ResourceNotFoundException;
import com.exemple.nexrag.service.rag.facade.CrudFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Spec : MultimodalCrudController — API CRUD des embeddings
 *
 * Principe SRP : valide uniquement le routage HTTP, le mapping HTTP 404/400
 * via CrudExceptionHandler, et la délégation à CrudFacade (mocké).
 * Note : ce controller est dans le package com.exemple.nexrag.controller
 * (différent de service.rag.controller).
 */
@DisplayName("Spec : MultimodalCrudController — API CRUD des embeddings")
@WebMvcTest(MultimodalCrudController.class)
@Import({ CrudExceptionHandler.class, TestWebConfig.class })
class MultimodalCrudControllerSpec {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CrudFacade crudFacade;

    // =========================================================================
    // Suppression individuelle — US-3 / AC-1
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 200 pour la suppression d'un embedding par ID")
    void shouldReturn200ForDeleteById() throws Exception {
        when(crudFacade.deleteById(eq("emb-1"), eq(EmbeddingType.TEXT)))
                .thenReturn(DeleteResponse.builder().success(true).embeddingId("emb-1").build());

        mockMvc.perform(delete("/api/v1/crud/file/emb-1")
                        .param("type", "text"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // =========================================================================
    // Suppression batch — US-3 / AC-2, 3, 4
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 200 pour la suppression de tous les fichiers d'un batch")
    void shouldReturn200ForDeleteBatchById() throws Exception {
        when(crudFacade.deleteBatchById("batch-1"))
                .thenReturn(DeleteResponse.builder().success(true).batchId("batch-1").build());

        mockMvc.perform(delete("/api/v1/crud/batch/batch-1/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DOIT retourner 200 pour la suppression batch de fichiers texte")
    void shouldReturn200ForDeleteTextBatch() throws Exception {
        when(crudFacade.deleteBatch(anyList(), eq(EmbeddingType.TEXT)))
                .thenReturn(DeleteResponse.builder().success(true).build());

        mockMvc.perform(delete("/api/v1/crud/files/text/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"emb-1\",\"emb-2\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DOIT retourner 200 pour la suppression batch de fichiers image")
    void shouldReturn200ForDeleteImageBatch() throws Exception {
        when(crudFacade.deleteBatch(anyList(), eq(EmbeddingType.IMAGE)))
                .thenReturn(DeleteResponse.builder().success(true).build());

        mockMvc.perform(delete("/api/v1/crud/files/image/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"img-1\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // =========================================================================
    // Suppression globale — US-3 / AC-4
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 200 pour la suppression globale avec confirmation correcte")
    void shouldReturn200ForDeleteAll() throws Exception {
        when(crudFacade.deleteAll("DELETE_ALL_FILES"))
                .thenReturn(DeleteResponse.builder().success(true).build());

        mockMvc.perform(delete("/api/v1/crud/files/all")
                        .param("confirmation", "DELETE_ALL_FILES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // =========================================================================
    // Lecture — US-3 / AC-5, 6, 7
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 200 pour la vérification de doublon")
    void shouldReturn200ForCheckDuplicate() throws Exception {
        when(crudFacade.checkDuplicate(any()))
                .thenReturn(DuplicateCheckResponse.builder().duplicate(false).build());

        mockMvc.perform(multipart("/api/v1/crud/check-duplicate")
                        .file(new MockMultipartFile("file", "doc.pdf", "application/pdf", "data".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(false));
    }

    @Test
    @DisplayName("DOIT retourner 200 avec les informations du batch")
    void shouldReturn200ForGetBatchInfo() throws Exception {
        when(crudFacade.getBatchInfo("batch-1"))
                .thenReturn(BatchInfoResponse.builder().batchId("batch-1").build());

        mockMvc.perform(get("/api/v1/crud/batch/batch-1/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value("batch-1"));
    }

    @Test
    @DisplayName("DOIT retourner 200 avec les statistiques système")
    void shouldReturn200ForGetSystemStats() throws Exception {
        when(crudFacade.getSystemStats())
                .thenReturn(SystemStatsResponse.builder().build());

        mockMvc.perform(get("/api/v1/crud/stats/system"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // @ControllerAdvice — exception mapping (US-3 / edge cases)
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 404 quand CrudFacade lève une ResourceNotFoundException")
    void shouldReturn404WhenResourceNotFoundExceptionThrown() throws Exception {
        when(crudFacade.deleteById(eq("unknown"), eq(EmbeddingType.TEXT)))
                .thenThrow(new ResourceNotFoundException("Embedding introuvable"));

        mockMvc.perform(delete("/api/v1/crud/file/unknown")
                        .param("type", "text"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DOIT retourner 400 quand le paramètre confirmation est absent")
    void shouldReturn400WhenConfirmationParamMissing() throws Exception {
        mockMvc.perform(delete("/api/v1/crud/files/all"))
                .andExpect(status().isBadRequest());
    }
}
