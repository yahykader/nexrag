# Quickstart: Phase 9 — Ingestion Store NgRx Tests

**Feature**: `016-ingestion-store-ngrx`  
**Generated**: 2026-05-04

---

## Prerequisites

1. All production source files in `agentic-rag-ui/src/app/features/ingestion/store/` are stable.
2. Two small production code additions are required before tests can pass (see below).
3. Test dependencies already installed from Phases 6–8: `@ngneat/spectator`, `@ngrx/store/testing`, `@ngrx/effects/testing`, Vitest.

---

## Required Production Code Changes First

### 1. Add no-op guard in `progress.reducer.ts` (FR-015)

```typescript
// store/progress/progress.reducer.ts — progressUpdate handler
on(ProgressActions.progressUpdate, (state, { progress }) => {
  // FR-015: no-op for unknown (unsubscribed) batchId
  if (!state.subscribedBatches.includes(progress.batchId)) {
    return state;
  }
  // ... rest of existing logic
}),
```

### 2. Add cross-store wiring effect in `rate-limit.effects.ts` (FR-016)

```typescript
// store/rate-limit/rate-limit.effects.ts
import * as IngestionActions from '../ingestion/ingestion.actions';

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

## Spec File Layout

All spec files are co-located with their source files:

```text
agentic-rag-ui/src/app/features/ingestion/store/
├── crud/
│   ├── crud.actions.ts             (existing)
│   ├── crud.actions.spec.ts        ← CREATE
│   ├── crud.reducer.ts             (existing)
│   ├── crud.reducer.spec.ts        ← CREATE
│   ├── crud.selectors.ts           (existing)
│   ├── crud.selectors.spec.ts      ← CREATE
│   ├── crud.effects.ts             (existing)
│   ├── crud.effects.spec.ts        ← CREATE
│   └── crud.state.ts               (existing)
├── ingestion/
│   ├── ingestion.actions.ts        (existing)
│   ├── ingestion.actions.spec.ts   ← CREATE
│   ├── ingestion.reducer.ts        (existing)
│   ├── ingestion.reducer.spec.ts   ← CREATE
│   ├── ingestion.selectors.ts      (existing)
│   ├── ingestion.selectors.spec.ts ← CREATE
│   ├── ingestion.effects.ts        (existing)
│   ├── ingestion.effects.spec.ts   ← CREATE
│   └── ingestion.state.ts          (existing)
├── progress/
│   ├── progress.actions.ts         (existing)
│   ├── progress.actions.spec.ts    ← CREATE
│   ├── progress.reducer.ts         (existing — add guard)
│   ├── progress.reducer.spec.ts    ← CREATE
│   ├── progress.selectors.ts       (existing)
│   ├── progress.selectors.spec.ts  ← CREATE
│   ├── progress.effects.ts         (existing)
│   ├── progress.effects.spec.ts    ← CREATE
│   └── progress.state.ts           (existing)
└── rate-limit/
    ├── rate-limit.actions.ts       (existing)
    ├── rate-limit.actions.spec.ts  ← CREATE
    ├── rate-limit.reducer.ts       (existing)
    ├── rate-limit.reducer.spec.ts  ← CREATE
    ├── rate-limit.selectors.ts     (existing)
    ├── rate-limit.selectors.spec.ts ← CREATE
    ├── rate-limit.effects.ts       (existing — add effect)
    ├── rate-limit.effects.spec.ts  ← CREATE
    └── rate-limit.state.ts         (existing)
```

---

## Spec Skeleton Patterns

### Reducer spec (pure function — no TestBed)

```typescript
import { crudReducer } from './crud.reducer';
import { initialCrudState } from './crud.state';
import * as CrudActions from './crud.actions';

describe('CrudReducer', () => {
  it('doit retourner l\'état initial', () => {
    const state = crudReducer(undefined, { type: '@@INIT' });
    expect(state).toEqual(initialCrudState);
  });

  it('deleteFile doit passer loading à true et incrémenter activeDeleteOperations', () => {
    const action = CrudActions.deleteFile({ embeddingId: 'emb-1', fileType: 'text' });
    const state = crudReducer(initialCrudState, action);
    expect(state.loading).toBe(true);
    expect(state.activeDeleteOperations).toBe(1);
    expect(state.deleteOperations[0].status).toBe('pending');
  });
});
```

### Selector spec (mock state — no TestBed)

```typescript
import { selectCrudLoading, selectDeleteOperations } from './crud.selectors';
import { initialCrudState } from './crud.state';

describe('CrudSelectors', () => {
  const mockState = { crud: { ...initialCrudState, loading: true } };

  it('selectCrudLoading doit retourner true', () => {
    expect(selectCrudLoading(mockState as any)).toBe(true);
  });
});
```

### Effects spec (`createServiceFactory` + `provideMockActions`)

```typescript
import { createServiceFactory, SpectatorService, mockProvider } from '@ngneat/spectator/jest';
import { provideMockActions } from '@ngrx/effects/testing';
import { provideMockStore } from '@ngrx/store/testing';
import { Subject } from 'rxjs';

describe('CrudEffects', () => {
  let spectator: SpectatorService<CrudEffects>;
  let actions$: Subject<any>;

  const createService = createServiceFactory({
    service: CrudEffects,
    providers: [
      mockProvider(CrudApiService),
      mockProvider(NotificationService),
    ],
    overrideProviders: [
      provideMockActions(() => actions$),
      provideMockStore({ initialState: { crud: initialCrudState } }),
    ]
  });

  beforeEach(() => {
    actions$ = new Subject();
    spectator = createService();
  });

  it('deleteFile$ doit dispatcher deleteFileSuccess quand l\'API réussit', (done) => {
    const crudApi = spectator.inject(CrudApiService);
    crudApi.deleteFile.mockReturnValue(of({ deletedCount: 1, message: 'ok' }));

    spectator.service.deleteFile$.subscribe(action => {
      expect(action).toEqual(CrudActions.deleteFileSuccess({ response: { deletedCount: 1, message: 'ok' } }));
      done();
    });

    actions$.next(CrudActions.deleteFile({ embeddingId: 'emb-1', fileType: 'text' }));
  });
});
```

### Rate-limit countdown spec (`vi.useFakeTimers`)

```typescript
describe('RateLimitEffects — startCountdown$', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('doit émettre decrementCountdown après 1 seconde', (done) => {
    // ... setup provideMockActions + provideMockStore
    const results: any[] = [];
    spectator.service.startCountdown$.subscribe(a => results.push(a));

    actions$.next(RateLimitActions.rateLimitExceeded({ retryAfterSeconds: 3, message: 'test' }));
    vi.advanceTimersByTime(1000);
    expect(results[0].type).toBe('[Rate Limit] Decrement Countdown');
    done();
  });
});
```

---

## Run Tests

```bash
# All Phase 9 tests
npm test -- --reporter=verbose

# Single sub-store
npm test -- --reporter=verbose store/crud

# With coverage report (advisory)
npm test -- --coverage
```
