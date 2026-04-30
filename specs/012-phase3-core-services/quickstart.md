# Quickstart: Run Phase 3 Core Services Tests

**Branch**: `012-phase3-core-services`
**Date**: 2026-04-30

---

## Prerequisites

```bash
# From agentic-rag-ui/ directory
npm install      # ensure @ngneat/spectator ^22.1.0 is installed
```

Apply the two production code changes documented in `data-model.md` (§10) before writing tests:
1. `notification.service.ts` — Toast ID format (`Date.now() + '-' + counter`)
2. `voice.service.ts` — no-op guard in `startRecording()`

---

## Run Phase 3 tests only

```bash
# From agentic-rag-ui/
ng test --include="**/core/services/**"
```

## Run with coverage

```bash
ng test --include="**/core/services/**" --code-coverage
```

Coverage report lands in `coverage/agentic-rag-ui/` — open `index.html` and verify all six
service files meet: statements ≥ 80 %, branches ≥ 75 %, functions ≥ 85 %.

## Run all tests

```bash
npm test
# or
ng test
```

---

## Spec file locations (co-located — Angular CLI convention)

```
agentic-rag-ui/src/app/core/services/
├── crud-api.service.ts                  ← production (DO NOT modify)
├── crud-api.service.spec.ts             ← CREATE: 8 test cases
├── ingestion-api.service.ts             ← production (DO NOT modify)
├── ingestion-api.service.spec.ts        ← CREATE: 7 test cases
├── streaming-api.service.ts             ← production (DO NOT modify)
├── streaming-api.service.spec.ts        ← CREATE: 7 test cases
├── websocket-progress.service.ts        ← production (DO NOT modify)
├── websocket-progress.service.spec.ts   ← CREATE: 6 test cases
├── notification.service.ts              ← production (update ID format)
├── notification.service.spec.ts         ← CREATE: 5 test cases
├── voice.service.ts                     ← production (add no-op guard)
└── voice.service.spec.ts                ← CREATE: 6 test cases
```

---

## Run a single spec in isolation

```bash
ng test --include="**/crud-api.service.spec.ts"
ng test --include="**/ingestion-api.service.spec.ts"
ng test --include="**/streaming-api.service.spec.ts"
ng test --include="**/websocket-progress.service.spec.ts"
ng test --include="**/notification.service.spec.ts"
ng test --include="**/voice.service.spec.ts"
```

All six commands MUST pass independently (Constitution Principle VI — test isolation).

---

## Expected output (green run)

```
PASS  src/app/core/services/crud-api.service.spec.ts
  CrudApiService
    ✓ doit appeler DELETE /api/v1/crud/file/:id?type=text pour deleteFile
    ✓ doit appeler DELETE /api/v1/crud/batch/:id/files pour deleteBatch
    ✓ doit appeler DELETE avec body pour deleteTextBatch
    ✓ doit appeler DELETE avec body pour deleteImageBatch
    ✓ doit appeler DELETE /api/v1/crud/files/all?confirmation=... pour deleteAllFiles
    ✓ doit envoyer un FormData en POST pour checkDuplicate
    ✓ doit appeler GET /api/v1/crud/batch/:id/info pour getBatchInfo
    ✓ doit appeler GET /api/v1/crud/stats/system pour getSystemStats

PASS  src/app/core/services/ingestion-api.service.spec.ts
  IngestionApiService
    ✓ doit envoyer un FormData sans batchId quand uploadFile est appelé sans batchId
    ✓ doit inclure batchId dans le FormData quand fourni à uploadFile
    ✓ doit appeler POST /api/v1/ingestion/upload/async pour uploadFileAsync
    ✓ doit envoyer plusieurs fichiers sous la clé "files" pour uploadBatchAsync
    ✓ doit propager l'erreur observable quand le serveur répond 422
    ✓ doit appeler GET /api/v1/ingestion/status/:id pour getBatchStatus
    ✓ doit appeler DELETE /api/v1/ingestion/rollback/:id pour rollbackBatch

PASS  src/app/core/services/streaming-api.service.spec.ts
  StreamingApiService
    ✓ doit émettre { type: "connected" } quand l'EventSource reçoit l'événement connected
    ✓ doit supprimer les balises <cite> du texte avant d'émettre l'événement token
    ✓ doit émettre { type: "complete" } et compléter le stream quand complete est reçu
    ✓ doit émettre { type: "error" } et errorer quand l'événement error nommé est reçu
    ✓ doit fermer l'EventSource quand l'abonné se désabonne avant complete
    ✓ doit appeler POST /api/v1/assistant/stream/:id/cancel pour cancelStream
    ✓ doit errorer l'observable immédiatement quand EventSource.onerror se déclenche

PASS  src/app/core/services/websocket-progress.service.spec.ts
  WebSocketProgressService
    ✓ doit résoudre la Promise quand onConnect se déclenche
    ✓ doit résoudre immédiatement sans créer un nouveau client si déjà connecté
    ✓ doit émettre le progress et propager via progress$ quand un message STOMP arrive
    ✓ doit compléter l'observable quand le stage est COMPLETED
    ✓ doit désabonner toutes les souscriptions et désactiver le client lors du disconnect
    ✓ doit tenter le fallback SockJS quand onWebSocketError se déclenche

PASS  src/app/core/services/notification.service.spec.ts
  NotificationService
    ✓ doit émettre un toast de type "success" avec le bon titre et message
    ✓ doit émettre un toast de type "error" avec la durée personnalisée
    ✓ doit émettre un toast de type "warning"
    ✓ doit émettre un toast de type "info"
    ✓ doit générer des IDs uniques pour deux toasts créés dans le même milliseconde

PASS  src/app/core/services/voice.service.spec.ts
  VoiceService
    ✓ doit retourner true quand getUserMedia est disponible
    ✓ doit retourner false quand getUserMedia est indisponible
    ✓ doit démarrer l'enregistrement et émettre true via getRecordingState
    ✓ doit résoudre avec un Blob audio et émettre false via getRecordingState à l'arrêt
    ✓ doit envoyer le FormData avec audio et language à POST /api/v1/voice/transcribe
    ✓ doit émettre l'erreur via getErrors() quand getUserMedia rejette

Test Suites: 6 passed, 6 total
Tests:       39 passed, 39 total
```

---

## Implementation order (recommended)

1. `notification.service.spec.ts` — no mocks, pure observable assertions; ideal warmup
2. `crud-api.service.spec.ts` — pure HTTP contract; straightforward `createHttpFactory`
3. `ingestion-api.service.spec.ts` — FormData assertions + error propagation
4. `streaming-api.service.spec.ts` — `MockEventSource` global stub; most complex setup
5. `websocket-progress.service.spec.ts` — STOMP mock; second most complex
6. `voice.service.spec.ts` — `MockMediaRecorder` + `getUserMedia` stubs
