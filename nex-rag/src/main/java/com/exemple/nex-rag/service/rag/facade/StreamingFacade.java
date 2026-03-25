package com.exemple.nexrag.service.rag.facade;

import com.exemple.nexrag.service.rag.streaming.model.StreamingRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Facade exposée au controller SSE.
 *
 * Principe ISP : interface fine → le controller ne dépend que de ce contrat.
 * Principe DIP : le controller dépend de cette abstraction,
 *                pas de {@code StreamingOrchestrator} ni de {@code EventEmitter}.
 *
 * @author ayahyaoui
 * @version 1.0
 */
public interface StreamingFacade {

    /**
     * Démarre un flux SSE pour la requête donnée.
     *
     * @param request requête de streaming (query + conversationId optionnel)
     * @return {@link SseEmitter} connecté au flux
     */
    SseEmitter startStream(StreamingRequest request);

    /**
     * Annule un flux SSE en cours.
     *
     * @param sessionId identifiant de la session à annuler
     */
    void cancelStream(String sessionId);
}