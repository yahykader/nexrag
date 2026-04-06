package com.exemple.nexrag.service.rag.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Spec unitaire de {@link RateLimitInterceptor}.
 *
 * SRP : valide uniquement le comportement de l'intercepteur HTTP —
 *       la logique de quota est déléguée à {@link RateLimitService} (mocké).
 * DIP : toutes les dépendances injectées via constructeur Mockito @InjectMocks.
 */
@DisplayName("Spec : RateLimitInterceptor — Limitation de débit par endpoint")
@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorSpec {

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RateLimitInterceptor interceptor;

    // Aucun stub global — objectMapper n'est utilisé que pour les réponses 429

    // -------------------------------------------------------------------------
    // AC-21.3 — Court-circuit OPTIONS
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT court-circuiter les requêtes OPTIONS sans appeler RateLimitService")
    void devraitCourtCircuiterOptionsEtIgnorerRateLimitService() throws Exception {
        // Given
        MockHttpServletRequest  req = new MockHttpServletRequest("OPTIONS", "/api/upload");
        MockHttpServletResponse res = new MockHttpServletResponse();

        // When
        boolean proceed = interceptor.preHandle(req, res, new Object());

        // Then
        assertThat(proceed).isTrue();
        verifyNoInteractions(rateLimitService);
    }

    // -------------------------------------------------------------------------
    // AC-21.2 — Requête autorisée + header remaining
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT autoriser la requête et inclure le header X-RateLimit-Remaining quand la limite n'est pas atteinte")
    void devraitAutoriserAvecHeaderRemainingQuandLimiteNonAtteinte() throws Exception {
        // Given
        when(rateLimitService.checkDefaultLimit("user-1"))
            .thenReturn(RateLimitResult.allowed(29L));

        MockHttpServletRequest  req = new MockHttpServletRequest("GET", "/api/documents");
        req.addHeader("X-User-Id", "user-1");
        MockHttpServletResponse res = new MockHttpServletResponse();

        // When
        boolean proceed = interceptor.preHandle(req, res, new Object());

        // Then
        assertThat(proceed).isTrue();
        assertThat(res.getHeader("X-RateLimit-Remaining")).isEqualTo("29");
    }

    // -------------------------------------------------------------------------
    // AC-21.1 + AC-21.7 — 429 avec corps JSON et headers Retry-After
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner 429 avec Retry-After et corps JSON quand la limite upload est atteinte")
    void devraitRetourner429AvecCorpsJsonQuandLimiteUploadAtteinte() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(any()))
            .thenReturn("{\"error\":\"Too Many Requests\"}");
        when(rateLimitService.checkUploadLimit("user-42"))
            .thenReturn(RateLimitResult.blocked(30L));

        MockHttpServletRequest  req = new MockHttpServletRequest("POST", "/api/upload");
        req.addHeader("X-User-Id", "user-42");
        MockHttpServletResponse res = new MockHttpServletResponse();

        // When
        boolean proceed = interceptor.preHandle(req, res, new Object());

        // Then
        assertThat(proceed).isFalse();
        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isEqualTo("30");
        assertThat(res.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(res.getHeader("X-RateLimit-Reset")).isNotNull();
    }

    // -------------------------------------------------------------------------
    // AC-21.4 — Résolution d'identité : X-Forwarded-For en fallback
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT utiliser le premier IP de X-Forwarded-For quand X-User-Id est absent")
    void devraitUtiliserPremierIpXForwardedForQuandPasDeUserId() throws Exception {
        // Given
        when(rateLimitService.checkDefaultLimit("203.0.113.5"))
            .thenReturn(RateLimitResult.allowed(30L));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/documents");
        req.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
        MockHttpServletResponse res = new MockHttpServletResponse();

        // When
        interceptor.preHandle(req, res, new Object());

        // Then
        verify(rateLimitService).checkDefaultLimit("203.0.113.5");
    }

    // -------------------------------------------------------------------------
    // AC-21.4 — Résolution d'identité : X-User-Id prioritaire sur IP
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT préférer le header X-User-Id avant l'IP quand les deux sont présents")
    void devraitPreferHeaderXUserIdAvantIp() throws Exception {
        // Given
        when(rateLimitService.checkDefaultLimit("user-99"))
            .thenReturn(RateLimitResult.allowed(30L));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/documents");
        req.addHeader("X-User-Id", "user-99");
        req.addHeader("X-Forwarded-For", "203.0.113.5");
        MockHttpServletResponse res = new MockHttpServletResponse();

        // When
        interceptor.preHandle(req, res, new Object());

        // Then
        verify(rateLimitService).checkDefaultLimit("user-99");
        verify(rateLimitService, never()).checkDefaultLimit("203.0.113.5");
    }

    // -------------------------------------------------------------------------
    // AC-21.5 — Routage : /search → checkSearchLimit
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT router l'URI /search vers checkSearchLimit")
    void devraitRouterSearchVersCheckSearchLimit() throws Exception {
        // Given
        when(rateLimitService.checkSearchLimit(anyString()))
            .thenReturn(RateLimitResult.allowed(50L));

        MockHttpServletRequest  req = new MockHttpServletRequest("POST", "/api/search");
        req.addHeader("X-User-Id", "user-7");
        MockHttpServletResponse res = new MockHttpServletResponse();

        // When
        interceptor.preHandle(req, res, new Object());

        // Then
        verify(rateLimitService).checkSearchLimit("user-7");
        verify(rateLimitService, never()).checkDefaultLimit(anyString());
    }

    // -------------------------------------------------------------------------
    // AC-21.5 — Routage : DELETE /file/{id} → checkDeleteLimit
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT router DELETE /file/{id} vers checkDeleteLimit")
    void devraitRouterDeleteFileVersCheckDeleteLimit() throws Exception {
        // Given
        when(rateLimitService.checkDeleteLimit(anyString()))
            .thenReturn(RateLimitResult.allowed(20L));

        MockHttpServletRequest  req = new MockHttpServletRequest("DELETE", "/api/file/abc-123");
        req.addHeader("X-User-Id", "user-3");
        MockHttpServletResponse res = new MockHttpServletResponse();

        // When
        interceptor.preHandle(req, res, new Object());

        // Then
        verify(rateLimitService).checkDeleteLimit("user-3");
        verify(rateLimitService, never()).checkDefaultLimit(anyString());
    }

    // -------------------------------------------------------------------------
    // AC-21.5 — Routage : /upload/batch → checkBatchLimit
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT router l'URI /upload/batch vers checkBatchLimit")
    void devraitRouterBatchVersCheckBatchLimit() throws Exception {
        // Given
        when(rateLimitService.checkBatchLimit(anyString()))
            .thenReturn(RateLimitResult.allowed(5L));

        MockHttpServletRequest  req = new MockHttpServletRequest("POST", "/api/upload/batch");
        req.addHeader("X-User-Id", "user-5");
        MockHttpServletResponse res = new MockHttpServletResponse();

        // When
        interceptor.preHandle(req, res, new Object());

        // Then
        verify(rateLimitService).checkBatchLimit("user-5");
        verify(rateLimitService, never()).checkDefaultLimit(anyString());
        verify(rateLimitService, never()).checkUploadLimit(anyString());
    }
}
