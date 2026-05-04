# Action Type Contracts — Ingestion Store (Phase 9)

All action type strings verified from source files. Tests MUST import action creators and assert `.type` rather than hardcoding strings.

---

## `store/crud/crud.actions.ts` — Feature prefix `[CRUD]`

| Action Creator | Type String |
|----------------|-------------|
| `deleteFile` | `[CRUD] Delete File` |
| `deleteFileSuccess` | `[CRUD] Delete File Success` |
| `deleteFileError` | `[CRUD] Delete File Error` |
| `deleteBatch` | `[CRUD] Delete Batch` |
| `deleteBatchSuccess` | `[CRUD] Delete Batch Success` |
| `deleteBatchError` | `[CRUD] Delete Batch Error` |
| `deleteTextBatch` | `[CRUD] Delete Text Batch` |
| `deleteTextBatchSuccess` | `[CRUD] Delete Text Batch Success` |
| `deleteTextBatchError` | `[CRUD] Delete Text Batch Error` |
| `deleteImageBatch` | `[CRUD] Delete Image Batch` |
| `deleteImageBatchSuccess` | `[CRUD] Delete Image Batch Success` |
| `deleteImageBatchError` | `[CRUD] Delete Image Batch Error` |
| `deleteAllFiles` | `[CRUD] Delete All Files` |
| `deleteAllFilesSuccess` | `[CRUD] Delete All Files Success` |
| `deleteAllFilesError` | `[CRUD] Delete All Files Error` |
| `checkDuplicate` | `[CRUD] Check Duplicate` |
| `checkDuplicateSuccess` | `[CRUD] Check Duplicate Success` |
| `checkDuplicateError` | `[CRUD] Check Duplicate Error` |
| `getBatchInfo` | `[CRUD] Get Batch Info` |
| `getBatchInfoSuccess` | `[CRUD] Get Batch Info Success` |
| `getBatchInfoError` | `[CRUD] Get Batch Info Error` |
| `getSystemStats` | `[CRUD] Get System Stats` |
| `getSystemStatsSuccess` | `[CRUD] Get System Stats Success` |
| `getSystemStatsError` | `[CRUD] Get System Stats Error` |
| `clearDeleteOperations` | `[CRUD] Clear Delete Operations` |
| `clearDuplicateChecks` | `[CRUD] Clear Duplicate Checks` |
| `clearBatchInfos` | `[CRUD] Clear Batch Infos` |
| `clearAll` | `[CRUD] Clear All` |

---

## `store/ingestion/ingestion.actions.ts` — Feature prefix `[Ingestion]`

| Action Creator | Type String |
|----------------|-------------|
| `uploadFileAsync` | `[Ingestion] Upload File Async` |
| `uploadFileAsyncAccepted` | `[Ingestion] Upload File Async Accepted` |
| `uploadFileAsyncError` | `[Ingestion] Upload File Async Error` |
| `uploadBatchAsync` | `[Ingestion] Upload Batch Async` |
| `uploadBatchAsyncAccepted` | `[Ingestion] Upload Batch Async Accepted` |
| `uploadBatchAsyncError` | `[Ingestion] Upload Batch Async Error` |
| `uploadFileRateLimited` | `[Ingestion] Upload File Rate Limited` |
| `addFilesToUpload` | `[Ingestion] Add Files To Upload` |
| `uploadFile` | `[Ingestion] Upload File` |
| `uploadFileSuccess` | `[Ingestion] Upload File Success` |
| `uploadFileError` | `[Ingestion] Upload File Error` |
| `addUpload` | `[Ingestion] Add Upload` |
| `updateUploadStatus` | `[Ingestion] Update Upload Status` |
| `setUploadMode` | `[Ingestion] Set Upload Mode` |
| `uploadFileDuplicate` | `[Ingestion] Upload File Duplicate` |
| `removeFile` | `[Ingestion] Remove File` |
| `clearAllFiles` | `[Ingestion] Clear All Files` |
| `uploadBatch` | `[Ingestion] Upload Batch` |
| `uploadBatchSuccess` | `[Ingestion] Upload Batch Success` |
| `uploadBatchError` | `[Ingestion] Upload Batch Error` |
| `loadStrategies` | `[Ingestion] Load Strategies` |
| `loadStrategiesSuccess` | `[Ingestion] Load Strategies Success` |
| `loadActiveIngestions` | `[Ingestion] Load Active Ingestions` |
| `loadActiveIngestionsSuccess` | `[Ingestion] Load Active Ingestions Success` |
| `loadStats` | `[Ingestion] Load Stats` |
| `loadStatsSuccess` | `[Ingestion] Load Stats Success` |
| `removeUpload` | `[Ingestion] Remove Upload` |
| `clearCompletedUploads` | `[Ingestion] Clear Completed Uploads` |
| `toggleUploadMode` | `[Ingestion] Toggle Upload Mode` |

---

## `store/progress/progress.actions.ts` — Feature prefix `[Progress]`

| Action Creator | Type String |
|----------------|-------------|
| `connectWebSocket` | `[Progress] Connect WebSocket` |
| `connectWebSocketSuccess` | `[Progress] Connect WebSocket Success` |
| `connectWebSocketError` | `[Progress] Connect WebSocket Error` |
| `disconnectWebSocket` | `[Progress] Disconnect WebSocket` |
| `subscribeToProgress` | `[Progress] Subscribe To Progress` |
| `unsubscribeFromProgress` | `[Progress] Unsubscribe From Progress` |
| `progressUpdate` | `[Progress] Progress Update` |
| `progressCompleted` | `[Progress] Progress Completed` |
| `progressError` | `[Progress] Progress Error` |
| `clearProgress` | `[Progress] Clear Progress` |
| `clearAllProgress` | `[Progress] Clear All Progress` |

---

## `store/rate-limit/rate-limit.actions.ts` — Feature prefix `[Rate Limit]`

| Action Creator | Type String |
|----------------|-------------|
| `rateLimitExceeded` | `[Rate Limit] Exceeded` |
| `rateLimitReset` | `[Rate Limit] Reset` |
| `updateRemainingTokens` | `[Rate Limit] Update Remaining Tokens` |
| `startCountdown` | `[Rate Limit] Start Countdown` |
| `decrementCountdown` | `[Rate Limit] Decrement Countdown` |
