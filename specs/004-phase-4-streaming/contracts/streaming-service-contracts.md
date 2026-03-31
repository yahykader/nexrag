# Contrats de service — Module Streaming (Phase 4)

**Branch**: `004-phase-4-streaming` | **Date**: 2026-03-30

Ces contrats définissent les interfaces internes Spring entre les composants du module
`streaming`. Ils sont utilisés pour guider les assertions dans les tests unitaires.

---

## 1. `ConversationManager`

**Responsabilité SRP** : Persistance et récupération des `ConversationState` dans Redis.
N'a aucune connaissance du streaming ni de la génération.

### Méthodes publiques testées

```java
// Crée une nouvelle conversation et la persiste dans Redis avec TTL
ConversationState createConversation(String userId)
  // POST-CONDITION : conversationId non-null, userId == paramètre, messages vide
  // SIDE-EFFECT   : redisTemplate.opsForValue().set(key, json, ttl, SECONDS) appelé

// Récupère une conversation. Retourne Optional.empty() si clé absente ou JSON invalide.
// DIVERGENCE (R-06#2) : ne vérifie PAS le userId — gap à corriger
Optional<ConversationState> getConversation(String conversationId)
  // POST-CONDITION : Optional.of(state) si clé présente et désérialisable
  // SIDE-EFFECT   : aucun

// Ajoute un message utilisateur. Lève IllegalArgumentException si conversation inconnue.
ConversationState addUserMessage(String conversationId, String content)
  // PRE-CONDITION : conversation doit exister (getConversation != empty)
  // POST-CONDITION : messages.last().role == "user", messages.last().content == content
  // SIDE-EFFECT   : saveConversation() appelé ; si size > 100, messages.remove(0)

// Ajoute la réponse de l'assistant.
ConversationState addAssistantMessage(String conversationId, String content,
                                      List<SourceReference> sources,
                                      Map<String, Object> metadata)
  // POST-CONDITION : messages.last().role == "assistant"
  // SIDE-EFFECT   : saveConversation() ; context mis à jour si sources != null

// Retourne les N derniers messages (sous-liste).
List<Message> getMessageHistory(String conversationId, int maxMessages)
  // POST-CONDITION : retourne au plus maxMessages messages, dans l'ordre original
  // EDGE CASE     : retourne empty list si conversation inconnue

// Enrichit la query avec les 3 derniers messages de l'historique.
String enrichQueryWithContext(String conversationId, String query)
  // POST-CONDITION : retourne query inchangée si conversation inconnue ou messages vide

// Supprime la conversation de Redis.
void deleteConversation(String conversationId)
  // SIDE-EFFECT : redisTemplate.delete(key) appelé

// Renouvelle le TTL sans modifier le contenu.
void refreshTTL(String conversationId)
  // SIDE-EFFECT : redisTemplate.expire(key, 3600, SECONDS) appelé
```

### Contrat de clé Redis

```
Clé    : "conversation:" + conversationId
Valeur : JSON sérialisé de ConversationState (Jackson ObjectMapper)
TTL    : state.getTtlSeconds() (défaut 3600 secondes)
```

---

## 2. `EventEmitter`

**Responsabilité SRP** : Emission d'événements SSE vers les clients connectés.
Ne persiste rien, ne connaît pas la logique RAG.

### Méthodes publiques testées

```java
// Enregistre un SseEmitter pour la session. Configure les callbacks de cleanup.
void registerSSE(String sessionId, SseEmitter emitter)
  // SIDE-EFFECT : sseEmitters.put(sessionId, emitter) ; tokenBuffers.put(sessionId, buffer)

// Émet un événement générique via SSE.
void emit(String sessionId, StreamingEvent event)
  // PRE-CONDITION  : session enregistrée via registerSSE()
  // SIDE-EFFECT   : emitter.send(...) ; si IOException, emitter supprimé de la map
  // EDGE CASE     : si sessionId inconnu, log warning et no-op

// Bufferise un token. Flush immédiat si buffer atteint TOKEN_BUFFER_SIZE (5).
void emitToken(String sessionId, String text, int index)
  // INVARIANT : tokens toujours émis dans l'ordre d'arrivée

// Émet Type.ERROR avec message + code.
void emitError(String sessionId, String message, String code)

// Émet Type.COMPLETE avec les données de réponse.
void emitComplete(String sessionId, Map<String, Object> responseData)

// Termine et ferme le SseEmitter (appelle emitter.complete()).
// INVARIANT DONE (Q2) : après complete(), aucun événement ne peut être émis pour cette session
void complete(String sessionId)
  // SIDE-EFFECT : sseEmitters.remove(sessionId) ; tokenBuffers.remove(sessionId)

// Termine avec erreur (appelle emitter.completeWithError(error)).
void completeWithError(String sessionId, Throwable error)
  // SIDE-EFFECT : sseEmitters.remove(sessionId) ; tokenBuffers.remove(sessionId)
```

### Séquence garantie

```
registerSSE(s, emitter)
  → emit*(s, ...)     [0..N TOKEN events]
  → emitComplete(s, data)  [Type.COMPLETE]
  → complete(s)            [fermeture SseEmitter — TERMINAL]
```

---

## 3. `StreamingOrchestrator`

**Responsabilité SRP** : Coordination du pipeline complet RAG → conversation → génération.
Délègue à `ConversationManager`, `EventEmitter` et `StreamingChatLanguageModel`.

### Méthode publique testée

```java
// Execute le pipeline streaming de façon asynchrone.
CompletableFuture<StreamingResponse> executeStreaming(String sessionId, StreamingRequest request)
  // ORDRE GARANTI (FR-010) :
  //   1. handleConversation()  → crée ou récupère la conversation + addUserMessage()
  //   2. enrichQueryWithContext() + retrievalAugmentor.execute()
  //   3. streamingModel.generate() avec StreamingResponseHandler
  //   4. onNext(token) → eventEmitter.emitToken()
  //   5. onComplete()  → assemblage réponse + conversationManager.addAssistantMessage()
  //   6. eventEmitter.emitComplete() + eventEmitter.complete()
  // TIMEOUT : 60s (code actuel) / 30s (spec cible — voir R-06#1)
  // EXCEPTION : toute exception est catchée → emitError() + completeWithError()
```

---

## 4. `OpenAiStreamingClient`

**Responsabilité SRP** : Consommation de l'API OpenAI en mode SSE via WebClient (WebFlux).
Indépendant de LangChain4j — utilisé uniquement pour les appels directs HTTP OpenAI.

### Méthode publique testée

```java
// Stream une réponse depuis l'API OpenAI. Appelle onToken pour chaque token, onDone à la fin.
Flux<String> streamCompletion(String prompt)
  // Format SSE attendu en réponse :
  //   data: {"choices":[{"delta":{"content":"token"}}]}\n\n
  //   data: [DONE]\n\n
  // SIDE-EFFECT : parsing des lignes SSE ; extraction du champ delta.content
  // ERREUR      : toute erreur HTTP ou parsing → signal d'erreur dans le Flux
```

### Contrat WireMock pour les tests

Stub minimal pour un flux valide :
```
POST /v1/chat/completions
Authorization: Bearer {api-key}
Content-Type: application/json

→ 200 OK
   Content-Type: text/event-stream
   Body:
     data: {"choices":[{"delta":{"content":"Bonjour"}}]}\n\n
     data: {"choices":[{"delta":{"content":" monde"}}]}\n\n
     data: [DONE]\n\n
```

Stub pour une erreur HTTP :
```
POST /v1/chat/completions → 429 Too Many Requests
```
