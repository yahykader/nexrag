# Research: Phase 4 — Streaming

**Branch**: `004-phase-4-streaming` | **Date**: 2026-03-30

## Résumé des décisions

Toutes les NEEDS CLARIFICATION ont été résolues lors du `/speckit.clarify` et complétées par
l'analyse directe du code source. Aucun point bloquant ne subsiste.

---

## R-01 — Stratégie de test de `RedisTemplate` avec Mockito

**Decision**: Mocker `RedisTemplate<String, String>` via `@Mock` Mockito, chaîner les stubs
sur `opsForValue()` → `ValueOperations`.

**Rationale**: `ConversationManager` ne dépend que de `redisTemplate.opsForValue().get(key)`,
`.set(key, json, ttl, unit)` et `redisTemplate.expire(key, ttl, unit)`. Ces appels sont
directement stubbables sans démarrer Redis. Testcontainers est réservé aux tests d'intégration
(Principe V constitution).

**Pattern**:
```java
@Mock RedisTemplate<String, String> redisTemplate;
@Mock ValueOperations<String, String> valueOps;

@BeforeEach void setup() {
    when(redisTemplate.opsForValue()).thenReturn(valueOps);
}
```

**Alternatives considérées**: EmbeddedRedis — rejeté car explicitement interdit par le
Principe V de la constitution ("un fake EmbeddedRedis n'est pas un substitut acceptable").

---

## R-02 — Test de `StreamingChatLanguageModel` (LangChain4j) avec Mockito

**Decision**: Mocker `StreamingChatLanguageModel` et capturer le `StreamingResponseHandler`
via `ArgumentCaptor`, puis déclencher manuellement `onNext(token)` / `onComplete()` /
`onError()` dans le test.

**Rationale**: `StreamingOrchestrator.streamAiResponse()` passe un `StreamingResponseHandler`
anonyme à `streamingModel.generate(message, handler)`. Mockito peut capturer ce callback et
permettre au test de simuler la séquence de tokens puis le signal de fin.

**Pattern**:
```java
@Mock StreamingChatLanguageModel streamingModel;

@Test void shouldEmitTokensThenComplete() {
    ArgumentCaptor<StreamingResponseHandler<AiMessage>> handlerCaptor =
        ArgumentCaptor.forClass(StreamingResponseHandler.class);
    doNothing().when(streamingModel).generate(any(UserMessage.class), handlerCaptor.capture());

    // Déclencher le streaming
    orchestrator.executeStreaming(sessionId, request);

    // Simuler tokens puis fin
    StreamingResponseHandler<AiMessage> handler = handlerCaptor.getValue();
    handler.onNext("Bonjour");
    handler.onNext(" monde");
    handler.onComplete(mock(Response.class));
}
```

**Alternatives considérées**: Utiliser un vrai `OpenAiStreamingChatModel` pointant sur
WireMock — écarté car l'orchestrateur utilise LangChain4j `StreamingChatLanguageModel`,
pas `OpenAiStreamingClient` directement. WireMock est réservé à `OpenAiStreamingClientSpec`.

---

## R-03 — Test de `WebClient` (Spring WebFlux) avec WireMock

**Decision**: `WireMockServer` sur un port aléatoire ; injecter l'URL WireMock dans
`StreamingConfig` via mock ; utiliser `aResponse().withBody(...)` avec `Content-Type:
text/event-stream`.

**Rationale**: `OpenAiStreamingClient` construit son `WebClient` à partir de
`config.getOpenAi().getApiUrl()`. En mockant `StreamingConfig`, on contrôle l'URL cible
sans modifier le code de production. La réponse SSE doit suivre le format `data: {...}\n\n`
puis `data: [DONE]\n\n`.

**Divergence identifiée**: La signature du constructeur dans le code de production est
`OpenAiStreamingClient(StreamingConfig config, ObjectMapper objectMapper)`, non
`(String url, String key)` comme dans l'exemple du test plan. Les tests doivent utiliser
la vraie signature avec `StreamingConfig` mocké.

**Pattern WireMock pour SSE**:
```java
wireMock.stubFor(post(urlEqualTo("/v1/chat/completions"))
    .willReturn(aResponse()
        .withHeader("Content-Type", "text/event-stream")
        .withChunkedDribbleDelay(5, 200)
        .withBody(
            "data: {\"choices\":[{\"delta\":{\"content\":\"Bonjour\"}}]}\n\n" +
            "data: {\"choices\":[{\"delta\":{\"content\":\" monde\"}}]}\n\n" +
            "data: [DONE]\n\n"
        )));
```

**Alternatives considérées**: `MockWebServer` (OkHttp) — écarté car WireMock est déjà
le standard du projet (constitution, Testing Standards & Tooling).

---

## R-04 — Test de `EventEmitter` avec `ScheduledExecutorService`

**Decision**: Tester `EventEmitter` en injectant un `SseEmitter` mocké via
`registerSSE(sessionId, mockEmitter)` et en appelant directement `emit()` / `emitToken()`.
Ne pas tester le scheduler — il est un détail d'implémentation.

**Rationale**: Le `ScheduledExecutorService` interne à `EventEmitter` (heartbeat + flush)
est un timer privé. Tester sa fréquence relèverait du test d'implémentation, pas de
comportement. Ce qui doit être testé : la logique d'émission, le buffering des tokens,
et le nettoyage des ressources à `complete()`.

**Note technique**: `SseEmitter` est une classe Spring concrète, difficile à mocker
directement. Créer une sous-classe de test ou utiliser `Mockito.spy()` sur une vraie
instance avec timeout élevé (`new SseEmitter(Long.MAX_VALUE)`).

**Pattern**:
```java
SseEmitter emitter = spy(new SseEmitter(Long.MAX_VALUE));
eventEmitter.registerSSE("session-1", emitter);
eventEmitter.emitToken("session-1", "Bonjour", 0);
// Forcer flush immédiat (appel privé via réflexion ou package-private test method)
```

**Alternatives considérées**: Refactorer `EventEmitter` pour accepter un
`SseEmitter.SseEventBuilder` — écarté car modification de production non nécessaire.

---

## R-05 — Gestion du `CountDownLatch` dans `StreamingOrchestratorSpec`

**Decision**: Utiliser `CompletableFuture.get(timeout, SECONDS)` dans les tests pour
attendre la fin du streaming simulé. Déclencher `onComplete` immédiatement dans le mock
du `StreamingResponseHandler` capturé.

**Rationale**: `streamAiResponse()` utilise un `CountDownLatch(1).await(60, SECONDS)`.
Si `onComplete` est déclenché immédiatement dans le test, le latch se libère sans délai
et le test reste ≤ 500 ms. Ne jamais laisser le latch expirer (timeout 60s) dans un test.

---

## R-06 — Divergences code vs spec identifiées

**Decision**: Documenter comme risques dans `data-model.md` et les couvrir par des tests
dédiés pour signaler les incohérences.

| # | Divergence | Code | Spec (clarifiée) | Action |
|---|-----------|------|-------------------|--------|
| 1 | Timeout streaming | 60s (`latch.await(60, SECONDS)`) | 30s (Q5) | Test documente la valeur effective ; tâche de mise à jour du code |
| 2 | userId dans `getConversation()` | Non vérifié (retourne par ID seul) | Scoping userId requis (Q4) | Test vérifie l'accès non autorisé → gap à corriger en production |
| 3 | Type DONE vs COMPLETE | `Type.COMPLETE` + `Type.GENERATION_COMPLETE` | DONE (logique) | Tests utilisent `Type.GENERATION_COMPLETE` pour fin de génération et `Type.COMPLETE` pour fin de flux |
| 4 | `MAX_MESSAGES` | Hardcodé à 100 | `maxHistory` configurable (spec) | Test vérifie la limite à 100 ; tâche d'externalisation dans `application.yml` |

---

## R-07 — Séquence d'événements validée pour `OpenAiStreamingClientSpec`

Après analyse du code source de `OpenAiStreamingClient` et du test plan :

```
Flux normal : TOKEN* → GENERATION_COMPLETE → COMPLETE
Flux erreur : TOKEN* → ERROR → (pas de COMPLETE)
Flux timeout : (aucun TOKEN) → ERROR implicite (via latch.await timeout)
```

**Note**: Le test plan original (nexrag-test-plan-speckit.md) utilise `EventType.DONE`
qui correspond à `StreamingEvent.Type.GENERATION_COMPLETE` dans le code de production.
Les `@DisplayName` doivent documenter cette correspondance.
