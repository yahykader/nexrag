# Feature Specification: Ingestion Store NgRx Unit Tests (Phase 9)

**Feature Branch**: `016-ingestion-store-ngrx`  
**Created**: 2026-05-04  
**Status**: Draft  
**Input**: User description: "read agentic-ui-test-plan-speckit.md file and create a specification from PHASE 9 — src/app/features/ingestion/store"

## User Scenarios & Testing *(mandatory)*

### User Story 1 — CRUD Delete Operations Are Fully Verified (Priority: P1)

A developer working on the document management UI needs confidence that every delete operation — single file, batch, text batch, image batch, and delete-all — correctly transitions the store state. They run the CRUD sub-store test suite and see that pending operations are tracked, success responses update the correct entry, error responses store diagnostic information, and the `activeDeleteOperations` counter never drops below zero regardless of dispatch order.

**Why this priority**: Delete operations are destructive and irreversible. A silent state bug (e.g., the counter underflowing or the batchInfos cache not being evicted on delete) would cause phantom UI states that are hard to reproduce in manual testing.

**Independent Test**: Can be fully tested by running only the `crud/` sub-store specs and verifying all reducer, selector, actions, and effects cases pass — delivers confidence in all document deletion flows.

**Acceptance Scenarios**:

1. **Given** the CRUD store is in its initial state, **When** a `deleteFile` action is dispatched, **Then** `loading` becomes `true`, `activeDeleteOperations` increments by 1, and a `pending` operation entry is added to `deleteOperations[]`.
2. **Given** a delete file operation is pending, **When** `deleteFileSuccess` is dispatched, **Then** `loading` becomes `false`, the matched operation status becomes `success`, and `activeDeleteOperations` decrements without going below 0.
3. **Given** a delete file operation is pending, **When** `deleteFileError` is dispatched, **Then** the error message is stored, the operation status becomes `error`, and `activeDeleteOperations` decrements safely.
4. **Given** `deleteAllFilesSuccess` is dispatched, **Then** both `batchInfos` and `duplicateChecks` caches are completely cleared.
5. **Given** `clearAll` is dispatched, **Then** the entire CRUD state returns to `initialCrudState`.
6. **Given** the effects layer receives a delete action, **When** the API call succeeds, **Then** the corresponding success action is dispatched; **When** the API call fails, **Then** the corresponding error action is dispatched.

---

### User Story 2 — Upload Lifecycle State Is Correctly Managed (Priority: P1)

A developer working on the file upload UI needs assurance that every upload lifecycle transition — from adding files through async/sync upload, rate-limiting, deduplication, removal, and clear — is correctly reflected in the ingestion sub-store. They run the ingestion sub-store tests and confirm that each file's status field transitions accurately and that upload mode switching works correctly.

**Why this priority**: Upload state drives the entire upload UI. Incorrect status transitions would display wrong progress indicators, misleading error messages, or prevent users from retrying uploads.

**Independent Test**: Can be fully tested by running only the `ingestion/` sub-store specs — delivers confidence that file upload state management is correct end-to-end.

**Acceptance Scenarios**:

1. **Given** the ingestion store is in its initial state (uploadMode: `async`), **When** `addFilesToUpload` is dispatched with a list of files, **Then** each file is added to `uploads[]` with status `pending`.
2. **Given** an upload is in `uploading` status, **When** `uploadFileRateLimited` is dispatched, **Then** the file's status becomes `rate-limited` and `retryAfterSeconds` is stored on the file entry.
3. **Given** an upload is in `uploading` status, **When** `uploadFileDuplicate` is dispatched, **Then** the file's status becomes `duplicate` and `existingBatchId` is stored.
4. **Given** `updateUploadStatus` is dispatched with `batchId` and `existingBatchId`, **Then** both optional fields are persisted alongside the new status.
5. **Given** `removeFile` is dispatched with a specific `fileId`, **Then** only that file is removed from `uploads[]`; all other files remain unchanged.
6. **Given** `clearAllFiles` is dispatched, **Then** `uploads[]` is emptied and all stat counters (`success`, `errors`, `duplicates`, `rateLimited`) reset to zero.
7. **Given** uploadMode is `async`, **When** `toggleUploadMode` is dispatched, **Then** uploadMode becomes `sync`; dispatching again returns it to `async`.
8. **Given** `clearCompletedUploads` is dispatched, **Then** uploads in `success`, `error`, `duplicate`, and `rate-limited` status are removed; only `pending` and `uploading` uploads remain.

---

### User Story 3 — Real-Time Progress Tracking Is Reliable (Priority: P2)

A developer integrating the WebSocket progress channel needs to verify that the progress sub-store correctly handles connection lifecycle events, per-batch progress updates, completion signals, and error conditions. They run the progress sub-store tests and confirm that batch state is isolated — one batch's events do not affect another — and that `clearAllProgress` fully resets the store.

**Why this priority**: Progress tracking is visible to end users via real-time progress bars. A bug where a batch's completion incorrectly marks another batch as done, or where WebSocket errors leave stale progress data, directly degrades the user experience.

**Independent Test**: Can be fully tested by running only the `progress/` sub-store specs — delivers confidence that WebSocket-driven progress events produce correct UI state.

**Acceptance Scenarios**:

1. **Given** `connectWebSocketSuccess` is dispatched, **Then** `isConnected` becomes `true`.
2. **Given** `connectWebSocketError` is dispatched with an error string, **Then** the error is stored and `isConnected` remains `false`.
3. **Given** `progressUpdate` is dispatched for `batchId-A`, **Then** only `batchId-A`'s percentage and message are updated; `batchId-B` data is unchanged.
4. **Given** `progressCompleted` is dispatched for a batch, **Then** `isComplete` becomes `true` for that batch only.
5. **Given** `progressError` is dispatched for a batch, **Then** the error is stored under that specific `batchId`.
6. **Given** `clearProgress` is dispatched with a specific `batchId`, **Then** only that batch's data is removed; other batches remain.
7. **Given** `clearAllProgress` is dispatched, **Then** all batch data is removed and the state returns to its initial form.

---

### User Story 4 — Rate Limit State Drives UI Feedback Accurately (Priority: P2)

A developer building the rate-limit indicator and toast components needs to verify that the rate-limit sub-store correctly records exceeded limits, tracks per-endpoint token counts, manages the countdown timer, and resets cleanly. They run the rate-limit sub-store tests and confirm that the countdown never goes negative and that `rateLimitReset` fully clears the blocked state.

**Why this priority**: The rate-limit UI is the primary feedback mechanism preventing users from being confused by rejected uploads. An incorrect `isRateLimited` state or a countdown that never reaches zero would leave users unable to retry.

**Independent Test**: Can be fully tested by running only the `rate-limit/` sub-store specs — delivers confidence that all rate-limit state transitions are correct.

**Acceptance Scenarios**:

1. **Given** the rate-limit store is in its initial state, **Then** `isRateLimited` is `false`, `retryAfterSeconds` is `0`, and all endpoint `remainingTokens` are `null`.
2. **Given** `rateLimitExceeded` is dispatched with `retryAfterSeconds: 30` and a message, **Then** `isRateLimited` becomes `true`, the message is stored, and `retryAfterSeconds` is `30`.
3. **Given** `rateLimitReset` is dispatched, **Then** `isRateLimited` returns to `false` and `retryAfterSeconds` returns to `0`.
4. **Given** `updateRemainingTokens` is dispatched for endpoint `upload` with `remaining: 7`, **Then** only `remainingTokens.upload` is updated to `7`; other endpoints are unchanged.
5. **Given** `startCountdown` is dispatched with `seconds: 15`, **Then** `retryAfterSeconds` is set to `15`.
6. **Given** `decrementCountdown` is dispatched repeatedly, **Then** `retryAfterSeconds` decreases by 1 each time and never goes below `0`.
7. **Given** the effects layer starts a countdown, **Then** `decrementCountdown` is dispatched once per second, stopping when `retryAfterSeconds` reaches `0`, at which point `rateLimitReset` is dispatched.

---

### Edge Cases

- What happens when `activeDeleteOperations` would go negative (e.g., success dispatched without a corresponding pending)? The `Math.max(0, ...)` guard must prevent underflow.
- What happens when `updateUploadStatus` targets a `fileId` that no longer exists in `uploads[]`? The state must remain unchanged with no errors thrown.
- What happens when `progressUpdate` is received for a `batchId` that was never subscribed? The reducer performs a no-op — state is unchanged and no new entry is created.
- What happens when `decrementCountdown` is dispatched when `retryAfterSeconds` is already `0`? It must remain at `0` (no negative values).
- What happens when `deleteAllFilesSuccess` is dispatched while individual delete operations are still pending? Both `batchInfos` and `duplicateChecks` must be cleared regardless.
- What happens when `toggleUploadMode` is dispatched while uploads are actively in progress? The mode changes but in-flight uploads are not affected.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The test suite MUST cover all four sub-stores: `crud/`, `ingestion/`, `progress/`, and `rate-limit/`.
- **FR-002**: Each sub-store MUST have separate spec files for actions, reducer, selectors, and effects.
- **FR-003**: Every reducer spec MUST verify the initial state is returned when an unknown action is dispatched.
- **FR-004**: Every action spec MUST verify the action type string matches the declared constant (e.g., `[CRUD] Delete File`) and that all required props are present.
- **FR-005**: Reducer specs MUST test both the happy path (action + correct state transition) and error path (error action + error stored in state) for every operation.
- **FR-006**: The `crud` reducer spec MUST verify `activeDeleteOperations` never drops below `0` regardless of dispatch order.
- **FR-007**: The `ingestion` reducer spec MUST verify all six upload status values: `pending`, `uploading`, `success`, `error`, `duplicate`, `rate-limited`.
- **FR-008**: The `ingestion` reducer spec MUST verify `clearCompletedUploads` removes uploads in `success`, `error`, `duplicate`, and `rate-limited` statuses, while retaining uploads in `pending` and `uploading` statuses.
- **FR-009**: The `progress` reducer spec MUST verify that updates for one `batchId` do not affect data stored under other `batchId` keys.
- **FR-010**: The `rate-limit` reducer spec MUST verify `decrementCountdown` cannot produce a negative `retryAfterSeconds`.
- **FR-011**: Effects specs MUST use mocked services — no real HTTP calls, no real WebSocket connections, no real timers (except where Vitest fake timers `vi.useFakeTimers()` are used for countdown effects).
- **FR-012**: Selector specs MUST use a pre-built mock state object to verify each selector returns the correct slice or derived value.
- **FR-013**: The `rate-limit` effects spec MUST verify the countdown emits `decrementCountdown` once per second and dispatches `rateLimitReset` on expiry.
- **FR-016**: The `rate-limit` effects spec MUST verify that `rateLimitExceeded` is dispatched in response to an incoming `uploadFileRateLimited` action — this is the sole cross-store propagation point. The ingestion effects MUST NOT dispatch `rateLimitExceeded` directly; they dispatch only `uploadFileRateLimited`.
- **FR-014**: All 16 spec files MUST pass without errors in the project's Vitest test runner.
- **FR-015**: The `progress` reducer spec MUST verify that dispatching `progressUpdate` for an unknown `batchId` produces no state change (no new entry, no error flag set).

### Key Entities

- **CrudState**: Tracks delete operations (with type, status, targetId), duplicate check results (keyed by filename), batch info cache (keyed by batchId), system stats, and a loading/error flag.
- **IngestionState**: Tracks the list of `UploadFile` objects (each with id, progress, status, batchId, error, retryAfterSeconds), upload counters by status category, active upload count, upload mode (sync/async), and strategies list.
- **ProgressState**: Tracks WebSocket connection status and a map of per-batch progress entries (percentage, message, isComplete, error), keyed by `batchId`.
- **RateLimitState**: Tracks whether the rate limit is active, the countdown in seconds, the limit message, and per-endpoint remaining token counts and configured limits.
- **DeleteOperation**: Represents a single in-flight or completed delete with type (`file`, `batch`, `text-batch`, `image-batch`, `all`), status (`pending`, `success`, `error`), targetId, deletedCount, and timestamp.
- **UploadFile**: Represents a single file being uploaded with status, batchId, asyncResponse, error message, existingBatchId (for duplicates), and retryAfterSeconds (for rate-limited files).

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 16 spec files execute without errors or skipped tests when the test suite is run.
- **SC-002**: Each of the 4 sub-stores achieves at least 80% branch coverage as reported by the test runner. This is an advisory target — coverage is reported but does not block the test suite from passing.
- **SC-003**: The complete Phase 9 test suite (52 test cases across 16 spec files) runs in under 30 seconds.
- **SC-004**: Zero tests rely on shared mutable state between test cases — each `it()` block is independently repeatable.
- **SC-005**: Every action type string tested in the actions specs matches the actual declared constant in the source file — no hardcoded strings that could drift.
- **SC-006**: All effects specs complete without real network calls, real timers, or real WebSocket connections.

---

## Assumptions

- All four sub-store source files (`*.actions.ts`, `*.reducer.ts`, `*.selectors.ts`, `*.effects.ts`, `*.state.ts`) are already implemented and stable — this phase adds tests, not new production code.
- The test runner is **Vitest** as configured in the project; no Karma or Jest configuration changes are required.
- All external service dependencies (HTTP API, WebSocket) are mocked via the testing utilities already used in Phases 6–8 (`provideMockStore`, `provideMockActions`, `createServiceFactory`).
- The `rate-limit` effects countdown timer is tested using Vitest fake timers — `vi.useFakeTimers()` and `vi.advanceTimersByTime(1000)` per tick — to avoid real time passage. Angular's `fakeAsync`/`tick()` is not used (incompatible with Vitest/no Zone.js).
- Selectors that derive computed values (e.g., `selectHasActiveProgress`, `selectPendingUploads`) are tested with representative mock state, not exhaustive permutations.
- The `ingestion` effects spec covers both `uploadMode: 'sync'` and `uploadMode: 'async'` branches in `addFilesToUpload$`.
- Test helper factories for mock `UploadFile`, `DeleteOperation`, and `UploadProgress` objects will follow the pattern established in the project's `test-helpers.ts`.

## Clarifications

### Session 2026-05-04

- Q: Which fake timer mechanism should the `rate-limit` effects spec use for the countdown? → A: Vitest fake timers — `vi.useFakeTimers()` + `vi.advanceTimersByTime(1000)` per tick (Option A).
- Q: When `progressUpdate` arrives for an unknown `batchId`, what is the expected reducer behavior? → A: No-op — state is unchanged, no new entry is created (Option A).
- Q: Should `clearCompletedUploads` also remove `duplicate` and `rate-limited` uploads, not just `success` and `error`? → A: Yes — all four terminal statuses are removed; only `pending` and `uploading` are retained (Option A).
- Q: Is the 80% branch coverage threshold a hard gate (blocking) or advisory? → A: Advisory — reported but non-blocking (Option B).
- Q: Who dispatches `rateLimitExceeded` in response to a 429 upload response? → A: Rate-limit effects listen for `uploadFileRateLimited` and dispatch `rateLimitExceeded`; ingestion effects stay self-contained (Option B).
