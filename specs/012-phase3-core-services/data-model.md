# Data Model: Phase 3 — Core Services Test Suite

**Branch**: `012-phase3-core-services`
**Date**: 2026-04-30

---

## Services under test — dependency map

| Service | Factory | External Deps | HttpClient |
|---------|---------|---------------|-----------|
| `CrudApiService` | `createHttpFactory` | — | ✅ |
| `IngestionApiService` | `createHttpFactory` | — | ✅ |
| `StreamingApiService` | `createServiceFactory` | `EventSource` (global), `NgZone` | ✅ (cancel/health only) |
| `WebSocketProgressService` | `createServiceFactory` | `@stomp/stompjs Client`, `NgZone` | ❌ |
| `NotificationService` | `createServiceFactory` | — | ❌ |
| `VoiceService` | `createHttpFactory` | `MediaRecorder` (global), `getUserMedia` | ✅ (transcribe only) |

---

## 1. `Toast`

Emitted by `NotificationService.toasts$`.

```ts
interface Toast {
  id: string;          // format: "<timestamp>-<counter>" e.g. "1714478000000-1"
  type: 'success' | 'error' | 'warning' | 'info';
  title: string;
  message: string;
  duration?: number;   // default: 5000 ms
}
```

**ID uniqueness rule**: Module-level counter `_toastCounter` incremented on each `show()` call.
Two synchronous calls within the same millisecond produce `"<ms>-1"` and `"<ms>-2"`.

**State**: No lifecycle transitions — `NotificationService` is a fire-and-forget bus.
Consumers are responsible for dismissal timing.

---

## 2. `StreamEvent`

Emitted by `StreamingApiService.stream()`.

```ts
interface StreamEvent {
  type: 'connected' | 'token' | 'complete' | 'error';
  sessionId?: string;       // present on 'connected'
  conversationId?: string;  // present on 'connected'
  text?: string;            // present on 'token' — citation-stripped
  index?: number;           // token sequence index
  response?: any;           // present on 'complete'
  metadata?: any;           // present on 'complete'
  error?: string;           // present on 'error'
  code?: string;            // present on 'error'
}
```

**SSE event → StreamEvent mapping**:

| SSE event name | `type` field | Observable action |
|----------------|-------------|------------------|
| `connected` | `'connected'` | emit, continue |
| `token` | `'token'` | emit (text stripped of `<cite>` tags), continue |
| `complete` | `'complete'` | emit, then `observer.complete()` + `close()` |
| `error` (named) | `'error'` | emit, then `observer.error()` + `close()` |
| `onerror` (native) | — | `observer.error()` + `close()` (no typed emit) |

**Citation stripping rule**: Any text matching `/<cite\s+index="\d+">.*?<\/cite>/g`
is removed from `token.text` before emission. The surrounding text is preserved.

---

## 3. `UploadProgress`

Emitted by `WebSocketProgressService.subscribeToProgress(batchId)` and `progress$`.

```ts
interface UploadProgress {
  batchId: string;
  filename: string;
  stage: UploadStage;
  progressPercentage: number;  // 0–100
  message: string;
  embeddingsCreated?: number;
  chunksCreated?: number;
  imagesProcessed?: number;
  timestamp?: string;
  _shouldClear?: boolean;      // UI hint — cleared by progress panel
  _clearAt?: number;           // timestamp for UI clear
}

type UploadStage =
  | 'UPLOAD'
  | 'PROCESSING'
  | 'CHUNKING'
  | 'EMBEDDING'
  | 'IMAGES'
  | 'COMPLETED'
  | 'ERROR';
```

**Observable completion rule**: `subscribeToProgress` observable completes when
`stage === 'COMPLETED'` or `stage === 'ERROR'`. All other stages are intermediate emissions.

**STOMP topic**: `/topic/upload-progress/{batchId}`

---

## 4. `DeleteResponse`

Returned by all `CrudApiService` delete methods.

```ts
interface DeleteResponse {
  success: boolean;
  deletedCount: number;
  message: string;
  embeddingId?: string;
  batchId?: string;
  type?: string;
  timestamp?: Date;
}
```

---

## 5. `DuplicateCheckResponse`

Returned by `CrudApiService.checkDuplicate()`.

```ts
interface DuplicateCheckResponse {
  isDuplicate: boolean;
  filename: string;
  existingBatchId?: string;
  message: string;
}
```

---

## 6. `BatchInfoResponse`

Returned by `CrudApiService.getBatchInfo()`.

```ts
interface BatchInfoResponse {
  found: boolean;
  batchId: string;
  textEmbeddings: number;
  imageEmbeddings: number;
  totalEmbeddings: number;
  message: string;
}
```

---

## 7. `IngestionResponse` / `AsyncResponse` / `StatusResponse`

```ts
interface IngestionResponse {
  success: boolean;
  batchId: string;
  filename: string;
  fileSize: number;
  textEmbeddings: number;
  imageEmbeddings: number;
  durationMs: number;
  streamingUsed: boolean;
  message: string;
  duplicate: boolean;
  existingBatchId?: string;
}

interface AsyncResponse {
  accepted: boolean;
  batchId: string;
  filename: string;
  message: string;
  statusUrl: string;
  duplicate: boolean;
  existingBatchId?: string;
}

interface StatusResponse {
  found: boolean;
  batchId: string;
  textEmbeddings: number;
  imageEmbeddings: number;
  totalEmbeddings: number;
  message: string;
}
```

**FormData payload rules** (enforced by tests):
- `uploadFile(file)` → `FormData { file }` — no `batchId` key
- `uploadFile(file, 'batch-7')` → `FormData { file, batchId: 'batch-7' }`
- `uploadBatchAsync([f1, f2])` → `FormData { files: [f1, f2] }` (two entries under same key)
- HTTP 422 → `observer.error()` — not swallowed

---

## 8. `WhisperResponse`

Returned by `VoiceService.transcribeWithWhisper()`.

```ts
interface WhisperResponse {
  success: boolean;
  transcript: string;
  language: string;
  audioSize: number;
  filename: string;
  transcriptLength?: number;
  error?: string;
}
```

**FormData payload rules**:
- Key `audio`: Blob with filename `'recording.webm'`
- Key `language`: string, default `'fr'`

---

## 9. HTTP base URLs (derived from `environment.apiUrl = '/api'`)

| Service | Base URL |
|---------|---------|
| `CrudApiService` | `/api/v1/crud` |
| `IngestionApiService` | `/api/v1/ingestion` |
| `StreamingApiService` | `/api/v1/assistant` |
| `VoiceService` | `/api/v1/voice` |

---

## 10. Production code changes required

Two minimal changes to production services are needed before writing tests:

### `notification.service.ts` — ID format

```ts
// Add at module level (outside class):
let _toastCounter = 0;

// Update show() method:
private show(type: Toast['type'], title: string, message: string, duration: number): void {
  const toast: Toast = {
    id: `${Date.now()}-${++_toastCounter}`,  // was: Date.now().toString()
    type, title, message, duration,
  };
  this.toastSubject.next(toast);
}
```

### `voice.service.ts` — no-op guard

```ts
async startRecording(): Promise<void> {
  if (this.isRecording()) return;  // ADD: no-op if already recording
  // ... rest of existing implementation unchanged
}
```
