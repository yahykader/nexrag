package com.exemple.nexrag.service.rag.facade;

import com.exemple.nexrag.service.rag.streaming.EventEmitter;
import com.exemple.nexrag.service.rag.streaming.StreamingOrchestrator;
import com.exemple.nexrag.service.rag.streaming.model.StreamingRequest;
import com.exemple.nexrag.util.SessionIdGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec : StreamingFacadeImpl — gestion du cycle de vie SSE.
 *
 * Principe SRP : chaque méthode de test couvre une opération SSE distincte.
 * Principe DIP : StreamingOrchestrator, EventEmitter et SessionIdGenerator
 *                sont injectés via @Mock.
 */
@DisplayName("Spec : StreamingFacadeImpl — Facade SSE de streaming RAG")
@ExtendWith(MockitoExtension.class)
class StreamingFacadeImplSpec {

    @Mock private StreamingOrchestrator orchestrator;
    @Mock private EventEmitter          eventEmitter;
    @Mock private SessionIdGenerator    sessionIdGenerator;

    @InjectMocks private StreamingFacadeImpl facade;

    // -------------------------------------------------------------------------
    // AC-17.1 — Requête valide → SseEmitter non nul et session enregistrée
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner un SseEmitter non nul et enregistrer la session SSE pour une requête valide")
    void shouldReturnNonNullSseEmitterAndRegisterSessionForValidRequest() {
        when(sessionIdGenerator.generate()).thenReturn("session-abc");
        var request = StreamingRequest.builder().query("Quel est le contenu du document ?").build();

        SseEmitter emitter = facade.startStream(request);

        assertThat(emitter).isNotNull();
        verify(sessionIdGenerator).generate();
        verify(eventEmitter).registerSSE(eq("session-abc"), any(SseEmitter.class));
    }

    // -------------------------------------------------------------------------
    // AC-17.2 — Annulation → event erreur émis et session complétée
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT émettre un event d'erreur et compléter la session lors de l'annulation d'un stream")
    void shouldEmitErrorEventAndCompleteSessionOnStreamCancel() {
        facade.cancelStream("session-abc");

        verify(eventEmitter).emitError(eq("session-abc"), anyString(), eq("CANCELLED"));
        verify(eventEmitter).complete("session-abc");
    }

    // -------------------------------------------------------------------------
    // AC-new — Deux requêtes consécutives → identifiants de session distincts
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT générer des identifiants de session distincts pour deux requêtes SSE consécutives")
    void shouldGenerateDistinctSessionIdsForConsecutiveStreamRequests() {
        when(sessionIdGenerator.generate())
            .thenReturn("session-001")
            .thenReturn("session-002");
        var request = StreamingRequest.builder().query("Question RAG").build();

        facade.startStream(request);
        facade.startStream(request);

        verify(eventEmitter).registerSSE(eq("session-001"), any(SseEmitter.class));
        verify(eventEmitter).registerSSE(eq("session-002"), any(SseEmitter.class));
    }
}
