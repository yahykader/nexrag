# Data Model: PHASE 9 — Tests d'Intégration

**Date**: 2026-05-07 | **Branch**: `009-phase-09-integration`

## Entités de test

### TestFixture

Représente un document de test utilisé comme données d'entrée pour les specs d'intégration.

| Champ | Type | Valeur |
|-------|------|--------|
| `name` | `String` | `"sample.pdf"`, `"sample.docx"`, `"sample.xlsx"`, `"sample.jpg"`, `"sample.txt"` |
| `mimeType` | `String` | `"application/pdf"`, `"application/vnd.openxmlformats-officedocument.wordprocessingml.document"`, etc. |
| `path` | `ClassPathResource` | `"fixtures/sample.pdf"`, … |
| `expectedMinChunks` | `int` | `≥ 1` (PDF: ≥ 2, DOCX: ≥ 1, XLSX: ≥ 1, image: ≥ 1, texte: ≥ 1) |

**États lifecycle** :
```
SOUMIS → SCANNÉ (antivirus) → DÉDUPLIQUÉ → PARSÉ → CHUNKÉ → EMBEDDI → STOCKÉ
                                   ↓ (doublon détecté)
                               DUPLICATE (arrêt pipeline)
                                   ↓ (virus détecté)
                               REJETÉ (arrêt pipeline)
```

---

### IngestionResponse (DTO vérifié dans les assertions)

Réponse JSON retournée par `POST /api/ingest`.

| Champ | Type | Exemple | Condition |
|-------|------|---------|-----------|
| `batchId` | `String` | `"batch-abc123"` | Status = `SUCCESS` |
| `status` | `enum` | `SUCCESS`, `DUPLICATE`, `REJECTED` | Toujours présent |
| `message` | `String` | `"Ingestion réussie"` | Optionnel |
| `documentsCount` | `int` | `1` | Status = `SUCCESS` |

---

### RetrievalResponse (DTO vérifié dans les assertions)

Réponse JSON retournée par l'endpoint de recherche / query.

| Champ | Type | Contrainte |
|-------|------|-----------|
| `passages` | `List<Passage>` | `size ≥ 3` pour une requête pertinente |
| `passages[].content` | `String` | Non vide |
| `passages[].score` | `double` | `0.0 ≤ score ≤ 1.0` |
| `passages[].rank` | `int` | Croissant (rank 1 = meilleur score) |
| `durationMs` | `long` | `≤ 3000` (SC-003) |

**Invariant** : `passages[i].score ≥ passages[i+1].score` (ordre décroissant de pertinence).

---

### StreamingEvent (événements SSE vérifiés par Awaitility)

Événements Server-Sent Events reçus lors d'un appel à `POST /api/stream`.

| Type | Champ `data` | Signification |
|------|-------------|---------------|
| `TOKEN` | `{"type":"token","content":"..."}` | Token de texte généré |
| `DONE` | `{"type":"done"}` | Fin de flux |
| `ERROR` | `{"type":"error","message":"..."}` | Erreur de génération |

**Invariant** : Au moins un événement `TOKEN` précède l'événement `DONE` (SC-004).

---

### RateLimitResponse (réponse HTTP 429 vérifiée)

Headers et corps retournés par `RateLimitInterceptor` lors d'un dépassement de quota.

| Élément | Valeur attendue |
|---------|----------------|
| HTTP Status | `429 Too Many Requests` |
| Header `Retry-After` | Secondes jusqu'au reset (entier positif) |
| Header `X-RateLimit-Remaining` | `"0"` |
| Header `X-RateLimit-Reset` | Epoch Unix (long) |
| Body `.error` | `"Too Many Requests"` |
| Body `.retryAfterSeconds` | Identique au header `Retry-After` |

---

## Fixtures de documents

| Fichier | Taille cible | Contenu | Requête de test |
|---------|-------------|---------|-----------------|
| `sample.pdf` | < 50 KB | 2 pages : "NexRAG est un système RAG multimodal…" | `"Qu'est-ce que NexRAG ?"` |
| `sample.docx` | < 30 KB | 1 page : même contenu adapté | `"Quels formats supporte NexRAG ?"` |
| `sample.xlsx` | < 20 KB | 1 feuille "Données", 5 lignes | `"Quelles technologies sont listées ?"` |
| `sample.jpg` | < 200 KB | Image JPEG 100×100 px (placeholder) | — (retrieval non testé pour images) |
| `sample.txt` | < 5 KB | Texte brut UTF-8, même contenu | `"Qu'est-ce que NexRAG ?"` |

**Convention de nommage** : les fichiers de fixture sont immuables — ne pas les modifier après création. Si un nouveau format est ajouté, ajouter un nouveau fichier sans renommer les existants.

---

## Mapping Spec → Classes de test

| FR | User Story | Classe IntegrationSpec | Méthodes @Test clés |
|----|-----------|----------------------|---------------------|
| FR-001 | US-1 | `IngestionPipelineIntegrationSpec` | `devraitIngererpdfEnMoinsDe10Secondes`, `devraitIngererdocx…`, `devraitIngererxlsx…`, `devraitIngererimage…`, `devraitIngérertexte…` |
| FR-002 | US-1 | `IngestionPipelineIntegrationSpec` | `devraitRetournerDuplicatePourMemeDocument`, `devraitGererIngestionConcurrenteAtomiquement` |
| FR-003 | US-2 | `RetrievalPipelineIntegrationSpec` | `devraitRetournerAuMoins3PassagesClassesEnMoinsDe3Secondes` |
| FR-004 | US-3 | `StreamingPipelineIntegrationSpec` | `devraitEmettreTokensAvantSignalDeFin` |
| FR-005 | US-2 | `RetrievalPipelineIntegrationSpec` | `devraitPreserverHistoriqueConversation` |
| FR-006 | US-4 | `RateLimitIntegrationSpec` | `devraitRetourner429AuDela10RequetesMinute`, `devraitEtreFailOpenSiRedisIndisponible` |
| FR-007 | US-1,3,5 | `AbstractIntegrationSpec` | (setup WireMock stubs) |
| FR-008 | US-1 | `IngestionPipelineIntegrationSpec` | `devraitRejeterFichierEicarAvecErreurVirus`, `devraitAccepterFichierSain` |
| FR-009 | US-5 | `AbstractIntegrationSpec` | (teardown `@BeforeEach`) |
| FR-010 | US-5 | `FullRagPipelineIntegrationSpec` | `devraitCompleterFluxCompletIngestionVersStreaming` |
