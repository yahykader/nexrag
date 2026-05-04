# Selector Contracts — Ingestion Store (Phase 9)

All selectors verified from source files. Feature key used in `createFeatureSelector` must match `app.config.ts` registration.

---

## `store/crud/crud.selectors.ts` — Feature key: `'crud'`

| Selector | Input | Output |
|----------|-------|--------|
| `selectCrudState` | root state | `CrudState` |
| `selectCrudLoading` | `CrudState` | `boolean` |
| `selectCrudError` | `CrudState` | `string \| null` |
| `selectDeleteOperations` | `CrudState` | `DeleteOperation[]` |
| `selectActiveDeleteOperations` | `CrudState` | `number` |
| `selectPendingDeleteOperations` | `DeleteOperation[]` | filtered array (status=`pending`) |
| `selectSuccessDeleteOperations` | `DeleteOperation[]` | filtered array (status=`success`) |
| `selectErrorDeleteOperations` | `DeleteOperation[]` | filtered array (status=`error`) |
| `selectDuplicateChecks` | `CrudState` | `{ [filename]: DuplicateCheck }` |
| `selectDuplicateCheckByFilename(filename)` | `DuplicateCheck map` | `DuplicateCheck \| undefined` |
| `selectBatchInfos` | `CrudState` | `{ [batchId]: BatchInfo }` |
| `selectBatchInfoById(batchId)` | `BatchInfo map` | `BatchInfo \| undefined` |
| `selectSystemStats` | `CrudState` | stats object |
| `selectSystemHealthy` | stats object | `boolean` (`redisHealthy === true`) |
| `selectTotalEmbeddings` | stats object | `number` (defaults `0`) |

---

## `store/ingestion/ingestion.selectors.ts` — Feature key: `'ingestion'`

| Selector | Input | Output |
|----------|-------|--------|
| `selectIngestionState` | root state | `IngestionState` |
| `selectUploads` | `IngestionState` | `UploadFile[]` |
| `selectPendingUploads` | `UploadFile[]` | filtered (status=`pending`) |
| `selectActiveUploads` | `UploadFile[]` | filtered (status=`uploading`) |
| `selectCompletedUploads` | `UploadFile[]` | filtered (status=`success \| error \| duplicate`) — **note: excludes `rate-limited`** |
| `selectStats` | `IngestionState` | stats object |
| `selectStrategies` | `IngestionState` | `any[]` |
| `selectActiveIngestions` | `IngestionState` | `any[]` |
| `selectUploadMode` | `IngestionState` | `'sync' \| 'async'` |
| `selectRateLimitedUploads` | `IngestionState` | filtered (status=`rate-limited`) |
| `selectRateLimitedCount` | `IngestionState` | `number` (`stats.rateLimited`) |

---

## `store/progress/progress.selectors.ts` — Feature key: `'progress'`

| Selector | Input | Output |
|----------|-------|--------|
| `selectProgressState` | root state | `ProgressState` |
| `selectWebSocketConnected` | `ProgressState` | `boolean` (`state.connected`) |
| `selectWebSocketConnecting` | `ProgressState` | `boolean` (`state.connecting`) |
| `selectProgressByBatch` | `ProgressState` | `{ [batchId]: UploadProgress }` |
| `selectProgressForBatch(batchId)` | progress map | `UploadProgress \| undefined` |
| `selectAllProgress` | progress map | `UploadProgress[]` (Object.values) |
| `selectActiveProgress` | `UploadProgress[]` | filtered (stage ≠ COMPLETED/ERROR and `_shouldClear` falsy) |
| `selectRecentlyCompleted` | `UploadProgress[]` | filtered (stage=COMPLETED/ERROR and `_clearAt > Date.now()`) |
| `selectActiveProgressCount` | active progress array | `number` |
| `selectWebSocketError` | `ProgressState` | `string \| null` |

---

## `store/rate-limit/rate-limit.selectors.ts` — Feature key: `'rateLimit'` (camelCase)

| Selector | Input | Output |
|----------|-------|--------|
| `selectRateLimitState` | root state | `RateLimitState` |
| `selectIsRateLimited` | `RateLimitState` | `boolean` |
| `selectRetryAfterSeconds` | `RateLimitState` | `number` |
| `selectRateLimitMessage` | `RateLimitState` | `string` |
| `selectRemainingTokens` | `RateLimitState` | `remainingTokens` object |
| `selectUploadRemaining` | `RateLimitState` | `number \| null` (`remainingTokens.upload`) |
| `selectRateLimitPercentage` | `RateLimitState` | `number` 0–100 (`null upload → 100`, else `Math.round(upload/limit*100)`) |
