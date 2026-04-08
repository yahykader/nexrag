# Contrats d'interface — Tests d'Intégration PHASE 9

**Feature**: 011-phase9-integration-tests  
**Date**: 2026-04-06  
**Scope**: Endpoints HTTP exercés par les tests d'intégration (comportements observables et attendus)

---

## Contrat 1 — Ingestion de document (POST /api/documents/upload)

**Exercé par**: `IngestionPipelineIntegrationSpec`, `FullRagPipelineIntegrationSpec`

### Requête

```
POST /api/documents/upload
Content-Type: multipart/form-data
Body: file=<binary>, metadata=<JSON optionnel>
```

### Réponses attendues selon scénario

| Scénario | Statut HTTP | Corps réponse (champ clé) |
|----------|-------------|--------------------------|
| Ingestion réussie | 200 OK | `{ "status": "SUCCESS", "documentId": "<uuid>" }` |
| Fichier déjà ingéré | 200 OK ou 409 | `{ "status": "DUPLICATE" }` |
| Virus détecté | 422 Unprocessable | `{ "status": "VIRUS_DETECTED", "error": "..." }` |
| Rate limit dépassé | 429 Too Many | `{ "error": "Rate limit exceeded" }` |
| Fichier invalide | 400 Bad Request | `{ "error": "..." }` |

### Contraintes de performance

- Temps de réponse < 10 000 ms pour un document de taille représentative
- La réponse SUCCESS doit être suivie immédiatement d'embeddings persistés (requêtables dans les 500 ms)

---

## Contrat 2 — Recherche / Retrieval (POST /api/search ou GET /api/search)

**Exercé par**: `RetrievalPipelineIntegrationSpec`, `FullRagPipelineIntegrationSpec`

### Requête

```
POST /api/search
Content-Type: application/json
Body: { "query": "<texte de la requête>", "conversationId": "<id optionnel>" }
```

### Réponses attendues

| Scénario | Statut HTTP | Corps réponse |
|----------|-------------|---------------|
| Requête pertinente | 200 OK | `{ "passages": [...], "scores": [...] }` — au moins 3 passages |
| Base vide | 200 OK | `{ "passages": [] }` |
| Requête vide | 400 Bad Request | `{ "error": "..." }` |

### Contraintes de performance

- Temps de réponse < 2 000 ms pour une base contenant un document de référence

---

## Contrat 3 — Streaming SSE (GET /api/chat/stream ou POST /api/assistant/stream)

**Exercé par**: `StreamingPipelineIntegrationSpec`, `FullRagPipelineIntegrationSpec`

### Requête

```
POST /api/assistant/stream
Content-Type: application/json
Accept: text/event-stream
Body: { "query": "<texte>", "conversationId": "<id>" }
```

### Format de réponse SSE

```
data: {"token": "Bonjour"}
data: {"token": " voici"}
data: {"token": " la réponse"}
data: [DONE]
```

### Contraintes

| Contrainte | Valeur |
|------------|--------|
| Premier événement `data:` reçu | < 3 000 ms après envoi |
| Événement `data: [DONE]` présent | Obligatoire pour toute réponse complète |
| Tokens non vides avant DONE | Au moins 1 token visible |

---

## Contrat 4 — Rate Limiting (tous endpoints protégés)

**Exercé par**: `RateLimitIntegrationSpec`

### Comportement attendu

| Endpoint | Seuil (req/min, config test) | Réponse sous seuil | Réponse au-dessus seuil |
|----------|------------------------------|-------------------|------------------------|
| `/api/documents/upload` | 10/min (config prod) | 200 OK | 429 Too Many Requests |
| `/api/search` | 50/min (config prod) | 200 OK | 429 Too Many Requests |

### En-têtes de réponse 429

```
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: <N>
X-RateLimit-Remaining: 0
Retry-After: <secondes>
```

---

## Contrat 5 — Stub WireMock OpenAI (interne aux tests)

**Usage**: Intercepter les appels sortants vers l'API OpenAI depuis le backend

### Stub POST /v1/embeddings

```json
{
  "object": "list",
  "data": [{
    "object": "embedding",
    "embedding": [0.1, 0.2, 0.3, "... 1536 valeurs ..."],
    "index": 0
  }],
  "model": "text-embedding-3-small",
  "usage": { "prompt_tokens": 10, "total_tokens": 10 }
}
```

### Stub POST /v1/chat/completions (non-streaming)

```json
{
  "id": "chatcmpl-test",
  "object": "chat.completion",
  "choices": [{
    "index": 0,
    "message": { "role": "assistant", "content": "Réponse de test NexRAG." },
    "finish_reason": "stop"
  }]
}
```

### Stub POST /v1/chat/completions (streaming SSE)

```
data: {"id":"chatcmpl-test","object":"chat.completion.chunk","choices":[{"delta":{"content":"Réponse"},"index":0}]}
data: {"id":"chatcmpl-test","object":"chat.completion.chunk","choices":[{"delta":{"content":" de test"},"index":0}]}
data: [DONE]
```
