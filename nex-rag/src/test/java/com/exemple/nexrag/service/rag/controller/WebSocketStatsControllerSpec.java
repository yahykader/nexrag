package com.exemple.nexrag.service.rag.controller;

import com.exemple.nexrag.config.TestWebConfig;
import com.exemple.nexrag.websocket.WebSocketSessionManager;
import com.exemple.nexrag.websocket.WebSocketSessionManager.SessionInfo;
import com.exemple.nexrag.websocket.WebSocketSessionManager.SessionStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Spec : WebSocketStatsController — statistiques et monitoring des sessions WebSocket
 *
 * Principe SRP : valide uniquement le routage HTTP, les champs de réponse JSON
 *                et le cas HTTP 404 pour une session inconnue.
 *                Délègue à WebSocketSessionManager (mocké).
 * Clean code    : {@code buildStats()} et {@code buildSessionInfo()} utilisent
 *                 les constructeurs corrects après le refactoring de SessionStats
 *                 et SessionInfo (suppression de @Data → @Getter uniquement).
 */
@DisplayName("Spec : WebSocketStatsController — statistiques des sessions WebSocket")
@WebMvcTest(WebSocketStatsController.class)
@Import(TestWebConfig.class)
class WebSocketStatsControllerSpec {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WebSocketSessionManager sessionManager;

    // =========================================================================
    // Statistiques globales — US-6 / AC-1
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 200 avec les statistiques globales des sessions")
    void shouldReturn200ForStats() throws Exception {
        when(sessionManager.getStats()).thenReturn(buildStats(3, 10));

        mockMvc.perform(get("/api/v1/websocket/stats"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // Nombre de sessions actives — US-6 / AC-2
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 200 avec le champ activeSessions pour le compte actif")
    void shouldReturn200ForActiveSessionCount() throws Exception {
        when(sessionManager.getActiveSessionCount()).thenReturn(5);

        mockMvc.perform(get("/api/v1/websocket/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSessions").value(5));
    }

    // =========================================================================
    // Liste des sessions — US-6 / AC-3
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 200 avec l'ensemble des identifiants de session actifs")
    void shouldReturn200ForSessionList() throws Exception {
        when(sessionManager.getActiveSessionIds()).thenReturn(Set.of("s1", "s2"));

        mockMvc.perform(get("/api/v1/websocket/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // =========================================================================
    // Info d'une session connue — US-6 / AC-4
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 200 avec les détails quand la session existe")
    void shouldReturn200ForKnownSessionInfo() throws Exception {
        when(sessionManager.getSessionInfo("s1")).thenReturn(buildSessionInfo("s1"));

        mockMvc.perform(get("/api/v1/websocket/session/s1"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // Session inconnue — US-6 / AC-5
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 404 quand la session demandée est inconnue")
    void shouldReturn404ForUnknownSessionInfo() throws Exception {
        when(sessionManager.getSessionInfo("unknown")).thenReturn(null);

        mockMvc.perform(get("/api/v1/websocket/session/unknown"))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Cleanup — US-6 / AC-6
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 200 avec les champs cleaned et remaining après le cleanup")
    void shouldReturn200ForCleanupWithCorrectFields() throws Exception {
        when(sessionManager.getActiveSessionCount())
                .thenReturn(5)  // avant cleanup
                .thenReturn(3); // après cleanup
        doNothing().when(sessionManager).cleanupInactiveSessions(anyLong());

        mockMvc.perform(post("/api/v1/websocket/cleanup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cleaned").value(2))
                .andExpect(jsonPath("$.remaining").value(3));
    }

    // =========================================================================
    // Health — US-6 / AC-7
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner 200 avec status UP, activeSessions et totalSessions")
    void shouldReturn200ForHealth() throws Exception {
        when(sessionManager.getStats()).thenReturn(buildStats(2, 8));

        mockMvc.perform(get("/api/v1/websocket/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.activeSessions").isNumber())
                .andExpect(jsonPath("$.totalSessions").isNumber());
    }

    // =========================================================================
    // Utilitaires de construction de stubs
    // =========================================================================

    /**
     * Construit un {@link SessionStats} via le constructeur complet.
     * Signature : (totalSessions, activeSessions, totalMessages, avgMessages, avgDuration)
     */
    private SessionStats buildStats(int active, int total) {
        return new SessionStats(total, active, 0L, 0.0, 0L);
    }

    /**
     * Construit un {@link SessionInfo} via le constructeur (sessionId, userId).
     */
    private SessionInfo buildSessionInfo(String sessionId) {
        return new SessionInfo(sessionId, "user-test");
    }
}