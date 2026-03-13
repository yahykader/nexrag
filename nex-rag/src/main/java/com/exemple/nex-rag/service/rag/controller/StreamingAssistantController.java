package com.exemple.nexrag.service.rag.controller;

import com.exemple.nexrag.service.rag.facade.StreamingFacade;
import com.exemple.nexrag.service.rag.streaming.model.StreamingRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Controller REST pour le streaming SSE (Server-Sent Events).
 *
 * Principe SRP : unique responsabilité → router les requêtes HTTP vers la facade.
 *                Zéro logique SSE ici — tout délégué à {@link StreamingFacade}.
 * Principe DIP : dépend de l'abstraction StreamingFacade, pas des services concrets.
 * Clean code   : zéro magic number, zéro helper générique, zéro try/catch.
 *
 * @author ayahyaoui
 * @version 2.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
@Tag(name = "Streaming Search", description = "API Streaming Search")
public class StreamingAssistantController {

    private final StreamingFacade streamingFacade;

    // =========================================================================
    // STREAMING — GET (EventSource navigateur)
    // =========================================================================

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
        summary     = "Stream SSE via GET",
        description = "Endpoint compatible EventSource (Angular, React, etc.)"
    )
    public SseEmitter streamGet(
            @Parameter(description = "Requête utilisateur")
            @RequestParam String query,
            @Parameter(description = "ID de conversation (optionnel)")
            @RequestParam(required = false) String conversationId) {

        log.info("📡 SSE GET — query={}, conversationId={}", truncate(query), conversationId);

        return streamingFacade.startStream(buildRequest(query, conversationId));
    }

    // =========================================================================
    // STREAMING — POST (body JSON)
    // =========================================================================

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
        summary     = "Stream SSE via POST",
        description = "Endpoint JSON avec options avancées"
    )
    public SseEmitter streamPost(
            @RequestBody StreamingRequest request) {

        log.info("📡 SSE POST — query={}, conversationId={}",
            truncate(request.getQuery()), request.getConversationId());

        return streamingFacade.startStream(request);
    }

    // =========================================================================
    // CONTRÔLE
    // =========================================================================

    @PostMapping("/stream/{sessionId}/cancel")
    @Operation(summary = "Annuler un flux SSE en cours")
    public ResponseEntity<Void> cancel(
            @Parameter(description = "Identifiant de session à annuler")
            @PathVariable String sessionId) {

        streamingFacade.cancelStream(sessionId);
        return ResponseEntity.ok().build();
    }

    // =========================================================================
    // HEALTH
    // =========================================================================

    @GetMapping("/stream/health")
    @Operation(summary = "Health check SSE")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("SSE streaming endpoint is healthy");
    }

    // =========================================================================
    // Utilitaire
    // =========================================================================

    /**
     * Tronque la query pour les logs.
     * Responsabilité limitée au controller (formatage des logs HTTP).
     */
    private String truncate(String text) {
        if (text == null || text.length() <= 50) return text;
        return text.substring(0, 50) + "...";
    }

    private StreamingRequest buildRequest(String query, String conversationId) {
        return StreamingRequest.builder()
            .query(query)
            .conversationId(conversationId)
            .build();
    }
}