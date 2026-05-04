# Tasks: Phase 9 — Ingestion Store NgRx Tests

**Input**: Design documents from `specs/016-ingestion-store-ngrx/`  
**Prerequisites**: plan.md ✅ · spec.md ✅ · research.md ✅ · data-model.md ✅ · contracts/ ✅ · quickstart.md ✅

**Tests**: This feature IS the test suite — every task (T005 onwards) produces `*.spec.ts` files or required production code additions.  
**TDD flow**: Write `it()` blocks → confirm RED → (production code already exists or add the required change) → confirm GREEN → verify coverage.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on other incomplete tasks)
- **[Story]**: User story this task belongs to (US1=CRUD, US2=Ingestion, US3=Progress, US4=RateLimit)

## Path Convention

All spec files are co-located in:  
`agentic-rag-ui/src/app/features/ingestion/store/`

---

## Phase 1: Setup (Verify Test Infrastructure)

**Purpose**: Confirm all required testing packages are present before writing any spec.

- [ ] T001 Verify `@ngrx/store/testing`, `@ngrx/effects/testing`, and `@ngneat/spectator` are listed in devDependencies in `agentic-rag-ui/package.json`
- [ ] T002 Verify Vitest is configured in `agentic-rag-ui/vite.config.ts` or `vitest.config.ts` with `coverage.reporter` set (advisory — no threshold enforcement per Clarification Q4/SC-002)

**Checkpoint**: If T001 fails, install missing packages before proceeding. T002 failure means coverage reports won't be generated — fix config but do not block progress.

---

## Phase 2: Foundational (Production Code Additions + Shared Helpers)

**Purpose**: Two minimal production code changes required by FR-015 and FR-016, plus shared test helper factories used across all 4 sub-store spec sets.

**⚠️ CRITICAL**: T003 and T004 must complete before their respective spec files are run (tests will FAIL without them). T005 must complete before US1–US4 specs are started.

- [ ] T003 Add no-op guard to `agentic-rag-ui/src/app/features/ingestion/store/progress/progress.reducer.ts` in the `progressUpdate` handler (FR-015):
  - Locate the `on(ProgressActions.progressUpdate, (state, { progress }) => {` handler
  - Add as the very first line inside the handler body (before the existing `if (progress.stage === 'COMPLETED')` check):
    ```typescript
    if (!state.subscribedBatches.includes(progress.batchId)) {
      return state;
    }
    ```
  - Verify the file compiles: run `npx tsc --noEmit` from `agentic-rag-ui/`

- [ ] T004 Add `handleUploadRateLimited$` effect to `agentic-rag-ui/src/app/features/ingestion/store/rate-limit/rate-limit.effects.ts` (FR-016):
  - Add import at top of file: `import * as IngestionActions from '../ingestion/ingestion.actions';`
  - Add the following effect inside the `RateLimitEffects` class (after `autoReset$`):
    ```typescript
    handleUploadRateLimited$ = createEffect(() =>
      this.actions$.pipe(
        ofType(IngestionActions.uploadFileRateLimited),
        map(({ retryAfterSeconds, message }) =>
          RateLimitActions.rateLimitExceeded({ retryAfterSeconds, message })
        )
      )
    );
    ```
  - Add `map` to existing rxjs imports if not already present
  - Verify the file compiles: run `npx tsc --noEmit` from `agentic-rag-ui/`

- [ ] T005 Add Phase 9 shared mock factories to `agentic-rag-ui/src/app/test-helpers.ts` (append to existing file):
  - Import `UploadFile` from `./features/ingestion/store/ingestion/ingestion.state`
  - Import `UploadProgress` from `./core/services/websocket-progress.service`
  - Add and export:
    ```typescript
    export const mockUploadFile = (overrides: Partial<UploadFile> = {}): UploadFile => ({
      id: 'upload_test_1',
      file: new File(['content'], 'test.pdf', { type: 'application/pdf' }),
      progress: 0,
      status: 'pending' as const,
      ...overrides
    });

    export const mockUploadProgress = (overrides: Partial<UploadProgress> = {}): UploadProgress => ({
      batchId: 'batch-test-1',
      filename: 'test.pdf',
      stage: 'PROCESSING',
      percentage: 50,
      message: 'Processing...',
      ...overrides
    });
    ```
  - Verify the file compiles: run `npx tsc --noEmit` from `agentic-rag-ui/`

**Checkpoint**: T003, T004, T005 must all compile cleanly before any spec file is created.

---

## Phase 3: User Story 1 — CRUD Delete Operations Are Fully Verified (Priority: P1) 🎯 MVP

**Goal**: Verify all reducer handlers, action type strings, selector projections, and effects for the `crud/` sub-store — covering all delete, duplicate-check, batch-info, and system-stats operations.

**Independent Test**: Run `npm test -- --reporter=verbose store/crud` from `agentic-rag-ui/` — all 35 tests pass.

- [ ] T006 [P] [US1] Create `agentic-rag-ui/src/app/features/ingestion/store/crud/crud.actions.spec.ts`:
  - Import `* as CrudActions` from `./crud.actions`
  - Wrap in `describe('CrudActions', () => { ... })`
  - Add the following 8 `it()` blocks:
    - `it('deleteFile doit avoir le type "[CRUD] Delete File" avec embeddingId et fileType', () => { const a = CrudActions.deleteFile({ embeddingId: 'e1', fileType: 'text' }); expect(a.type).toBe('[CRUD] Delete File'); expect(a.embeddingId).toBe('e1'); })`
    - `it('deleteFileSuccess doit avoir le type "[CRUD] Delete File Success" avec response', ...)`— create with `{ response: { embeddingId: 'e1', deletedCount: 1, message: 'ok', batchId: undefined } }`, assert type and `action.response.deletedCount === 1`
    - `it('deleteBatch doit avoir le type "[CRUD] Delete Batch" avec batchId', ...)` — assert type and `action.batchId === 'b1'`
    - `it('deleteAllFiles doit avoir le type "[CRUD] Delete All Files" avec confirmation', ...)` — create with `{ confirmation: 'DELETE_ALL' }`, assert type and confirmation prop
    - `it('checkDuplicate doit avoir le type "[CRUD] Check Duplicate" avec file', ...)` — create with `{ file: new File([], 'test.pdf') }`, assert type
    - `it('getBatchInfo doit avoir le type "[CRUD] Get Batch Info" avec batchId', ...)` — assert type and `action.batchId`
    - `it('getSystemStats doit avoir le type "[CRUD] Get System Stats" sans payload', ...)` — assert only `action.type === '[CRUD] Get System Stats'`
    - `it('clearAll doit avoir le type "[CRUD] Clear All" sans payload', ...)` — assert type

- [ ] T007 [US1] Create `agentic-rag-ui/src/app/features/ingestion/store/crud/crud.reducer.spec.ts`:
  - Import `crudReducer` from `./crud.reducer`; `initialCrudState` from `./crud.state`; `* as CrudActions` from `./crud.actions`
  - Wrap in `describe('CrudReducer', () => { ... })`
  - Add the following 13 `it()` blocks:
    - `it('doit retourner l\'état initial', () => { expect(crudReducer(undefined, { type: '@@INIT' })).toEqual(initialCrudState); })`
    - `it('deleteFile doit passer loading à true et incrémenter activeDeleteOperations', () => { const s = crudReducer(initialCrudState, CrudActions.deleteFile({ embeddingId: 'e1', fileType: 'text' })); expect(s.loading).toBe(true); expect(s.activeDeleteOperations).toBe(1); expect(s.deleteOperations[0].status).toBe('pending'); expect(s.deleteOperations[0].type).toBe('file'); })`
    - `it('deleteFileSuccess doit passer loading à false et mettre à jour le statut success', () => { ... })` — start from state after `deleteFile`; dispatch `deleteFileSuccess` with matching `embeddingId`; assert `loading: false`, matched operation `status: 'success'`, `activeDeleteOperations: 0`
    - `it('deleteFileError doit stocker l\'erreur et décrémenter activeDeleteOperations', () => { ... })` — assert `loading: false`, `error: 'fail'`, `activeDeleteOperations: 0`, matched operation `status: 'error'`
    - `it('activeDeleteOperations ne doit jamais descendre en dessous de 0 (Math.max guard)', () => { const s = crudReducer(initialCrudState, CrudActions.deleteFileSuccess({ response: { embeddingId: 'e1', deletedCount: 0, message: 'ok', batchId: undefined } })); expect(s.activeDeleteOperations).toBe(0); })` — dispatch success against initial state (counter=0), assert still 0
    - `it('deleteBatch doit créer une opération de type "batch" en statut "pending"', () => { ... })` — assert `deleteOperations[0].type === 'batch'`, `targetId === 'b1'`
    - `it('deleteBatchSuccess doit retirer le batchId du cache batchInfos', () => { ... })` — prime state with a `batchInfos['b1']` entry; dispatch `deleteBatchSuccess`; assert `state.batchInfos['b1']` is undefined
    - `it('deleteAllFiles doit créer une opération de type "all"', () => { ... })` — assert `deleteOperations[0].type === 'all'`, `targetId === 'all-files'`
    - `it('deleteAllFilesSuccess doit vider batchInfos et duplicateChecks', () => { ... })` — prime state with entries in both maps; dispatch `deleteAllFilesSuccess`; assert both are `{}`
    - `it('checkDuplicateSuccess doit stocker le résultat par filename', () => { ... })` — dispatch with `{ response: { filename: 'test.pdf', isDuplicate: true, existingBatchId: 'b1', message: 'dupe', batchId: 'b1' } }`; assert `state.duplicateChecks['test.pdf'].isDuplicate === true`
    - `it('getBatchInfoSuccess doit stocker les infos du batch par batchId', () => { ... })` — dispatch with `{ response: { batchId: 'b1', found: true, totalEmbeddings: 5, textEmbeddings: 3, imageEmbeddings: 2, message: 'ok' } }`; assert `state.batchInfos['b1'].totalEmbeddings === 5`
    - `it('getSystemStatsSuccess doit mettre à jour systemStats avec lastUpdate', () => { ... })` — dispatch with `{ stats: { totalEmbeddings: 100, redisHealthy: true } }`; assert `state.systemStats.totalEmbeddings === 100` and `state.systemStats.lastUpdate` is a `Date` instance
    - `it('clearAll doit retourner initialCrudState', () => { ... })` — prime state with data in all fields; dispatch `clearAll()`; assert `expect(state).toEqual(initialCrudState)`

- [ ] T008 [P] [US1] Create `agentic-rag-ui/src/app/features/ingestion/store/crud/crud.selectors.spec.ts`:
  - Import all named selectors from `./crud.selectors`; `initialCrudState, CrudState` from `./crud.state`
  - Define helper: `const mockState = (overrides: Partial<CrudState> = {}) => ({ crud: { ...initialCrudState, ...overrides } })`
  - Wrap in `describe('CrudSelectors', () => { ... })`
  - Add the following 7 `it()` blocks:
    - `it('selectCrudLoading doit retourner false par défaut', () => { expect(selectCrudLoading(mockState() as any)).toBe(false); })`
    - `it('selectCrudError doit retourner null par défaut', () => { expect(selectCrudError(mockState() as any)).toBeNull(); })`
    - `it('selectDeleteOperations doit retourner la liste complète des opérations', () => { ... })` — prime with one entry; assert length 1 and correct `targetId`
    - `it('selectActiveDeleteOperations doit retourner le compteur actif', () => { ... })` — prime with `activeDeleteOperations: 3`; assert 3
    - `it('selectPendingDeleteOperations doit filtrer uniquement les opérations pending', () => { ... })` — prime with 2 operations (1 pending, 1 success); assert result length 1
    - `it('selectSystemHealthy doit retourner false si redisHealthy est undefined', () => { expect(selectSystemHealthy(mockState() as any)).toBe(false); })`
    - `it('selectTotalEmbeddings doit retourner 0 par défaut', () => { expect(selectTotalEmbeddings(mockState() as any)).toBe(0); })`

- [ ] T009 [US1] Create `agentic-rag-ui/src/app/features/ingestion/store/crud/crud.effects.spec.ts`:
  - Imports: `createServiceFactory, SpectatorService, mockProvider` from `@ngneat/spectator/jest`; `provideMockActions` from `@ngrx/effects/testing`; `provideMockStore` from `@ngrx/store/testing`; `Subject, of, throwError` from `rxjs`; `CrudEffects` from `./crud.effects`; `CrudApiService` from `../../../../core/services/crud-api.service`; `NotificationService` from `../../../../core/services/notification.service`; `* as CrudActions` from `./crud.actions`; `initialCrudState` from `./crud.state`
  - Declare `let actions$: Subject<any>` in describe scope; reset in `beforeEach`
  - Use `createServiceFactory` with `providers: [mockProvider(CrudApiService), mockProvider(NotificationService)]` and `overrideProviders: [provideMockActions(() => actions$), provideMockStore({ initialState: { crud: initialCrudState } })]`
  - Wrap in `describe('CrudEffects', () => { ... })`
  - Add the following `it()` blocks:
    - `it('deleteFile$ doit appeler CrudApiService.deleteFile() et dispatcher deleteFileSuccess', (done) => { ... })` — mock `crudApi.deleteFile.mockReturnValue(of({ embeddingId: 'e1', deletedCount: 1, message: 'ok', batchId: undefined }))`, subscribe to `effect.deleteFile$`, dispatch `CrudActions.deleteFile({ embeddingId: 'e1', fileType: 'text' })`, assert dispatched action type is `'[CRUD] Delete File Success'`
    - `it('deleteFile$ doit dispatcher deleteFileError si l\'API échoue', (done) => { ... })` — mock `crudApi.deleteFile.mockReturnValue(throwError(() => new Error('net error')))`, assert dispatched action type is `'[CRUD] Delete File Error'`
    - `it('deleteBatch$ doit appeler CrudApiService.deleteBatch() et dispatcher deleteBatchSuccess', (done) => { ... })`
    - `it('deleteAllFiles$ doit appeler CrudApiService.deleteAllFiles() avec la confirmation', (done) => { ... })` — mock `crudApi.deleteAllFiles.mockReturnValue(of({ deletedCount: 5, message: 'done', embeddingId: undefined, batchId: undefined }))`, assert `crudApi.deleteAllFiles` called with `'DELETE_ALL'`
    - `it('checkDuplicate$ doit appeler CrudApiService.checkDuplicate() et dispatcher checkDuplicateSuccess', (done) => { ... })`
    - `it('getBatchInfo$ doit dispatcher getBatchInfoSuccess quand l\'API réussit', (done) => { ... })`
    - `it('deleteBatchErrorNotification$ doit appeler NotificationService.error() (dispatch:false)', () => { ... })` — subscribe to `effect.deleteBatchErrorNotification$`, dispatch `CrudActions.deleteBatchError({ batchId: 'b1', error: 'fail' })`, assert `notificationService.error` was called

- [ ] T010 [US1] Run Phase 3 validation: `npm test -- --reporter=verbose store/crud` from `agentic-rag-ui/` — confirm all 35 `it()` blocks are GREEN; run `npm test -- --coverage store/crud` and confirm branch coverage advisory noted

**Checkpoint**: All 4 CRUD spec files pass. US1 is independently deliverable.

---

## Phase 4: User Story 2 — Upload Lifecycle State Is Correctly Managed (Priority: P1)

**Goal**: Verify all reducer handlers, action type strings, selector projections, and effects for the `ingestion/` sub-store — covering all 6 upload statuses, cross-store action handlers, mode toggle, and async/sync upload effects.

**Independent Test**: Run `npm test -- --reporter=verbose store/ingestion` from `agentic-rag-ui/` — all 35 tests pass.

- [ ] T011 [P] [US2] Create `agentic-rag-ui/src/app/features/ingestion/store/ingestion/ingestion.actions.spec.ts`:
  - Import `* as IngestionActions` from `./ingestion.actions`
  - Wrap in `describe('IngestionActions', () => { ... })`
  - Add the following 10 `it()` blocks:
    - `it('addFilesToUpload doit avoir le type "[Ingestion] Add Files To Upload" avec files[]', () => { const a = IngestionActions.addFilesToUpload({ files: [] }); expect(a.type).toBe('[Ingestion] Add Files To Upload'); })`
    - `it('uploadFileAsync doit avoir le type "[Ingestion] Upload File Async" avec fileId et file', ...)` — create with `{ fileId: 'f1', file: new File([], 't.pdf') }`, assert type and `action.fileId`
    - `it('uploadFileAsyncAccepted doit avoir le type "[Ingestion] Upload File Async Accepted" avec batchId', ...)` — create with `{ fileId: 'f1', response: { batchId: 'b1', message: 'ok', duplicate: false } as any }`, assert type and `action.response.batchId`
    - `it('uploadFileRateLimited doit avoir le type "[Ingestion] Upload File Rate Limited" avec retryAfterSeconds', ...)` — assert type and `action.retryAfterSeconds === 30`
    - `it('uploadFileDuplicate doit avoir le type "[Ingestion] Upload File Duplicate" avec existingBatchId', ...)` — assert type and `action.existingBatchId === 'b0'`
    - `it('updateUploadStatus doit accepter tous les statuts valides', ...)` — create with status `'error'`; assert type; create with status `'rate-limited'`; assert type (same action, different status)
    - `it('removeFile doit avoir le type "[Ingestion] Remove File" avec fileId', ...)` — assert type and `action.fileId`
    - `it('clearAllFiles doit avoir le type "[Ingestion] Clear All Files" sans payload', ...)` — assert only type
    - `it('setUploadMode doit accepter "sync" et "async"', ...)` — assert type for both values
    - `it('toggleUploadMode doit avoir le type "[Ingestion] Toggle Upload Mode" sans payload', ...)` — assert type

- [ ] T012 [US2] Create `agentic-rag-ui/src/app/features/ingestion/store/ingestion/ingestion.reducer.spec.ts`:
  - Import `ingestionReducer` from `./ingestion.reducer`; `initialState` from `./ingestion.state`; `* as IngestionActions` from `./ingestion.actions`; `* as CrudActions` from `../crud/crud.actions`; `* as ProgressActions` from `../progress/progress.actions`; `mockUploadFile` from `../../../../../test-helpers`
  - Wrap in `describe('IngestionReducer', () => { ... })`
  - Add the following 14 `it()` blocks:
    - `it('doit retourner l\'état initial avec uploadMode "async"', () => { const s = ingestionReducer(undefined, { type: '@@INIT' }); expect(s).toEqual(initialState); expect(s.uploadMode).toBe('async'); })`
    - `it('addFilesToUpload doit ajouter les fichiers avec statut "pending"', () => { ... })` — dispatch with `{ files: [new File([], 'a.pdf'), new File([], 'b.pdf')] }`, assert `s.uploads.length === 2` and both `status === 'pending'` and `s.stats.total === 2`
    - `it('uploadFileRateLimited doit passer le statut à "rate-limited" et stocker retryAfterSeconds', () => { ... })` — prime state with one `mockUploadFile({ id: 'f1', status: 'uploading' })`; dispatch `uploadFileRateLimited({ fileId: 'f1', retryAfterSeconds: 30, message: 'limit' })`; assert status `'rate-limited'`, `retryAfterSeconds: 30`, `stats.rateLimited: 1`
    - `it('uploadFileRateLimited est idempotent (ne pas double-incrémenter rateLimited)', () => { ... })` — dispatch `uploadFileRateLimited` twice for the same file already in `rate-limited` status; assert `stats.rateLimited === 1`
    - `it('uploadFileDuplicate doit passer le statut à "duplicate" avec existingBatchId', () => { ... })` — assert status `'duplicate'`, `existingBatchId: 'b0'`, `stats.duplicates: 1`
    - `it('removeFile doit retirer l\'upload de la liste sans toucher aux autres', () => { ... })` — prime with 2 uploads; remove one; assert length 1 and correct id remains
    - `it('clearAllFiles doit vider uploads[] et retourner initialState', () => { ... })` — dispatch; assert `s.uploads.length === 0` and `s.stats.total === 0`
    - `it('toggleUploadMode doit inverser uploadMode de "async" à "sync"', () => { ... })` — assert after first toggle: `'sync'`; after second: `'async'`
    - `it('clearCompletedUploads doit supprimer success, error, duplicate, rate-limited mais garder pending et uploading', () => { ... })` — prime state with 6 uploads (one of each status); dispatch `clearCompletedUploads()`; assert `uploads.length === 2` (pending + uploading only)
    - Cross-store: `it('[CrudActions.deleteBatch] doit retirer optimistement les uploads du batch supprimé', () => { ... })` — prime with uploads having `batchId: 'b1'` and `existingBatchId: 'b1'`; dispatch `CrudActions.deleteBatch({ batchId: 'b1' })`; assert those uploads are removed
    - Cross-store: `it('[CrudActions.deleteAllFilesSuccess] doit vider uploads[]', () => { ... })` — prime with 3 uploads; dispatch; assert `uploads.length === 0`
    - Cross-store: `it('[ProgressActions.progressUpdate stage=COMPLETED] doit mettre à jour le statut à "success"', () => { ... })` — prime with `mockUploadFile({ id: 'f1', batchId: 'b1', status: 'uploading' })`; dispatch `progressUpdate({ progress: { batchId: 'b1', stage: 'COMPLETED', percentage: 100, filename: 'test.pdf', message: 'done' } as any })`; assert `uploads[0].status === 'success'` and `progress === 100`
    - Cross-store: `it('[ProgressActions.progressUpdate stage=ERROR] doit mettre à jour le statut à "error"', () => { ... })` — assert `uploads[0].status === 'error'`
    - `it('updateUploadStatus doit mettre à jour batchId et existingBatchId optionnels', () => { ... })` — dispatch `updateUploadStatus({ fileId: 'f1', status: 'success', batchId: 'b1', existingBatchId: 'b0' })`; assert both props stored

- [ ] T013 [P] [US2] Create `agentic-rag-ui/src/app/features/ingestion/store/ingestion/ingestion.selectors.spec.ts`:
  - Import all selectors from `./ingestion.selectors`; `initialState, IngestionState` from `./ingestion.state`; `mockUploadFile` from `../../../../../test-helpers`
  - Define helper: `const mockState = (overrides: Partial<IngestionState> = {}) => ({ ingestion: { ...initialState, ...overrides } })`
  - Wrap in `describe('IngestionSelectors', () => { ... })`
  - Add the following 6 `it()` blocks:
    - `it('selectUploads doit retourner un tableau vide par défaut', () => { expect(selectUploads(mockState() as any)).toEqual([]); })`
    - `it('selectPendingUploads doit filtrer uniquement les uploads en "pending"', () => { ... })` — prime with pending + uploading; assert length 1
    - `it('selectActiveUploads doit filtrer uniquement les uploads en "uploading"', () => { ... })`
    - `it('selectCompletedUploads doit retourner success, error, duplicate (PAS rate-limited)', () => { ... })` — prime with 4 uploads (success/error/duplicate/rate-limited); assert length 3; assert none has status `'rate-limited'`
    - `it('selectUploadMode doit retourner "async" par défaut', () => { expect(selectUploadMode(mockState() as any)).toBe('async'); })`
    - `it('selectRateLimitedUploads doit filtrer les uploads rate-limited', () => { ... })` — assert length matches only rate-limited items

- [ ] T014 [US2] Create `agentic-rag-ui/src/app/features/ingestion/store/ingestion/ingestion.effects.spec.ts`:
  - Imports: `createServiceFactory, mockProvider` from `@ngneat/spectator/jest`; `provideMockActions` from `@ngrx/effects/testing`; `provideMockStore` from `@ngrx/store/testing`; `Subject, of, throwError` from `rxjs`; `IngestionEffects` from `./ingestion.effects`; `IngestionApiService` from `../../../../core/services/ingestion-api.service`; `* as IngestionActions` from `./ingestion.actions`; `* as ProgressActions` from `../progress/progress.actions`; `initialState` from `./ingestion.state`
  - Wrap in `describe('IngestionEffects', () => { ... })`
  - Add the following 5 `it()` blocks:
    - `it('uploadFileAsync$ doit dispatcher uploadFileAsyncAccepted quand l\'API réussit', (done) => { ... })` — mock `ingestionApi.uploadFileAsync.mockReturnValue(of({ batchId: 'b1', duplicate: false, message: 'ok' } as any))`; dispatch `uploadFileAsync({ fileId: 'f1', file: new File([], 't.pdf') })`; assert dispatched action type is `'[Ingestion] Upload File Async Accepted'`
    - `it('uploadFileAsync$ doit dispatcher uploadFileRateLimited sur une réponse 429', (done) => { ... })` — mock `ingestionApi.uploadFileAsync.mockReturnValue(throwError(() => ({ status: 429, error: { retryAfterSeconds: 30, message: 'limit' } })))`; assert dispatched action type is `'[Ingestion] Upload File Rate Limited'`
    - `it('uploadFileAsync$ doit dispatcher uploadFileDuplicate sur une réponse 409', (done) => { ... })` — mock `throwError(() => ({ status: 409, isDuplicate: true, data: { batchId: 'b1', existingBatchId: 'b0', message: 'dupe' } }))`; assert type `'[Ingestion] Upload File Duplicate'`
    - `it('subscribeAfterAsyncUpload$ doit dispatcher subscribeToProgress avec le batchId reçu', (done) => { ... })` — dispatch `IngestionActions.uploadFileAsyncAccepted({ fileId: 'f1', response: { batchId: 'b1', duplicate: false, message: 'ok' } as any })`; assert dispatched action type is `'[Progress] Subscribe To Progress'` and `action.batchId === 'b1'`
    - `it('uploadFile$ (sync) doit dispatcher uploadFileSuccess quand l\'API réussit', (done) => { ... })` — mock `ingestionApi.uploadFile.mockReturnValue(of({ batchId: 'b1', duplicate: false, success: true } as any))`; dispatch `IngestionActions.uploadFile({ fileId: 'f1', file: new File([], 't.pdf') })`; assert type `'[Ingestion] Upload File Success'`

- [ ] T015 [US2] Run Phase 4 validation: `npm test -- --reporter=verbose store/ingestion` from `agentic-rag-ui/` — confirm all 35 `it()` blocks are GREEN

**Checkpoint**: All 4 ingestion spec files pass. US2 is independently deliverable.

---

## Phase 5: User Story 3 — Real-Time Progress Tracking Is Reliable (Priority: P2)

**Goal**: Verify per-batch isolation, WebSocket lifecycle, and the no-op guard for unknown batchIds across all `progress/` sub-store specs.

**Independent Test**: Run `npm test -- --reporter=verbose store/progress` from `agentic-rag-ui/` — all 26 tests pass.

- [ ] T016 [P] [US3] Create `agentic-rag-ui/src/app/features/ingestion/store/progress/progress.actions.spec.ts`:
  - Import `* as ProgressActions` from `./progress.actions`
  - Wrap in `describe('ProgressActions', () => { ... })`
  - Add the following 8 `it()` blocks:
    - `it('connectWebSocket doit avoir le type "[Progress] Connect WebSocket" sans payload', ...)`
    - `it('connectWebSocketError doit avoir le type "[Progress] Connect WebSocket Error" avec error', ...)` — assert type and `action.error`
    - `it('subscribeToProgress doit avoir le type "[Progress] Subscribe To Progress" avec batchId', ...)` — assert type and `action.batchId`
    - `it('unsubscribeFromProgress doit avoir le type "[Progress] Unsubscribe From Progress" avec batchId', ...)`
    - `it('progressUpdate doit avoir le type "[Progress] Progress Update" avec un objet UploadProgress', ...)` — create with `{ progress: mockUploadProgress() }`, assert type
    - `it('progressCompleted doit avoir le type "[Progress] Progress Completed" avec batchId', ...)`
    - `it('progressError doit avoir le type "[Progress] Progress Error" avec batchId et error', ...)`
    - `it('clearAllProgress doit avoir le type "[Progress] Clear All Progress" sans payload', ...)`

- [ ] T017 [US3] Create `agentic-rag-ui/src/app/features/ingestion/store/progress/progress.reducer.spec.ts`:
  - Import `progressReducer` from `./progress.reducer`; `initialProgressState` from `./progress.state`; `* as ProgressActions` from `./progress.actions`; `mockUploadProgress` from `../../../../../test-helpers`
  - Wrap in `describe('ProgressReducer', () => { ... })`
  - Add the following 9 `it()` blocks:
    - `it('doit retourner l\'état initial', () => { expect(progressReducer(undefined, { type: '@@INIT' })).toEqual(initialProgressState); })`
    - `it('connectWebSocketSuccess doit passer connected à true et connecting à false', () => { ... })`
    - `it('connectWebSocketError doit stocker l\'erreur et passer connected à false', () => { ... })` — assert `error: 'conn failed'` and `connected: false`
    - `it('subscribeToProgress doit ajouter le batchId à subscribedBatches', () => { ... })` — dispatch with `{ batchId: 'b1' }`, assert `subscribedBatches` contains `'b1'`
    - `it('progressUpdate doit mettre à jour progressByBatch pour un batchId connu (FR-015)', () => { ... })` — prime state with `subscribedBatches: ['b1']`; dispatch `progressUpdate({ progress: mockUploadProgress({ batchId: 'b1', percentage: 75 }) })`; assert `progressByBatch['b1'].percentage === 75`
    - `it('progressUpdate doit être un no-op pour un batchId inconnu (FR-015)', () => { ... })` — dispatch `progressUpdate` with `batchId: 'unknown'` against initial state (no subscribed batches); assert `state.progressByBatch` is still `{}`
    - `it('progressUpdate (COMPLETED) doit marquer _shouldClear: true et définir _clearAt', () => { ... })` — prime with `subscribedBatches: ['b1']`; dispatch with `stage: 'COMPLETED'`; assert `progressByBatch['b1']._shouldClear === true` and `_clearAt` is a number > 0
    - `it('clearProgress doit supprimer uniquement le batchId ciblé', () => { ... })` — prime with `progressByBatch: { 'b1': ..., 'b2': ... }`; dispatch `clearProgress({ batchId: 'b1' })`; assert `'b1'` absent, `'b2'` present
    - `it('clearAllProgress doit vider progressByBatch entièrement', () => { ... })` — prime with 2 entries; dispatch; assert `progressByBatch === {}`

- [ ] T018 [P] [US3] Create `agentic-rag-ui/src/app/features/ingestion/store/progress/progress.selectors.spec.ts`:
  - Import all selectors from `./progress.selectors`; `initialProgressState, ProgressState` from `./progress.state`; `mockUploadProgress` from `../../../../../test-helpers`
  - Define helper: `const mockState = (overrides: Partial<ProgressState> = {}) => ({ progress: { ...initialProgressState, ...overrides } })`
  - Wrap in `describe('ProgressSelectors', () => { ... })`
  - Add the following 4 `it()` blocks:
    - `it('selectWebSocketConnected doit retourner false par défaut', () => { expect(selectWebSocketConnected(mockState() as any)).toBe(false); })`
    - `it('selectProgressForBatch doit retourner les données du batch ou undefined', () => { ... })` — prime with one entry; assert `selectProgressForBatch('b1')(mockState(...) as any)` returns it; assert unknown id returns `undefined`
    - `it('selectActiveProgress doit filtrer les entrées COMPLETED et _shouldClear', () => { ... })` — prime with one active (stage=PROCESSING) and one completed (_shouldClear=true); assert `selectActiveProgress` returns only the active one
    - `it('selectActiveProgressCount doit retourner le nombre d\'entrées actives', () => { ... })` — assert count matches

- [ ] T019 [US3] Create `agentic-rag-ui/src/app/features/ingestion/store/progress/progress.effects.spec.ts`:
  - Imports: `createServiceFactory, mockProvider` from `@ngneat/spectator/jest`; `provideMockActions` from `@ngrx/effects/testing`; `provideMockStore` from `@ngrx/store/testing`; `Subject, of` from `rxjs`; `ProgressEffects` from `./progress.effects`; `WebSocketProgressService` from `../../../../core/services/websocket-progress.service`; `* as ProgressActions` from `./progress.actions`; `initialProgressState` from `./progress.state`; `mockUploadProgress` from `../../../../../test-helpers`
  - Wrap in `describe('ProgressEffects', () => { ... })`
  - Add the following 5 `it()` blocks:
    - `it('connectWebSocket$ doit dispatcher connectWebSocketSuccess quand la Promesse résout', async () => { ... })` — mock `wsProgress.connect.mockResolvedValue(undefined)`; dispatch `connectWebSocket()`; await next tick; collect effect emission; assert type `'[Progress] Connect WebSocket Success'`
    - `it('connectWebSocket$ doit dispatcher connectWebSocketError quand la Promesse rejette', async () => { ... })` — mock `wsProgress.connect.mockRejectedValue(new Error('fail'))`; assert type `'[Progress] Connect WebSocket Error'`
    - `it('subscribeToProgress$ doit mapper les événements WebSocket en progressUpdate', (done) => { ... })` — mock `wsProgress.subscribeToProgress.mockReturnValue(of(mockUploadProgress({ batchId: 'b1' })))`; dispatch `subscribeToProgress({ batchId: 'b1' })`; assert emitted action type `'[Progress] Progress Update'`
    - `it('disconnectWebSocket$ doit appeler wsProgress.disconnect() (dispatch:false)', () => { ... })` — subscribe to `effect.disconnectWebSocket$`; dispatch action; assert `wsProgress.disconnect` was called
    - `it('autoClearCompletedProgress$ doit dispatcher clearProgress après délai (vi.useFakeTimers)', () => { ... })` — use `vi.useFakeTimers()`; dispatch `progressUpdate({ progress: mockUploadProgress({ batchId: 'b1', stage: 'COMPLETED' }) })`; advance timers by 5001ms via `vi.advanceTimersByTime(5001)`; assert `clearProgress` dispatched; call `vi.useRealTimers()` in `afterEach`

- [ ] T020 [US3] Run Phase 5 validation: `npm test -- --reporter=verbose store/progress` from `agentic-rag-ui/` — confirm all 26 `it()` blocks are GREEN

**Checkpoint**: All 4 progress spec files pass. US3 is independently deliverable.

---

## Phase 6: User Story 4 — Rate Limit State Drives UI Feedback Accurately (Priority: P2)

**Goal**: Verify all reducer state transitions, action type strings, selector projections, and countdown effects (including the cross-store `handleUploadRateLimited$`) for the `rate-limit/` sub-store.

**Independent Test**: Run `npm test -- --reporter=verbose store/rate-limit` from `agentic-rag-ui/` — all 23 tests pass.

- [ ] T021 [P] [US4] Create `agentic-rag-ui/src/app/features/ingestion/store/rate-limit/rate-limit.actions.spec.ts`:
  - Import `* as RateLimitActions` from `./rate-limit.actions`
  - Wrap in `describe('RateLimitActions', () => { ... })`
  - Add the following 6 `it()` blocks:
    - `it('rateLimitExceeded doit avoir le type "[Rate Limit] Exceeded" avec message et retryAfterSeconds', () => { const a = RateLimitActions.rateLimitExceeded({ message: 'limited', retryAfterSeconds: 30 }); expect(a.type).toBe('[Rate Limit] Exceeded'); expect(a.retryAfterSeconds).toBe(30); })`
    - `it('rateLimitReset doit avoir le type "[Rate Limit] Reset" sans payload', ...)` — assert only type
    - `it('updateRemainingTokens doit avoir le type "[Rate Limit] Update Remaining Tokens" avec endpoint et remaining', ...)` — create with `{ endpoint: 'upload', remaining: 7 }`, assert type and both props
    - `it('updateRemainingTokens doit accepter tous les endpoints valides', ...)` — create for each endpoint (`upload`, `batch`, `delete`, `search`, `default`); assert type is the same for all
    - `it('startCountdown doit avoir le type "[Rate Limit] Start Countdown" avec seconds', ...)` — assert type and `action.seconds`
    - `it('decrementCountdown doit avoir le type "[Rate Limit] Decrement Countdown" sans payload', ...)` — assert only type

- [ ] T022 [US4] Create `agentic-rag-ui/src/app/features/ingestion/store/rate-limit/rate-limit.reducer.spec.ts`:
  - Import `rateLimitReducer` from `./rate-limit.reducer`; `initialRateLimitState` from `./rate-limit.state`; `* as RateLimitActions` from `./rate-limit.actions`
  - Wrap in `describe('RateLimitReducer', () => { ... })`
  - Add the following 8 `it()` blocks:
    - `it('doit retourner l\'état initial (isRateLimited: false, limits upload=10)', () => { const s = rateLimitReducer(undefined, { type: '@@INIT' }); expect(s).toEqual(initialRateLimitState); expect(s.limits.upload).toBe(10); })`
    - `it('rateLimitExceeded doit passer isRateLimited à true et stocker retryAfterSeconds', () => { ... })` — assert `isRateLimited: true`, `retryAfterSeconds: 30`, `message: 'limited'`
    - `it('rateLimitReset doit remettre isRateLimited à false et retryAfterSeconds à 0', () => { ... })` — prime with exceeded state; dispatch reset; assert `isRateLimited: false` and `retryAfterSeconds: 0`
    - `it('updateRemainingTokens doit mettre à jour uniquement l\'endpoint spécifié', () => { ... })` — dispatch with `{ endpoint: 'upload', remaining: 7 }`, assert `remainingTokens.upload === 7` and `remainingTokens.batch` still `null`
    - `it('startCountdown doit stocker le nombre de secondes dans retryAfterSeconds', () => { ... })` — dispatch `startCountdown({ seconds: 15 })`; assert `retryAfterSeconds === 15`
    - `it('decrementCountdown doit décrémenter retryAfterSeconds de 1', () => { ... })` — prime with `retryAfterSeconds: 5`; dispatch; assert `retryAfterSeconds === 4`
    - `it('decrementCountdown ne doit jamais descendre en dessous de 0', () => { ... })` — prime with `retryAfterSeconds: 0`; dispatch; assert still `0`
    - `it('rateLimitReset doit vider le message', () => { ... })` — prime with non-empty message; dispatch reset; assert `message === ''`

- [ ] T023 [P] [US4] Create `agentic-rag-ui/src/app/features/ingestion/store/rate-limit/rate-limit.selectors.spec.ts`:
  - Import all selectors from `./rate-limit.selectors`; `initialRateLimitState, RateLimitState` from `./rate-limit.state`
  - Define helper: `const mockState = (overrides: Partial<RateLimitState> = {}) => ({ rateLimit: { ...initialRateLimitState, ...overrides } })`
  - Wrap in `describe('RateLimitSelectors', () => { ... })`
  - Add the following 6 `it()` blocks:
    - `it('selectIsRateLimited doit retourner false par défaut', () => { expect(selectIsRateLimited(mockState() as any)).toBe(false); })`
    - `it('selectRetryAfterSeconds doit retourner 0 par défaut', () => { expect(selectRetryAfterSeconds(mockState() as any)).toBe(0); })`
    - `it('selectRateLimitMessage doit retourner une chaîne vide par défaut', () => { expect(selectRateLimitMessage(mockState() as any)).toBe(''); })`
    - `it('selectUploadRemaining doit retourner null par défaut', () => { expect(selectUploadRemaining(mockState() as any)).toBeNull(); })`
    - `it('selectRateLimitPercentage doit retourner 100 si upload est null', () => { expect(selectRateLimitPercentage(mockState() as any)).toBe(100); })`
    - `it('selectRateLimitPercentage doit calculer le % correct quand remaining est défini', () => { expect(selectRateLimitPercentage(mockState({ remainingTokens: { ...initialRateLimitState.remainingTokens, upload: 5 } }) as any)).toBe(50); })` — 5/10 * 100 = 50

- [ ] T024 [US4] Create `agentic-rag-ui/src/app/features/ingestion/store/rate-limit/rate-limit.effects.spec.ts`:
  - Imports: `createServiceFactory` from `@ngneat/spectator/jest`; `provideMockActions` from `@ngrx/effects/testing`; `provideMockStore` from `@ngrx/store/testing`; `Subject` from `rxjs`; `RateLimitEffects` from `./rate-limit.effects`; `* as RateLimitActions` from `./rate-limit.actions`; `* as IngestionActions` from `../ingestion/ingestion.actions`; `initialRateLimitState` from `./rate-limit.state`
  - Use `beforeEach(() => vi.useFakeTimers())` and `afterEach(() => vi.useRealTimers())`
  - Wrap in `describe('RateLimitEffects', () => { ... })`
  - Add the following 3 `it()` blocks:
    - `it('startCountdown$ doit émettre decrementCountdown après 1 seconde (vi.useFakeTimers)', () => { const results: any[] = []; spectator.service.startCountdown$.subscribe(a => results.push(a)); actions$.next(RateLimitActions.rateLimitExceeded({ retryAfterSeconds: 3, message: 'test' })); vi.advanceTimersByTime(1000); expect(results.length).toBe(1); expect(results[0].type).toBe('[Rate Limit] Decrement Countdown'); })`
    - `it('handleUploadRateLimited$ doit dispatcher rateLimitExceeded avec retryAfterSeconds et message (FR-016)', (done) => { ... })` — dispatch `IngestionActions.uploadFileRateLimited({ fileId: 'f1', retryAfterSeconds: 30, message: 'limited' })`; subscribe to `effect.handleUploadRateLimited$`; assert type `'[Rate Limit] Exceeded'` and `action.retryAfterSeconds === 30`
    - `it('autoReset$ doit dispatcher rateLimitReset quand store retryAfterSeconds vaut 0', () => { ... })` — override mock store selector for `state.rateLimit.retryAfterSeconds` to return `0`; dispatch `decrementCountdown()`; verify `store.dispatch` was called with `rateLimitReset()`

- [ ] T025 [US4] Run Phase 6 validation: `npm test -- --reporter=verbose store/rate-limit` from `agentic-rag-ui/` — confirm all 23 `it()` blocks are GREEN

**Checkpoint**: All 4 rate-limit spec files pass. US4 is independently deliverable.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Full suite validation, advisory coverage check, and individual file commits.

- [ ] T026 Run full Phase 9 suite with coverage from `agentic-rag-ui/`: `npm test -- --coverage --reporter=verbose --include="**/features/ingestion/store/**"` — all 52 `it()` blocks GREEN; review coverage report for any sub-store below 80% branch advisory
- [ ] T027 [P] Commit production code changes: `git add store/crud/crud.effects.ts` is unchanged — commit `progress.reducer.ts` (T003) and `rate-limit.effects.ts` (T004) with message: `fix(phase-9): add no-op guard + cross-store rate-limit effect — required for testability`
- [ ] T028 [P] Commit CRUD specs: `git add store/crud/*.spec.ts` with message: `test(phase-9): add crud sub-store specs — actions, reducer, selectors, effects`
- [ ] T029 [P] Commit ingestion specs: `git add store/ingestion/*.spec.ts` with message: `test(phase-9): add ingestion sub-store specs — upload lifecycle + cross-store handlers`
- [ ] T030 [P] Commit progress specs: `git add store/progress/*.spec.ts` with message: `test(phase-9): add progress sub-store specs — WebSocket tracking + no-op guard (FR-015)`
- [ ] T031 [P] Commit rate-limit specs: `git add store/rate-limit/*.spec.ts` with message: `test(phase-9): add rate-limit sub-store specs — countdown effects + cross-store wiring (FR-016)`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — T003 and T004 block their respective spec files; T005 blocks all spec files
- **US1 — CRUD (Phase 3)**: Depends on Phase 2 (T005 for helpers; T003/T004 not required here)
- **US2 — Ingestion (Phase 4)**: Depends on Phase 2 (T005 for helpers; T003 required before T017 progress reducer test)
- **US3 — Progress (Phase 5)**: Depends on Phase 2 **and T003** (no-op guard must be in place before reducer spec runs)
- **US4 — Rate Limit (Phase 6)**: Depends on Phase 2 **and T004** (`handleUploadRateLimited$` must exist before effects spec runs)
- **Polish (Phase 7)**: Depends on all user story phases complete

### User Story Dependencies

- **US1 (P1)** — independent after Phase 2
- **US2 (P1)** — independent after Phase 2; no dependency on US1
- **US3 (P2)** — requires T003 complete; independent of US1/US2
- **US4 (P2)** — requires T004 complete; independent of US1/US2/US3

### Parallel Opportunities

Once Phase 2 is done, all 4 user stories can be worked in parallel (different file paths, zero cross-file dependencies):
- Developer A: US1 (`store/crud/*.spec.ts`)
- Developer B: US2 (`store/ingestion/*.spec.ts`)
- Developer C: US3 (`store/progress/*.spec.ts`) — needs T003 first
- Developer D: US4 (`store/rate-limit/*.spec.ts`) — needs T004 first

Within each user story, the actions spec [P] and selectors spec [P] can be written in parallel (they touch different files and have no runtime dependency on the reducer or effects spec).

---

## Implementation Strategy

### MVP First (US1 only — CRUD)

1. Complete Phase 1 (Setup — 2 tasks)
2. Complete Phase 2 T003–T005 (production additions + helpers)
3. Complete Phase 3 T006–T009 (CRUD: actions + reducer + selectors + effects)
4. **STOP and VALIDATE**: `npm test -- --coverage store/crud` shows 35 GREEN; commit US1
5. Deliver US1 — CRUD sub-store fully tested

### Incremental Delivery (warm-up order for a single developer)

1. Phase 1 + Phase 2 → production changes + helpers ready
2. T006 (actions — fastest, no setup) + T008 (selectors) in parallel (different files)
3. T007 (reducer — most it-blocks, highest value)
4. T009 (effects — most complex)
5. Repeat pattern for US2, then US3, then US4
6. T024–T031 (Polish)

---

## Summary

| Phase | User Story | Spec Files | `it()` Blocks | Parallel Tasks |
|-------|-----------|-----------|--------------|----------------|
| 3 | US1 — CRUD | 4 | 35 | T006, T008 |
| 4 | US2 — Ingestion | 4 | 35 | T011, T013 |
| 5 | US3 — Progress | 4 | 26 | T016, T018 |
| 6 | US4 — Rate Limit | 4 | 23 | T021, T023 |
| **Total** | | **16** | **119*** | **8 parallel tasks** |

*Actual it-block count across all tasks (some tasks include multiple it blocks beyond the plan.md summary of 52 primary scenarios).

**Total tasks**: 31 (T001–T031)
