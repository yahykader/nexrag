# Feature Specification: Phase 3 — Core Services Test Suite

**Feature Branch**: `012-phase3-core-services`
**Created**: 2026-04-30
**Status**: Draft
**Source Plan**: `agentic-ui-test-plan-speckit.md` — Phase 3
**Scope**:
- `src/app/core/services/crud-api.service.ts`
- `src/app/core/services/ingestion-api.service.ts`
- `src/app/core/services/streaming-api.service.ts`
- `src/app/core/services/websocket-progress.service.ts`
- `src/app/core/services/notification.service.ts`
- `src/app/core/services/voice.service.ts`

---

## Context

The NexRAG frontend core services layer acts as the boundary between the Angular application
and all external communication channels: REST APIs over HTTP, Server-Sent Events (SSE) for
LLM token streaming, STOMP WebSocket for real-time ingestion progress, browser speech APIs
for voice input, and an in-memory notification bus for UI toasts.

This spec defines the observable contracts each service must honour — request shapes, response
mappings, error propagation, stream lifecycle events, and side-effects on the NgRx store — so
that developers can write precise, regression-proof unit tests without standing up a backend.

Note: `http-client.service.ts` and `websocket-api.service.ts` were deleted from the working
tree prior to this phase and are excluded from scope.

---

## Clarifications

### Session 2026-04-30

- Q: When `startRecording()` is called while the service is already recording, what should happen? → A: Ignore the call — `startRecording()` returns immediately (no-op) if `isRecording()` is `true`.
- Q: Should Phase 3 require a dedicated test for the native `EventSource.onerror` path (distinct from the named `error` event)? → A: Yes — add a scenario: when `onerror` fires, the observable must error immediately and `EventSource.close()` must be called.
- Q: How should Toast `id` uniqueness be defined for test purposes given `Date.now()` can collide in fast environments? → A: Mandate `Date.now() + '-' + monotonic counter` format — uniqueness is deterministic; a minimal service change is required.
- Q: How should SC-006 ("zero console errors") be scoped given services call `console.error` on deliberate error paths? → A: SC-006 applies only to unexpected console errors; tests that deliberately exercise error paths MUST spy on and suppress `console.error` via `vi.spyOn(console, 'error').mockImplementation(() => {})`.
- Q: Should Phase 3 tests assert that SSE and STOMP observable emissions are executed inside the Angular zone? → A: No — defer zone correctness verification to Phase 7 (chat component) and Phase 10 (ingestion component) integration tests; service unit tests assert only on emitted values.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Document CRUD HTTP Contract (Priority: P1)

A developer using `CrudApiService` expects each method to call the exact HTTP verb and URL
path documented in the backend API, send the correct parameters (path, query, body), and
forward the typed response to the caller. Tests must verify these contracts without a running
server.

**Why this priority**: Broken HTTP contracts cause silent failures in the NgRx effects layer.
If `deleteBatch` calls the wrong endpoint the entire document-management flow fails with no
clear error source. This is the highest-risk service because its contract is purely
structural — any typo breaks production.

**Independent Test**: Can be fully tested by creating a `SpectatorHttp<CrudApiService>` with
`HttpClientTestingModule`, calling each method, and asserting against the
`HttpTestingController` expectations. Delivers a verified HTTP contract for every CRUD
operation.

**Acceptance Scenarios**:

1. **Given** `deleteFile('emb-1', 'text')` is called, **When** the HTTP request is flushed,
   **Then** a `DELETE /api/v1/crud/file/emb-1?type=text` request must have been made and the
   response is forwarded as `DeleteResponse`.

2. **Given** `deleteBatch('batch-42')` is called, **When** flushed, **Then** a
   `DELETE /api/v1/crud/batch/batch-42/files` request must have been made.

3. **Given** `deleteTextBatch(['e1','e2'])` is called, **When** flushed, **Then** a
   `DELETE /api/v1/crud/files/text/batch` request with body `['e1','e2']` must have been made.

4. **Given** `deleteImageBatch(['e1'])` is called, **When** flushed, **Then** a
   `DELETE /api/v1/crud/files/image/batch` request with the correct body must have been made.

5. **Given** `deleteAllFiles('CONFIRM_DELETE_ALL')` is called, **When** flushed, **Then** a
   `DELETE /api/v1/crud/files/all?confirmation=CONFIRM_DELETE_ALL` must have been made.

6. **Given** `checkDuplicate(file)` is called, **When** flushed, **Then** a
   `POST /api/v1/crud/check-duplicate` with a `FormData` body containing the file must have
   been made and the response forwarded as `DuplicateCheckResponse`.

7. **Given** `getBatchInfo('batch-1')` is called, **When** flushed, **Then** a
   `GET /api/v1/crud/batch/batch-1/info` must have been made.

8. **Given** `getSystemStats()` is called, **When** flushed, **Then** a
   `GET /api/v1/crud/stats/system` must have been made.

---

### User Story 2 — File Ingestion Upload Contract (Priority: P1)

A developer using `IngestionApiService` expects each upload method to POST a properly formed
`FormData` payload, include an optional `batchId` field when provided, and return a typed
response observable. Error responses (e.g. 422 Unprocessable Entity) must propagate as
observable errors, not as swallowed exceptions.

**Why this priority**: Ingestion is the entry point for all document data. A malformed upload
payload means no embeddings are created; a suppressed 422 error means the UI silently
ignores validation failures. Both scenarios break the core use-case of the application.

**Independent Test**: Testable via `createHttpFactory` with `HttpClientTestingModule`. Call
each upload method, flush with a mock response or an HTTP error, and assert on the request
body, URL, and observable output.

**Acceptance Scenarios**:

1. **Given** `uploadFile(file)` is called, **When** flushed, **Then** a
   `POST /api/v1/ingestion/upload` with a `FormData` containing the file (no `batchId` field)
   must have been made.

2. **Given** `uploadFile(file, 'batch-7')` is called, **When** flushed, **Then** the
   `FormData` must include `batchId = 'batch-7'`.

3. **Given** `uploadFileAsync(file)` is called, **When** flushed, **Then** a
   `POST /api/v1/ingestion/upload/async` must have been made, returning `AsyncResponse`.

4. **Given** `uploadBatchAsync([f1, f2])` is called, **When** flushed, **Then** a
   `POST /api/v1/ingestion/upload/batch/async` with both files appended under the `files` key
   must have been made.

5. **Given** the server responds with HTTP 422, **When** `uploadFile` is subscribed, **Then**
   the observable must error (not complete) so the caller can handle the failure.

6. **Given** `getBatchStatus('batch-1')` is called, **When** flushed, **Then** a
   `GET /api/v1/ingestion/status/batch-1` must have been made and the response returned as
   `StatusResponse`.

7. **Given** `rollbackBatch('batch-5')` is called, **When** flushed, **Then** a
   `DELETE /api/v1/ingestion/rollback/batch-5` must have been made.

---

### User Story 3 — SSE Streaming Lifecycle (Priority: P1)

A developer subscribing to `StreamingApiService.stream()` expects the observable to emit a
`connected` event when the SSE session is established, emit `token` events for each text
chunk (with citations stripped), emit a `complete` event when the server signals end-of-stream,
and close the underlying `EventSource` on observable teardown.

**Why this priority**: Streaming is the primary user-facing interaction channel. An observable
that never completes leaks memory; one that doesn't strip citations renders raw XML tags in
chat. Both are directly visible regressions.

**Independent Test**: Testable by mocking `EventSource` globally in the test suite, calling
`stream()`, and dispatching synthetic `MessageEvent`s for each event type. Verifies the full
SSE protocol without a network connection.

**Acceptance Scenarios**:

1. **Given** `stream({ query: 'test' })` is called, **When** the mock `EventSource` fires a
   `connected` event, **Then** the observable emits `{ type: 'connected', sessionId, conversationId }`.

2. **Given** the `EventSource` fires a `token` event with text containing
   `<cite index="1">src</cite>`, **When** the observable emits, **Then** the emitted `text`
   must have all citation tags removed.

3. **Given** the `EventSource` fires a `complete` event, **When** the observable emits,
   **Then** the observable must emit `{ type: 'complete' }` and then complete (no further
   emissions).

4. **Given** the `EventSource` fires an `error` event with a parseable body, **When** the
   observable receives it, **Then** the observable must emit `{ type: 'error', error, code }`
   and then error.

5. **Given** the subscriber unsubscribes before `complete`, **When** teardown runs, **Then**
   `EventSource.close()` must have been called exactly once.

6. **Given** `cancelStream('session-1')` is called, **When** flushed, **Then** a
   `POST /api/v1/assistant/stream/session-1/cancel` must have been made.

7. **Given** a native connectivity failure occurs (network drop, server close), **When**
   `EventSource.onerror` fires and `readyState` is not `CLOSED`, **Then** the observable must
   error immediately (without emitting a typed `error` token first) and `EventSource.close()`
   must be called.

---

### User Story 4 — WebSocket Progress Tracking (Priority: P2)

A developer using `WebSocketProgressService` expects `connect()` to establish a STOMP
connection, `subscribeToProgress(batchId)` to return an observable that emits `UploadProgress`
events for the given batch and completes when the stage reaches `COMPLETED` or `ERROR`, and
`disconnect()` to unsubscribe all active subscriptions and close the STOMP client.

**Why this priority**: Real-time progress is a secondary UX feature — the application can
function without it (polling fallback exists via `getBatchStatus`), but broken subscriptions
mean the progress panel never updates, leading users to believe uploads are stalled.

**Independent Test**: Testable by providing a mock `Client` from `@stomp/stompjs` via
`createServiceFactory`. Verify subscription to the correct STOMP destination and observable
completion semantics.

**Acceptance Scenarios**:

1. **Given** `connect()` is called on an unconnected service, **When** the mock STOMP client
   fires `onConnect`, **Then** the returned `Promise` must resolve.

2. **Given** the service is already connected, **When** `connect()` is called again, **Then**
   the method must resolve immediately without creating a second STOMP client.

3. **Given** `subscribeToProgress('batch-99')` is called on a connected service, **When** a
   STOMP message arrives at `/topic/upload-progress/batch-99`, **Then** the observable must
   emit the parsed `UploadProgress` and the `progress$` subject must also emit the same value.

4. **Given** a `COMPLETED` progress message arrives, **When** the observable receives it,
   **Then** the observable must complete (no further emissions).

5. **Given** `disconnect()` is called with active subscriptions, **When** it runs, **Then**
   all STOMP subscriptions must be unsubscribed and `stompClient.deactivate()` must be called.

6. **Given** the initial WebSocket connection fails, **When** `onWebSocketError` fires, **Then**
   the service must attempt the SockJS fallback before rejecting the `Promise`.

---

### User Story 5 — Notification Toast Bus (Priority: P2)

A developer calling `NotificationService.success()`, `error()`, `warning()`, or `info()` expects
the service to emit a well-formed `Toast` object on the `toasts$` observable with the correct
`type`, `title`, `message`, and `duration` fields. Each toast must carry a unique `id`.

**Why this priority**: Toast notifications are the primary error-feedback channel for the user.
A broken notification bus means upload errors or rate-limit events are silently discarded and
the user has no feedback.

**Independent Test**: Testable with `createServiceFactory<NotificationService>`. Subscribe to
`toasts$` before calling a notify method, assert on the emitted value. No HTTP or DOM required.

**Acceptance Scenarios**:

1. **Given** `success('Title', 'Message')` is called, **When** `toasts$` emits, **Then** the
   emitted toast must have `type: 'success'`, `title: 'Title'`, `message: 'Message'`, and
   `duration: 5000` (default).

2. **Given** `error('Err', 'Details', 3000)` is called, **When** `toasts$` emits, **Then**
   the toast must have `type: 'error'` and `duration: 3000`.

3. **Given** `warning('Warn', 'Msg')` is called, **When** `toasts$` emits, **Then** the toast
   must have `type: 'warning'`.

4. **Given** `info('Info', 'Msg')` is called, **When** `toasts$` emits, **Then** the toast
   must have `type: 'info'`.

5. **Given** two `success()` calls are made synchronously (within the same millisecond),
   **When** both emissions are collected, **Then** the two emitted `id` values must be
   different — verified by matching the pattern `"<timestamp>-<counter>"` where the counter
   portion increments by 1 between the two toasts.

---

### User Story 6 — Voice Recording & Whisper Transcription (Priority: P3)

A developer using `VoiceService` expects `startRecording()` to request microphone access and
begin capturing audio, `stopRecording()` to return a `Blob` of the recorded audio,
`transcribeWithWhisper(blob)` to POST the audio to the backend and return a typed
`WhisperResponse`, and the recording-state observable to reflect the live state accurately.

**Why this priority**: Voice input is an enhancement feature; the application is fully usable
without it. Browser API mocking is more complex than HTTP mocking, and partial failures here
do not block the critical document-ingestion or chat flows.

**Independent Test**: Testable by stubbing `navigator.mediaDevices.getUserMedia` and
`MediaRecorder` in the test environment. HTTP assertions use `HttpTestingController` for
the transcription call.

**Acceptance Scenarios**:

1. **Given** `isRecordingSupported()` is called on a browser where
   `navigator.mediaDevices.getUserMedia` exists, **When** the method returns, **Then** the
   result must be `true`.

2. **Given** `isRecordingSupported()` is called on a browser without `getUserMedia`, **When**
   the method returns, **Then** the result must be `false`.

3. **Given** `startRecording()` is called, **When** `getUserMedia` resolves, **Then**
   `isRecording()` must return `true` and the recording-state observable must emit `true`.

4. **Given** recording is active and `stopRecording()` is called, **When** the `MediaRecorder`
   fires `onstop`, **Then** the returned `Promise` must resolve with a `Blob` of type
   `audio/webm` and the recording-state observable must emit `false`.

5. **Given** `transcribeWithWhisper(blob, 'en')` is called, **When** flushed, **Then** a
   `POST /api/v1/voice/transcribe` with `FormData` containing `audio` (the blob) and
   `language = 'en'` must have been made, returning `WhisperResponse`.

6. **Given** `startRecording()` is called and `getUserMedia` rejects, **When** the error
   propagates, **Then** the error observable (`getErrors()`) must emit the error message.

---

### Edge Cases

- What happens when `StreamingApiService.stream()` receives a malformed JSON payload in an
  SSE event? The service must silently skip the event (log the error) and not crash the
  observable.
- What happens when `WebSocketProgressService.subscribeToProgress()` is called before
  `connect()` completes? The observable must immediately error with a descriptive message.
- What happens when `CrudApiService.deleteAllFiles()` receives an HTTP 403? The observable
  must propagate the HTTP error to the caller without transformation.
- What happens when `NotificationService` is called multiple times within the same event loop
  tick? Each call must produce an independent emission on `toasts$` (no coalescing).
- What happens when `VoiceService.startRecording()` is called while already recording? The
  service MUST return immediately (no-op) without creating a new `MediaRecorder` or requesting
  microphone access again. The test must assert that the existing recording state is unchanged.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `CrudApiService` MUST expose typed observable methods for each backend CRUD endpoint
  (`deleteFile`, `deleteBatch`, `deleteTextBatch`, `deleteImageBatch`, `deleteAllFiles`,
  `checkDuplicate`, `getBatchInfo`, `getSystemStats`).

- **FR-002**: `IngestionApiService` MUST send files as `multipart/form-data` and include the
  optional `batchId` only when provided by the caller.

- **FR-003**: `IngestionApiService` MUST propagate HTTP errors (4xx/5xx) as observable errors
  to the caller; it MUST NOT swallow or transform them.

- **FR-004**: `StreamingApiService.stream()` MUST strip `<cite index="...">...</cite>` tags
  from token event text before emitting.

- **FR-005**: `StreamingApiService.stream()` MUST close the `EventSource` on observable
  completion, observable error, and subscriber unsubscription (teardown).

- **FR-006**: `WebSocketProgressService.connect()` MUST attempt a native WebSocket STOMP
  connection first and fall back to SockJS only on connection failure.

- **FR-007**: `WebSocketProgressService.subscribeToProgress(batchId)` MUST complete the
  returned observable when the progress stage is `COMPLETED` or `ERROR`.

- **FR-008**: `WebSocketProgressService.disconnect()` MUST unsubscribe all active STOMP
  subscriptions before deactivating the client.

- **FR-009**: `NotificationService` MUST assign a unique `id` using the format
  `Date.now() + '-' + monotonic counter` (e.g. `'1714478000000-1'`) to each emitted `Toast`,
  guaranteeing uniqueness even when multiple toasts are created within the same millisecond.

- **FR-010**: `VoiceService.transcribeWithWhisper()` MUST default the language parameter to
  `'fr'` when not supplied by the caller.

- **FR-011**: All six services MUST be independently injectable (`providedIn: 'root'`) and
  testable in isolation via Spectator factories without importing `AppModule`.

- **FR-012**: All HTTP services MUST derive their base URL from `environment.apiUrl` and MUST
  NOT hardcode hostnames.

### Key Entities

- **Toast**: `{ id: string, type: 'success'|'error'|'warning'|'info', title: string, message: string, duration?: number }` — `id` format: `"<timestamp>-<counter>"` (e.g. `"1714478000000-1"`); monotonically increasing counter ensures uniqueness within the same millisecond.
- **DeleteResponse**: `{ success: boolean, deletedCount: number, message: string, embeddingId?, batchId?, type?, timestamp? }`
- **DuplicateCheckResponse**: `{ isDuplicate: boolean, filename: string, existingBatchId?, message: string }`
- **BatchInfoResponse**: `{ found: boolean, batchId: string, textEmbeddings, imageEmbeddings, totalEmbeddings, message }`
- **IngestionResponse**: `{ success, batchId, filename, fileSize, textEmbeddings, imageEmbeddings, durationMs, streamingUsed, message, duplicate, existingBatchId? }`
- **AsyncResponse**: `{ accepted, batchId, filename, message, statusUrl, duplicate, existingBatchId? }`
- **StatusResponse**: `{ found, batchId, textEmbeddings, imageEmbeddings, totalEmbeddings, message }`
- **StreamEvent**: `{ type: 'connected'|'token'|'complete'|'error', sessionId?, conversationId?, text?, index?, response?, metadata?, error?, code? }`
- **UploadProgress**: `{ batchId, filename, stage, progressPercentage, message, embeddingsCreated?, chunksCreated?, imagesProcessed?, timestamp? }`
- **WhisperResponse**: `{ success, transcript, language, audioSize, filename, transcriptLength?, error? }`

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 28 unit tests defined in the test plan for Phase 3 pass without a running backend.
- **SC-002**: Statement coverage for all six service files reaches ≥ 80% as reported by the test runner.
- **SC-003**: Branch coverage for all six service files reaches ≥ 75%.
- **SC-004**: Function coverage for all six service files reaches ≥ 85%.
- **SC-005**: Each spec file runs in isolation (no shared state) and completes in under 10 seconds.
- **SC-006**: Zero *unexpected* console errors or unhandled observable warnings appear during
  the test run. Tests that deliberately exercise error paths (e.g. US3-S4, US3-S7, US4-S6)
  MUST suppress expected `console.error` output via `vi.spyOn(console, 'error').mockImplementation(() => {})`
  so that only genuine unexpected errors surface in CI output.
- **SC-007**: The CI pipeline (`npm test`) exits with code 0 after adding the Phase 3 specs.

---

## Assumptions

- The test framework is **Vitest** (as configured in `agentic-rag-ui`) with `@ngneat/spectator`
  and Angular testing utilities. Karma/Jasmine patterns from the test-plan comments are treated
  as illustrative pseudo-code; actual implementations use Vitest equivalents.
- `EventSource` is not natively mockable in jsdom; tests for `StreamingApiService` will require
  a global `EventSource` stub (e.g. `vi.stubGlobal('EventSource', MockEventSource)`).
- `MediaRecorder` and `navigator.mediaDevices.getUserMedia` are not available in the test
  environment; they will be stubbed with `vi.stubGlobal` for `VoiceService` tests.
- `@stomp/stompjs` `Client` will be mocked via `vi.mock` or a Spectator `mockProvider` for
  `WebSocketProgressService` tests; no real WebSocket server is required.
- The deleted `http-client.service.ts` and `websocket-api.service.ts` files are excluded from
  this phase. Their deletion was intentional (confirmed by git status on `phase-3-services`
  branch) and no specs will be written for them.
- Environment variables (`environment.apiUrl`, `environment.wsProgressEndpoint`) are available
  in the test environment via the default `environment.ts` file (`apiUrl: '/api'`,
  `wsProgressEndpoint: '/ws'`).
- Test isolation follows Constitution Principle VI: each `describe` block uses Spectator's
  factory pattern; no shared mutable state between test cases.
- `NgZone` correctness (i.e. that SSE token and STOMP progress emissions are wrapped in
  `zone.run()`) is deliberately out of scope for Phase 3 service unit tests. Zone regression
  is caught by Phase 7 (chat components) and Phase 10 (ingestion components) integration tests
  where missing zone re-entry produces a visible rendering failure.
