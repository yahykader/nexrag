# Feature Specification: Phase 2 — HTTP Interceptors Test Suite

**Feature Branch**: `011-phase2-interceptors`
**Created**: 2026-04-28
**Status**: Draft
**Source Plan**: `agentic-ui-test-plan-speckit.md` — Phase 2
**Scope**: `src/app/core/interceptors/duplicate-interceptor.ts`
        `src/app/core/interceptors/rate-limit.interceptor.ts`

---

## Context

The NexRAG frontend uses two functional HTTP interceptors (`HttpInterceptorFn`) that sit in
the Angular HTTP pipeline. This spec defines what the test suite for those interceptors must
verify — the observable behaviors, error-transformation contracts, NgRx side-effects, and
header-manipulation rules — so that developers can write precise, regression-proof specs.

---

## Clarifications

### Session 2026-04-28

- Q: Should `duplicateInterceptor` tests follow the test-plan's in-flight deduplication
  scenarios, or stay scoped to 409 error enrichment? → A: Scope stays at 409 error
  enrichment only. Test-plan scenario titles ("annuler une requête identique déjà en cours")
  are superseded by the acceptance scenarios in US1 of this spec. In-flight deduplication
  is out of scope for this phase and belongs in a separate feature branch if ever needed.
- Q: Should the `rateLimitInterceptor` automatically retry requests after the backoff delay
  ("doit retenter la requête après le délai de backoff")? → A: Retry is out of scope.
  The interceptor re-throws the 429 error; the UI countdown component handles user-driven
  retry. The retry scenario from the test plan is dropped from this spec.
- Q: Which test setup pattern should be mandated for functional interceptor tests? → A:
  `createHttpFactory` with `providers: [provideHttpClient(withInterceptors([interceptorFn]))]`.
  Spectator-first approach; consistent with Constitution Principle VI across all HTTP tests.
- Q: Should `updateRemainingTokens` dispatch be tested on non-200 error responses (401,
  403, 500) that carry the rate-limit header? → A: No. Tests cover only 200 (dispatch) and
  missing-header (no dispatch). Header behaviour on 4xx/5xx is deferred — it is backend
  infrastructure behaviour outside the interceptor's documented contract.
- Q: How should `localStorage` state be managed between tests in `rate-limit.interceptor.spec.ts`
  to guarantee isolation? → A: `afterEach(() => localStorage.clear())` in the spec file.
  Simplest idiomatic approach; ensures no userId bleeds across test cases.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Duplicate-File 409 Error Enrichment (Priority: P1)

A user uploads a file that already exists in the system. The backend returns HTTP 409 Conflict.
The interceptor MUST transform that raw HTTP error into a structured duplicate-error object
before it reaches any component or store effect, so that the UI can display a meaningful
"file already exists" message without each consumer parsing raw HTTP responses.

> **Scope note** (clarified 2026-04-28): this interceptor handles 409 response enrichment
> only. In-flight request cancellation / deduplication is explicitly out of scope.

**Why this priority**: Duplicate detection is the first guard against polluting the vector
store with redundant embeddings. A broken interceptor causes silent data duplication or
opaque error messages for the end-user.

**Independent Test**: Can be verified in complete isolation by simulating an HTTP 409
response and asserting on the enriched error object that the interceptor emits — no Angular
component or NgRx store required.

**Acceptance Scenarios**:

1. **Given** an HTTP request is in flight, **When** the server responds with status 409
   and a body containing `{ filename, batchId, existingBatchId, message }`,
   **Then** the interceptor rethrows an enriched error with `isDuplicate: true`,
   `status: 409`, a `data` object containing all four fields, and the original
   `HttpErrorResponse` preserved under `originalError`.

2. **Given** the 409 body is missing the `filename` field, **When** the interceptor
   processes the error, **Then** `data.filename` MUST default to `"Unknown"` and
   the test MUST confirm the interceptor does not throw a runtime exception.

3. **Given** the 409 body is missing `batchId`, **When** the interceptor processes the
   error, **Then** `data.batchId` MUST default to `null` and `data.existingBatchId`
   MUST also default to `null`.

4. **Given** an HTTP request is in flight, **When** the server responds with HTTP 200,
   **Then** the interceptor MUST pass the response through unchanged (no transformation,
   no side effect).

5. **Given** an HTTP request is in flight, **When** the server responds with HTTP 500,
   **Then** the interceptor MUST rethrow the original `HttpErrorResponse` unchanged
   (the enrichment logic is exclusive to 409).

---

### User Story 2 — Rate-Limit Header Injection (Priority: P2)

A user is identified by a session-level user ID stored locally. Every outgoing HTTP request
MUST carry that identity so the backend rate-limiter can apply per-user quotas. When the ID
is absent (anonymous session), requests MUST be sent without the header.

**Why this priority**: Missing the user-identity header causes all requests to share the
anonymous rate-limit bucket, incorrectly throttling users who are actually identified.

**Independent Test**: Verified by inspecting the cloned request object that the interceptor
passes to `next` — assert presence or absence of `X-User-Id` based on localStorage state.

**Acceptance Scenarios**:

1. **Given** `localStorage` contains `userId = "user-42"`, **When** the interceptor
   processes any outgoing request, **Then** the forwarded request MUST include the header
   `X-User-Id: user-42`.

2. **Given** `localStorage` does NOT contain a `userId` key, **When** the interceptor
   processes any outgoing request, **Then** the forwarded request MUST NOT include the
   `X-User-Id` header.

3. **Given** `userId` is present, **When** the interceptor clones the request,
   **Then** the original request object MUST remain unmodified (immutability contract).

---

### User Story 3 — Remaining-Token Tracking (Priority: P2)

After each successful API call, the backend includes an `X-RateLimit-Remaining` response
header indicating how many requests the user can still make for that endpoint. The interceptor
MUST translate that header value into an NgRx store update so the UI can display a live
quota indicator without each component reading HTTP headers directly.

**Why this priority**: Without live token tracking, the rate-limit indicator shows stale
data and users are surprised by sudden 429 rejections with no prior warning.

**Independent Test**: Verified by simulating an `HttpResponse` event carrying the header
and asserting that `updateRemainingTokens` was dispatched to the NgRx store with the correct
endpoint type and remaining count.

**Acceptance Scenarios**:

1. **Given** a successful response for `/api/v1/upload/...`, **When** it carries
   `X-RateLimit-Remaining: 7`, **Then** the interceptor MUST dispatch
   `updateRemainingTokens({ endpoint: 'upload', remaining: 7 })`.

2. **Given** a successful response for `/api/v1/upload/batch/...`, **When** it carries
   the remaining-tokens header, **Then** `endpoint` MUST be `'batch'` (batch takes
   precedence over upload in the URL-matching logic).

3. **Given** a successful response for `/api/v1/search/...`, **When** it carries the
   header, **Then** `endpoint` MUST be `'search'`.

4. **Given** a successful response for `/api/v1/documents/delete/...`, **When** it carries
   the header, **Then** `endpoint` MUST be `'delete'`.

5. **Given** a successful response for `/api/v1/something-else`, **When** it carries the
   header, **Then** `endpoint` MUST be `'default'`.

6. **Given** a successful response that does NOT include `X-RateLimit-Remaining`,
   **When** the interceptor processes it, **Then** `updateRemainingTokens` MUST NOT
   be dispatched.

> **Scope note** (clarified 2026-04-28): `updateRemainingTokens` dispatch on non-200
> error responses (401, 403, 500) is explicitly deferred — test only 200 and missing-header
> cases.

---

### User Story 4 — Rate-Limit Exceeded (429) Handling (Priority: P1)

When the user has exhausted their quota, the backend returns HTTP 429 Too Many Requests with
a body specifying how many seconds the client must wait before retrying. The interceptor MUST
dispatch the rate-limit state to the NgRx store AND still propagate the error so upstream
callers can respond (e.g., show a toast). Swallowing the error silently is not acceptable.

> **Scope note** (clarified 2026-04-28): automatic retry-after-backoff is explicitly out of
> scope. The interceptor does NOT retry. Retry responsibility belongs to the user via the
> rate-limit countdown UI component.

**Why this priority**: Silently dropping the 429 hides quota exhaustion from component-level
error handlers and prevents the rate-limit countdown UI from activating.

**Independent Test**: Verified by simulating an `HttpErrorResponse` with `status: 429` and
asserting: (a) the NgRx store received `rateLimitExceeded`, (b) the observable errors out
with the original `HttpErrorResponse`.

**Acceptance Scenarios**:

1. **Given** the server responds with HTTP 429 and body
   `{ message: "Rate limit dépassé", retryAfterSeconds: 60 }`,
   **When** the interceptor processes the error, **Then** it MUST dispatch
   `rateLimitExceeded({ message: "Rate limit dépassé", retryAfterSeconds: 60 })` to the
   NgRx store.

2. **Given** the 429 body is missing `retryAfterSeconds`, **When** the interceptor
   processes the error, **Then** it MUST dispatch with `retryAfterSeconds: 60`
   (the documented default fallback).

3. **Given** the server responds with HTTP 429, **When** the interceptor processes the
   error, **Then** it MUST rethrow the original `HttpErrorResponse` so downstream
   subscribers receive the error (error is NOT swallowed).

4. **Given** the server responds with HTTP 200 (normal success), **When** the interceptor
   processes the response, **Then** `rateLimitExceeded` MUST NOT be dispatched.

---

### Edge Cases

- What happens when the 409 body is `null` or `undefined`?
  → `data.filename` defaults to `"Unknown"`, `data.batchId` and `data.existingBatchId`
    default to `null`, `data.message` defaults to `"Ce fichier existe déjà"`.
- What happens when `X-RateLimit-Remaining` value is not a valid integer string?
  → `parseInt` returns `NaN`; the dispatched `remaining` value is `NaN`.
  This is a known edge case; the spec does NOT require the interceptor to validate it,
  but the test MUST document this behaviour for observability.
- What if `localStorage` is unavailable (e.g., private-browsing restriction)?
  → No `X-User-Id` header is added; the interceptor silently proceeds without the header.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The test suite MUST cover `duplicate-interceptor.ts` with at least one
  success-path test, one 409-path test, one 500-path test, and one missing-field test.

- **FR-002**: The test suite MUST cover `rate-limit.interceptor.ts` with: header-injection
  (with / without userId), `X-RateLimit-Remaining` dispatch for all 5 endpoint types,
  the missing-header no-dispatch case, the 429-dispatch case, the 429-default-fallback
  case, and the 429-error-propagation case.

- **FR-003**: Every `it()` block MUST use the French imperative naming convention
  `"doit [action] quand [condition]"` as required by Constitution Principle VIII.

- **FR-004**: All NgRx store interactions in `rate-limit.interceptor.ts` tests MUST be
  verified via a mock `Store` provided through `provideMockStore` — no real NgRx store
  bootstrapping.

- **FR-005**: Both interceptor test files MUST use `createServiceFactory` from
  `@ngneat/spectator` with `service: HttpClient` and
  `providers: [provideHttpClient(withInterceptors([interceptorFn])), provideHttpClientTesting()]`.
  `createHttpFactory` is NOT permitted — it imports the deprecated `HttpClientTestingModule`
  which conflicts with `withInterceptors`. `HttpTestingController` is obtained via
  `spectator.inject(HttpTestingController)`. Plain `TestBed` without Spectator is also
  NOT permitted.

- **FR-006**: The `rateLimitInterceptor` tests MUST spy on `store.dispatch` and assert
  exact action type and payload.

- **FR-007**: Tests MUST NOT make real HTTP calls; all requests MUST be intercepted and
  flushed via `HttpTestingController`.

- **FR-008**: Branch coverage for both interceptors MUST reach 100 % (safety-critical path
  per Constitution Principle IX — duplicate detection and rate-limit handling). The known
  deferred gap (header dispatch on 4xx/5xx error responses) is an accepted exclusion;
  document it in the spec checklist notes if coverage tooling flags it.

### Key Entities

- **DuplicateError**: The enriched error object emitted by `duplicateInterceptor` on 409.
  Fields: `isDuplicate: true`, `status: 409`, `data: DuplicateResponse`, `originalError: HttpErrorResponse`.

- **DuplicateResponse**: Normalised payload inside `DuplicateError.data`.
  Fields: `success: false`, `duplicate: true`, `filename: string`, `batchId: string | null`,
  `existingBatchId: string | null`, `message: string` (plus any extra fields from the backend body).

- **RateLimitError** (backend body on 429): `error: string`, `message: string`,
  `retryAfterSeconds: number`, `timestamp: number`.

- **EndpointType**: Union `'upload' | 'batch' | 'delete' | 'search' | 'default'`.
  Determined by URL-pattern matching inside `getEndpointType(url)`.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All test cases listed in `agentic-ui-test-plan-speckit.md` Phase 2 are
  implemented with no empty (`{ ... }`) test bodies remaining.

- **SC-002**: Branch coverage for `duplicate-interceptor.ts` reaches 100 %
  (verified via `ng test --code-coverage`).

- **SC-003**: Branch coverage for `rate-limit.interceptor.ts` reaches 100 %.

- **SC-004**: The Phase 2 test suite executes in under 5 seconds on a developer workstation
  (no real I/O, no timers beyond `fakeAsync`).

- **SC-005**: All 5 endpoint-type routing paths are covered by dedicated test cases,
  with explicit assertions on the dispatched `endpoint` value.

- **SC-006**: Zero tests rely on the order of execution; each spec passes when run in
  isolation (verified by running a single spec file with `ng test --include`).

---

## Assumptions

- `HttpClientTestingModule` from `@angular/common/http/testing` is available and compatible
  with Angular 21 / Vitest — no Karma/Jasmine runner is required.
- `@ngneat/spectator` v22 is already installed (`devDependencies`) and its `createHttpFactory`
  works with functional interceptors (`HttpInterceptorFn`) via `withInterceptors([...])`.
- The NgRx store is provided via `provideMockStore` from `@ngrx/store/testing`; the tests
  do NOT need a real store with reducers loaded.
- `localStorage` is reset between tests via `afterEach(() => localStorage.clear())` in
  `rate-limit.interceptor.spec.ts`; no global Vitest isolation flag is required.
- The `getEndpointType` helper function is not exported; it is tested indirectly through the
  interceptor's observable output (dispatched action payload).
- French language is used for all `describe` / `it` labels and assertion failure messages,
  consistent with Constitution Principle VIII.
- The test files are co-located with the source files:
  `src/app/core/interceptors/duplicate-interceptor.spec.ts`
  `src/app/core/interceptors/rate-limit.interceptor.spec.ts`
