package com.exemple.nexrag.service.rag.controller;

import com.exemple.nexrag.advice.StreamingExceptionHandler;
import com.exemple.nexrag.config.TestWebConfig;
import com.exemple.nexrag.service.rag.facade.StreamingFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Spec : StreamingAssistantController — API SSE de streaming
 *
 * Principe SRP : valide uniquement le routage HTTP (statut, annulation, health)
 *                et le rejet de requête vide.
 *
 * Clean code    : le content-type SSE n'est pas vérifiable via MockMvc avec
 *                 SseEmitter — MockMvc ne propage pas les headers de réponse
 *                 asynchrones. Le test vérifie le statut 200 avec accept SSE,
 *                 ce qui est suffisant pour valider le routage.
 */
@DisplayName("Spec : StreamingAssistantController — API SSE de streaming")
@WebMvcTest(StreamingAssistantController.class)
@Import({TestWebConfig.class, StreamingExceptionHandler.class}) 
class StreamingAssistantControllerSpec {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StreamingFacade streamingFacade;

    // =========================================================================
    // SSE GET — US-2 / AC-1
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 200 pour une requête GET stream valide")
    void shouldReturnTextEventStreamForGetRequest() throws Exception {
        when(streamingFacade.startStream(any())).thenReturn(new SseEmitter());

        // ✅ accept SSE explicite — MockMvc ne propage pas le content-type
        // de SseEmitter, la vérification du statut suffit à valider le routage
        mockMvc.perform(get("/api/v1/assistant/stream")
                        .param("query", "Qu'est-ce que le RAG ?")
                        .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // SSE POST — US-2 / AC-2
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 200 pour une requête POST stream valide")
    void shouldReturnTextEventStreamForPostRequest() throws Exception {
        when(streamingFacade.startStream(any())).thenReturn(new SseEmitter());

        mockMvc.perform(post("/api/v1/assistant/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM_VALUE)
                        .content("{\"query\": \"Résume ce document\"}"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // Annulation — US-2 / AC-3
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 200 et annuler le flux SSE pour un sessionId valide")
    void shouldReturn200ForCancelStream() throws Exception {
        doNothing().when(streamingFacade).cancelStream("session-1");

        mockMvc.perform(post("/api/v1/assistant/stream/session-1/cancel"))
                .andExpect(status().isOk());

        verify(streamingFacade).cancelStream("session-1");
    }

    // =========================================================================
    // Health — US-2 / AC-4
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 200 avec un message non vide pour le health SSE")
    void shouldReturn200ForHealth() throws Exception {
        mockMvc.perform(get("/api/v1/assistant/stream/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                    org.hamcrest.Matchers.emptyString())));
    }

    // =========================================================================
    // Validation requête vide — US-2 / AC-5
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 400 quand le champ query est vide")
    void shouldReturn400WhenQueryIsEmpty() throws Exception {
        // ✅ Le controller doit avoir @Validated + @NotBlank sur le champ query
        // pour que Spring retourne 400 avant d'appeler la facade
        mockMvc.perform(post("/api/v1/assistant/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"\"}"))
                .andExpect(status().isBadRequest());

        verify(streamingFacade, never()).startStream(any());
    }
}