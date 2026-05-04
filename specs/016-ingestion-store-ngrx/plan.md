# Implementation Plan: Phase 9 — Ingestion Store NgRx Tests

**Branch**: `016-ingestion-store-ngrx` | **Date**: 2026-05-04 | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/016-ingestion-store-ngrx/spec.md`

---

## Summary

Create 16 Vitest spec files co-located with the 20 ingestion store source files across 4 sub-stores (`crud/`, `ingestion/`, `progress/`, `rate-limit/`). Two minor production code additions are required first: a no-op guard in `progress.reducer.ts` for unknown `batchId` (FR-015), and a cross-store effect `handleUploadRateLimited$` in `rate-limit.effects.ts` (FR-016). All tests are pure unit tests — no Angular bootstrapping, no real HTTP, no real WebSocket, no real timers. The `rate-limit` countdown effects are tested with Vitest fake timers (`vi.useFakeTimers()`).

---

## Technical Context

**Language/Version**: TypeScript 5.9 / Angular 21 (standalone)  
**Primary Dependencies**: `@ngrx/store ^21.0.1`, `@ngrx/effects ^21.0.1`, `@ngneat/spectator ^22.1.0`, `@ngrx/store/testing ^21.0.1`, `@ngrx/effects/testing ^21.0.1`, Vitest `^4.0.8`  
**Storage**: N/A — no persistence in unit tests; feature keys: `crud`, `ingestion`, `progress`, `rateLimit`  
**Testing**: Vitest (`npm test`); `vi.useFakeTimers()` for countdown effects; `createServiceFactory` for effects; direct pure-function calls for reducers and selectors  
**Target Platform**: Node.js / JSDOM (Vitest environment)  
**Project Type**: Frontend SPA — NgRx store layer unit tests (Phase 9 of 14)  
**Performance Goals**: Full Phase 9 suite ≤ 30 s (SC-003); individual spec files ≤ 5 s  
**Constraints**: No real HTTP calls; no real WebSocket; no real timers except via `vi.useFakeTimers()`; all service dependencies mocked; `NotificationService` mocked for `{ dispatch: false }` CrudEffects  
**Scale/Scope**: 16 spec files, 52 `it()` blocks, 0 integration tests; 2 minor production code additions

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Requirement | Status |
|-----------|-------------|--------|
| VI — Test Isolation | Reducers: pure function calls, no TestBed. Selectors: direct projection with mock state. Effects: `createServiceFactory` + `provideMockActions` + `provideMockStore`. No `StoreModule.forRoot()`. | **PASS** — planned |
| VI — Co-location | Each `*.spec.ts` placed beside its `*.ts` in the same sub-store directory | **PASS** — planned |
| VII — SRP | One spec file per source file (actions / reducer / selectors / effects per sub-store); state files need no spec (type-only) | **PASS** — 16 spec files for 16 testable source files |
| VIII — Naming | `describe('CrudReducer', ...)` / `describe('IngestionActions', ...)` — English; `it('doit ...')` — French imperative | **PASS** — planned |
| IX — Coverage | ≥80% branch per sub-store — advisory (SC-002, Clarification Q4) | **PASS** — advisory only |
| X — NgRx Testing | Reducers: pure functions. Selectors: mock state. Effects: `provideMockActions` + `provideMockStore`. | **PASS** — planned |

**Production code additions**: Two minimal changes strictly required for testability — permitted by constitution.

**GATE RESULT: PASS** — no violations. No Complexity Tracking entry needed.

---

## Project Structure

### Documentation (this feature)

```text
specs/016-ingestion-store-ngrx/
├── plan.md              ← this file
├── research.md          ← Phase 0 complete
├── data-model.md        ← Phase 1 complete
├── quickstart.md        ← Phase 1 complete
├── contracts/
│   ├── action-types.md  ← Phase 1 complete (all 63 action type strings)
│   └── selectors.md     ← Phase 1 complete (all 31 selectors)
├── checklists/
│   └── requirements.md  ← quality checklist (all pass)
└── tasks.md             ← Phase 2 output (/speckit.tasks — not yet created)
```

### Source Code (Angular frontend)

```text
agentic-rag-ui/src/app/features/ingestion/store/
├── crud/
│   ├── crud.state.ts               (existing — no spec needed, type-only)
│   ├── crud.actions.ts             (existing)
│   ├── crud.actions.spec.ts        ← CREATE (8 it-blocks)
│   ├── crud.reducer.ts             (existing)
│   ├── crud.reducer.spec.ts        ← CREATE (13 it-blocks)
│   ├── crud.selectors.ts           (existing)
│   ├── crud.selectors.spec.ts      ← CREATE (7 it-blocks)
│   ├── crud.effects.ts             (existing)
│   └── crud.effects.spec.ts        ← CREATE (7 it-blocks + { dispatch:false } verifications)
├── ingestion/
│   ├── ingestion.state.ts          (existing — no spec needed)
│   ├── ingestion.actions.ts        (existing)
│   ├── ingestion.actions.spec.ts   ← CREATE (10 it-blocks)
│   ├── ingestion.reducer.ts        (existing)
│   ├── ingestion.reducer.spec.ts   ← CREATE (14 it-blocks incl. cross-store handlers)
│   ├── ingestion.selectors.ts      (existing)
│   ├── ingestion.selectors.spec.ts ← CREATE (6 it-blocks)
│   ├── ingestion.effects.ts        (existing)
│   └── ingestion.effects.spec.ts   ← CREATE (5 it-blocks)
├── progress/
│   ├── progress.state.ts           (existing — no spec needed)
│   ├── progress.actions.ts         (existing)
│   ├── progress.actions.spec.ts    ← CREATE (8 it-blocks)
│   ├── progress.reducer.ts         (existing — ADD no-op guard for FR-015)
│   ├── progress.reducer.spec.ts    ← CREATE (9 it-blocks)
│   ├── progress.selectors.ts       (existing)
│   ├── progress.selectors.spec.ts  ← CREATE (4 it-blocks)
│   ├── progress.effects.ts         (existing)
│   └── progress.effects.spec.ts    ← CREATE (5 it-blocks)
└── rate-limit/
    ├── rate-limit.state.ts         (existing — no spec needed)
    ├── rate-limit.actions.ts       (existing)
    ├── rate-limit.actions.spec.ts  ← CREATE (6 it-blocks)
    ├── rate-limit.reducer.ts       (existing)
    ├── rate-limit.reducer.spec.ts  ← CREATE (8 it-blocks)
    ├── rate-limit.selectors.ts     (existing)
    ├── rate-limit.selectors.spec.ts ← CREATE (6 it-blocks)
    ├── rate-limit.effects.ts       (existing — ADD handleUploadRateLimited$ for FR-016)
    └── rate-limit.effects.spec.ts  ← CREATE (3 it-blocks)
```

---

## Implementation Blueprint

### Step 0 — Production Code Additions (do first)

**0.1** Add no-op guard to `progress.reducer.ts` `progressUpdate` handler:
```typescript
if (!state.subscribedBatches.includes(progress.batchId)) {
  return state;
}
```

**0.2** Add `handleUploadRateLimited$` effect to `rate-limit.effects.ts`:
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

---

### Step 1 — `store/crud/` (P1 — highest risk, destructive operations)

**Priority order**: reducer → actions → selectors → effects

**`crud.reducer.spec.ts`** — 13 `it()` blocks  
Key assertions: initial state returned, `deleteFile` sets loading + increments `activeDeleteOperations`, `deleteFileSuccess` updates operation status, `Math.max(0, ...)` guard prevents underflow, `deleteAllFilesSuccess` clears both `batchInfos` and `duplicateChecks`, `checkDuplicateSuccess` stores by filename key, `clearAll` returns `initialCrudState`.

**`crud.actions.spec.ts`** — 8 `it()` blocks  
Key pattern: `const action = CrudActions.deleteFile({ embeddingId: 'e1', fileType: 'text' }); expect(action.type).toBe('[CRUD] Delete File');`

**`crud.selectors.spec.ts`** — 7 `it()` blocks  
Key pattern: build `mockState = { crud: { ...initialCrudState, loading: true } }`, assert each selector projection. Include `selectSystemHealthy` (returns false when `redisHealthy` undefined) and `selectTotalEmbeddings` (returns 0 when undefined).

**`crud.effects.spec.ts`** — 7 `it()` blocks + `{ dispatch: false }` verifications  
Services to mock: `CrudApiService`, `NotificationService`.  
Key scenarios: `deleteFile$` success path, `deleteFile$` error path, `deleteBatch$` success, `deleteAllFiles$` success, `checkDuplicate$`, `getBatchInfo$`, `getSystemStats$`.  
For `{ dispatch: false }` effects (`deleteBatchErrorNotification$`, etc.): verify `notificationService.error()` or `notificationService.success()` was called.

---

### Step 2 — `store/ingestion/` (P1 — upload lifecycle)

**`ingestion.reducer.spec.ts`** — 14 `it()` blocks  
Includes cross-store action tests:
- `CrudActions.deleteBatch` → optimistically removes uploads by batchId/existingBatchId
- `CrudActions.deleteAllFilesSuccess` → clears `uploads[]`
- `ProgressActions.progressUpdate` (COMPLETED) → sets status `success`, progress 100
- `ProgressActions.progressUpdate` (ERROR) → sets status `error`
- `clearCompletedUploads` → retains only `pending` + `uploading`; removes `success`, `error`, `duplicate`, `rate-limited`
- `toggleUploadMode` → cycles `async` ↔ `sync`
- `uploadFileRateLimited` → idempotent counter (`isAlreadyRateLimited` guard)

**`ingestion.actions.spec.ts`** — 10 `it()` blocks  
Verify all action type strings match contract (`contracts/action-types.md`).

**`ingestion.selectors.spec.ts`** — 6 `it()` blocks  
Note: `selectCompletedUploads` test must confirm it returns only 3 statuses (`success`, `error`, `duplicate`), NOT `rate-limited`.

**`ingestion.effects.spec.ts`** — 5 `it()` blocks  
Services to mock: `IngestionApiService`.  
Key scenarios: `uploadFileAsync$` success → `uploadFileAsyncAccepted`; `uploadFileAsync$` 429 → `uploadFileRateLimited`; `uploadFileAsync$` 409 → `uploadFileDuplicate`; `subscribeAfterAsyncUpload$` → `subscribeToProgress`; `uploadFile$` (sync) success → `uploadFileSuccess`.

---

### Step 3 — `store/progress/` (P2 — WebSocket progress)

**`progress.reducer.spec.ts`** — 9 `it()` blocks  
Key scenarios: `connectWebSocketSuccess` → `connected: true`; `subscribeToProgress` → adds batchId to `subscribedBatches`; `progressUpdate` for subscribed batchId → updates `progressByBatch`; `progressUpdate` for unknown batchId → no-op (FR-015); `progressUpdate` with COMPLETED stage → sets `_shouldClear: true` and `_clearAt`; `clearProgress` → removes only target batchId; `clearAllProgress` → empties map.

**`progress.actions.spec.ts`** — 8 `it()` blocks  
Standard action type string verification.

**`progress.selectors.spec.ts`** — 4 `it()` blocks  
Key: `selectActiveProgress` filters by stage and `_shouldClear`; `selectRecentlyCompleted` uses `_clearAt > Date.now()`.

**`progress.effects.spec.ts`** — 5 `it()` blocks  
Services to mock: `WebSocketProgressService`.  
Key: `connectWebSocket$` resolves Promise → `connectWebSocketSuccess`; Promise rejection → `connectWebSocketError`; `subscribeToProgress$` maps WebSocket Observable events to `progressUpdate`; `autoClearCompletedProgress$` uses `timer(5000)` (use `vi.useFakeTimers()`).

---

### Step 4 — `store/rate-limit/` (P2 — rate limit feedback)

**`rate-limit.reducer.spec.ts`** — 8 `it()` blocks  
Key: `rateLimitExceeded` → `isRateLimited: true`; `rateLimitReset` → full reset; `updateRemainingTokens` → updates only specified endpoint; `decrementCountdown` → `Math.max(0, seconds - 1)`.

**`rate-limit.actions.spec.ts`** — 6 `it()` blocks  
Standard action type string verification.

**`rate-limit.selectors.spec.ts`** — 6 `it()` blocks  
Key: `selectRateLimitPercentage` with `null` upload returns 100; with `upload: 5, limit: 10` returns 50.

**`rate-limit.effects.spec.ts`** — 3 `it()` blocks (use `vi.useFakeTimers()`)  
Key scenarios:
1. `startCountdown$`: dispatch `rateLimitExceeded({ retryAfterSeconds: 3 })`, advance by 1000ms → `decrementCountdown` emitted.
2. `handleUploadRateLimited$` (FR-016): dispatch `uploadFileRateLimited` → `rateLimitExceeded` emitted with correct props.
3. `autoReset$`: mock store to return `retryAfterSeconds: 0` after decrement → `rateLimitReset` dispatched.

---

## Shared Test Helpers

Add to `src/app/test-helpers.ts` (existing file):

```typescript
// Mock factories for Phase 9
export const mockUploadFile = (overrides: Partial<UploadFile> = {}): UploadFile => ({
  id: 'upload_test_1',
  file: new File(['content'], 'test.pdf', { type: 'application/pdf' }),
  progress: 0,
  status: 'pending',
  ...overrides
});

export const mockDeleteOperation = (overrides: Partial<DeleteOperation> = {}): DeleteOperation => ({
  id: 'del-op-1', type: 'file', targetId: 'emb-1',
  status: 'pending', timestamp: new Date(),
  ...overrides
});

export const mockUploadProgress = (overrides: Partial<UploadProgress> = {}): UploadProgress => ({
  batchId: 'batch-test-1', filename: 'test.pdf',
  stage: 'PROCESSING', percentage: 50, message: 'Processing...',
  ...overrides
});
```
