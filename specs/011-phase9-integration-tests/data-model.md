# Data Model: PHASE 9 — Tests d'Intégration NexRAG

**Feature**: 011-phase9-integration-tests  
**Date**: 2026-04-06

---

## Entités de test (fixtures et états observables)

### 1. DocumentFixture

Représente un fichier de test préchargé dans les ressources de test, utilisé comme entrée du pipeline d'ingestion.

| Attribut | Type | Contraintes |
|----------|------|-------------|
| `path` | `ClassPathResource` | Non null — chemin dans `src/test/resources/fixtures/` |
| `format` | `enum {PDF, DOCX, XLSX, IMAGE}` | Non null |
| `contentType` | `String` | Ex: `application/pdf`, `image/png` |
| `sizeBytes` | `long` | > 0 |
| `isVirusInfected` | `boolean` | `true` uniquement pour `eicar.com` |

**Instances prédéfinies**:
- `sample.pdf` — PDF de 2 pages, contenu textuel connu
- `sample.docx` — DOCX 1 page
- `sample.xlsx` — XLSX 1 feuille, données tabulaires
- `sample.png` — image PNG 800×600
- `eicar.com` — fichier EICAR (virus de test ClamAV)

**Emplacement**: `src/test/resources/fixtures/`

---

### 2. IngestionResult

Représente le résultat observable d'une ingestion réussie, vérifié par requête directe sur la base vectorielle.

| Attribut | Type | Contraintes |
|----------|------|-------------|
| `documentId` | `String` | Non null, unique par document |
| `embeddingCount` | `int` | > 0 après ingestion réussie |
| `status` | `enum {SUCCESS, DUPLICATE, VIRUS_DETECTED, ERROR}` | Non null |
| `ingestionDurationMs` | `long` | < 10 000 (10 secondes) |

**Transitions d'état**:
```
[SOUMIS] → (antivirus clean?) → [VIRUS_DETECTED si infecté]
         → (hash connu?)      → [DUPLICATE si déjà ingéré]
         → (traitement OK?)   → [SUCCESS si pipeline complet]
         → (erreur pipeline?) → [ERROR]
```

---

### 3. RetrievalResult

Représente le résultat d'une requête de retrieval, vérifié en sortie du pipeline RAG.

| Attribut | Type | Contraintes |
|----------|------|-------------|
| `query` | `String` | Non null, non vide |
| `passages` | `List<String>` | Taille ≥ 3 pour une requête pertinente |
| `scores` | `List<Double>` | Chaque score ∈ [0.0, 1.0] |
| `retrievalDurationMs` | `long` | < 2 000 (2 secondes) |

---

### 4. StreamingSession

Représente une session de streaming SSE, avec capture du premier fragment et de l'historique.

| Attribut | Type | Contraintes |
|----------|------|-------------|
| `conversationId` | `String` | Non null après initialisation |
| `firstTokenReceivedMs` | `long` | < 3 000 (3 secondes depuis envoi requête) |
| `tokens` | `List<String>` | Non vide avant événement DONE |
| `isDone` | `boolean` | `true` après réception de `data: [DONE]` |
| `turnCount` | `int` | ≥ 2 pour valider le maintien de l'historique |

---

### 5. RateLimitObservation

Représente une observation du comportement du rate limiter en conditions d'intégration.

| Attribut | Type | Contraintes |
|----------|------|-------------|
| `endpoint` | `String` | Ex: `/api/documents/upload`, `/api/search` |
| `requestCount` | `int` | Nombre total de requêtes envoyées |
| `acceptedCount` | `int` | Doit être ≤ seuil configuré |
| `rejectedStatus` | `int` | 429 (HTTP Too Many Requests) pour les requêtes refusées |
| `windowDurationMs` | `long` | Fenêtre temporelle de mesure |

---

## Structure des fichiers de test

```
src/test/
├── java/com/exemple/nexrag/service/rag/integration/
│   ├── AbstractIntegrationSpec.java          ← base class: conteneurs + WireMock + DynamicPropertySource
│   ├── IngestionPipelineIntegrationSpec.java ← US-22: tous formats + antivirus + déduplication
│   ├── RetrievalPipelineIntegrationSpec.java ← US-23 (retrieval): ≥ 3 passages, < 2s
│   ├── StreamingPipelineIntegrationSpec.java ← US-23 (streaming): premier token < 3s, historique
│   ├── RateLimitIntegrationSpec.java         ← US-3: seuil upload + seuil search
│   └── FullRagPipelineIntegrationSpec.java   ← US-22 + US-23 combinés: flux complet
│
└── resources/
    ├── application-integration-test.yml      ← profil Spring dédié intégration
    ├── fixtures/
    │   ├── sample.pdf
    │   ├── sample.docx
    │   ├── sample.xlsx
    │   ├── sample.png
    │   └── virus/
    │       └── eicar.com
    └── wiremock/
        ├── embeddings-response.json          ← stub POST /v1/embeddings
        ├── chat-completion-response.json     ← stub POST /v1/chat/completions (non-stream)
        └── chat-completion-stream.json       ← stub SSE /v1/chat/completions (stream)
```

---

## Contraintes de validation

| Contrainte | Valeur | Source |
|------------|--------|--------|
| Ingestion max duration | < 10 000 ms | SC-001, AC-22.1 |
| Retrieval max duration | < 2 000 ms | SC-004 (clarification Q2) |
| First streaming token | < 3 000 ms | SC-005 (clarification Q2) |
| Passages minimum par requête pertinente | ≥ 3 | AC-23.1, SC-004 |
| Embeddings en base après ingestion | COUNT > 0 | AC-22.2 |
| Statut deuxième ingestion même fichier | DUPLICATE | AC-22.3 |
| Statut ingestion fichier EICAR | VIRUS_DETECTED | FR-010 |
| Requêtes refusées au-delà du seuil | HTTP 429 | SC-007 |
| Durée totale suite CI | < 10 min | SC-008 (clarification Q5) |
