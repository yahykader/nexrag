# Feature Specification: PHASE 11 — Ingestion Upload Page Test Suite

**Feature Branch**: `018-ingestion-pages-tests`  
**Created**: 2026-05-05  
**Status**: Draft  
**Input**: User description: "read agentic-ui-test-plan-speckit.md file and create a specification from PHASE 11 — src/app/features/ingestion/pages"

## Clarifications

### Session 2026-05-05

- Q: Should child components (`UploadZoneComponent`, `UploadItemComponent`, `ProgressPanelComponent`, `DeleteAllButtonComponent`) be imported as real standalone dependencies or mocked in the page spec? → A: Real standalone imports for all 4 child components — verifies template bindings end-to-end.
- Q: Should the `strategies$` selector and strategy display be covered by Phase 11 tests? → A: Assert `loadStrategies()` dispatch on `ngOnInit` only; no strategy display test (template has no strategy-selection UI).
- Q: Which upload mode should `startAllUploads()` tests cover — async only, sync only, or both? → A: Async mode only (`uploadFileAsync` dispatched) — matches the default `uploadMode = 'async'`; sync path is out of Phase 11 scope.
- Q: How should integration tests simulate store state evolution between steps? → A: `mockStore.overrideSelector(selector, value)` + `mockStore.refreshState()` between steps — targeted selector-level control, consistent with Phase 10 precedent.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Upload Page Initialization and Lifecycle (Priority: P1)

A developer working on `UploadPageComponent` can verify that the page initializes correctly on load — dispatching the WebSocket connection and strategy-load actions — renders all required child components in their initial state, and dispatches the disconnect action on teardown.

**Why this priority**: Page lifecycle (init/destroy) actions are the foundation of all real-time progress tracking; a regression here silently breaks WebSocket connectivity for all upload sessions.

**Independent Test**: Run the page component spec with `provideMockStore` and verify dispatched actions at `ngOnInit` and `ngOnDestroy` — fully self-contained and delivers confidence in the page bootstrap contract.

**Acceptance Scenarios**:

1. **Given** the component is instantiated, **When** `ngOnInit` fires, **Then** `ProgressActions.connectWebSocket()` is dispatched to the store.
2. **Given** the component is instantiated, **When** `ngOnInit` fires, **Then** `IngestionActions.loadStrategies()` is dispatched to the store.
3. **Given** the component is mounted and active, **When** the component is destroyed (`ngOnDestroy`), **Then** `ProgressActions.disconnectWebSocket()` is dispatched.
4. **Given** the page is rendered, **When** no uploads exist in the store, **Then** the empty-state element is visible.
5. **Given** the page is rendered, **When** the component initialises, **Then** `UploadZoneComponent` is present in the DOM.
6. **Given** the page is rendered, **When** the component initialises, **Then** `DeleteAllButtonComponent` is present in the DOM.

---

### User Story 2 - Upload Zone Interaction and File Dispatch (Priority: P1)

A developer can verify that selecting or dropping files on the upload zone causes the page to dispatch the correct ingestion action, and that the zone is correctly disabled when too many active uploads are running or the rate limit is active.

**Why this priority**: File dispatch is the critical handoff between the user's file selection and the NgRx ingestion pipeline; a broken dispatch silently swallows files.

**Independent Test**: Run `UploadPageComponent` specs with mocked store providing `activeUploads` and `isRateLimited` state; trigger the `filesSelected` output and assert dispatched actions — self-contained.

**Acceptance Scenarios**:

1. **Given** the upload zone emits a `filesSelected` event with a list of files, **When** `onFilesSelected()` is called, **Then** `IngestionActions.addFilesToUpload({ files })` is dispatched.
2. **Given** the store reports `isRateLimited = true`, **When** the component renders, **Then** the upload zone receives `disabled = true` as an input.
3. **Given** more than 5 active uploads are in the store, **When** the component renders, **Then** the upload zone receives `disabled = true`.
4. **Given** fewer than 5 active uploads and `isRateLimited = false`, **When** the component renders, **Then** the upload zone receives `disabled = false`.

---

### User Story 3 - Upload List Display by Status Group (Priority: P1)

A developer can verify that the page correctly groups and renders `UploadItemComponent` rows for pending, active, and completed uploads from the store, and that each group heading reflects the correct count.

**Why this priority**: Status grouping is the primary at-a-glance feedback for batch uploads; incorrect rendering hides files or misrepresents pipeline state.

**Independent Test**: Run `UploadPageComponent` specs with mocked store state providing uploads in each status group; assert the correct number of `UploadItemComponent` instances are rendered — self-contained.

**Acceptance Scenarios**:

1. **Given** the store holds 3 pending uploads, **When** the component renders, **Then** 3 `UploadItemComponent` instances appear under the "En attente" heading.
2. **Given** the store holds 2 active uploads, **When** the component renders, **Then** 2 `UploadItemComponent` instances appear under the "En cours" heading.
3. **Given** the store holds 4 completed uploads, **When** the component renders, **Then** 4 `UploadItemComponent` instances appear under the "Terminés" heading.
4. **Given** the `remove` output of an `UploadItemComponent` emits a `fileId`, **When** `removeUpload(fileId)` is called, **Then** `IngestionActions.removeUpload({ fileId })` is dispatched.

---

### User Story 4 - Upload Mode Toggle and Stats Display (Priority: P2)

A developer can verify that the Async/Sync mode toggle dispatches the correct action and that the stats cards surface upload metrics from the store.

**Why this priority**: Upload mode selection affects backend ingestion strategy; an incorrectly rendered toggle could silently switch users to a different processing mode.

**Independent Test**: Run `UploadPageComponent` specs with mocked store providing `uploadMode` and `stats` state; click the toggle button and assert the dispatched action and button active-class state.

**Acceptance Scenarios**:

1. **Given** the store reports `uploadMode = 'async'`, **When** the component renders, **Then** the Async button has the active class and the Sync button is the outline variant.
2. **Given** the user clicks the upload mode toggle button, **When** `toggleUploadMode()` is called, **Then** `IngestionActions.toggleUploadMode()` is dispatched.
3. **Given** the store provides stats (`total`, `success`, `errors`, `duplicates`, `rateLimited`), **When** the component renders, **Then** each stats card displays the correct value from the store.

---

### User Story 5 - Rate Limit Banner and Start Upload Guard (Priority: P2)

A developer can verify that the rate-limit warning banner is shown when the store signals a rate limit, the countdown is displayed, and the "Start all uploads" action is blocked while rate-limited.

**Why this priority**: The rate limit guard prevents failed uploads from silently consuming the user's quota; a missing guard causes confusing 429 errors with no user feedback.

**Independent Test**: Run `UploadPageComponent` specs with mocked store flipping `isRateLimited` and `retryAfterSeconds`; assert banner visibility and button disabled state.

**Acceptance Scenarios**:

1. **Given** the store reports `isRateLimited = false`, **When** the component renders, **Then** the rate-limit warning banner is not shown.
2. **Given** the store reports `isRateLimited = true` with `retryAfterSeconds = 30`, **When** the component renders, **Then** the warning banner is visible and displays "30s".
3. **Given** pending uploads exist and `isRateLimited = true`, **When** the "Uploader tous" button is rendered, **Then** it is disabled and shows rate-limit copy.
4. **Given** pending uploads exist, `isRateLimited = false`, and `uploadMode = 'async'`, **When** the user clicks "Uploader tous", **Then** `IngestionActions.uploadFileAsync` is dispatched for each pending file. The sync path (`uploadFile`) is out of Phase 11 scope.
5. **Given** the user clicks "Vider terminés", **When** `clearCompleted()` is called, **Then** `IngestionActions.clearCompletedUploads()` is dispatched.

---

### User Story 6 - Progress Panel and WebSocket Status (Priority: P2)

A developer can verify that `ProgressPanelComponent` is rendered only when active progress entries exist, and that the WebSocket status badge reflects the connection state from the store.

**Why this priority**: The progress panel is the sole source of real-time ingestion feedback; failing to render it when batches are active hides pipeline progress entirely.

**Independent Test**: Run `UploadPageComponent` specs with mocked store toggling `activeProgressCount` and `wsConnected`; assert the panel's presence and badge class.

**Acceptance Scenarios**:

1. **Given** the store reports `activeProgressCount = 0`, **When** the component renders, **Then** `ProgressPanelComponent` is not shown.
2. **Given** the store reports `activeProgressCount > 0`, **When** the component renders, **Then** `ProgressPanelComponent` is visible.
3. **Given** the store reports `wsConnected = true`, **When** the component renders, **Then** the WebSocket badge carries the success class.
4. **Given** the store reports `wsConnected = false`, **When** the component renders, **Then** the WebSocket badge carries the danger class.

---

### User Story 7 - Integration: Full Upload-to-Done Flow (Priority: P3)

A developer can verify the full page-level integration scenario: files are selected → upload actions are dispatched → the store transitions upload statuses → the completed list is shown.

**Why this priority**: End-to-end page integration catches wiring bugs invisible in unit tests — e.g., missing selector subscriptions, incorrect action payloads, or broken component input bindings.

**Independent Test**: Run the integration tests with `provideMockStore`; use `overrideSelector` + `refreshState()` to evolve state step by step and assert DOM state at each stage — self-contained, no real store or effects needed.

**Acceptance Scenarios**:

1. **Given** the page is initialized with an empty upload list, **When** the upload zone emits 2 files, **Then** 2 items appear in the pending list and `addFilesToUpload` is dispatched.
2. **Given** 2 pending uploads are in the store, **When** the store transitions them to `uploading` status, **Then** both items move to the "En cours" list and the pending list is empty.
3. **Given** uploads have completed, **When** the store transitions them to `success` status, **Then** they appear in the "Terminés" list, the active list is empty, and the empty-state element is hidden.

---

### Edge Cases

- What does the page render when all three upload lists are empty simultaneously (empty-state must be shown)?
- What happens when `startAllUploads()` is called but `pendingUploads$` emits an empty array?
- How does the rate-limit banner behave if `retryAfterSeconds` drops to 0 while the component is still mounted?
- What does the WebSocket badge show during the brief moment between component init and the first `wsConnected` emission?
- What happens if `IngestionActions.loadStrategies()` dispatched at init fails at the effect level — does the page still render?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The spec MUST use `createComponentFactory` from Spectator with `provideMockStore` — no real store, real HTTP calls, or real WebSocket connections.
- **FR-002**: The spec MUST verify that `ProgressActions.connectWebSocket()` and `IngestionActions.loadStrategies()` are dispatched on `ngOnInit`. The `strategies$` selector and any strategy-display UI are out of scope — only the dispatch action is tested.
- **FR-003**: The spec MUST verify that `ProgressActions.disconnectWebSocket()` is dispatched on `ngOnDestroy`.
- **FR-004**: The spec MUST verify that `UploadZoneComponent` receives `disabled = true` when `isRateLimited = true` in the store.
- **FR-005**: The spec MUST verify that `UploadZoneComponent` receives `disabled = true` when more than 5 active uploads are present in the store.
- **FR-006**: The spec MUST verify that `IngestionActions.addFilesToUpload({ files })` is dispatched when the `filesSelected` output fires.
- **FR-007**: The spec MUST cover all three upload status groups (pending, active, completed) and assert the correct number of `UploadItemComponent` instances rendered per group.
- **FR-008**: The spec MUST verify the rate-limit banner visibility and `retryAfterSeconds` display for both `isRateLimited = true` and `isRateLimited = false` states.
- **FR-009**: The spec MUST verify the "Uploader tous" button is disabled with rate-limit copy when `isRateLimited = true`. When `isRateLimited = false` and `uploadMode = 'async'`, clicking the button MUST result in `IngestionActions.uploadFileAsync` being dispatched for each pending file. The `uploadFile` (sync) dispatch path is out of Phase 11 scope.
- **FR-010**: The spec MUST verify `IngestionActions.toggleUploadMode()` is dispatched on toggle, and the active button class reflects the current `uploadMode` from the store.
- **FR-011**: The spec MUST verify `ProgressPanelComponent` is shown only when `activeProgressCount > 0` and hidden otherwise.
- **FR-012**: The spec MUST verify the WebSocket badge class reflects the `wsConnected` selector value.
- **FR-013**: At least 3 integration test cases MUST simulate state evolution across multiple steps using `mockStore.overrideSelector(selector, value)` + `mockStore.refreshState()` between steps. `mockStore.setState()` is not used — selector-level overrides ensure only the component's subscribed selectors are affected per step.
- **FR-014**: The spec MUST verify the empty-state element is visible when all three upload lists are empty.
- **FR-015**: All 10 test cases defined in Phase 11 of `agentic-ui-test-plan-speckit.md` (7 unit + 3 integration) MUST be covered without omission.
- **FR-016**: Specs MUST NOT modify production component files to accommodate tests.
- **FR-017**: All 4 child components (`UploadZoneComponent`, `UploadItemComponent`, `ProgressPanelComponent`, `DeleteAllButtonComponent`) MUST be imported as real standalone dependencies — not mocked — so that `[disabled]` bindings, `*ngFor` rendering, and output event wiring are verified end-to-end within the page spec. Each child component's own providers are satisfied via `mockProvider` where needed.

### Key Entities

- **UploadPageComponent**: The page-level shell that orchestrates all ingestion child components and owns the WebSocket lifecycle, upload dispatch, and mode toggle.
- **UploadFile**: Represents a single file in the ingestion queue — attributes: `id`, `file`, `status` (`pending | uploading | success | error | duplicate | rate-limited`), `batchId?`, `retryAfterSeconds?`.
- **IngestionState**: NgRx slice — key selectors exercised in tests: `selectPendingUploads`, `selectActiveUploads`, `selectCompletedUploads`, `selectStats`, `selectUploadMode`, `selectStrategies`.
- **ProgressState**: NgRx slice — key selectors: `selectActiveProgress`, `selectActiveProgressCount`, `selectWebSocketConnected`.
- **RateLimitState**: NgRx slice — key selectors: `selectIsRateLimited`, `selectRetryAfterSeconds`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 10 test cases defined in Phase 11 (7 unit + 3 integration) pass without failures or skips.
- **SC-002**: The single spec file runs to completion in under 10 seconds on a standard developer machine.
- **SC-003**: Statement coverage for `UploadPageComponent` reaches or exceeds 80% as reported by the test runner.
- **SC-004**: Branch coverage for conditional display logic (rate-limit banner, progress panel, empty-state, upload zone disabled) reaches or exceeds 75%.
- **SC-005**: Zero real WebSocket connections, real HTTP calls, or real NgRx effects execute during the test suite.
- **SC-006**: The 3 integration tests demonstrate DOM state changes across consecutive store state transitions without requiring any modifications to production code.

## Assumptions

- The test runner is Vitest (already configured in the project) — Karma/Jasmine is not used.
- Spectator `^22.1.0` is already installed and compatible with Angular 21 standalone components.
- `provideMockStore` from `@ngrx/store/testing ^21.0.1` is available for all NgRx-dependent specs.
- `UploadPageComponent` already exists at `src/app/features/ingestion/pages/upload-page/upload-page.component.ts` — no new production code is required to write these tests.
- Child components are imported as real standalone dependencies in the spec so that `@Input` bindings and output event wiring are exercised; this does not constitute a router-level integration test.
- The "rate-limit indicator" referenced in the Phase 11 test plan maps to the inline rate-limit banner implemented directly in `UploadPageComponent`'s template — no separate `RateLimitIndicatorComponent` file exists at the page level (it was covered in Phase 10 components).
- `fakeAsync` / `tick` are not required for Phase 11 tests — countdown timer behavior was covered in Phase 10.
- No router-level tests (guard resolution, route activation, navigation) are required in Phase 11; those are deferred to Phase 13 (workspace page).
- The `startAllUploads()` rate-limit guard is treated as a unit-testable page method; its internal subscription logic is covered by asserting either dispatched or non-dispatched actions from the mock store.
- The `strategies$` observable and strategy-selection UI are out of Phase 11 scope — no template renders strategies currently. Only the `loadStrategies()` dispatch is verified.
