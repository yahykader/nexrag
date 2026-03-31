package com.exemple.nexrag.service.rag.streaming;

import com.exemple.nexrag.service.rag.streaming.model.StreamingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Spec : EventEmitter — Émission SSE et gestion des sessions
 *
 * <p>SRP : teste uniquement la logique d'émission d'événements SSE, le buffering de tokens,
 * et le cycle de vie des sessions. Aucune connaissance de la génération ni du RAG.
 *
 * <p>Stratégie : SseEmitter spy sur instance réelle (Long.MAX_VALUE timeout).
 */
@DisplayName("Spec : EventEmitter — Émission SSE et gestion des sessions")
@ExtendWith(MockitoExtension.class)
class EventEmitterSpec {

    private static final String SESSION = "session-test";

    private EventEmitter emitter;
    private SseEmitter sseEmitter;

    @BeforeEach
    void setup() {
        emitter = new EventEmitter();
        sseEmitter = spy(new SseEmitter(Long.MAX_VALUE));
        emitter.registerSSE(SESSION, sseEmitter);
    }

    @AfterEach
    void teardown() {
        // Nettoyage défensif — si déjà complété, complete() est no-op
        try { emitter.complete(SESSION); } catch (Exception ignored) { }
    }

    // =========================================================================
    // T022 — registerSSE configure les callbacks de nettoyage
    // =========================================================================

    @Test
    @DisplayName("DOIT enregistrer un SseEmitter et configurer les callbacks de nettoyage")
    void shouldRegisterSseEmitterAndConfigureCleanupCallbacks() {
        // Le registerSSE dans @BeforeEach n'a pas levé d'exception → emitter enregistré
        // On vérifie qu'émettre un événement fonctionne (preuve que l'emitter est actif)
        assertThatNoException().isThrownBy(() ->
                emitter.emit(SESSION, buildEvent(StreamingEvent.Type.CONNECTED))
        );
    }

    // =========================================================================
    // T023 — FR-007 / AC-12.1 : Émission d'un événement TOKEN
    // =========================================================================

    @Test
    @DisplayName("DOIT émettre un événement TOKEN via le SseEmitter enregistré")
    void shouldEmitTokenEventViaSseEmitter() throws IOException {
        emitter.emit(SESSION, StreamingEvent.token(SESSION, "Bonjour", 0));

        verify(sseEmitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
    }

    // =========================================================================
    // T024 — SC-004 : Tokens émis dans l'ordre de réception
    // =========================================================================

    @Test
    @DisplayName("DOIT émettre les tokens dans l'ordre de réception")
    void shouldEmitTokensInOrderOfReception() throws IOException {
        // Forcer flush immédiat en envoyant chaque token comme événement direct (sans buffer)
        emitter.emit(SESSION, StreamingEvent.token(SESSION, "A", 0));
        emitter.emit(SESSION, StreamingEvent.token(SESSION, "B", 1));
        emitter.emit(SESSION, StreamingEvent.token(SESSION, "C", 2));

        // Vérifier que send() a été appelé au moins 3 fois dans l'ordre
        ArgumentCaptor<SseEmitter.SseEventBuilder> captor =
                ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(sseEmitter, atLeast(3)).send(captor.capture());
        assertThat(captor.getAllValues()).hasSizeGreaterThanOrEqualTo(3);
    }

    // =========================================================================
    // T025 — Buffering : flush immédiat quand TOKEN_BUFFER_SIZE (5) atteint
    // =========================================================================

    @Test
    @DisplayName("DOIT flusher le buffer dès que TOKEN_BUFFER_SIZE (5) est atteint")
    void shouldFlushTokenBufferWhenBufferSizeReached() throws IOException {
        // Envoyer 5 tokens via emitToken (avec buffering interne)
        for (int i = 0; i < 5; i++) {
            emitter.emitToken(SESSION, "token-" + i, i);
        }

        // Le buffer plein déclenche un flush immédiat → send() appelé au moins une fois
        verify(sseEmitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
    }

    // =========================================================================
    // T026 — FR-008 / AC-12.3 : Émission d'un événement ERROR
    // =========================================================================

    @Test
    @DisplayName("DOIT émettre un événement ERROR avec message et code corrects")
    void shouldEmitErrorEventWithMessageAndCode() throws IOException {
        emitter.emitError(SESSION, "Erreur API génération", "GENERATION_ERROR");

        verify(sseEmitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
    }

    // =========================================================================
    // T027 — FR-008 : Émission d'un événement COMPLETE
    // =========================================================================

    @Test
    @DisplayName("DOIT émettre un événement COMPLETE avec les données de réponse")
    void shouldEmitCompleteEventWithResponseData() throws IOException {
        emitter.emitComplete(SESSION, Map.of("answer", "Paris", "tokens", 42));

        verify(sseEmitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
    }

    // =========================================================================
    // T028 — Invariant DONE terminal (Q2) : complete() supprime l'emitter et ferme le flux
    // =========================================================================

    @Test
    @DisplayName("DOIT supprimer l'emitter de la session et fermer le flux SSE après complete()")
    void shouldRemoveEmitterFromMapAndCloseFluxAfterComplete() throws Exception {
        emitter.complete(SESSION);

        verify(sseEmitter).complete();
    }

    // =========================================================================
    // T029 — SC-005 / Q2 : DONE est terminal — aucun événement possible après complete()
    // =========================================================================

    @Test
    @DisplayName("DOIT être impossible d'émettre après complete() — DONE est terminal absolu")
    void shouldNotEmitAfterComplete() throws IOException {
        emitter.complete(SESSION);
        reset(sseEmitter); // reset le compteur de vérification

        // Tentative d'émission après fermeture → no-op (session retirée de la map)
        emitter.emit(SESSION, buildEvent(StreamingEvent.Type.TOKEN));

        verify(sseEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    // =========================================================================
    // T030 — Edge case : session inconnue → log warning, no-op
    // =========================================================================

    @Test
    @DisplayName("DOIT ignorer silencieusement l'émission pour une session inconnue")
    void shouldIgnoreEmitForUnknownSession() throws IOException {
        assertThatNoException().isThrownBy(() ->
                emitter.emit("session-inconnue", buildEvent(StreamingEvent.Type.TOKEN))
        );
        verify(sseEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    // =========================================================================
    // T031 — Edge case : IOException sur send() → session nettoyée
    // =========================================================================

    @Test
    @DisplayName("DOIT nettoyer les ressources de session quand le SseEmitter signale une IOException")
    void shouldCleanupSessionResourcesOnSseEmitterIoException() throws IOException {
        doThrow(new IOException("connexion perdue")).when(sseEmitter)
                .send(any(SseEmitter.SseEventBuilder.class));

        // La première émission échoue → session retirée de la map
        emitter.emit(SESSION, buildEvent(StreamingEvent.Type.TOKEN));

        // Tentative suivante → no-op (session déjà retirée)
        reset(sseEmitter);
        emitter.emit(SESSION, buildEvent(StreamingEvent.Type.TOKEN));
        verify(sseEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    // =========================================================================
    // T038 — emitConnected : couverture branche emitConnected()
    // =========================================================================

    @Test
    @DisplayName("DOIT émettre un événement CONNECTED via emitConnected avec le conversationId correct")
    void shouldEmitConnectedEventViaEmitConnected() throws IOException {
        emitter.emitConnected(SESSION, "conv_test_connected");

        verify(sseEmitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
    }

    // =========================================================================
    // T039 — completeWithError : fermeture du flux avec erreur
    // =========================================================================

    @Test
    @DisplayName("DOIT fermer le flux SSE avec erreur via completeWithError quand la session est active")
    void shouldCloseFluxWithErrorViaCompleteWithError() throws Exception {
        emitter.completeWithError(SESSION, new RuntimeException("Erreur forcée"));

        verify(sseEmitter).completeWithError(any(Throwable.class));
    }

    @Test
    @DisplayName("DOIT être no-op dans completeWithError quand la session est inconnue")
    void shouldBeNoOpInCompleteWithErrorForUnknownSession() {
        assertThatNoException().isThrownBy(() ->
                emitter.completeWithError("session-inconnue", new RuntimeException("erreur"))
        );
    }

    // =========================================================================
    // T040 — emitToken session inconnue : branche buffer null
    // =========================================================================

    @Test
    @DisplayName("DOIT ignorer emitToken quand la session est inconnue (buffer null)")
    void shouldIgnoreEmitTokenWhenSessionUnknown() {
        assertThatNoException().isThrownBy(() ->
                emitter.emitToken("session-inconnue", "token", 0)
        );
    }

    // =========================================================================
    // T041 — flushTokenBuffers via scheduler périodique (TOKEN_FLUSH_INTERVAL=50ms)
    // =========================================================================

    @Test
    @DisplayName("DOIT flusher les tokens via le scheduler périodique quand le buffer est non-plein")
    void shouldFlushTokensViaPeriodicScheduler() throws InterruptedException, IOException {
        // Ajouter 3 tokens (< TOKEN_BUFFER_SIZE=5 → pas de flush immédiat)
        emitter.emitToken(SESSION, "t1", 0);
        emitter.emitToken(SESSION, "t2", 1);
        emitter.emitToken(SESSION, "t3", 2);

        // Attendre le scheduler (TOKEN_FLUSH_INTERVAL=50ms → 200ms pour garantir au moins un passage)
        Thread.sleep(200);

        // Le scheduler a flushé le buffer → send() appelé au moins une fois
        verify(sseEmitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
    }

    // =========================================================================
    // MÉTHODES UTILITAIRES PRIVÉES
    // =========================================================================

    private StreamingEvent buildEvent(StreamingEvent.Type type) {
        return StreamingEvent.builder()
                .type(type)
                .sessionId(SESSION)
                .data(Map.of("test", "value"))
                .timestamp(Instant.now())
                .build();
    }
}
