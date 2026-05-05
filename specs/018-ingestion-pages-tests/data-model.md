# Data Model: PHASE 11 — Ingestion Upload Page Test Suite

**Branch**: `018-ingestion-pages-tests` | **Date**: 2026-05-05

> This phase introduces **no new entities or state shapes**. All data consumed by `UploadPageComponent` is read from NgRx slices already defined and fully tested in Phases 9 and 10. This document captures the relevant subset of those models used in the page spec.

---

## Entities Used by `UploadPageComponent`

### `UploadFile` (from `ingestion.state.ts`)

| Field | Type | Constraints |
|-------|------|-------------|
| `id` | `string` | Unique per file in session |
| `file` | `File` | Browser `File` object |
| `status` | `'pending' \| 'uploading' \| 'success' \| 'error' \| 'duplicate' \| 'rate-limited'` | Required |
| `progress` | `number` | 0–100 |
| `batchId` | `string \| undefined` | Set after async upload accepted |
| `existingBatchId` | `string \| undefined` | Set on duplicate detection |
| `retryAfterSeconds` | `number \| undefined` | Set on rate-limit response |

**Test factory**: `mockUploadFile(overrides?)` from `ingestion-test.helpers.ts`

---

### `UploadProgress` (from `websocket-progress.service.ts`)

| Field | Type | Constraints |
|-------|------|-------------|
| `batchId` | `string` | Identifies the upload batch |
| `filename` | `string` | Display name |
| `stage` | `string` | `'PROCESSING' \| 'COMPLETED' \| 'ERROR'` |
| `progressPercentage` | `number` | 0–100 |
| `message` | `string` | Human-readable status |

**Test factory**: `mockUploadProgress(overrides?)` from `ingestion-test.helpers.ts`

---

## NgRx State Slices — Selectors Used in Page Tests

### Ingestion Slice (`selectIngestionState`)

| Selector | Returns | Used to test |
|----------|---------|--------------|
| `selectPendingUploads` | `UploadFile[]` | Pending list count, "Uploader tous" button visibility |
| `selectActiveUploads` | `UploadFile[]` | Active list count, upload zone `disabled` guard (>5) |
| `selectCompletedUploads` | `UploadFile[]` | Completed list count |
| `selectStats` | `{ total, success, errors, duplicates, rateLimited }` | Stats cards values |
| `selectUploadMode` | `'sync' \| 'async'` | Mode toggle button active class, `startAllUploads()` dispatch |
| `selectUploads` | `UploadFile[]` | All uploads (passed to `DeleteAllButtonComponent` child) |

### Progress Slice (`selectProgressState`)

| Selector | Returns | Used to test |
|----------|---------|--------------|
| `selectActiveProgress` | `UploadProgress[]` | Passed to `ProgressPanelComponent` |
| `selectActiveProgressCount` | `number` | Controls `*ngIf` on progress panel wrapper |
| `selectWebSocketConnected` | `boolean` | WebSocket badge class (`bg-success` / `bg-danger`) |

### Rate-Limit Slice (`selectRateLimitState`)

| Selector | Returns | Used to test |
|----------|---------|--------------|
| `selectIsRateLimited` | `boolean` | Rate-limit banner visibility, upload zone `disabled`, button disabled state |
| `selectRetryAfterSeconds` | `number` | Countdown displayed in banner |

---

## Mock Initial State Shape

The `mockFullIngestionState()` helper (to be added to `ingestion-test.helpers.ts`) composes all three slices:

```ts
export const mockFullIngestionState = (overrides: {
  uploads?: UploadFile[];
  isRateLimited?: boolean;
  retryAfterSeconds?: number;
  wsConnected?: boolean;
  activeProgressCount?: number;
} = {}) => ({
  ...mockIngestionState(overrides.uploads ?? []),
  ...mockProgressState(),
  ...mockRateLimitState({
    isRateLimited: overrides.isRateLimited ?? false,
    retryAfterSeconds: overrides.retryAfterSeconds ?? 0,
  }),
});
```

> **Note**: `mockIngestionState`, `mockProgressState`, and `mockRateLimitState` already exist in `ingestion-test.helpers.ts`. The new `mockFullIngestionState` composes them to simplify `UploadPageComponent` spec setup.

---

## State Transitions Exercised in Integration Tests

```
Empty store (all lists = [])
        │
        ▼ overrideSelector(selectPendingUploads, [file])
Pending list: 1 item
        │
        ▼ overrideSelector(selectActiveUploads, [file{status:'uploading'}])
Active list: 1 item, pending: 0
        │
        ▼ overrideSelector(selectCompletedUploads, [file{status:'success'}])
Completed list: 1 item, active: 0 → empty-state hidden
```
