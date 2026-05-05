# Feature Specification: PHASE 10 — Ingestion Components Test Suite

**Feature Branch**: `017-ingestion-components-tests`  
**Created**: 2026-05-04  
**Status**: Complete  
**Input**: User description: "read agentic-ui-test-plan-speckit.md file and create a specification for the PHASE 10 — src/app/features/ingestion/components"

## Clarifications

### Session 2026-05-04

- Q: Should `UploadItemComponent` have a dedicated test case for the `rate-limited` status (making it six total, not five)? → A: Yes — add a `rate-limited` test case; update FR-003 and US2 to reference all six upload statuses.
- Q: Should the `UploadItemComponent` spec include a test for the `batchId = undefined` error path on `success` delete? → A: Yes — add one test asserting that `NotificationService.error()` is called and no modal opens when `status = 'success'` and `batchId` is `undefined`.
- Q: What condition gates the `DeleteAllButtonComponent` disabled state? → A: Both — disabled when `isDeleting = true` (active delete in progress) AND when the upload list is empty; both guards require separate test cases.
- Q: How should `@ViewChild` child modals be handled in component specs (`DeleteAllButtonComponent` → `DeleteAllModalComponent`, `UploadItemComponent` → `DeleteBatchModalComponent`)? → A: Include the real child modal as an imported standalone dependency — test the full `@ViewChild` open/close wiring within the component spec itself.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - File Upload Zone Validation (Priority: P1)

A developer adding or modifying the upload zone component can verify that the drag-and-drop area correctly accepts, filters, and emits files — and that rate-limit state visually disables the zone without silently allowing invalid input.

**Why this priority**: The upload zone is the entry point of the entire ingestion pipeline. Regressions here break the primary user journey before any file reaches the backend.

**Independent Test**: Run `UploadZoneComponent` specs in isolation against mocked store state; the suite is fully self-contained and delivers confidence in the file-selection contract.

**Acceptance Scenarios**:

1. **Given** a user drags a valid file over the drop area, **When** the `dragover` event fires, **Then** the component applies the `drag-over` CSS class and prevents default browser behavior.
2. **Given** a user drops one or more files, **When** all files pass format and size validation, **Then** the `filesSelected` output emits the accepted file list.
3. **Given** a user drops a file whose extension is not in the allowed list, **When** the drop event fires, **Then** the file is filtered out and `filesSelected` is not emitted for that file.
4. **Given** the store reports `isRateLimited = true`, **When** the component renders, **Then** the drop zone is visually disabled and does not process any drop or click events.
5. **Given** the component receives `maxFileSize` input, **When** the template renders, **Then** the human-readable maximum size is displayed in the UI.

---

### User Story 2 - Upload Item Status Display (Priority: P1)

A developer working on per-file upload rows can verify that each `UploadItemComponent` correctly renders file metadata, maps every upload status to the right icon/color, and emits the correct action when the user clicks delete.

**Why this priority**: Upload items are the primary feedback mechanism during ingestion; incorrect status display misleads users about pipeline progress.

**Independent Test**: Run `UploadItemComponent` specs with a mocked store and `UploadFile` inputs covering all six statuses; delivers confidence in status-to-UI mapping.

**Acceptance Scenarios**:

1. **Given** an `UploadFile` input with `status = 'uploading'`, **When** the component renders, **Then** a progress bar is visible.
2. **Given** an `UploadFile` input with `status = 'success'`, **When** the component renders, **Then** a success icon is shown and no progress bar is visible.
3. **Given** an `UploadFile` input with `status = 'error'`, **When** the component renders, **Then** an error icon is shown.
4. **Given** an `UploadFile` input with `status = 'duplicate'`, **When** the component renders, **Then** a warning icon is shown.
5. **Given** the user clicks the delete button, **When** the upload status is `pending` or `uploading`, **Then** the `removeUpload` action is dispatched without opening a modal.
6. **Given** the user clicks the delete button, **When** the upload status is `success` or `duplicate`, **Then** the delete-batch modal opens with the correct `batchId` and `filename`.
7. **Given** an `UploadFile` input with `status = 'rate-limited'`, **When** the component renders, **Then** the fallback icon and neutral color are applied (no dedicated icon exists for this status).
8. **Given** an `UploadFile` with `status = 'success'` and `batchId = undefined`, **When** the user clicks the delete button, **Then** `NotificationService.error()` is called and no modal opens.

---

### User Story 3 - Real-Time Progress Panel (Priority: P2)

A developer can verify that `ProgressPanelComponent` correctly reads WebSocket-driven progress state from the store, displays the overall percentage, and hides itself when no batch is active.

**Why this priority**: The progress panel is the user's only real-time feedback during backend processing; a hidden or stale panel removes transparency from the ingestion flow.

**Independent Test**: Run `ProgressPanelComponent` specs with mocked store state toggling active progress entries; self-contained and delivers confidence in visibility logic.

**Acceptance Scenarios**:

1. **Given** the store contains one or more active progress entries, **When** the component renders, **Then** the overall progress percentage is displayed.
2. **Given** the store contains no active progress entries, **When** the component renders, **Then** the panel is hidden from view.
3. **Given** the store's `selectWebSocketConnected` returns `true`, **When** the component renders, **Then** a connection status indicator is shown.

---

### User Story 4 - Delete All Confirmation Flow (Priority: P2)

A developer can verify that `DeleteAllButtonComponent` is correctly disabled when the list is empty, opens a confirmation modal on click, dispatches the delete-all action on confirmation, and cancels cleanly.

**Why this priority**: Mass deletion is irreversible; the confirmation flow must be robust to prevent accidental data loss.

**Independent Test**: Run `DeleteAllButtonComponent` and `DeleteAllModalComponent` specs with mocked store; covers the full confirm/cancel contract independently.

**Acceptance Scenarios**:

1. **Given** the upload list is empty, **When** the component renders, **Then** the delete-all button is disabled.
2. **Given** a delete operation is already in progress (`isDeleting = true`), **When** the component renders, **Then** the delete-all button is disabled.
3. **Given** at least one file exists and no delete is in progress, **When** the user clicks the delete-all button, **Then** the confirmation modal opens.
4. **Given** the modal is open, **When** the user clicks "Confirmer", **Then** the `confirmed` output event is emitted and the modal closes.
5. **Given** the modal is open, **When** the user clicks "Annuler", **Then** the `cancelled` output event is emitted and no delete action is dispatched.

---

### User Story 5 - Delete Batch Modal (Priority: P2)

A developer can verify that `DeleteBatchModalComponent` displays the correct batch name, emits `confirmed` with the right `batchId`, and emits `cancelled` without side effects.

**Why this priority**: Single-batch deletion is the most common targeted operation; incorrect payload emission would delete the wrong batch.

**Independent Test**: Run `DeleteBatchModalComponent` specs with `@Input` bindings; delivers confidence in emitted event payloads.

**Acceptance Scenarios**:

1. **Given** a `batchId` and `filename` are passed as inputs, **When** the modal opens, **Then** the filename is displayed in the confirmation message.
2. **Given** the modal is open, **When** the user clicks confirm, **Then** the `confirmed` output emits the correct `batchId`.
3. **Given** the modal is open, **When** the user clicks cancel, **Then** the `cancelled` output emits and the modal closes without dispatching any action.

---

### User Story 6 - Rate Limit Indicator and Toast (Priority: P3)

A developer can verify that `RateLimitIndicatorComponent` shows a live countdown when rate-limited and hides itself otherwise, and that `RateLimitToastComponent` displays the rate-limit message and auto-dismisses after the retry delay.

**Why this priority**: Rate limiting is a less frequent but business-critical scenario; poor UX here leaves users confused about why uploads are blocked.

**Independent Test**: Run `RateLimitIndicatorComponent` and `RateLimitToastComponent` specs with mocked store state and fake timers; self-contained.

**Acceptance Scenarios**:

1. **Given** the store reports `isRateLimited = false`, **When** the indicator renders, **Then** it is hidden.
2. **Given** the store reports `isRateLimited = true` with `retryAfterSeconds = 30`, **When** the indicator renders, **Then** the countdown is displayed and decrements each second.
3. **Given** a rate-limit event occurs, **When** the toast component renders, **Then** the rate-limit message is visible.
4. **Given** the toast is visible, **When** the `retryAfter` period elapses, **Then** the toast disappears automatically.

---

### Edge Cases

- What happens when a file with `size = 0` is dropped onto the upload zone?
- When `batchId` is `undefined` on a `success` delete, `NotificationService.error()` is called and no modal opens (resolved — see FR-003).
- What does `ProgressPanelComponent` render when `selectWebSocketConnected` is `false` but progress entries exist?
- What happens when `DeleteAllButtonComponent`'s 10-second safety timeout fires before the store confirms deletion?
- How does `RateLimitIndicatorComponent` handle a `retryAfterSeconds` that reaches 0 mid-countdown?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Each component spec file MUST be independently runnable with mocked NgRx store state using `provideMockStore`.
- **FR-002**: `UploadZoneComponent` specs MUST cover drag-and-drop events (`dragover`, `dragleave`, `drop`), file-input click, format/size filtering, and rate-limited disabled state.
- **FR-003**: `UploadItemComponent` specs MUST cover all six upload statuses (`pending`, `uploading`, `success`, `error`, `duplicate`, `rate-limited`), delete button behavior per status, modal interaction for `success`/`duplicate` cases, and the defensive path where `batchId` is `undefined` on a `success` delete (error notification shown, no modal opened).
- **FR-004**: `ProgressPanelComponent` specs MUST verify visibility toggle based on active progress entries and display of the overall progress percentage.
- **FR-005**: `DeleteAllButtonComponent` specs MUST verify two independent disabled conditions: (1) upload list is empty (`selectAllUploads` returns no items), and (2) a delete operation is already in progress (`selectCrudLoading = true`). Both must be tested as separate cases. Modal-open behavior is verified only when the button is enabled. The real `DeleteAllModalComponent` MUST be imported as a standalone dependency in the spec so that `@ViewChild` wiring is exercised end-to-end.
- **FR-005b**: `UploadItemComponent` specs that test modal interaction (`success`/`duplicate` delete paths) MUST import the real `DeleteBatchModalComponent` as a standalone dependency so that the `@ViewChild` open/close sequence is fully verified within the component spec.
- **FR-006**: `DeleteAllModalComponent` specs MUST verify the confirmation message, `confirmed` event emission on confirm, `cancelled` event emission on cancel, and modal closure.
- **FR-007**: `DeleteBatchModalComponent` specs MUST verify that the `filename` is displayed, and that `confirmed` emits the correct `batchId` while `cancelled` emits without side effects.
- **FR-008**: `RateLimitIndicatorComponent` specs MUST verify hidden state when `isRateLimited = false`, visible countdown when `isRateLimited = true`, and decrement behavior using fake timers.
- **FR-009**: `RateLimitToastComponent` specs MUST verify message display and automatic dismissal after the `retryAfter` delay using fake timers.
- **FR-010**: All specs MUST use Spectator (`createComponentFactory`) for component instantiation and `mockProvider` / `provideMockStore` for dependencies — no real store or real HTTP calls.
- **FR-011**: All specs MUST align with the test cases defined in `agentic-ui-test-plan-speckit.md` Phase 10 (32 unit tests across 8 spec files).
- **FR-012**: Specs MUST NOT introduce test-only logic into production component files.

### Key Entities

- **UploadFile**: Represents a single file in the ingestion queue — attributes: `id`, `file` (File object), `status` (`pending | uploading | success | error | duplicate | rate-limited`), `batchId?`, `existingBatchId?`, `retryAfterSeconds?`.
- **UploadProgress**: Real-time WebSocket progress entry — attributes: `batchId`, `percentage`, `currentStage`, `message`, `isComplete`, `isError`.
- **RateLimitState**: NgRx slice representing rate-limit status — key fields: `isRateLimited`, `retryAfterSeconds`, `remainingTokens` per endpoint.
- **CrudState**: NgRx slice for delete operations — key fields: `isLoading`, `activeDeleteOperations`, `deleteOperations[]`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 32 unit tests defined in Phase 10 of the test plan pass without failures or skips.
- **SC-002**: Each of the 8 spec files runs independently in under 5 seconds on a standard developer machine.
- **SC-003**: Statement coverage for all 8 components reaches or exceeds 80% as reported by the test runner.
- **SC-004**: Branch coverage for conditional status/display logic in `UploadItemComponent` and `UploadZoneComponent` reaches or exceeds 75%.
- **SC-005**: Zero real backend or WebSocket calls are made during the test suite execution — all external dependencies are mocked.
- **SC-006**: Adding a new upload status value to the ingestion state causes exactly one test to fail, demonstrating that the status-to-UI mapping is fully covered.

## Assumptions

- The test runner is Vitest (already configured in the project) — Karma/Jasmine is not used.
- Spectator version `^22.1.0` is already installed and compatible with Angular 21 standalone components.
- `provideMockStore` from `@ngrx/store/testing ^21.0.1` is available for all NgRx-dependent specs.
- All 8 component files listed in Phase 10 already exist in `src/app/features/ingestion/components/` — no new production code is required to write these tests.
- The `delete-all-button` and `delete-all-modal` are treated as separate spec files despite living in sibling directories, matching the test plan structure.
- Fake timers (`fakeAsync` / `tick`) are used for time-dependent assertions in `RateLimitIndicatorComponent` and `RateLimitToastComponent`.
- The `UploadZoneComponent` format validation targets file extension and size only; MIME-type sniffing is out of scope for Phase 10.
- No router-level integration tests (full page + routing) are required in Phase 10; those are deferred to Phase 11 (upload page). Parent-child `@ViewChild` wiring between a component and its modal child IS tested within Phase 10 by importing the real child modal as a standalone dependency — this does not constitute a page-level integration test.
