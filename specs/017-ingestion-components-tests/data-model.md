# Data Model: PHASE 10 — Ingestion Components Test Suite

**Branch**: `017-ingestion-components-tests` | **Date**: 2026-05-04

This document defines the test data structures and mock factories required across all 8 Phase 10 spec files.

---

## Entities

### UploadFile

Represents a single file in the ingestion queue. Used as `@Input() upload` in `UploadItemComponent`.

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `id` | `string` | Yes | Unique ID (e.g. `'file-1'`) |
| `file` | `File` | Yes | Browser `File` object |
| `status` | `'pending' \| 'uploading' \| 'success' \| 'error' \| 'duplicate' \| 'rate-limited'` | Yes | All 6 values must be exercised (FR-003) |
| `batchId` | `string \| undefined` | No | Present after async upload accepted |
| `existingBatchId` | `string \| undefined` | No | Present when duplicate detected |
| `retryAfterSeconds` | `number \| undefined` | No | Present when rate-limited |

**State transition coverage required** (one test per status — SC-006):

```
pending → uploading → success
                   → error
                   → duplicate
                   → rate-limited
```

**Mock factory**:
```ts
export const mockUploadFile = (overrides: Partial<UploadFile> = {}): UploadFile => ({
  id: 'file-1',
  file: new File(['content'], 'doc.pdf', { type: 'application/pdf' }),
  status: 'pending',
  batchId: undefined,
  existingBatchId: undefined,
  retryAfterSeconds: undefined,
  ...overrides
});
```

---

### UploadProgress

Real-time WebSocket progress entry. Used by `ProgressPanelComponent` and `UploadItemComponent` via `selectProgressForBatch`.

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `batchId` | `string` | Yes | Links progress to an `UploadFile` |
| `percentage` | `number` | Yes | 0–100 |
| `currentStage` | `string` | Yes | e.g. `'UPLOAD'`, `'PROCESSING'`, `'COMPLETED'` |
| `message` | `string` | No | Human-readable status message |
| `isComplete` | `boolean` | Yes | `true` when stage is `COMPLETED` |
| `isError` | `boolean` | Yes | `true` when stage is `ERROR` |

**Mock factory**:
```ts
export const mockUploadProgress = (overrides: Partial<UploadProgress> = {}): UploadProgress => ({
  batchId: 'batch-1',
  percentage: 50,
  currentStage: 'PROCESSING',
  message: 'Traitement en cours...',
  isComplete: false,
  isError: false,
  ...overrides
});
```

---

### RateLimitState (NgRx slice)

Drives `RateLimitIndicatorComponent` and `RateLimitToastComponent`. Read via `selectIsRateLimited`, `selectUploadRemaining`, `selectRateLimitPercentage`, `selectRetryAfterSeconds`, `selectRateLimitMessage`.

| Field | Type | Notes |
|-------|------|-------|
| `isRateLimited` | `boolean` | `false` by default |
| `retryAfterSeconds` | `number` | 0 by default |
| `message` | `string` | empty by default |
| `remainingTokens.upload` | `number \| null` | `null` = unknown |
| `limits.upload` | `number` | 10 by default |

**Initial state for `provideMockStore`**:
```ts
export const mockRateLimitState = (overrides = {}) => ({
  rateLimit: {
    isRateLimited: false,
    retryAfterSeconds: 0,
    message: '',
    remainingTokens: { upload: null, batch: null, delete: null, search: null, default: null },
    limits: { upload: 10, batch: 5, delete: 20, search: 50, default: 30 },
    ...overrides
  }
});
```

---

### CrudState (NgRx slice)

Drives `DeleteAllButtonComponent` (loading + operations). Read via `selectCrudLoading`, `selectActiveDeleteOperations`.

| Field | Type | Notes |
|-------|------|-------|
| `isLoading` | `boolean` | `false` by default |
| `activeDeleteOperations` | `number` | 0 by default |
| `deleteOperations` | `DeleteOperation[]` | `[]` by default |
| `batchInfos` | `Record<string, any>` | `{}` by default |

**Initial state for `provideMockStore`**:
```ts
export const mockCrudState = (overrides = {}) => ({
  crud: {
    isLoading: false,
    activeDeleteOperations: 0,
    deleteOperations: [],
    batchInfos: {},
    duplicateChecks: {},
    systemStats: null,
    error: null,
    ...overrides
  }
});
```

---

## Mock Store Initial States per Component

| Spec file | Required store slices |
|-----------|----------------------|
| `upload-zone.component.spec.ts` | `rateLimit.isRateLimited` |
| `upload-item.component.spec.ts` | `progress`, `crud.activeDeleteOperations` |
| `progress-panel.component.spec.ts` | `progress` (active entries, wsConnected) |
| `delete-all-button.component.spec.ts` | `crud.isLoading`, `ingestion.uploads` |
| `delete-all-modal.component.spec.ts` | none (pure component, no store) |
| `delete-batch-modal.component.spec.ts` | none (pure component, no store) |
| `rate-limit-indicator.component.spec.ts` | `rateLimit.isRateLimited`, `rateLimit` selectors |
| `rate-limit-toast.component.spec.ts` | `rateLimit.isRateLimited`, `rateLimit.retryAfterSeconds`, `rateLimit.message` |

> `DeleteAllModalComponent` and `DeleteBatchModalComponent` have no store dependency — they are pure `@Input` / `@Output` components. No `provideMockStore` needed.

---

## Shared Test Helpers Location

All mock factories above should be defined in:
```
src/app/features/ingestion/components/testing/ingestion-test.helpers.ts
```
(or inline per spec if preferred — co-location is acceptable for Phase 10)
