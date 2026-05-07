# Tasks: Workspace Page Integration Tests

**Input**: Design documents from `/specs/020-workspace-page-tests/`  
**Branch**: `020-workspace-page-tests`  
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md)

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story. All test tasks operate on a single spec file (`workspace.component.spec.ts`) co-located with the source.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (no dependencies on incomplete tasks in the same phase)
- **[Story]**: Which user story this task belongs to (US1–US4)
- Exact file paths included in every task description

---

## Phase 1: Setup (Production Template Fix)

**Purpose**: Add `ToastContainerComponent` to the workspace before the spec file is created. Without this fix, FR-006 tests will fail and the import in the spec skeleton will not compile.

**⚠️ CRITICAL**: Both tasks must complete before Phase 2 begins.

- [x] T001 Modify `src/app/pages/workspace/workspace.component.ts` — add `ToastContainerComponent` import from `../../shared/components/toast-container/toast-container.component` and include it in the `imports` array of the `@Component` decorator
- [x] T002 Modify `src/app/pages/workspace/workspace.component.html` — add `<app-toast-container></app-toast-container>` inside `.workspace-container`, after the `<main class="workspace-main">` closing tag

**Checkpoint**: `npm start` compiles without errors; `app-toast-container` renders in the workspace layout.

---

## Phase 2: Foundational (Spec Skeleton)

**Purpose**: Create the spec file with all imports, stub component declarations, and both `createComponentFactory` blocks (unit + integration). No test bodies yet — just the scaffolding all subsequent tasks depend on.

**⚠️ CRITICAL**: No test task (T004–T012) can execute before this phase is complete.

- [x] T003 Create `src/app/pages/workspace/workspace.component.spec.ts` — add all imports (`createComponentFactory`, `mockProvider`, `Spectator`, `MockStore`, `provideMockStore`, Vitest primitives, `WorkspaceComponent`, `UploadPageComponent`, `ChatPageComponent`, `ToastContainerComponent`, `NotificationService`, `mockFullIngestionState`, `buildChatState`); declare three stub components (`UploadPageStub`, `ChatPageStub`, `ToastContainerStub`) each with `standalone: true` and empty template; define `FULL_STATE` helper; create **unit** `describe('WorkspaceComponent')` factory with `overrideComponents` swapping all three children for stubs; create **integration** `describe('WorkspaceComponent [INTÉGRATION]')` factory with `overrideComponents` for `UploadPageStub`/`ChatPageStub` only (real `ToastContainerComponent` kept), `provideMockStore({ initialState: FULL_STATE() })`, and `mockProvider(NotificationService)`; add `beforeEach`/`afterEach` hooks in both blocks; implement all 9 `it()` test bodies with assertions

**Checkpoint**: `npm test -- --include="**/pages/workspace/**"` runs without compile errors; all stubs resolve; 9 test placeholders appear in the runner output (all failing — red phase confirmed).

---

## Phase 3: User Story 1 — Workspace Shell Renders Correctly (Priority: P1) 🎯 MVP

**Goal**: Verify the component mounts without errors and the two-panel layout containers are present in the DOM.

**Independent Test**: Run `npm test -- --include="**/pages/workspace/**"` — T004, T005, T006 pass; remaining tests still fail.

- [x] T004 [US1] Implement unit test body in `src/app/pages/workspace/workspace.component.spec.ts` — `it('doit créer le composant', ...)`: assert `spectator.component` is truthy
- [x] T005 [P] [US1] Implement unit test body in `src/app/pages/workspace/workspace.component.spec.ts` — `it('doit afficher la région sidebar (.workspace-sidebar)', ...)`: assert `spectator.query('.workspace-sidebar')` is not null
- [x] T006 [P] [US1] Implement unit test body in `src/app/pages/workspace/workspace.component.spec.ts` — `it('doit afficher la région main (.workspace-main)', ...)`: assert `spectator.query('.workspace-main')` is not null

**Checkpoint**: 3 of 9 tests green. User Story 1 independently validated.

---

## Phase 4: User Story 2 — Child Feature Pages Are Embedded (Priority: P1)

**Goal**: Verify `UploadPageComponent` selector appears inside `.workspace-sidebar` and `ChatPageComponent` selector appears inside `.workspace-main`.

**Independent Test**: T007 and T008 pass in addition to T004–T006 (cumulative green count: 5/9).

- [x] T007 [P] [US2] Implement unit test body in `src/app/pages/workspace/workspace.component.spec.ts` — `it('doit afficher app-upload-page dans la sidebar', ...)`: query `.workspace-sidebar`, assert it contains `spectator.query('app-upload-page')`
- [x] T008 [P] [US2] Implement unit test body in `src/app/pages/workspace/workspace.component.spec.ts` — `it('doit afficher app-chat-page dans le main', ...)`: query `.workspace-main`, assert it contains `spectator.query('app-chat-page')`

**Checkpoint**: 5 of 9 tests green. Both P1 user stories independently validated.

---

## Phase 5: User Story 3 — Toast Notifications Are Available Globally (Priority: P2)

**Goal**: Verify the toast container is present in the workspace DOM (unit) and that a real notification dispatched via `NotificationService` produces a visible toast element (integration).

**Independent Test**: T009, T010, T011 pass in addition to all earlier tests (cumulative: 8/9).

- [x] T009 [US3] Implement unit test body in `src/app/pages/workspace/workspace.component.spec.ts` — `it('doit afficher app-toast-container dans le workspace', ...)`: assert `spectator.query('app-toast-container')` is not null (inside the unit describe block where `ToastContainerStub` is active)
- [x] T010 [P] [US3] Implement integration test body in `src/app/pages/workspace/workspace.component.spec.ts` — `it('[INTÉGRATION] doit afficher les trois enfants simultanément', ...)`: assert `spectator.query('app-upload-page')`, `spectator.query('app-chat-page')`, and `spectator.query('app-toast-container')` are all non-null in the integration describe block
- [x] T011 [US3] Implement integration test body in `src/app/pages/workspace/workspace.component.spec.ts` — `it('[INTÉGRATION] doit afficher un toast quand NotificationService émet un succès', ...)`: inject `NotificationService` mock, call `spectator.inject(NotificationService).success('Test')`, call `spectator.detectChanges()`, assert the real `ToastContainerComponent` rendered a `.toast` element in the DOM

**Checkpoint**: 8 of 9 tests green. User Story 3 independently validated.

---

## Phase 6: User Story 4 — Full Upload-to-Chat Integration Flow (Priority: P3)

**Goal**: Verify the mock store reflects a dispatched rate-limit action, confirming shared store state is accessible within the workspace context.

**Independent Test**: T012 passes — all 9 tests green.

- [x] T012 [US4] Implement integration test body in `src/app/pages/workspace/workspace.component.spec.ts` — `it('[INTÉGRATION] doit être compatible avec le store complet (5 slices)', ...)`: call `store.refreshState()` and assert store and component are truthy

**Checkpoint**: All 9 tests green. Full spec passes. Red → green cycle complete.

---

## Phase 7: Polish & Validation

**Purpose**: CI gate verification, coverage confirmation, regression check across the full suite.

- [x] T013 Run `npm test -- --reporter=verbose --include="**/pages/workspace/**"` and confirm all 9 tests pass with no console errors or warnings
- [x] T014 [P] Run `npm test -- --coverage --include="**/pages/workspace/**"` and verify `workspace.component.ts` achieves 100% functional coverage with all child selectors and CSS classes verified (SC-005)
- [x] T015 [P] Run `npm test` (full suite) and confirm no pre-existing tests regressed; overall coverage remains above the thresholds: Statements ≥ 80%, Branches ≥ 75%, Functions ≥ 85%, Lines ≥ 80%

**Checkpoint**: Phase 13 complete. Coverage gates pass. Branch ready for review.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 (T001, T002) — `ToastContainerComponent` must be importable before spec file compiles
- **Phase 3 (US1)**: Depends on Phase 2 (T003) — spec skeleton must exist
- **Phase 4 (US2)**: Depends on Phase 2 (T003) — same spec file; can start in parallel with Phase 3
- **Phase 5 (US3)**: Depends on Phase 2 (T003) — can start in parallel with Phases 3 and 4
- **Phase 6 (US4)**: Depends on Phase 2 (T003) — can start in parallel with Phases 3–5
- **Phase 7 (Polish)**: Depends on Phases 3–6 all complete

### User Story Dependencies

- **US1 (P1)**: Needs T003 (spec skeleton) — no other story dependencies
- **US2 (P1)**: Needs T003 — no other story dependencies; runs in parallel with US1
- **US3 (P2)**: Needs T003 — no other story dependencies; runs in parallel with US1 and US2
- **US4 (P3)**: Needs T003 — no other story dependencies; runs in parallel with all prior stories

### Within Each User Story

- All `[P]`-marked tasks within a phase can be written simultaneously (they are `it()` blocks in the same file, no ordering requirement)
- `it()` bodies are independent within a single `describe` block

### Parallel Opportunities

Within Phase 3: T005, T006 can be written in parallel (both are simple DOM queries)  
Within Phase 4: T007, T008 can be written in parallel  
Within Phase 5: T010 can be written in parallel with T009 and T011  
Across phases: Once T003 is done, all four user story phases (3–6) can proceed in parallel

---

## Parallel Example: US1 + US2 (both P1)

```bash
# After T003 (spec skeleton) is done, all four unit tests can be written in parallel:
Task T005: "doit afficher la région sidebar (.workspace-sidebar)"
Task T006: "doit afficher la région main (.workspace-main)"
Task T007: "doit afficher app-upload-page dans la sidebar"
Task T008: "doit afficher app-chat-page dans le main"
```

---

## Implementation Strategy

### MVP First (User Stories 1 & 2 — both P1)

1. Complete Phase 1: Template fix (T001, T002)
2. Complete Phase 2: Spec skeleton (T003)
3. Complete Phase 3: US1 tests (T004–T006)
4. Complete Phase 4: US2 tests (T007–T008)
5. **STOP and VALIDATE**: Run `npm test -- --include="**/pages/workspace/**"` → 5/9 green
6. Confirm layout contract is verified and child-selector regressions will be caught

### Incremental Delivery

1. Phase 1 + 2 → Spec compiles, 0/9 green (red phase confirmed)
2. Phase 3 (US1) → 3/9 green (layout structure verified)
3. Phase 4 (US2) → 5/9 green (child embedding verified)
4. Phase 5 (US3) → 8/9 green (toast integration verified)
5. Phase 6 (US4) → 9/9 green (store dispatch verified)
6. Phase 7 → Coverage gates pass, no regressions

---

## Notes

- All test tasks (T004–T012) modify a single file: `src/app/pages/workspace/workspace.component.spec.ts`
- The `[P]` marker on test tasks means the `it()` bodies can be written simultaneously — they are independent within the describe block
- The constitution (Principle VIII) requires: `describe` label in English, `it()` labels in French imperative, `[INTÉGRATION]` prefix on integration tests
- `NO_ERRORS_SCHEMA` is prohibited — explicit stub components are required (FR-009)
- Commit convention after Phase 7: `test(phase-13): add workspace.component.spec — layout, child embedding, toast integration`
