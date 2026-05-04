# Research: Phase 9 — Ingestion Store NgRx Tests

**Feature**: `016-ingestion-store-ngrx`  
**Generated**: 2026-05-04  
**Sources**: codebase exploration of all 20 files in `store/crud/`, `store/ingestion/`, `store/progress/`, `store/rate-limit/`; constitution (Phase 6/8 precedents); clarification session 2026-05-04

---

## Decision 1: Timer Testing — `vi.useFakeTimers()` (Clarification Q1)

**Decision**: Use Vitest's built-in `vi.useFakeTimers()` / `vi.advanceTimersByTime(ms)` for the `rate-limit` effects countdown tests. Restore real timers in `afterEach` via `vi.useRealTimers()`.

**Rationale**: The project test runner is Vitest; Angular's `fakeAsync`/`tick()` requires Zone.js patching which Vitest does not load. Using `fakeAsync` would throw `Error: Zone.js is needed for the fakeAsync() test helper`. The `startCountdown$` effect uses `interval(1000)` from RxJS, which Vitest's fake timers can control via `vi.advanceTimersByTime(1000)`.

**Critical finding — countdown variable capture bug**: The `startCountdown$` effect in `rate-limit.effects.ts` captures `retryAfterSeconds` from the action payload at dispatch time:
```typescript
switchMap(({ retryAfterSeconds }) =>
  interval(1000).pipe(
    takeWhile(() => retryAfterSeconds > 0),  // ← captured variable, never changes
    map(() => RateLimitActions.decrementCountdown())
  )
)
```
Since `retryAfterSeconds` is the original action value and never decremented, `takeWhile` is always `true` → the interval runs indefinitely per `rateLimitExceeded` dispatch. The stop mechanism relies on `autoReset$`, which reads from the store and dispatches `rateLimitReset` when store value hits 0. Tests must mock the store's `retryAfterSeconds` selector to verify this two-effect pipeline.

**Alternatives considered**:
- `fakeAsync`/`tick()` — rejected (Zone.js incompatibility with Vitest).
- Real timers with a 1ms interval — rejected; introduces test flakiness and slow execution.

---

## Decision 2: Unknown `batchId` in Progress Reducer — No-Op Guard Required (Clarification Q2)

**Decision**: The `progress` reducer `progressUpdate` handler currently creates a new entry for any `batchId`, including unknown ones (not in `subscribedBatches`). The spec requires a no-op for unknown batches. A guard must be added to the reducer before tests are written, or the spec must be adjusted to test the current behavior.

**Resolution**: Add a guard to the reducer:
```typescript
on(ProgressActions.progressUpdate, (state, { progress }) => {
  // No-op if batchId not subscribed
  if (!state.subscribedBatches.includes(progress.batchId)) {
    return state;
  }
  // ... existing logic
})
```
This aligns with FR-015 and makes the test deterministic.

**Rationale**: Creating phantom progress entries for unsubscribed batches would cause spurious UI progress bars. The `subscribedBatches` array already tracks subscriptions — using it as a guard is a minimal change.

**Alternatives considered**:
- Test the current behavior (creates new entry) — rejected; the spec explicitly requires no-op.

---

## Decision 3: `clearCompletedUploads` Removes All 4 Terminal Statuses (Clarification Q3)

**Decision**: The `clearCompletedUploads` reducer correctly filters to keep only `pending` and `uploading` uploads:
```typescript
on(IngestionActions.clearCompletedUploads, (state) => ({
  ...state,
  uploads: state.uploads.filter(u =>
    u.status === 'pending' || u.status === 'uploading'
  )
}))
```
This removes `success`, `error`, `duplicate`, AND `rate-limited` — all 4 terminal statuses. FR-008 (clarified) is already correctly implemented.

**Selector discrepancy discovered**: `selectCompletedUploads` selector only includes 3 statuses (`success`, `error`, `duplicate`) — it excludes `rate-limited`. This selector may need updating separately, but it is out of scope for Phase 9 (test writing). Note in the selector spec that `selectCompletedUploads` does NOT include `rate-limited` uploads.

**Rationale**: The reducer behavior (all 4 removed) is correct and should be verified by FR-008 tests. The selector discrepancy is a pre-existing inconsistency to flag but not fix in this phase.

---

## Decision 4: Coverage Threshold — Advisory (Clarification Q4)

**Decision**: 80% branch coverage per sub-store is an advisory target. No Vitest `coverageThreshold` configuration is added to `vitest.config.ts` for this phase.

**Rationale**: Enforcing a hard gate now could block Phase 9 completion if RxJS branches in complex effects (e.g., `syncProgressToUploadStatus$` dispatching `NO_OP`) are genuinely hard to cover. Coverage is reported via `npm test -- --coverage` and reviewed manually.

---

## Decision 5: Cross-Store Propagation Responsibility (Clarification Q5)

**Decision**: `RateLimitEffects` listens for `uploadFileRateLimited` and dispatches `rateLimitExceeded`. The ingestion effects do NOT dispatch `rateLimitExceeded` directly; they dispatch only `uploadFileRateLimited`.

**Finding**: The current `rate-limit.effects.ts` does NOT implement this cross-store wiring. The `startCountdown$` effect only listens to `rateLimitExceeded`, not `uploadFileRateLimited`. A new effect must be added to `RateLimitEffects`:
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
FR-016 requires this effect to be tested. This is a minor production code addition required before the test can be written (similar to the `ConfirmationService` addition in Phase 8).

---

## Decision 6: Mock State Shape for `provideMockStore`

**Decision**: Use feature-key-based mock state objects matching `app.config.ts` registration:
```typescript
// Feature keys from app.config.ts
{
  ingestion: ingestionState,   // feature key: 'ingestion'
  progress: progressState,     // feature key: 'progress'
  crud: crudState,             // feature key: 'crud'
  rateLimit: rateLimitState,   // feature key: 'rateLimit' (camelCase!)
}
```
Important: the `rateLimit` feature key is camelCase (not `rate-limit`). `createFeatureSelector<RateLimitState>('rateLimit')` must match.

**Rationale**: `createFeatureSelector` uses the registered key from `provideStore({...})`. Using `'rate-limit'` instead of `'rateLimit'` would cause all rate-limit selectors to return `undefined`.

---

## Decision 7: Effects Test Setup Pattern

**Decision**: Follow the Phase 6 (chat store) pattern for all effects specs:
- Use `createServiceFactory` from `@ngneat/spectator` for the effects class under test.
- Provide `provideMockActions(actions$)` for the actions stream.
- Provide `provideMockStore({ initialState })` for store access.
- Mock all service dependencies with `mockProvider(ServiceClass)` or `SpyObject<ServiceClass>`.
- For `{ dispatch: false }` effects: verify the side effect (e.g., `notificationService.error` was called) NOT a dispatched action.

**Key pattern for `{ dispatch: false }` effects in CrudEffects**:
```typescript
// deleteBatchErrorNotification$ — verify NotificationService.error() called
const notification = spectator.inject(NotificationService);
actions$.next(deleteBatchError({ batchId: 'b1', error: 'test' }));
expect(notification.error).toHaveBeenCalled();
```

---

## Decision 8: Progress Effects — Promise-Based WebSocket Connection

**Decision**: `ProgressEffects.connectWebSocket$` wraps `WebSocketProgressService.connect()` which returns a `Promise`. Mock it with:
```typescript
wsProgressService.connect.mockResolvedValue(void 0);   // success
wsProgressService.connect.mockRejectedValue(new Error('conn failed'));  // error
```
The effect maps the resolved value to `connectWebSocketSuccess()` and the rejection to `connectWebSocketError()`.

---

## Key Ingestion Reducer Cross-Store Handlers

The `ingestion` reducer responds to actions from other sub-stores — these must be tested in `ingestion.reducer.spec.ts`:

| Action from other store | Effect on Ingestion state |
|------------------------|--------------------------|
| `CrudActions.deleteBatch` | Removes uploads matching `batchId` or `existingBatchId` (optimistic delete) |
| `CrudActions.deleteBatchError` | Stores error string in `state.error` |
| `CrudActions.deleteAllFilesSuccess` | Clears `uploads[]` entirely |
| `ProgressActions.progressUpdate` (COMPLETED) | Sets matching upload's status to `success` + progress 100 |
| `ProgressActions.progressUpdate` (ERROR) | Sets matching upload's status to `error` |

---

## Production Code Changes Required Before Tests

| File | Change | Reason |
|------|--------|--------|
| `store/progress/progress.reducer.ts` | Add `subscribedBatches` guard in `progressUpdate` handler | FR-015: no-op for unknown batchId |
| `store/rate-limit/rate-limit.effects.ts` | Add `handleUploadRateLimited$` effect | FR-016: cross-store propagation |
