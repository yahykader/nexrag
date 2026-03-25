package com.exemple.nexrag.service.rag.facade;

import com.exemple.nexrag.constant.SseConstants;
import com.exemple.nexrag.util.SessionIdGenerator;
import com.exemple.nexrag.service.rag.streaming.EventEmitter;
import com.exemple.nexrag.service.rag.streaming.StreamingOrchestrator;
import com.exemple.nexrag.service.rag.streaming.model.StreamingRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;

/**
 * Implémentation de la facade SSE.
 *
 * Principe SRP : unique responsabilité → orchestrer le cycle de vie d'un flux SSE.
 * Principe DIP : dépend des abstractions {@link StreamingOrchestrator}
 *                et {@link EventEmitter}.
 *
 * @author ayahyaoui
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingFacadeImpl implements StreamingFacade {

    private static final String ERROR_CODE_STREAMING = "STREAMING_ERROR";
    private static final String ERROR_CODE_CANCELLED  = "CANCELLED";
    private static final String MSG_CANCELLED         = "Stream annulé par l'utilisateur";

    private final StreamingOrchestrator orchestrator;
    private final EventEmitter          eventEmitter;
    private final SessionIdGenerator    sessionIdGenerator;

    // -------------------------------------------------------------------------
    // StreamingFacade API
    // -------------------------------------------------------------------------

    @Override
    public SseEmitter startStream(StreamingRequest request) {
        String     sessionId = sessionIdGenerator.generate();
        SseEmitter emitter   = createEmitter();

        log.info("🚀 Démarrage SSE — sessionId={}, conversationId={}",
            sessionId, request.getConversationId());

        eventEmitter.registerSSE(sessionId, emitter);
        launchAsync(sessionId, request);

        return emitter;
    }

    @Override
    public void cancelStream(String sessionId) {
        log.info("🛑 Annulation stream — sessionId={}", sessionId);
        eventEmitter.emitError(sessionId, MSG_CANCELLED, ERROR_CODE_CANCELLED);
        eventEmitter.complete(sessionId);
    }

    // -------------------------------------------------------------------------
    // Privé
    // -------------------------------------------------------------------------

    /**
     * Crée un {@link SseEmitter} avec le timeout configuré.
     */
    private SseEmitter createEmitter() {
        return new SseEmitter(SseConstants.TIMEOUT_MS);
    }

    /**
     * Lance le pipeline de streaming dans un thread séparé.
     * Isole le CompletableFuture du flux principal (clean code).
     */
    private void launchAsync(String sessionId, StreamingRequest request) {
        CompletableFuture.runAsync(() -> {
            try {
                eventEmitter.emitConnected(sessionId, request.getConversationId());
                orchestrator.executeStreaming(sessionId, request).join();
            } catch (Exception e) {
                log.error("❌ Erreur SSE — sessionId={}", sessionId, e);
                eventEmitter.emitError(sessionId, e.getMessage(), ERROR_CODE_STREAMING);
                eventEmitter.completeWithError(sessionId, e);
            }
        });
    }
}