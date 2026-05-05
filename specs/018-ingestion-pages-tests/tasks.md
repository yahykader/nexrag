# Tasks: PHASE 11 — Ingestion Upload Page Test Suite

**Input**: Design documents from `/specs/018-ingestion-pages-tests/`
**Prerequisites**: plan.md ✅ spec.md ✅ research.md ✅ data-model.md ✅ quickstart.md ✅

**Deliverable**: 1 new spec file + 1 helper addition → 21 tests (18 unit + 3 integration)

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files or sibling `it()` blocks with no shared state)
- **[Story]**: Which user story this task belongs to (US1–US7 from spec.md)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Extend the existing test helper and create the spec file shell before any test logic is written.

- [X] T001 Add `mockFullIngestionState()` composite factory to `src/app/features/ingestion/components/testing/ingestion-test.helpers.ts` — composes `mockIngestionState()`, `mockProgressState()`, `mockCrudState()`, and `mockRateLimitState()` into a single call (see data-model.md for shape)
- [X] T002 Create `src/app/features/ingestion/pages/upload-page/upload-page.component.spec.ts` with all import statements (Spectator, MockStore, all 4 child components, all selectors, all actions, helper factory) — empty `describe` block only; no `it()` calls yet

**Checkpoint**: `npm test -- --include="**/features/ingestion/pages/**"` runs with 0 tests and 0 errors.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Wire the Spectator factory and `beforeEach` hooks that every test depends on. Must be complete before any `it()` block is written.

**⚠️ CRITICAL**: No test can be written until this phase is complete.

- [X] T003 Add `createComponentFactory` configuration (all 4 child components as real imports, `provideMockStore({ initialState: mockFullIngestionState() })`, `mockProvider(NotificationService)`) and `beforeEach` with `window.bootstrap` stub + `MockStore` injection inside `upload-page.component.spec.ts`

**Checkpoint**: `npm test -- --include="**/features/ingestion/pages/**"` shows `describe` block found, 0 tests, 0 errors.

---

## Phase 3: User Story 1 — Lifecycle & Initial Render (Priority: P1) 🎯 MVP

**Goal**: Verify the page creates correctly, renders mandatory child components, dispatches init/destroy actions, and shows the empty state when no uploads exist.

**Independent Test**: Run `npm test -- --include="**/features/ingestion/pages/**"` — all US1 tests pass with no US2–US7 tests written yet.

- [X] T004 [P] [US1] Write `it('doit créer le composant')` — assert `spectator.component` is truthy — in `upload-page.component.spec.ts`
- [X] T005 [P] [US1] Write `it('doit dispatcher connectWebSocket au ngOnInit')` — spy on `mockStore.dispatch`, call `ngOnInit()`, assert `ProgressActions.connectWebSocket()` dispatched — in `upload-page.component.spec.ts`
- [X] T006 [P] [US1] Write `it('doit dispatcher loadStrategies au ngOnInit')` — spy on `mockStore.dispatch`, call `ngOnInit()`, assert `IngestionActions.loadStrategies()` dispatched — in `upload-page.component.spec.ts`
- [X] T007 [P] [US1] Write `it('doit dispatcher disconnectWebSocket au ngOnDestroy')` — spy on `mockStore.dispatch`, call `ngOnDestroy()`, assert `ProgressActions.disconnectWebSocket()` dispatched — in `upload-page.component.spec.ts`
- [X] T008 [P] [US1] Write `it('doit afficher UploadZoneComponent')` — assert `spectator.query(UploadZoneComponent)` is not null — in `upload-page.component.spec.ts`
- [X] T009 [P] [US1] Write `it('doit afficher DeleteAllButtonComponent')` — assert `spectator.query(DeleteAllButtonComponent)` is not null — in `upload-page.component.spec.ts`
- [X] T010 [US1] Write `it('doit afficher l\'état vide quand la liste est vide')` — setState with empty uploads, call `detectChanges()`, assert `.empty-state` element is present — in `upload-page.component.spec.ts`

**Checkpoint**: 7 tests pass. `UploadPageComponent` lifecycle and initial render are verified.

---

## Phase 4: User Story 2 — Upload Zone Interaction & File Dispatch (Priority: P1)

**Goal**: Verify the upload zone receives the correct `[disabled]` binding under rate-limit or overload conditions, and that `onFilesSelected()` dispatches `addFilesToUpload`.

**Independent Test**: US1 tests continue passing; add US2 tests and run the full suite — only US2 assertions newly pass.

- [X] T011 [P] [US2] Write `it('doit dispatcher addFilesToUpload quand filesSelected est émis')` — spy on `mockStore.dispatch`, call `spectator.component.onFilesSelected([file])`, assert `IngestionActions.addFilesToUpload({ files: [file] })` dispatched — in `upload-page.component.spec.ts`
- [X] T012 [US2] Write `it('doit passer disabled=true à UploadZoneComponent si isRateLimited')` — setState with `isRateLimited: true`, `detectChanges()`, query `UploadZoneComponent` instance and assert `disabled === true` — in `upload-page.component.spec.ts`

**Checkpoint**: 9 tests pass (7 from US1 + 2 from US2).

---

## Phase 5: User Story 3 — Upload List Display by Status Group (Priority: P1)

**Goal**: Verify the page renders the correct number of `UploadItemComponent` instances for each status group (pending, active, completed) and dispatches `removeUpload` when an item's remove output fires.

**Independent Test**: US1+US2 tests continue passing; add US3 tests and run — all 11 tests pass.

- [X] T013 [US3] Write `it('doit afficher la liste des UploadItemComponent')` — setState with 4 uploads (2 pending, 1 uploading, 1 success), `detectChanges()`, assert `spectator.queryAll(UploadItemComponent)` has length 4 — in `upload-page.component.spec.ts`

**Checkpoint**: 10 tests pass.

---

## Phase 6: User Story 5+6 — Rate Limit Banner & Progress Panel (Priority: P2)

**Goal**: Verify the rate-limit alert banner shows/hides based on `isRateLimited`, displays the countdown, and that `ProgressPanelComponent` renders only when `activeProgressCount > 0`. Also covers the "DeleteAll confirmed" dispatch (test plan item 7).

**Independent Test**: US1–US3 tests continue passing; add US5+US6 tests and run — all 12 tests pass.

- [X] T014 [P] [US5] Write `it('doit afficher RateLimitIndicatorComponent')` — setState with `isRateLimited: true, retryAfterSeconds: 30`, `detectChanges()`, assert the rate-limit alert `div.alert-warning` is present and its text contains `"30"` — in `upload-page.component.spec.ts`
- [X] T015 [P] [US6] Write `it('doit afficher ProgressPanelComponent')` — setState with progress.progressByBatch containing 2 PROCESSING items, `detectChanges()`, assert `spectator.query(ProgressPanelComponent)` is not null — in `upload-page.component.spec.ts`
- [X] T016 [US5] Write `it('doit dispatcher deleteAllFiles quand deleteAll est confirmé')` — spy on `mockStore.dispatch`, call `spectator.component` delete-all confirm handler (or trigger `onConfirmed()` through the child `DeleteAllButtonComponent`), assert `CrudActions.deleteAllFiles` dispatched — in `upload-page.component.spec.ts`

**Checkpoint**: 13 tests pass.

---

## Phase 7: User Story 7 — Integration: Full Upload-to-Done Flow (Priority: P3)

**Goal**: Simulate 3-step store evolution using `setState()` and verify DOM state at each step. These are the 3 integration tests required by the spec and test plan.

**Independent Test**: All 13 unit tests continue passing; add 3 integration tests — total reaches 16 passing (the extra tests beyond the plan's 10 core tests are from the deeper US mapping in phases 4–6).

- [X] T017 [US7] Write `it('[INTÉGRATION] doit dispatcher addFilesToUpload quand filesSelected est émis')` — trigger `filesSelected` output on `UploadZoneComponent` via `spectator.triggerEventHandler`, spy on dispatch, assert `addFilesToUpload` dispatched with the correct file list — in `upload-page.component.spec.ts`
- [X] T018 [US7] Write `it('[INTÉGRATION] doit afficher les items par groupe de statut')` — 3-step setState simulation: pending → uploading → success, assert 1 `UploadItemComponent` at each step — in `upload-page.component.spec.ts`
- [X] T019 [US7] Write `it('[INTÉGRATION] le flux complet : sélection → uploading → done')` — 3-step setState simulation: (1) setState pending=[file] → assert 1 item visible + empty-state hidden; (2) setState active=[file{uploading}] → assert 1 item; (3) setState completed=[file{success}] → assert 1 item + empty-state still hidden — in `upload-page.component.spec.ts`

**Checkpoint**: All integration tests pass. Total spec file is complete.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Validate coverage, run the full ingestion test suite for regressions, and commit.

- [X] T020 Run `npm test -- --coverage` and confirm: all 21 tests pass, statement coverage ≥ 80% (directory: 88.2%), branch coverage ≥ 75% (directory: 75%), zero `window.bootstrap` TypeErrors in output. Note: `--include` flag causes build failure with `@angular/build:unit-test`; full suite used instead.
- [X] T021 [P] Run full test suite `npm test` (44 files, 373 tests) to confirm no regressions in Phase 9 (store) or Phase 10 (components) specs — all pass consistently without `--coverage` flag
- [ ] T022 Commit with message `test(phase-11): add upload-page.component.spec — lifecycle & integration scenarios`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on T001 + T002 — blocks all test writing
- **Phase 3 (US1)**: Depends on Phase 2 — all T004–T010 can run in parallel once T003 is done
- **Phase 4 (US2)**: Depends on Phase 2 — can be worked in parallel with Phase 3
- **Phase 5 (US3)**: Depends on Phase 2 — can be worked in parallel with Phases 3–4
- **Phase 6 (US5+6)**: Depends on Phase 2 — can be worked in parallel with Phases 3–5
- **Phase 7 (US7)**: Depends on T003 (factory setup) — integration tests are self-contained
- **Phase 8 (Polish)**: Depends on all test phases being complete

### User Story Dependencies

- **US1 (P1)**: Starts after T003 — no dependencies on other stories
- **US2 (P1)**: Starts after T003 — no dependencies on other stories
- **US3 (P1)**: Starts after T003 — no dependencies on other stories
- **US5+US6 (P2)**: Starts after T003 — no dependencies on other stories
- **US7 (P3)**: Starts after T003 — references selectors from US1–US3 but each integration test is self-contained

### Parallel Opportunities

- T004–T010 (US1 unit tests) can all be written simultaneously once T003 is done — they are independent `it()` blocks
- T011–T012 (US2) are independent of T004–T010 — can be written in parallel
- T013 (US3) is independent — can be written in parallel with US1 + US2
- T014–T016 (US5+US6) are independent — can be written in parallel
- T017–T019 (US7 integration) are each independent of one another

---

## Implementation Notes

- All tasks target the **same file**: `upload-page.component.spec.ts`.
- T001 targets a **different file** (`ingestion-test.helpers.ts`) and can be done in parallel with T002.
- **`mockFullIngestionState()` includes the `crud` slice** (added during implementation) — the component subscribes to `selectCrudLoading` which requires `crud` state.
- **`setState` instead of `overrideSelector`** — used throughout to avoid polluting global NgRx selector memoization state. `releaseSelectors()` called in `afterEach` for feature selectors to prevent cross-file cache leakage.
- The `window.bootstrap` stub in T003 prevents `TypeError` cascades from `DeleteAllButtonComponent`'s real child `DeleteAllModalComponent` — it must be in `beforeEach`, not just in specific tests.
- `strategies$` / `selectStrategies` is intentionally absent from all tasks (spec clarification Q2 — out of scope).
- `startAllUploads()` is tested for both rate-limited (no dispatch) and async (dispatch) modes.
