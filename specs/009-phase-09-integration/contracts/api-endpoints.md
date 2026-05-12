# API Contracts: PHASE 9 — Tests d'Intégration

**Date**: 2026-05-07 | **Branch**: `009-phase-09-integration`

Les endpoints ci-dessous sont exercés par les tests d'intégration. Chaque contrat décrit la requête envoyée, la réponse attendue, et la classe de test responsable.

---

## POST /api/ingest

**Testé par** : `IngestionPipelineIntegrationSpec`, `FullRagPipelineIntegrationSpec`

**Requête** :
```
Content-Type: multipart/form-data
Field: file = <MultipartFile>
Field: metadata = <JSON optionnel>
```

**Réponses contractuelles** :

| Scénario | HTTP Status | Body |
|----------|-------------|------|
| Fichier valide, premier envoi | `202 Accepted` | `{"batchId":"…","status":"SUCCESS"}` |
| Fichier déjà ingéré | `409 Conflict` | `{"status":"DUPLICATE"}` |
| Fichier infecté (EICAR) | `400 Bad Request` | `{"status":"REJECTED","message":"Virus détecté"}` |
| Fichier manquant | `400 Bad Request` | Validation error |
| Quota upload dépassé | `429 Too Many Requests` | `{"error":"Too Many Requests","retryAfterSeconds":N}` |

**Contrainte de performance** : réponse reçue en < 10 s pour un document de test (SC-001).

---

## POST /api/stream

**Testé par** : `StreamingPipelineIntegrationSpec`, `FullRagPipelineIntegrationSpec`

**Requête** :
```
Content-Type: application/json
Body: {"query":"…","conversationId":"…"}
```

**Réponse contractuelle** :
```
Content-Type: text/event-stream
data: {"type":"token","content":"NexRAG"}
data: {"type":"token","content":" est"}
data: {"type":"token","content":" disponible"}
data: {"type":"done"}
```

| Scénario | Comportement attendu |
|----------|---------------------|
| Requête valide | ≥ 1 événement `TOKEN` avant `DONE` (SC-004) |
| Requête vide | `400 Bad Request` (pas de flux SSE) |
| Erreur génération | Événement `ERROR` émis, flux fermé proprement |

**Contrainte de performance** : premier événement `TOKEN` reçu en < 5 s (SC-004).

---

## GET /api/search (ou endpoint de requête RAG)

**Testé par** : `RetrievalPipelineIntegrationSpec`

**Requête** :
```
Content-Type: application/json
Body: {"query":"Qu'est-ce que NexRAG ?","conversationId":"conv-abc"}
```

**Réponse contractuelle** :
```json
{
  "passages": [
    {"content": "NexRAG est un système RAG…", "score": 0.92, "rank": 1},
    {"content": "Il supporte l'ingestion…",  "score": 0.87, "rank": 2},
    {"content": "Les formats supportés…",    "score": 0.81, "rank": 3}
  ],
  "durationMs": 1450
}
```

| Contrainte | Valeur |
|-----------|--------|
| `passages.size()` | `≥ 3` pour requête pertinente (SC-003) |
| `durationMs` | `≤ 3000` (SC-003) |
| Ordre | `passages[i].score ≥ passages[i+1].score` |

---

## DELETE /api/files (nettoyage inter-tests)

**Utilisé par** : `AbstractIntegrationSpec.@BeforeEach`

**Requête** :
```
DELETE /api/files
```

**Réponse** : `204 No Content` — supprime tous les documents et leurs vecteurs associés.

> Ce contrat n'est pas un AC de spec — c'est une opération de maintenance de l'environnement de test utilisée dans le `@BeforeEach` pour garantir l'isolation (FR-009).

---

## POST /api/upload (rate limiting)

**Testé par** : `RateLimitIntegrationSpec`

**Scénario de dépassement de quota** :

| Requête N° | Résultat attendu |
|-----------|-----------------|
| 1–10 | `202 Accepted` (dans le quota upload: 10/min) |
| 11+ | `429 Too Many Requests` |

**Headers requis sur 429** :

| Header | Valeur |
|--------|--------|
| `Retry-After` | Entier positif (secondes) |
| `X-RateLimit-Remaining` | `"0"` |
| `X-RateLimit-Reset` | Epoch Unix |

---

## WireMock Stubs (service IA externe simulé)

**Stub 1 — Embeddings** :

```
POST /v1/embeddings
→ 200 OK
{
  "object": "list",
  "data": [{"object":"embedding","embedding":[0.1, 0.1, … ×1536],"index":0}],
  "model": "text-embedding-3-small"
}
```

**Stub 2 — Chat streaming** :

```
POST /v1/chat/completions
→ 200 OK  Content-Type: text/event-stream
data: {"choices":[{"delta":{"content":"NexRAG"},"finish_reason":null}]}
data: {"choices":[{"delta":{"content":" est"},"finish_reason":null}]}
data: {"choices":[{"delta":{"content":" disponible"},"finish_reason":null}]}
data: [DONE]
```
