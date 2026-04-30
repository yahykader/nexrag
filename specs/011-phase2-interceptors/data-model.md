# Data Model: Phase 2 — HTTP Interceptors Test Suite

**Branch**: `011-phase2-interceptors`
**Date**: 2026-04-28

---

## Entities produced / consumed by the interceptors under test

### 1. `DuplicateEnrichedError`

Emitted by `duplicateInterceptor` when a 409 response is received.
This is the observable error that reaches component/effect error handlers.

```ts
interface DuplicateEnrichedError {
  isDuplicate: true;           // always true — discriminator field
  status: 409;                 // always 409
  data: DuplicateResponseData; // normalised body from the backend
  originalError: HttpErrorResponse; // the raw Angular error, preserved
}
```

**State transitions**:
- HTTP 200–399 → interceptor is transparent; `DuplicateEnrichedError` never emitted
- HTTP 409    → `DuplicateEnrichedError` emitted (replaces raw `HttpErrorResponse`)
- HTTP 4xx/5xx (other) → original `HttpErrorResponse` re-thrown unchanged

---

### 2. `DuplicateResponseData`

The normalised body inside `DuplicateEnrichedError.data`.
Fields with defaults are safe to destructure without null-checking downstream.

```ts
interface DuplicateResponseData {
  success: false;              // always false
  duplicate: true;             // always true
  filename: string;            // default: "Unknown" if missing in backend body
  batchId: string | null;      // default: null if missing
  existingBatchId: string | null; // default: batchId value if missing, else null
  message: string;             // default: "Ce fichier existe déjà"
  // + any extra fields spread from error.error (pass-through)
}
```

**Validation rules**:
- `filename` MUST never be `undefined` — always string (falls back to `"Unknown"`)
- `batchId` MAY be `null`
- `existingBatchId` MUST equal `batchId` when `existingBatchId` is absent from backend body

---

### 3. `RateLimitBackendError`

The 429 response body shape expected from the NexRAG backend.
Consumed by `rateLimitInterceptor` to extract the countdown.

```ts
interface RateLimitBackendError {
  error: string;               // e.g. "Too Many Requests"
  message: string;             // human-readable message for the UI
  retryAfterSeconds: number;   // seconds to wait before retrying
  timestamp: number;           // Unix epoch ms (informational)
}
```

**Defaults applied by interceptor**:
- `retryAfterSeconds`: defaults to `60` when field is absent or falsy

---

### 4. `EndpointType` (union)

Computed by `getEndpointType(url: string)` inside `rate-limit.interceptor.ts`.
Passed as the `endpoint` field of the dispatched `updateRemainingTokens` action.

```ts
type EndpointType = 'upload' | 'batch' | 'delete' | 'search' | 'default';
```

**URL-matching rules** (priority order — first match wins):

| Priority | URL pattern | EndpointType |
|----------|-------------|--------------|
| 1 | contains `/upload/batch` | `'batch'` |
| 2 | contains `/upload` | `'upload'` |
| 3 | contains `/delete` OR `DELETE` | `'delete'` |
| 4 | contains `/search` | `'search'` |
| 5 | (no match) | `'default'` |

---

### 5. NgRx actions dispatched (output side-effects)

These are NOT entities created by the interceptors, but they are the observable
outputs verified by the test suite.

| Action creator | Dispatched when | Payload |
|----------------|----------------|---------|
| `updateRemainingTokens` | 200 response with `X-RateLimit-Remaining` header | `{ endpoint: EndpointType, remaining: number }` |
| `rateLimitExceeded` | 429 response | `{ message: string, retryAfterSeconds: number }` |

---

### 6. HTTP request mutation

`rateLimitInterceptor` MAY clone the request and add a header:

| Condition | Mutation |
|-----------|---------|
| `localStorage.getItem('userId')` is non-null | Adds `X-User-Id: <userId>` to cloned request |
| `localStorage.getItem('userId')` is null | Request forwarded unchanged |
