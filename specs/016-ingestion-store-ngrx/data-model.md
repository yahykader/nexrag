# Data Model: Phase 9 — Ingestion Store NgRx Tests

**Feature**: `016-ingestion-store-ngrx`  
**Generated**: 2026-05-04

---

## Store Feature Keys (from `app.config.ts`)

| Slice | Feature Key | Reducer |
|-------|-------------|---------|
| CRUD | `'crud'` | `crudReducer` |
| Ingestion | `'ingestion'` | `ingestionReducer` |
| Progress | `'progress'` | `progressReducer` |
| Rate Limit | `'rateLimit'` | `rateLimitReducer` |

---

## 1 — `CrudState` (`store/crud/crud.state.ts`)

```typescript
interface CrudState {
  deleteOperations: DeleteOperation[];    // list of in-flight/completed deletes
  activeDeleteOperations: number;         // count, floor 0 (Math.max guard)
  duplicateChecks: { [filename: string]: DuplicateCheck };
  batchInfos:      { [batchId: string]:  BatchInfo };
  systemStats:     { totalStrategies?, activeIngestions?, trackedBatches?,
                     totalEmbeddings?, filesInProgress?,
                     redisHealthy?, systemStatus?, lastUpdate? };
  loading: boolean;
  error:   string | null;
}

const initialCrudState: CrudState = {
  deleteOperations: [], activeDeleteOperations: 0,
  duplicateChecks: {}, batchInfos: {}, systemStats: {},
  loading: false, error: null
};
```

### `DeleteOperation`

| Field | Type | Notes |
|-------|------|-------|
| `id` | `string` | generated: `"file-{embeddingId}-{Date.now()}"` etc. |
| `type` | `'file' \| 'batch' \| 'text-batch' \| 'image-batch' \| 'all'` | |
| `targetId` | `string` | `embeddingId`, `batchId`, or `"all-files"` |
| `status` | `'pending' \| 'success' \| 'error'` | |
| `message` | `string?` | from API response or error |
| `deletedCount` | `number?` | from API response |
| `timestamp` | `Date` | set at creation |

### `DuplicateCheck`

| Field | Type |
|-------|------|
| `filename` | `string` (map key) |
| `isDuplicate` | `boolean` |
| `existingBatchId` | `string?` |
| `message` | `string` |
| `timestamp` | `Date` |

### `BatchInfo`

| Field | Type |
|-------|------|
| `batchId` | `string` (map key) |
| `found` | `boolean` |
| `textEmbeddings` | `number?` |
| `imageEmbeddings` | `number?` |
| `totalEmbeddings` | `number?` |
| `message` | `string?` |
| `timestamp` | `Date` |

---

## 2 — `IngestionState` (`store/ingestion/ingestion.state.ts`)

```typescript
interface IngestionState {
  uploads:          UploadFile[];
  activeUploads:    number;           // floor 0 (Math.max guard)
  stats: {
    total:       number;
    success:     number;
    errors:      number;
    duplicates:  number;
    rateLimited: number;
  };
  strategies:       any[];
  activeIngestions: any[];
  loading:          boolean;
  error:            string | null;
  uploadMode:       'sync' | 'async';  // default: 'async'
}
```

### `UploadFile`

| Field | Type | Notes |
|-------|------|-------|
| `id` | `string` | generated: `"upload_{Date.now()}_{index}"` |
| `file` | `File` | native File object |
| `progress` | `number` | 0–100 |
| `status` | `'pending' \| 'uploading' \| 'success' \| 'error' \| 'duplicate' \| 'rate-limited'` | 6 valid values |
| `batchId` | `string?` | set after accepted |
| `response` | `IngestionResponse?` | sync response |
| `asyncResponse` | `AsyncResponse?` | async response |
| `error` | `string?` | error message |
| `message` | `string?` | info message (duplicate, rate-limit) |
| `existingBatchId` | `string?` | duplicate's original batch |
| `retryAfterSeconds` | `number?` | rate-limited countdown |

### Cross-store handlers in `ingestionReducer`

| Triggering Action | Effect on `IngestionState` |
|-------------------|---------------------------|
| `CrudActions.deleteBatch` | Optimistically removes uploads where `upload.existingBatchId === batchId \|\| upload.batchId === batchId` |
| `CrudActions.deleteBatchError` | Sets `state.error` |
| `CrudActions.deleteAllFilesSuccess` | Clears `uploads[]` to `[]` |
| `ProgressActions.progressUpdate` (stage=COMPLETED) | Finds upload by `batchId`, sets status `success`, progress 100 |
| `ProgressActions.progressUpdate` (stage=ERROR) | Finds upload by `batchId`, sets status `error` |

### Selector note: `selectCompletedUploads`

Returns uploads with status `success | error | duplicate` — does NOT include `rate-limited`. This is a pre-existing inconsistency; the selector spec must test current behavior (3 statuses), not 4.

---

## 3 — `ProgressState` (`store/progress/progress.state.ts`)

```typescript
interface ProgressState {
  connected:        boolean;
  connecting:       boolean;
  error:            string | null;
  progressByBatch:  { [batchId: string]: UploadProgress };
  subscribedBatches: string[];
}

const initialProgressState: ProgressState = {
  connected: false, connecting: false,
  error: null, progressByBatch: {}, subscribedBatches: []
};
```

### `UploadProgress` (from `WebSocketProgressService`)

| Field | Type | Notes |
|-------|------|-------|
| `batchId` | `string` | |
| `filename` | `string` | |
| `stage` | `string` | `'PROCESSING' \| 'COMPLETED' \| 'ERROR'` etc. |
| `percentage` | `number` | 0–100 |
| `message` | `string` | |
| `embeddingsCreated` | `number?` | |
| `imagesProcessed` | `number?` | |
| `_shouldClear` | `boolean?` | set by reducer when COMPLETED/ERROR |
| `_clearAt` | `number?` | `Date.now() + 5000` set by reducer |

### Guard to add (FR-015)

```typescript
// In progressReducer, progressUpdate handler:
if (!state.subscribedBatches.includes(progress.batchId)) {
  return state;  // no-op for unknown batchId
}
```

---

## 4 — `RateLimitState` (`store/rate-limit/rate-limit.state.ts`)

```typescript
interface RateLimitState {
  isRateLimited:    boolean;
  retryAfterSeconds: number;
  message:          string;
  remainingTokens: {
    upload: number | null;
    batch:  number | null;
    delete: number | null;
    search: number | null;
    default: number | null;
  };
  limits: {
    upload:  10;   // req/min
    batch:   5;
    delete:  20;
    search:  50;
    default: 30;
  };
}

const initialRateLimitState: RateLimitState = {
  isRateLimited: false, retryAfterSeconds: 0, message: '',
  remainingTokens: { upload: null, batch: null, delete: null, search: null, default: null },
  limits: { upload: 10, batch: 5, delete: 20, search: 50, default: 30 }
};
```

### `decrementCountdown` floor guard

The reducer must ensure `retryAfterSeconds` never goes below 0:
```typescript
on(RateLimitActions.decrementCountdown, (state) => ({
  ...state,
  retryAfterSeconds: Math.max(0, state.retryAfterSeconds - 1)
}))
```
Verify this guard exists or add it.

---

## Mock State Factories (for Selector and Effects Specs)

```typescript
// crud mock state
function mockCrudState(overrides: Partial<CrudState> = {}): { crud: CrudState } {
  return { crud: { ...initialCrudState, ...overrides } };
}

// ingestion mock state
function mockIngestionState(overrides: Partial<IngestionState> = {}): { ingestion: IngestionState } {
  return { ingestion: { ...initialState, ...overrides } };
}

// progress mock state
function mockProgressState(overrides: Partial<ProgressState> = {}): { progress: ProgressState } {
  return { progress: { ...initialProgressState, ...overrides } };
}

// rateLimit mock state
function mockRateLimitState(overrides: Partial<RateLimitState> = {}): { rateLimit: RateLimitState } {
  return { rateLimit: { ...initialRateLimitState, ...overrides } };
}

// UploadFile factory
function mockUploadFile(overrides: Partial<UploadFile> = {}): UploadFile {
  return {
    id: 'upload_test_1',
    file: new File(['content'], 'test.pdf', { type: 'application/pdf' }),
    progress: 0,
    status: 'pending',
    ...overrides
  };
}

// DeleteOperation factory
function mockDeleteOperation(overrides: Partial<DeleteOperation> = {}): DeleteOperation {
  return {
    id: 'del-op-1', type: 'file', targetId: 'emb-1',
    status: 'pending', timestamp: new Date(),
    ...overrides
  };
}
```
