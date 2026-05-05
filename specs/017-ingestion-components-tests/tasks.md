# Tasks: PHASE 10 — Ingestion Components Test Suite

**Input**: Design documents from `/specs/017-ingestion-components-tests/`
**Prerequisites**: plan.md ✅ · spec.md ✅ · research.md ✅ · data-model.md ✅ · quickstart.md ✅

**Organization**: Tasks are grouped by user story. Each user story phase produces one independently runnable spec file (or two for US4 and US6 which each cover two sibling components).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel with other [P] tasks in the same phase (different files, no shared state)
- **[Story]**: Maps to user story from spec.md (US1–US6)
- All paths are relative to `agentic-rag-ui/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create the shared test data fixtures used across all 8 spec files and confirm the test environment works.

- [X] T001 Create shared mock factory file at `src/app/features/ingestion/components/testing/ingestion-test.helpers.ts` with the following exports (see data-model.md for full types):
  - `mockUploadFile(overrides?)` — returns an `UploadFile` with `status:'pending'`, a `File` object named `'doc.pdf'`, `id:'file-1'`, and optional overrides
  - `mockUploadProgress(overrides?)` — returns an `UploadProgress` with `batchId:'batch-1'`, `percentage:50`, `currentStage:'PROCESSING'`, `isComplete:false`, `isError:false`
  - `mockRateLimitState(overrides?)` — returns an NgRx initial state slice `{ rateLimit: { isRateLimited:false, retryAfterSeconds:0, message:'', remainingTokens:{upload:null,...}, limits:{upload:10,batch:5,delete:20,search:50,default:30} } }`
  - `mockCrudState(overrides?)` — returns an NgRx initial state slice `{ crud: { isLoading:false, activeDeleteOperations:0, deleteOperations:[], batchInfos:{}, duplicateChecks:{}, systemStats:null, error:null } }`
  - `mockIngestionState(uploads?)` — returns `{ ingestion: { uploads: uploads ?? [], uploadMode:'async' } }`

- [X] T002 Verify existing Vitest + Spectator setup works by running `npm test -- --include="**/app.spec.ts"` from `agentic-rag-ui/`; confirm it exits 0 (no environment issues before writing Phase 10 specs)

**Checkpoint**: Shared helpers available; test environment confirmed working.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Apply the one required production code change before writing the `DeleteAllButtonComponent` spec. All other specs (T004–T006, T008–T011) can start in parallel after Phase 1.

**⚠️ CRITICAL for T007**: T007 cannot pass until T003 is complete.

- [X] T003 Update `src/app/features/ingestion/components/delete-all-button/delete-all-button.component.ts`:
  - Import `selectAllUploads` from `../../store/ingestion/ingestion.selectors` and `combineLatest` from `rxjs`
  - Replace the existing `isDeleting$: Observable<boolean>` with `isDisabled$: Observable<boolean>`
  - In the constructor: `this.isDisabled$ = combineLatest([this.store.select(selectCrudLoading), this.store.select(selectAllUploads)]).pipe(map(([loading, uploads]) => loading || uploads.length === 0))`
  - Update the HTML template binding from `[disabled]="isDeleting$ | async"` to `[disabled]="isDisabled$ | async"` (check `delete-all-button.component.html`)
  - Verify the component still compiles: `npx tsc --noEmit` from `agentic-rag-ui/`

**Checkpoint**: Production code change merged; T007 can now proceed.

---

## Phase 3: User Story 1 — File Upload Zone Validation (Priority: P1) 🎯 MVP

**Goal**: Verify that `UploadZoneComponent` correctly handles drag-and-drop, file filtering, size display, and rate-limit disabled state.

**Independent Test**: `npm test -- --include="**/upload-zone.component.spec.ts"` — all 6 tests pass.

- [X] T004 [P] [US1] Write `src/app/features/ingestion/components/upload-zone/upload-zone.component.spec.ts` with exactly 6 `it()` tests using `createComponentFactory` (standalone, imports `[CommonModule]`, `provideMockStore` with `mockRateLimitState()`):

  1. `'doit créer le composant'` — `expect(spectator.component).toBeTruthy()`
  2. `'doit appliquer la classe "drag-over" lors du dragover'` — dispatch a `dragover` event on the drop zone element via `spectator.dispatchMouseEvent` or `spectator.element.dispatchEvent(new DragEvent('dragover', { bubbles:true, cancelable:true }))`, then assert `spectator.element.querySelector('.upload-zone')` has class `drag-over` (or `isDragging === true`)
  3. `'doit émettre filesSelected avec les fichiers valides au drop'` — spy on `filesSelected` output, dispatch a `drop` DragEvent with a mock `FileList` containing one file under `maxFileSize`, assert emit called with the file array
  4. `'doit ne pas émettre filesSelected pour un fichier de taille 0'` — dispatch `drop` with a `File` of size 0, assert `filesSelected` was NOT emitted
  5. `'doit afficher la taille max autorisée formatée'` — set `spectator.setInput('maxFileSize', 5 * 1024 * 1024 * 1024)`, call `detectChanges()`, assert the template contains `'5'` and `'GB'`
  6. `'doit désactiver la zone quand isRateLimited est true'` — override `selectIsRateLimited` selector to emit `true`, refresh state, assert the drop zone has a disabled attribute or `disabled` class applied; dispatch a `drop` event and assert `filesSelected` was NOT emitted

  **Constitution compliance**: `describe('UploadZoneComponent', ...)`, all `it()` labels in French imperative. No real store.

**Checkpoint**: US1 spec passes independently.

---

## Phase 4: User Story 2 — Upload Item Status Display (Priority: P1)

**Goal**: Verify that `UploadItemComponent` maps all 6 upload statuses to correct icons/colors, handles delete button behavior per status, and guards the `batchId = undefined` edge case.

**Independent Test**: `npm test -- --include="**/upload-item.component.spec.ts"` — all 9 tests pass.

- [X] T005 [P] [US2] Write `src/app/features/ingestion/components/upload-item/upload-item.component.spec.ts` with exactly 9 `it()` tests:

  Setup: `createComponentFactory` with standalone imports `[CommonModule, DeleteBatchModalComponent]`, providers `[provideMockStore({ initialState: { ...mockCrudState(), progress: { entries:{}, isConnected:false } } }), mockProvider(NotificationService)]`. Add Bootstrap stub in `beforeEach`:
  ```ts
  (window as any).bootstrap = { Modal: class { constructor(_:any){} show(){} hide(){} static getInstance(_:any){ return null; } } };
  ```

  1. `'doit afficher le nom du fichier'` — `spectator.setInput('upload', mockUploadFile())`, assert filename visible in template
  2. `'doit afficher une barre de progression pour le statut "uploading"'` — set `status:'uploading'`, assert a `<progress>` or progress bar element is present
  3. `'doit afficher l\'icône de succès pour le statut "success"'` — set `status:'success'`, assert template contains Bootstrap icon class `bi-check-circle-fill`
  4. `'doit afficher l\'icône d\'erreur pour le statut "error"'` — set `status:'error'`, assert `bi-x-circle-fill` present
  5. `'doit afficher l\'icône d\'avertissement pour le statut "duplicate"'` — set `status:'duplicate'`, assert `bi-exclamation-triangle-fill` present
  6. `'doit afficher l\'icône par défaut pour le statut "rate-limited"'` — set `status:'rate-limited'`, assert `bi-file-earmark` (fallback default) present; assert `bi-check-circle-fill`, `bi-x-circle-fill`, `bi-exclamation-triangle-fill` are all absent
  7. `'doit dispatcher removeUpload au click supprimer quand statut est "pending"'` — set `status:'pending'`, spy `mockStore.dispatch`, click delete button, assert `IngestionActions.removeUpload({ fileId:'file-1' })` dispatched; assert `spectator.component.deleteModal.openModal` was NOT called
  8. `'doit ouvrir le modal de suppression quand statut est "success" et batchId est défini'` — set `status:'success', batchId:'batch-abc'`, spy on `spectator.component.deleteModal.openModal`, click delete button, use `fakeAsync` + `tick(0)`, assert `openModal` was called; assert `deleteModal.batchId === 'batch-abc'` and `deleteModal.filename === 'doc.pdf'`
  9. `'doit afficher une notification d\'erreur si batchId est undefined lors d\'une suppression "success"'` — set `status:'success', batchId:undefined`, spy `notificationService.error`, click delete button, assert `notificationService.error` was called; assert modal NOT opened

  **Constitution compliance**: 6 statuses covered (SC-006), `@ViewChild` wiring verified via real `DeleteBatchModalComponent` import.

**Checkpoint**: US2 spec passes independently.

---

## Phase 5: User Story 3 — Real-Time Progress Panel (Priority: P2)

**Goal**: Verify that `ProgressPanelComponent` reads active progress entries from the store, displays the global percentage, and hides itself when no batch is active.

**Independent Test**: `npm test -- --include="**/progress-panel.component.spec.ts"` — all 4 tests pass.

- [X] T006 [P] [US3] Write `src/app/features/ingestion/components/progress-panel/progress-panel.component.spec.ts` with exactly 4 `it()` tests:

  Setup: `createComponentFactory` with standalone imports `[CommonModule, AsyncPipe]`, providers `[provideMockStore({ initialState: { progress: { entries:{}, activeProgress:[], recentlyCompleted:[], isConnected:false } } })]`.

  1. `'doit créer le composant'` — `expect(spectator.component).toBeTruthy()`
  2. `'doit afficher le pourcentage d\'avancement quand des batches sont actifs'` — override `selectActiveProgress` to return `[mockUploadProgress({ percentage:75 })]`, `mockStore.refreshState()`, `detectChanges()`, assert template text contains `'75'`
  3. `'doit se connecter au sélecteur de progression du store'` — spy on `mockStore.select`, trigger `ngOnInit`, assert `selectActiveProgress` selector was called
  4. `'doit se masquer si aucun batch n\'est en cours'` — override `selectActiveProgress` to return `[]`, `detectChanges()`, assert the panel container has `*ngIf` falsy (element absent from DOM or has hidden class)

  **Constitution compliance**: No real WebSocket connections; store-only testing.

**Checkpoint**: US3 spec passes independently.

---

## Phase 6: User Story 4 — Delete All Confirmation Flow (Priority: P2)

**Goal**: Verify both the button's two disabled conditions and the modal's confirm/cancel contract.

**Independent Test**: `npm test -- --include="**/delete-all-button.component.spec.ts" --include="**/delete-all-modal.component.spec.ts"` — all 8 tests pass.

- [X] T007 [US4] Write `src/app/features/ingestion/components/delete-all-button/delete-all-button.component.spec.ts` with exactly 4 `it()` tests. **Depends on T003** (production change must be in place):

  Setup: `createComponentFactory` with standalone imports `[CommonModule, DeleteAllModalComponent]`, providers `[provideMockStore({ initialState: { ...mockCrudState(), ...mockIngestionState([]) } }), mockProvider(NotificationService)]`. Bootstrap stub in `beforeEach`.

  1. `'doit être disabled quand la liste d\'uploads est vide'` — initial state has empty uploads, `detectChanges()`, assert delete button has `disabled` attribute
  2. `'doit être disabled quand une suppression est en cours'` — override `selectCrudLoading` to `true`, `refreshState()`, `detectChanges()`, assert delete button has `disabled` attribute
  3. `'doit être actif quand la liste n\'est pas vide et aucune suppression en cours'` — override uploads to `[mockUploadFile()]` and `isLoading:false`, assert button does NOT have `disabled` attribute
  4. `'doit ouvrir le modal de confirmation au click quand le bouton est actif'` — set active state (uploads not empty), spy `spectator.component.modal.openModal`, click button, `tick(0)`, assert `openModal` called

- [X] T008 [P] [US4] Write `src/app/features/ingestion/components/delete-all-modal/delete-all-modal.component.spec.ts` with exactly 4 `it()` tests. No store dependency (pure component):

  Setup: `createComponentFactory({ component: DeleteAllModalComponent, imports: [CommonModule, FormsModule] })`. Bootstrap stub in `beforeEach`.

  1. `'doit afficher le message de confirmation à l\'étape 1'` — call `spectator.component.openModal()`, `detectChanges()`, assert heading contains `'Étape 1'`
  2. `'doit émettre confirmed au click "Confirmer" avec le texte DELETE_ALL_FILES saisi'` — advance modal to step 3 (`component.step = 3`), set `component.confirmationText = 'DELETE_ALL_FILES'`, spy `confirmed` output, call `component.validateAndDelete()`, assert `confirmed` emitted once
  3. `'doit émettre cancelled au click "Annuler"'` — spy `cancelled` output, call `component.cancel()`, assert `cancelled` emitted once
  4. `'doit réinitialiser l\'état à la fermeture'` — call `component.openModal()`, advance to step 2, call `component.cancel()`, assert `component.step === 1` and `component.confirmationText === ''` and `component.isDeleting === false`

**Checkpoint**: US4 spec passes independently (both files).

---

## Phase 7: User Story 5 — Delete Batch Modal (Priority: P2)

**Goal**: Verify `DeleteBatchModalComponent` correctly displays the batch filename, emits `confirmed` (void) when the user confirms, and emits `cancelled` without side effects.

**Independent Test**: `npm test -- --include="**/delete-batch-modal.component.spec.ts"` — all 3 tests pass.

- [X] T009 [P] [US5] Write `src/app/features/ingestion/components/delete-batch-modal/delete-batch-modal.component.spec.ts` with exactly 3 `it()` tests. No store dependency:

  Setup: `createComponentFactory({ component: DeleteBatchModalComponent, imports: [CommonModule] })`. Bootstrap stub in `beforeEach`. Important: `confirmed` emits `void` — the `batchId` is verified via `@Input()`, not via event payload (see research R-004).

  1. `'doit afficher le nom du fichier passé en entrée'` — `spectator.setInput('filename', 'rapport.pdf')`, `spectator.setInput('batchId', 'batch-xyz')`, `detectChanges()`, assert template contains `'rapport.pdf'`
  2. `'doit émettre confirmed (sans payload) et passer isDeleting à true au click confirmer'` — spy on `confirmed` output, call `component.confirm()`, assert `confirmed` emitted once; assert `component.isDeleting === true`
  3. `'doit émettre cancelled et ne pas modifier isDeleting au click annuler'` — spy on `cancelled` output, call `component.cancel()`, assert `cancelled` emitted once; assert `component.isDeleting === false`

**Checkpoint**: US5 spec passes independently.

---

## Phase 8: User Story 6 — Rate Limit Indicator and Toast (Priority: P3)

**Goal**: Verify `RateLimitIndicatorComponent` shows/hides based on rate-limit state and that `RateLimitToastComponent` displays the rate-limit message and hides when the limit clears.

**Independent Test**: `npm test -- --include="**/rate-limit-indicator.component.spec.ts" --include="**/rate-limit-toast.component.spec.ts"` — all 5 tests pass.

- [X] T010 [P] [US6] Write `src/app/features/ingestion/components/rate-limit-indicator/rate-limit-indicator.component.spec.ts` with exactly 3 `it()` tests:

  Setup: `createComponentFactory` with standalone imports `[CommonModule, AsyncPipe]`, providers `[provideMockStore({ initialState: mockRateLimitState() })]`. Note: countdown is driven by store effects (Phase 9) — do NOT use `fakeAsync`; use `mockStore.overrideSelector` + `refreshState()` to simulate state changes (see research R-006).

  1. `'doit se masquer quand isRateLimited est false'` — override `selectIsRateLimited` → `false`, `detectChanges()`, assert the indicator container is absent from DOM (or has `*ngIf false`)
  2. `'doit afficher le compte à rebours quand isRateLimited est true'` — override `selectIsRateLimited` → `true`, override `selectUploadRemaining` → `30`, `refreshState()`, `detectChanges()`, assert an element containing `'30'` is visible
  3. `'doit mettre à jour l\'affichage quand le store émet une nouvelle valeur de jetons restants'` — override `selectUploadRemaining` → `8`, `refreshState()`, `detectChanges()`, assert template now shows `'8'`

- [X] T011 [P] [US6] Write `src/app/features/ingestion/components/rate-limit-toast/rate-limit-toast.component.spec.ts` with exactly 2 `it()` tests:

  Setup: `createComponentFactory` with standalone imports `[CommonModule, AsyncPipe]`, providers `[provideMockStore({ initialState: mockRateLimitState() })]`.

  1. `'doit afficher le message de rate limit quand isRateLimited est true'` — override `selectIsRateLimited` → `true`, override `selectRateLimitMessage` → `'Limite dépassée'`, `refreshState()`, `detectChanges()`, assert template contains `'Limite dépassée'`
  2. `'doit se masquer quand isRateLimited repasse à false'` — start with `isRateLimited:true`, assert toast visible; override to `false`, `refreshState()`, `detectChanges()`, assert toast element absent or hidden

**Checkpoint**: US6 spec passes independently (both files).

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Validate the complete Phase 10 suite against all quality gates before marking the feature done.

- [X] T012 Run the full Phase 10 test suite from `agentic-rag-ui/`: `npm test -- --include="**/features/ingestion/components/**"` — verify all ~35 tests pass with 0 failures and 0 skips (SC-001); fix any failures before proceeding

- [X] T013 [P] Run coverage for Phase 10 components: `npm test -- --include="**/features/ingestion/components/**" --coverage` — verify reported metrics meet Constitution Principle IX thresholds: statements ≥ 80 %, branches ≥ 75 %, functions ≥ 85 %, lines ≥ 80 % (SC-003, SC-004); document any gaps and add missing tests if thresholds are not met

- [X] T014 Update `specs/017-ingestion-components-tests/spec.md`: change `**Status**: Draft` to `**Status**: Complete`

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1 (Setup: T001, T002)
  └─► Phase 2 (Foundational: T003)
        └─► T007 (DeleteAllButton spec — requires T003 production change)

Phase 1 (Setup: T001, T002)
  └─► Phase 3 (US1: T004) — can start immediately after T001
  └─► Phase 4 (US2: T005) — can start immediately after T001
  └─► Phase 5 (US3: T006) — can start immediately after T001
  └─► Phase 6 partial (US4: T008) — can start immediately after T001
  └─► Phase 7 (US5: T009) — can start immediately after T001
  └─► Phase 8 (US6: T010, T011) — can start immediately after T001

All spec files (T004–T011) ──► T012 (run full suite) ──► T013 (coverage)
T012 ──► T014 (mark Complete)
```

### User Story Dependencies

| Story | Priority | Depends On | Can Parallelize With |
|-------|----------|-----------|---------------------|
| US1 (UploadZone) | P1 | T001 | US2, US3, US4-modal, US5, US6 |
| US2 (UploadItem) | P1 | T001 | US1, US3, US4-modal, US5, US6 |
| US3 (ProgressPanel) | P2 | T001 | US1, US2, US4-modal, US5, US6 |
| US4 button (DeleteAllButton) | P2 | T001, **T003** | US4 modal can proceed early |
| US4 modal (DeleteAllModal) | P2 | T001 | All other stories |
| US5 (DeleteBatchModal) | P2 | T001 | All other stories |
| US6 indicator (RateLimitIndicator) | P3 | T001 | US6 toast |
| US6 toast (RateLimitToast) | P3 | T001 | US6 indicator |

### Within Each Phase

- Spec file setup (imports, factory, beforeEach) → individual `it()` tests
- Tests must be written in red phase (failing) before assertions can be verified (Constitution workflow)
- Commit per spec file: `test(phase-10): add <ComponentName>Spec — <brief>`

---

## Parallel Examples

### Immediate parallel start after T001 + T002

```bash
# All 5 of these can run simultaneously:
Task T004: "write upload-zone.component.spec.ts"
Task T005: "write upload-item.component.spec.ts"
Task T006: "write progress-panel.component.spec.ts"
Task T008: "write delete-all-modal.component.spec.ts"
Task T009: "write delete-batch-modal.component.spec.ts"
```

### After T003 unblocks:

```bash
Task T007: "write delete-all-button.component.spec.ts"
```

### Phase 8 (US6) parallel:

```bash
Task T010: "write rate-limit-indicator.component.spec.ts"
Task T011: "write rate-limit-toast.component.spec.ts"
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2 only — both P1)

1. Complete Phase 1 (T001, T002)
2. Complete T004 (UploadZone spec) + T005 (UploadItem spec) in parallel
3. **STOP and VALIDATE**: `npm test -- --include="**/upload-zone.component.spec.ts" --include="**/upload-item.component.spec.ts"` — 15 tests pass
4. These two specs cover the primary upload flow entry point and status feedback

### Incremental Delivery

1. Setup (T001, T002) → foundation ready
2. US1 + US2 (T004, T005 parallel) → P1 coverage done ✅
3. Production change (T003) → unblocks US4 button
4. US3 + US4 + US5 (T006, T007, T008, T009 parallel) → P2 coverage done ✅
5. US6 (T010, T011 parallel) → P3 coverage done ✅
6. Polish (T012, T013, T014) → all gates green, feature marked complete

---

## Notes

- `[P]` tasks use different files and have no shared state — safe to run concurrently
- Bootstrap modal stub is needed in `beforeEach` for: T005, T007, T008, T009
- `confirmed` in `DeleteBatchModalComponent` emits `void` — do NOT assert event payload (see research R-004)
- `RateLimitIndicatorComponent` tests use `mockStore.overrideSelector` + `refreshState()` — NOT `fakeAsync` (see research R-006)
- Commit message format per Constitution: `test(phase-10): add <SpecFileName> — <brief description>`
- T003 is a production code change — commit separately as `feat(delete-all-button): add empty-list disabled guard`
