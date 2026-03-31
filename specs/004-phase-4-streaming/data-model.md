# Data Model: Phase 4 — Streaming

**Branch**: `004-phase-4-streaming` | **Date**: 2026-03-30

## Entités de domaine

### 1. `ConversationState`

Persistée dans Redis. Clé Redis : `conversation:{conversationId}`. TTL : 3600s (1h).

| Champ | Type Java | Contraintes | Notes |
|-------|-----------|-------------|-------|
| `conversationId` | `String` | Non-null, unique, format `conv_` + 16 hex chars | Généré par `UUID.randomUUID()` tronqué |
| `userId` | `String` | Non-null | Propriétaire — **scoping requis (Q4)** mais `getConversation()` ne le vérifie pas actuellement |
| `createdAt` | `Instant` | Non-null | Immuable après création |
| `lastActivity` | `Instant` | Non-null | Mis à jour à chaque `addUserMessage` / `addAssistantMessage` |
| `messages` | `List<Message>` | Non-null, max 100 (`MAX_MESSAGES`) | Ordre chronologique FIFO |
| `context` | `List<ContextItem>` | Non-null, peut être vide | Documents RAG utilisés dans la conversation |
| `metadata` | `Map<String, Object>` | Non-null, peut être vide | Données arbitraires |
| `ttlSeconds` | `int` | Défaut 3600 | Passé à Redis `.expire()` à chaque sauvegarde |

**Règle de troncature** : Quand `messages.size() > MAX_MESSAGES`, `messages.remove(0)` — le
message le plus ancien est supprimé. `MAX_MESSAGES = 100` (hardcodé, non configurable via
`application.yml` — voir R-06 divergence #4).

**Risque identifié** : `getConversation(conversationId)` retourne la conversation sans
vérifier le `userId`. La spec (Q4) exige un contrôle d'accès. Ce gap doit être adressé
avant merge (voir tâches dans `tasks.md`).

---

### 2. `Message` (inner class de `ConversationState`)

| Champ | Type Java | Contraintes | Notes |
|-------|-----------|-------------|-------|
| `role` | `String` | `"user"` ou `"assistant"` | Pas d'enum actuellement |
| `content` | `String` | Non-null, non-vide | Contenu textuel du message |
| `timestamp` | `Instant` | Non-null | Horodatage de création |
| `sources` | `List<SourceReference>` | Nullable | Présent uniquement pour les messages `assistant` |
| `metadata` | `Map<String, Object>` | Non-null | Peut contenir `tokens`, `duration_ms`, `cost_usd` |

---

### 3. `StreamingEvent`

Événement unitaire émis vers le client SSE. Non persisté.

| Champ | Type Java | Contraintes |
|-------|-----------|-------------|
| `type` | `StreamingEvent.Type` | Non-null — voir enum ci-dessous |
| `sessionId` | `String` | Non-null |
| `conversationId` | `String` | Nullable |
| `data` | `Map<String, Object>` | Nullable |
| `timestamp` | `Instant` | Non-null |

**Enum `StreamingEvent.Type`** — séquences valides par flux :

```
Flux normal  : QUERY_RECEIVED → QUERY_TRANSFORMED → ROUTING_DECISION
             → RETRIEVAL_COMPLETE → CONTEXT_READY → GENERATION_START
             → TOKEN* → GENERATION_COMPLETE → COMPLETE

Flux erreur  : [...] → ERROR        (COMPLETE n'est PAS émis)

Flux vide    : GENERATION_START → GENERATION_COMPLETE → COMPLETE
```

**Invariant DONE (clarification Q2)** : `COMPLETE` est terminal absolu. Une fois émis,
`EventEmitter.complete(sessionId)` est appelé immédiatement, supprimant l'emitter de la
`ConcurrentHashMap`. Aucun événement ultérieur ne peut être émis pour cette session.

**Correspondance spec → code** :
- Spec `TOKEN` = `Type.TOKEN`
- Spec `DONE` = `Type.GENERATION_COMPLETE` (fin génération) + `Type.COMPLETE` (fermeture flux)
- Spec `ERROR` = `Type.ERROR`

---

### 4. `StreamingRequest`

| Champ | Type Java | Contraintes | Notes |
|-------|-----------|-------------|-------|
| `query` | `String` | Non-null, non-vide | Question de l'utilisateur |
| `conversationId` | `String` | Nullable | Si null, nouvelle conversation créée |
| `userId` | `String` | Non-null | Propriétaire de la session |
| `temperature` | `double` | 0.0–1.0, défaut 0.7 | Paramètre de génération |

---

### 5. `StreamingResponse`

Réponse finale assemblée par `StreamingOrchestrator` (non streamée, renvoyée via
`CompletableFuture`).

| Champ | Type Java | Notes |
|-------|-----------|-------|
| `sessionId` | `String` | |
| `conversationId` | `String` | ID de la conversation mise à jour |
| `query` | `String` | Query originale |
| `answer` | `String` | Réponse complète (concat de tous les tokens) |
| `sources` | `List<SourceReference>` | Sources RAG utilisées |
| `citations` | `List<Citation>` | Citations extraites du texte (`<cite index="N">`) |
| `metadata` | `Metadata` | Durées, nombre de tokens, chunks |

---

## Transitions d'état d'une conversation

```
[Inexistant]
    │  createConversation(userId)
    ▼
[Active] ──────────────────────── addUserMessage()
    │                              addAssistantMessage()
    │                              enrichQueryWithContext()
    │                              refreshTTL()
    │
    ├── TTL Redis expiré ─────────▶ [Supprimée automatiquement par Redis]
    │
    └── deleteConversation() ─────▶ [Supprimée explicitement]
```

---

## Risques & points de vigilance pour les tests

| Ref | Risque | Impact sur tests |
|-----|--------|-----------------|
| R-06#1 | Timeout 60s dans le code vs 30s dans la spec | Tester avec mock synchrone ; documenter le delta |
| R-06#2 | `getConversation()` sans contrôle userId | Écrire un test qui échoue → signaler le gap |
| R-06#3 | `Type.COMPLETE` ≠ `DONE` sémantique | Assertions sur `Type.GENERATION_COMPLETE` + `Type.COMPLETE` |
| R-06#4 | `MAX_MESSAGES = 100` hardcodé | Tester la limite à 100 ; noter l'absence de configurabilité |
