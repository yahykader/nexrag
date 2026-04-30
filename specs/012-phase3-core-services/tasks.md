---
description: "Task list for Phase 3 — Core Services Test Suite"
---

# Tasks: Phase 3 — Core Services Test Suite

**Input**: Design documents from `specs/012-phase3-core-services/`
**Prerequisites**: plan.md ✅ spec.md ✅ research.md ✅ data-model.md ✅ quickstart.md ✅

**Tests**: This feature IS the test suite — all tasks produce test code and two minimal
production code fixes. Every spec file MUST be written against the existing production code.
Production code is only modified where required by the clarifications (Toast ID, VoiceService guard).

**Organization**: Tasks are grouped by user story. US1, US2, US3 (all P1) can be implemented
in parallel after foundational changes are applied. US4 and US5 (P2) can be implemented in
parallel after P1 stories pass. US6 (P3) is implemented last.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no shared dependencies)
- **[Story]**: Which user story this task belongs to (US1–US6)
- Exact file paths included in every task description

## Path Conventions

All spec files are co-located with production sources:

```
agentic-rag-ui/src/app/core/services/
├── crud-api.service.ts                  (existing — DO NOT modify)
├── crud-api.service.spec.ts             ← CREATE in Phase 3
├── ingestion-api.service.ts             (existing — DO NOT modify)
├── ingestion-api.service.spec.ts        ← CREATE in Phase 4
├── notification.service.ts              ← UPDATE in Phase 2
├── notification.service.spec.ts         ← CREATE in Phase 6
├── streaming-api.service.ts             (existing — DO NOT modify)
├── streaming-api.service.spec.ts        ← CREATE in Phase 5
├── voice.service.ts                     ← UPDATE in Phase 2
├── voice.service.spec.ts                ← CREATE in Phase 8
├── websocket-progress.service.ts        (existing — DO NOT modify)
└── websocket-progress.service.spec.ts   ← CREATE in Phase 7
```

---

## Phase 1: Setup — Verify Test Infrastructure

**Purpose**: Confirm the Angular + Vitest + Spectator pipeline is operational and all
required imports resolve before writing any spec files.

- [x] T001 Run `ng test --include="src/app/app.spec.ts"` from `agentic-rag-ui/` and confirm the test runner exits with zero failures — validates the `@angular/build:unit-test` + Vitest globals pipeline

- [x] T002 [P] Confirm `createHttpFactory` resolves: add a temporary one-line import `import { createHttpFactory } from '@ngneat/spectator/vitest'` in a scratch file, run `npx tsc --noEmit` from `agentic-rag-ui/`, then remove the scratch file — confirms the Vitest Spectator entry point is available

**Checkpoint**: Test pipeline confirmed operational. Proceed to Phase 2.

---

## Phase 2: Foundational — Production Code Fixes (pre-conditions for ALL test assertions)

**Purpose**: Apply the two minimal production code changes identified in the clarification
phase. These MUST be complete before writing any spec file — they fix real behaviour gaps,
not testability hacks.

**⚠️ CRITICAL**: No user story spec work can begin until T003 and T004 are complete and green.

- [x] T003 In `agentic-rag-ui/src/app/core/services/notification.service.ts`: add `let _toastCounter = 0;` at module level (after imports, outside the class); update the `show()` method to use `id: \`${Date.now()}-${++_toastCounter}\`` instead of `id: Date.now().toString()` — run `npx tsc --noEmit` from `agentic-rag-ui/` to confirm no type errors

- [x] T004 In `agentic-rag-ui/src/app/core/services/voice.service.ts`: add `if (this.isRecording()) return;` as the first line of the `startRecording()` async method body (before the `try` block) — run `npx tsc --noEmit` to confirm no type errors

**Checkpoint**: Both production fixes applied and type-checked. All six spec file phases can now proceed.

---

## Phase 3: User Story 1 — Document CRUD HTTP Contract (Priority: P1) 🎯 MVP

**Goal**: Verify that every `CrudApiService` method calls the exact HTTP verb, URL path,
query parameters, and body documented in the backend API contract.

**Independent Test**: Run `ng test --include="**/crud-api.service.spec.ts"` — if 8 tests
pass, this story is fully verified in isolation.

**Spec file**: `agentic-rag-ui/src/app/core/services/crud-api.service.spec.ts`

### Test harness

- [x] T005 [US1] Create `agentic-rag-ui/src/app/core/services/crud-api.service.spec.ts` with the test harness: import `createHttpFactory, SpectatorHttp, HttpMethod` from `@ngneat/spectator/vitest`; import `CrudApiService` from `./crud-api.service`; declare `spectator: SpectatorHttp<CrudApiService>` and `createHttp = createHttpFactory(CrudApiService)`; add `beforeEach(() => spectator = createHttp())`; wrap in `describe('CrudApiService', () => { ... })`

### Test cases — US1 Acceptance Scenarios

- [x] T006 [US1] In `crud-api.service.spec.ts`, write `it('doit appeler DELETE /api/v1/crud/file/:id?type=text pour deleteFile')`: call `spectator.service.deleteFile('emb-1', 'text').subscribe()`; use `spectator.expectOne('/api/v1/crud/file/emb-1?type=text', HttpMethod.DELETE).flush({ success: true, deletedCount: 1, message: 'OK' })`; assert no error thrown

- [x] T007 [US1] In `crud-api.service.spec.ts`, write `it('doit appeler DELETE /api/v1/crud/batch/:id/files pour deleteBatch')`: call `spectator.service.deleteBatch('batch-42').subscribe()`; expect `DELETE /api/v1/crud/batch/batch-42/files`; flush success response

- [x] T008 [US1] In `crud-api.service.spec.ts`, write `it('doit appeler DELETE avec body [e1,e2] pour deleteTextBatch')`: call `spectator.service.deleteTextBatch(['e1','e2']).subscribe()`; capture with `spectator.expectOne('/api/v1/crud/files/text/batch', HttpMethod.DELETE)`; assert `req.request.body` deep-equals `['e1','e2']`; flush success response

- [x] T009 [US1] In `crud-api.service.spec.ts`, write `it('doit appeler DELETE avec body [e1] pour deleteImageBatch')`: same pattern as T008 but for `/api/v1/crud/files/image/batch` with body `['e1']`

- [x] T010 [US1] In `crud-api.service.spec.ts`, write `it('doit appeler DELETE /api/v1/crud/files/all?confirmation=CONFIRM_DELETE_ALL pour deleteAllFiles')`: call `spectator.service.deleteAllFiles('CONFIRM_DELETE_ALL').subscribe()`; expect `DELETE /api/v1/crud/files/all?confirmation=CONFIRM_DELETE_ALL`; flush success response

- [x] T011 [US1] In `crud-api.service.spec.ts`, write `it('doit envoyer un FormData en POST /api/v1/crud/check-duplicate pour checkDuplicate')`: create `new File([''], 'test.pdf', { type: 'application/pdf' })`; call `spectator.service.checkDuplicate(file).subscribe()`; expect `POST /api/v1/crud/check-duplicate`; assert `req.request.body instanceof FormData`; flush `{ isDuplicate: false, filename: 'test.pdf', message: 'OK' }`

- [x] T012 [US1] In `crud-api.service.spec.ts`, write `it('doit appeler GET /api/v1/crud/batch/:id/info pour getBatchInfo')`: call `spectator.service.getBatchInfo('batch-1').subscribe()`; expect `GET /api/v1/crud/batch/batch-1/info`; flush `{ found: true, batchId: 'batch-1', textEmbeddings: 2, imageEmbeddings: 0, totalEmbeddings: 2, message: 'OK' }`

- [x] T013 [US1] In `crud-api.service.spec.ts`, write `it('doit appeler GET /api/v1/crud/stats/system pour getSystemStats')`: call `spectator.service.getSystemStats().subscribe()`; expect `GET /api/v1/crud/stats/system`; flush `{}`

### Validation

- [x] T014 [US1] Run `ng test --include="**/crud-api.service.spec.ts"` from `agentic-rag-ui/` — confirm all 8 tests pass (green); fix any failing test before proceeding

**Checkpoint**: US1 complete. `crud-api.service.spec.ts` has 8 passing tests. Independently deliverable.

---

## Phase 4: User Story 2 — File Ingestion Upload Contract (Priority: P1)

**Goal**: Verify that `IngestionApiService` sends correctly structured `FormData` payloads,
conditionally appends `batchId`, and propagates HTTP 422 errors as observable errors.

**Independent Test**: Run `ng test --include="**/ingestion-api.service.spec.ts"` — if 7 tests pass.

**Spec file**: `agentic-rag-ui/src/app/core/services/ingestion-api.service.spec.ts`

### Test harness

- [x] T015 [P] [US2] Create `agentic-rag-ui/src/app/core/services/ingestion-api.service.spec.ts` with the test harness: import `createHttpFactory, SpectatorHttp, HttpMethod` from `@ngneat/spectator/vitest`; import `IngestionApiService` from `./ingestion-api.service`; declare `spectator: SpectatorHttp<IngestionApiService>` and `createHttp = createHttpFactory(IngestionApiService)`; add `beforeEach(() => spectator = createHttp())`; wrap in `describe('IngestionApiService', () => { ... })`

### Test cases — US2 Acceptance Scenarios

- [x] T016 [US2] In `ingestion-api.service.spec.ts`, write `it('doit envoyer un FormData sans batchId quand uploadFile est appelé sans batchId')`: create `new File(['content'], 'doc.pdf')`; call `spectator.service.uploadFile(file).subscribe()`; expect `POST /api/v1/ingestion/upload`; assert `(req.request.body as FormData).has('batchId') === false`; flush a valid `IngestionResponse`

- [x] T017 [US2] In `ingestion-api.service.spec.ts`, write `it('doit inclure batchId dans le FormData quand fourni à uploadFile')`: call `spectator.service.uploadFile(file, 'batch-7').subscribe()`; expect `POST /api/v1/ingestion/upload`; assert `(req.request.body as FormData).get('batchId') === 'batch-7'`; flush response

- [x] T018 [US2] In `ingestion-api.service.spec.ts`, write `it('doit appeler POST /api/v1/ingestion/upload/async pour uploadFileAsync')`: call `spectator.service.uploadFileAsync(file).subscribe()`; expect `POST /api/v1/ingestion/upload/async`; flush `{ accepted: true, batchId: 'b1', filename: 'doc.pdf', message: 'OK', statusUrl: '/status/b1', duplicate: false }`

- [x] T019 [US2] In `ingestion-api.service.spec.ts`, write `it('doit envoyer plusieurs fichiers sous la clé "files" pour uploadBatchAsync')`: create `[f1, f2]` as two `File` objects; call `spectator.service.uploadBatchAsync([f1, f2]).subscribe()`; expect `POST /api/v1/ingestion/upload/batch/async`; assert `(req.request.body as FormData).getAll('files').length === 2`; flush response

- [x] T020 [US2] In `ingestion-api.service.spec.ts`, write `it('doit propager l\'erreur observable quand le serveur répond 422')`: declare `let caught = false`; call `spectator.service.uploadFile(file).subscribe({ error: () => { caught = true; } })`; expect `POST /api/v1/ingestion/upload`; flush `{ message: 'Unprocessable' }` with `{ status: 422, statusText: 'Unprocessable Entity' }`; assert `caught === true`

- [x] T021 [US2] In `ingestion-api.service.spec.ts`, write `it('doit appeler GET /api/v1/ingestion/status/:id pour getBatchStatus')`: call `spectator.service.getBatchStatus('batch-1').subscribe()`; expect `GET /api/v1/ingestion/status/batch-1`; flush a valid `StatusResponse`

- [x] T022 [US2] In `ingestion-api.service.spec.ts`, write `it('doit appeler DELETE /api/v1/ingestion/rollback/:id pour rollbackBatch')`: call `spectator.service.rollbackBatch('batch-5').subscribe()`; expect `DELETE /api/v1/ingestion/rollback/batch-5`; flush `{}`

### Validation

- [x] T023 [US2] Run `ng test --include="**/ingestion-api.service.spec.ts"` from `agentic-rag-ui/` — confirm all 7 tests pass; fix any failing test before proceeding

**Checkpoint**: US2 complete. `ingestion-api.service.spec.ts` has 7 passing tests.

---

## Phase 5: User Story 3 — SSE Streaming Lifecycle (Priority: P1)

**Goal**: Verify the full SSE observable contract of `StreamingApiService.stream()` including
citation stripping, stream completion, named error event, native `onerror`, and teardown close.

**Independent Test**: Run `ng test --include="**/streaming-api.service.spec.ts"` — if 7 tests pass.

**Spec file**: `agentic-rag-ui/src/app/core/services/streaming-api.service.spec.ts`

### MockEventSource class + test harness

- [x] T024 [P] [US3] Create `agentic-rag-ui/src/app/core/services/streaming-api.service.spec.ts` with: (1) `MockEventSource` class defined at file scope with `static lastInstance`, `private handlers`, `onerror` field, `readyState = 1`, constructor storing `this` in `lastInstance`, `addEventListener(type, handler)`, `emit(type, data)` helper that fires `new MessageEvent(type, { data: JSON.stringify(data) })`, and `close()` setting `readyState = 2`; (2) test harness: import `createServiceFactory, SpectatorService` from `@ngneat/spectator/vitest`; import `provideHttpClient` from `@angular/common/http`; import `provideHttpClientTesting, HttpTestingController` from `@angular/common/http/testing`; import `StreamingApiService` from `./streaming-api.service`; wire `createServiceFactory({ service: StreamingApiService, providers: [provideHttpClient(), provideHttpClientTesting()] })`; `beforeEach`: stub global `vi.stubGlobal('EventSource', MockEventSource)` then call `createService()`; `afterEach`: `vi.unstubAllGlobals()` + `controller.verify()`

### Test cases — US3 Acceptance Scenarios

- [x] T025 [US3] In `streaming-api.service.spec.ts`, write `it('doit émettre { type: "connected" } quand l\'EventSource reçoit l\'événement connected')`: subscribe to `spectator.service.stream({ query: 'hello' })`; call `MockEventSource.lastInstance!.emit('connected', { sessionId: 's1', conversationId: 'c1' })`; assert emitted event equals `{ type: 'connected', sessionId: 's1', conversationId: 'c1' }`

- [x] T026 [US3] In `streaming-api.service.spec.ts`, write `it('doit supprimer les balises <cite> du texte avant d\'émettre l\'événement token')`: subscribe; emit `token` event with `{ text: 'Hello <cite index="1">source</cite> world', count: 0 }`; assert emitted event has `text === 'Hello  world'` (citation removed)

- [x] T027 [US3] In `streaming-api.service.spec.ts`, write `it('doit émettre { type: "complete" } et compléter l\'observable quand complete est reçu')`: declare `let completed = false`; subscribe with `{ next: e => events.push(e), complete: () => { completed = true; } }`; emit `complete` event with `{ response: { text: 'final' } }`; assert `events[0].type === 'complete'` and `completed === true`

- [x] T028 [US3] In `streaming-api.service.spec.ts`, write `it('doit émettre { type: "error" } puis errorer quand l\'événement error nommé est reçu')`: spy `vi.spyOn(console, 'error').mockImplementation(() => {})`; subscribe; emit `error` event with `{ message: 'LLM error', code: 'E500' }`; assert typed error token emitted before observable errors; assert `errored === true`

- [x] T029 [US3] In `streaming-api.service.spec.ts`, write `it('doit fermer l\'EventSource quand l\'abonné se désabonne avant complete')`: subscribe then immediately unsubscribe via `sub.unsubscribe()`; assert `MockEventSource.lastInstance!.readyState === 2` (EventSource.close() was called)

- [x] T030 [US3] In `streaming-api.service.spec.ts`, write `it('doit appeler POST /api/v1/assistant/stream/:id/cancel pour cancelStream')`: call `spectator.service.cancelStream('session-1').subscribe()`; expect `POST /api/v1/assistant/stream/session-1/cancel`; flush `{}`

- [x] T031 [US3] In `streaming-api.service.spec.ts`, write `it('doit errorer l\'observable immédiatement quand EventSource.onerror se déclenche')`: spy `vi.spyOn(console, 'error').mockImplementation(() => {})`; declare `let errored = false`; subscribe with `{ error: () => { errored = true; } }`; set `MockEventSource.lastInstance!.readyState = 1` (OPEN); trigger `MockEventSource.lastInstance!.onerror!(new Event('error'))`; assert `errored === true` and `MockEventSource.lastInstance!.readyState === 2`

### Validation

- [x] T032 [US3] Run `ng test --include="**/streaming-api.service.spec.ts"` from `agentic-rag-ui/` — confirm all 7 tests pass; fix any failing test before proceeding

**Checkpoint**: US3 complete. `streaming-api.service.spec.ts` has 7 passing tests.
US1, US2, US3 (all P1) now complete — core HTTP and SSE contracts fully verified.

---

## Phase 6: User Story 5 — Notification Toast Bus (Priority: P2)

**Goal**: Verify `NotificationService` emits well-formed `Toast` objects with the correct type,
title, message, duration, and deterministic unique IDs using the counter-based format.

**Independent Test**: Run `ng test --include="**/notification.service.spec.ts"` — if 5 tests pass.

**Spec file**: `agentic-rag-ui/src/app/core/services/notification.service.spec.ts`

### Test harness

- [x] T033 [P] [US5] Create `agentic-rag-ui/src/app/core/services/notification.service.spec.ts` with the test harness: import `createServiceFactory, SpectatorService` from `@ngneat/spectator/vitest`; import `NotificationService, Toast` from `./notification.service`; declare `spectator: SpectatorService<NotificationService>` and `createService = createServiceFactory(NotificationService)`; add `beforeEach(() => spectator = createService())`; wrap in `describe('NotificationService', () => { ... })`

### Test cases — US5 Acceptance Scenarios

- [x] T034 [US5] In `notification.service.spec.ts`, write `it('doit émettre un toast de type "success" avec titre, message et durée par défaut 5000')`: subscribe to `spectator.service.toasts$`; call `spectator.service.success('Titre', 'Message')`; assert emitted toast matches `{ type: 'success', title: 'Titre', message: 'Message', duration: 5000 }`

- [x] T035 [US5] In `notification.service.spec.ts`, write `it('doit émettre un toast de type "error" avec la durée personnalisée 3000')`: call `spectator.service.error('Err', 'Details', 3000)`; assert `{ type: 'error', duration: 3000 }`

- [x] T036 [US5] In `notification.service.spec.ts`, write `it('doit émettre un toast de type "warning"')`: call `spectator.service.warning('Warn', 'Msg')`; assert `toast.type === 'warning'`

- [x] T037 [US5] In `notification.service.spec.ts`, write `it('doit émettre un toast de type "info"')`: call `spectator.service.info('Info', 'Msg')`; assert `toast.type === 'info'`

- [x] T038 [US5] In `notification.service.spec.ts`, write `it('doit générer des IDs uniques pour deux toasts créés dans le même milliseconde')`: collect two IDs by calling `success()` twice synchronously; assert `ids[0] !== ids[1]`; assert both IDs match `/^\d+-\d+$/` (timestamp-counter format)

### Validation

- [x] T039 [US5] Run `ng test --include="**/notification.service.spec.ts"` from `agentic-rag-ui/` — confirm all 5 tests pass; fix any failing test before proceeding

**Checkpoint**: US5 complete. `notification.service.spec.ts` has 5 passing tests.

---

## Phase 7: User Story 4 — WebSocket Progress Tracking (Priority: P2)

**Goal**: Verify `WebSocketProgressService` STOMP connection lifecycle, batch subscription
observables, `COMPLETED`/`ERROR` stage completion semantics, and SockJS fallback trigger.

**Independent Test**: Run `ng test --include="**/websocket-progress.service.spec.ts"` — if 6 tests pass.

**Spec file**: `agentic-rag-ui/src/app/core/services/websocket-progress.service.spec.ts`

### STOMP Client mock + test harness

- [x] T040 [P] [US4] Create `agentic-rag-ui/src/app/core/services/websocket-progress.service.spec.ts` with: (1) `mockSubscription = { unsubscribe: vi.fn() }` and `mockStompClient` object with fields `connected: false`, `activate: vi.fn()`, `deactivate: vi.fn()`, `subscribe: vi.fn().mockReturnValue(mockSubscription)`, `onConnect: null`, `onStompError: null`, `onWebSocketError: null`; (2) module-level `vi.mock('@stomp/stompjs', () => ({ Client: vi.fn().mockImplementation(() => mockStompClient) }))`; (3) test harness: import `createServiceFactory, SpectatorService` from `@ngneat/spectator/vitest`; import `WebSocketProgressService, UploadProgress` from `./websocket-progress.service`; `beforeEach`: `vi.clearAllMocks()`, reset `mockStompClient.connected = false`, call `createService()`; wrap in `describe('WebSocketProgressService', () => { ... })`

### Test cases — US4 Acceptance Scenarios

- [x] T041 [US4] In `websocket-progress.service.spec.ts`, write `it('doit résoudre la Promise quand onConnect se déclenche')`: call `const p = spectator.service.connect()`; immediately trigger `mockStompClient.onConnect?.({ command: 'CONNECTED', headers: {}, body: '' })`; `await expect(p).resolves.toBeUndefined()`

- [x] T042 [US4] In `websocket-progress.service.spec.ts`, write `it('doit résoudre immédiatement sans créer un nouveau client si déjà connecté')`: set `mockStompClient.connected = true`; call `await spectator.service.connect()`; assert `mockStompClient.activate` was NOT called (no new client activated)

- [x] T043 [US4] In `websocket-progress.service.spec.ts`, write `it('doit émettre le progress et propager via progress$ quand un message STOMP arrive')`: set `mockStompClient.connected = true`; declare `const emitted: UploadProgress[] = []`; subscribe to `spectator.service.subscribeToProgress('b1')`; capture `const msgCb = mockStompClient.subscribe.mock.calls[0][1]`; call `msgCb({ body: JSON.stringify({ batchId: 'b1', filename: 'doc.pdf', stage: 'CHUNKING', progressPercentage: 50, message: 'Chunking...' }) })`; assert `emitted[0].progressPercentage === 50`

- [x] T044 [US4] In `websocket-progress.service.spec.ts`, write `it('doit compléter l\'observable quand le stage est COMPLETED')`: set `mockStompClient.connected = true`; subscribe with `{ complete: () => { completed = true; } }`; capture message callback; invoke it with `stage: 'COMPLETED'`; assert `completed === true`

- [x] T045 [US4] In `websocket-progress.service.spec.ts`, write `it('doit désabonner toutes les souscriptions et désactiver le client lors du disconnect')`: manually populate a subscription by calling `subscribeToProgress('b1')` on a connected service; call `spectator.service.disconnect()`; assert `mockSubscription.unsubscribe` was called and `mockStompClient.deactivate` was called

- [x] T046 [US4] In `websocket-progress.service.spec.ts`, write `it('doit tenter le fallback SockJS quand onWebSocketError se déclenche')`: call `spectator.service.connect()` (do NOT resolve onConnect first); trigger `mockStompClient.onWebSocketError?.({ type: 'error' })`; assert `mockStompClient.activate` was called twice (once native, once SockJS fallback attempts to re-activate)

### Validation

- [x] T047 [US4] Run `ng test --include="**/websocket-progress.service.spec.ts"` from `agentic-rag-ui/` — confirm all 6 tests pass; fix any failing test before proceeding

**Checkpoint**: US4 and US5 (both P2) now complete. WebSocket + notification layer fully verified.

---

## Phase 8: User Story 6 — Voice Recording & Whisper Transcription (Priority: P3)

**Goal**: Verify `VoiceService` recording lifecycle via mocked `MediaRecorder` and `getUserMedia`,
and the Whisper transcription POST contract. Verify the no-op guard and error propagation.

**Independent Test**: Run `ng test --include="**/voice.service.spec.ts"` — if 6 tests pass.

**Spec file**: `agentic-rag-ui/src/app/core/services/voice.service.spec.ts`

### MockMediaRecorder class + test harness

- [x] T048 [US6] Create `agentic-rag-ui/src/app/core/services/voice.service.spec.ts` with: (1) `mockStream = { getTracks: () => [{ stop: vi.fn() }] } as unknown as MediaStream`; (2) `MockMediaRecorder` class with `static lastInstance`, `state: RecordingState = 'inactive'`, `stream`, `ondataavailable`, `onstop`, `onerror` fields, `constructor(stream)` storing `this` in `lastInstance`, `start(_?)` setting `state = 'recording'`, `stop()` setting `state = 'inactive'` then calling `this.onstop?.()`; (3) test harness: import `createHttpFactory, SpectatorHttp, HttpMethod` from `@ngneat/spectator/vitest`; import `VoiceService` from `./voice.service`; `beforeEach`: `vi.stubGlobal('navigator', { mediaDevices: { getUserMedia: vi.fn().mockResolvedValue(mockStream) } })` and `vi.stubGlobal('MediaRecorder', MockMediaRecorder)` then call `createHttp()`; `afterEach`: `vi.unstubAllGlobals()`; wrap in `describe('VoiceService', () => { ... })`

### Test cases — US6 Acceptance Scenarios

- [x] T049 [US6] In `voice.service.spec.ts`, write `it('doit retourner true quand getUserMedia est disponible')`: assert `spectator.service.isRecordingSupported() === true`

- [x] T050 [US6] In `voice.service.spec.ts`, write `it('doit retourner false quand getUserMedia est indisponible')`: `vi.stubGlobal('navigator', { mediaDevices: undefined })`; assert `spectator.service.isRecordingSupported() === false`

- [x] T051 [US6] In `voice.service.spec.ts`, write `it('doit démarrer l\'enregistrement et émettre true via getRecordingState')`: declare `const states: boolean[] = []`; subscribe to `spectator.service.getRecordingState()`; `await spectator.service.startRecording()`; assert `states` contains `true` and `spectator.service.isRecording() === true`

- [x] T052 [US6] In `voice.service.spec.ts`, write `it('doit résoudre stopRecording avec un Blob audio/webm et émettre false via getRecordingState')`: `await spectator.service.startRecording()`; call `const blobPromise = spectator.service.stopRecording()`; trigger `MockMediaRecorder.lastInstance!.onstop!()`; `const blob = await blobPromise`; assert `blob instanceof Blob` and recording state emitted `false`

- [x] T053 [US6] In `voice.service.spec.ts`, write `it('doit envoyer FormData avec audio et language=en à POST /api/v1/voice/transcribe pour transcribeWithWhisper')`: create `const blob = new Blob(['audio'], { type: 'audio/webm' })`; call `spectator.service.transcribeWithWhisper(blob, 'en').subscribe()`; expect `POST /api/v1/voice/transcribe`; assert `(req.request.body as FormData).get('language') === 'en'` and `(req.request.body as FormData).get('audio') instanceof Blob`; flush `{ success: true, transcript: 'hello', language: 'en', audioSize: 5, filename: 'recording.webm' }`

- [x] T054 [US6] In `voice.service.spec.ts`, write `it('doit émettre le message d\'erreur via getErrors() quand getUserMedia rejette')`: override `navigator.mediaDevices.getUserMedia` to `vi.fn().mockRejectedValue(new Error('Permission denied'))`; subscribe to `spectator.service.getErrors()`; `await spectator.service.startRecording().catch(() => {})`; assert errors observable emitted `'Permission denied'`

### Validation

- [x] T055 [US6] Run `ng test --include="**/voice.service.spec.ts"` from `agentic-rag-ui/` — confirm all 6 tests pass; fix any failing test before proceeding

**Checkpoint**: US6 complete. All 6 user stories fully implemented.

---

## Phase 9: Polish & Coverage Validation

**Purpose**: Enforce the coverage gates from Constitution Principle IX and produce a clean
full-suite run.

- [x] T056 Run `ng test --code-coverage --include="**/core/services/**"` from `agentic-rag-ui/`; open `coverage/agentic-rag-ui/` and verify **each of the six service files** meets: Statements ≥ 80 %, Branches ≥ 75 %, Functions ≥ 85 % — document any uncovered branch as a known exception in `specs/012-phase3-core-services/checklists/requirements.md`

- [x] T057 [P] Run `ng test --include="**/core/services/**"` (no coverage flag) one final time to confirm a clean green pass — record the total test count (expected: 39) in the commit message

- [x] T058 [P] Update `specs/012-phase3-core-services/checklists/requirements.md` — mark all checklist items `[x]` and add final note: "Phase 3 complete — 39 tests passing, coverage gates met for all 6 service files"

- [x] T059 Run `npm test` from `agentic-rag-ui/` to confirm the full Phase 1–3 suite (models + interceptors + services) still passes without regressions (CI gate)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup; BLOCKS all user story phases
- **US1 Phase 3**: After Phase 2 — independent of US2/US3/US4/US5/US6
- **US2 Phase 4**: After Phase 2 — independent of all other stories (different file)
- **US3 Phase 5**: After Phase 2 — independent of all other stories (different file)
- **US5 Phase 6**: After Phase 2 — can run in parallel with US1/US2/US3
- **US4 Phase 7**: After Phase 2 — can run in parallel with US1/US2/US3/US5
- **US6 Phase 8**: After Phase 2 — can run in parallel with all P1/P2 stories
- **Polish (Phase 9)**: Depends on ALL user story phases being green

### User Story Dependencies

- **US1**: No dependency on other stories — only depends on Phase 2 (production changes)
- **US2**: No dependency on other stories — only depends on Phase 2
- **US3**: No dependency on other stories — only depends on Phase 2
- **US4**: No dependency on P1 stories — only depends on Phase 2 (STOMP mock independent)
- **US5**: No dependency on other stories — only depends on Phase 2 (notification fix)
- **US6**: No dependency on other stories — only depends on Phase 2 (voice guard)

### Within Each Phase

- Harness task (T005 / T015 / T024 / T033 / T040 / T048) MUST complete before individual `it()` tasks
- All `it()` tasks within the same spec file share the file — write sequentially in the order listed
- Validation task is last in each phase and required before marking the phase complete

### Parallel Opportunities

All six spec files can be worked simultaneously by different developers after T003+T004 complete:

```bash
# Developer A — Phase 3 (US1)
ng test --include="**/crud-api.service.spec.ts" --watch

# Developer B — Phase 4 (US2)
ng test --include="**/ingestion-api.service.spec.ts" --watch

# Developer C — Phase 5 (US3)
ng test --include="**/streaming-api.service.spec.ts" --watch

# Developer D — Phase 6 (US5)
ng test --include="**/notification.service.spec.ts" --watch

# Developer E — Phase 7 (US4)
ng test --include="**/websocket-progress.service.spec.ts" --watch

# Developer F — Phase 8 (US6)
ng test --include="**/voice.service.spec.ts" --watch
```

---

## Parallel Examples

### All harness tasks (can run in parallel after Phase 2):

```bash
# Simultaneously:
Task T005: Create crud-api.service.spec.ts harness
Task T015: Create ingestion-api.service.spec.ts harness
Task T024: Create streaming-api.service.spec.ts harness (includes MockEventSource)
Task T033: Create notification.service.spec.ts harness
Task T040: Create websocket-progress.service.spec.ts harness (includes STOMP mock)
Task T048: Create voice.service.spec.ts harness (includes MockMediaRecorder)
```

### All validation tasks (only after their respective harness + it() tasks complete):

```bash
# Run in parallel once their spec files are complete:
Task T014: ng test --include="**/crud-api.service.spec.ts"
Task T023: ng test --include="**/ingestion-api.service.spec.ts"
Task T032: ng test --include="**/streaming-api.service.spec.ts"
Task T039: ng test --include="**/notification.service.spec.ts"
Task T047: ng test --include="**/websocket-progress.service.spec.ts"
Task T055: ng test --include="**/voice.service.spec.ts"
```

---

## Implementation Strategy

### MVP First (US1 only — Phase 3)

1. Complete Phase 1: Setup (T001–T002)
2. Complete Phase 2: Foundational (T003–T004) — REQUIRED even for MVP
3. Complete Phase 3: US1 (T005–T014)
4. **STOP and VALIDATE**: `ng test --include="**/crud-api.service.spec.ts"` — 8 tests pass
5. CrudApiService HTTP contract is verified; other services remain untested

### Incremental Delivery

1. Setup + Foundational (T001–T004) → pre-conditions met
2. US1 / Phase 3 (T005–T014) → `crud-api.service.spec.ts` green ✅
3. US2 / Phase 4 (T015–T023) → `ingestion-api.service.spec.ts` green ✅
4. US3 / Phase 5 (T024–T032) → `streaming-api.service.spec.ts` green ✅
5. US5 / Phase 6 (T033–T039) → `notification.service.spec.ts` green ✅
6. US4 / Phase 7 (T040–T047) → `websocket-progress.service.spec.ts` green ✅
7. US6 / Phase 8 (T048–T055) → `voice.service.spec.ts` green ✅
8. Polish (T056–T059) → coverage gates confirmed ✅

### Parallel Team Strategy (6 developers)

After Setup + Foundational complete:
- **Developer A**: Phase 3 (T005 → T014) — `crud-api.service.spec.ts`
- **Developer B**: Phase 4 (T015 → T023) — `ingestion-api.service.spec.ts`
- **Developer C**: Phase 5 (T024 → T032) — `streaming-api.service.spec.ts`
- **Developer D**: Phase 6 (T033 → T039) — `notification.service.spec.ts`
- **Developer E**: Phase 7 (T040 → T047) — `websocket-progress.service.spec.ts`
- **Developer F**: Phase 8 (T048 → T055) — `voice.service.spec.ts`
- All: Phase 9 (T056–T059) — coverage + commit after merge

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks in the same phase
- US labels map directly to spec.md user stories: US1=P1, US2=P1, US3=P1, US4=P2, US5=P2, US6=P3
- ALL test bodies MUST be fully implemented — no `{ ... }` stubs in committed specs
- Production files already exist — verify tests compile and run (red before green)
- `vi.spyOn(console, 'error').mockImplementation(() => {})` REQUIRED in T028, T031, T054 error-path tests (SC-006 compliance)
- `vi.unstubAllGlobals()` in `afterEach` is mandatory for T024–T032 and T048–T055
- `vi.clearAllMocks()` in `beforeEach` is mandatory for T040–T047 (STOMP mock reuse)
- Commit format per Constitution: `test(phase-3): add <SpecFileName> — <brief description>`
