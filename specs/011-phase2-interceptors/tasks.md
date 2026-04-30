---
description: "Task list for Phase 2 — HTTP Interceptors Test Suite"
---

# Tasks: Phase 2 — HTTP Interceptors Test Suite

**Input**: Design documents from `specs/011-phase2-interceptors/`
**Prerequisites**: plan.md ✅ spec.md ✅ research.md ✅ data-model.md ✅ quickstart.md ✅

**Tests**: This feature IS the test suite — all tasks produce test code.
Every test MUST be written RED-first (failing), then the existing production code
makes it GREEN. No production code is modified.

**Organization**: Tasks are grouped by user story. US1 and {US4, US2, US3} map to
separate spec files and can be implemented by different developers in parallel after
the setup phase completes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no shared dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- Exact file paths included in every task description

## Path Conventions

All spec files are co-located with production sources:

```
agentic-rag-ui/src/app/core/interceptors/
├── duplicate-interceptor.ts              ← existing (DO NOT MODIFY)
├── duplicate-interceptor.spec.ts         ← CREATE in Phase 3
├── rate-limit.interceptor.ts             ← existing (DO NOT MODIFY)
└── rate-limit.interceptor.spec.ts        ← CREATE in Phase 4
```

---

## Phase 1: Setup — Verify Test Infrastructure

**Purpose**: Confirm the Angular + Vitest + Spectator pipeline is operational before
writing any new spec files.

- [X] T001 Run `ng test --include="src/app/app.spec.ts"` from `agentic-rag-ui/` and confirm the test runner exits with zero failures — this validates the `@angular/build:unit-test` + Vitest globals pipeline

- [X] T002 [P] Confirm `@ngneat/spectator/vitest` resolves correctly: add a temporary import `import { createServiceFactory } from '@ngneat/spectator/vitest'` at the top of a scratch file and run `npx tsc --noEmit` from `agentic-rag-ui/` — remove the scratch file after confirmation

- [X] T003 [P] Confirm `provideHttpClientTesting` is importable: add a temporary import `import { provideHttpClientTesting } from '@angular/common/http/testing'` in a scratch file and run `npx tsc --noEmit` from `agentic-rag-ui/` — remove after confirmation

**Checkpoint**: Test pipeline confirmed operational. Both spec file phases can now proceed in parallel.

---

## Phase 2: Foundational — No blocking prerequisites

There are no shared infrastructure components required before the user story
phases begin. Both `duplicate-interceptor.spec.ts` and `rate-limit.interceptor.spec.ts`
are fully self-contained. Proceed directly to Phase 3 and Phase 4 in parallel if
team capacity allows.

---

## Phase 3: User Story 1 — Duplicate-File 409 Error Enrichment (Priority: P1) 🎯 MVP

**Goal**: Verify that `duplicateInterceptor` transforms a 409 `HttpErrorResponse` into
a structured `DuplicateEnrichedError` object, handles missing body fields with documented
defaults, and passes non-409 responses unchanged.

**Independent Test**: Run `ng test --include="**/duplicate-interceptor.spec.ts"` — if 5
tests pass, this story is fully verified in isolation.

**Spec file**: `agentic-rag-ui/src/app/core/interceptors/duplicate-interceptor.spec.ts`

### Test harness (write before individual test cases)

- [X] T004 [US1] Create `agentic-rag-ui/src/app/core/interceptors/duplicate-interceptor.spec.ts` with the test harness: import `createServiceFactory, SpectatorService` from `@ngneat/spectator/vitest`; import `HttpClient, provideHttpClient, withInterceptors` from `@angular/common/http`; import `HttpTestingController, provideHttpClientTesting` from `@angular/common/http/testing`; import `duplicateInterceptor` from `./duplicate-interceptor`; declare `spectator`, `controller`, `createService` variables; wire `createServiceFactory({ service: HttpClient, providers: [provideHttpClient(withInterceptors([duplicateInterceptor])), provideHttpClientTesting()] })`; add `beforeEach` that calls `createService()` and injects `HttpTestingController`; add `afterEach` that calls `controller.verify()`

### Test cases — US1 Acceptance Scenarios

- [X] T005 [US1] In `duplicate-interceptor.spec.ts`, write `it('doit enrichir l\'erreur avec isDuplicate=true, status=409 et data normalisée quand le serveur répond 409')`: subscribe to `spectator.service.get('/api/test')`, flush `{ filename: 'doc.pdf', batchId: 'b1', existingBatchId: 'b0', message: 'Ce fichier existe déjà' }` with `{ status: 409, statusText: 'Conflict' }`, assert error object has `isDuplicate: true`, `status: 409`, `data.filename === 'doc.pdf'`, `data.batchId === 'b1'`, `originalError` instanceof `HttpErrorResponse`

- [X] T006 [US1] In `duplicate-interceptor.spec.ts`, write `it('doit utiliser "Unknown" pour filename quand le champ est absent du body 409')`: flush `{}` with status 409, assert `error.data.filename === 'Unknown'`

- [X] T007 [US1] In `duplicate-interceptor.spec.ts`, write `it('doit utiliser null pour batchId et existingBatchId quand les champs sont absents du body 409')`: flush `{}` with status 409, assert `error.data.batchId === null` and `error.data.existingBatchId === null`

- [X] T008 [US1] In `duplicate-interceptor.spec.ts`, write `it('doit laisser passer la réponse 200 sans transformation ni erreur')`: subscribe to `spectator.service.get('/api/test')`, flush `{ ok: true }` with status 200, assert response received without error (no error callback triggered)

- [X] T009 [US1] In `duplicate-interceptor.spec.ts`, write `it('doit retransmettre l\'erreur originale sans modification quand le statut est 500')`: flush `{}` with status 500, assert error received is an `HttpErrorResponse` with `status: 500` and NOT enriched (no `isDuplicate` field)

### Validation

- [X] T010 [US1] Run `ng test --include="**/duplicate-interceptor.spec.ts"` from `agentic-rag-ui/` — confirm all 5 tests pass (green); fix any test that is skipped or still `{ ... }` stub before proceeding

**Checkpoint**: US1 complete. `duplicate-interceptor.spec.ts` has 5 passing tests.
This story is independently testable and deliverable.

---

## Phase 4: US4 + US2 + US3 — Rate-Limit Interceptor (P1 + P2)

**Goal**: Verify the full behaviour of `rateLimitInterceptor`: `X-User-Id` header injection
(US2), `updateRemainingTokens` dispatch for all 5 endpoint types (US3), and `rateLimitExceeded`
dispatch + error propagation on 429 (US4).

**Independent Test**: Run `ng test --include="**/rate-limit.interceptor.spec.ts"` — if 13
tests pass, all three stories are verified.

**Spec file**: `agentic-rag-ui/src/app/core/interceptors/rate-limit.interceptor.spec.ts`

### Test harness (write before individual test cases)

- [X] T011 Create `agentic-rag-ui/src/app/core/interceptors/rate-limit.interceptor.spec.ts` with the test harness: import `createServiceFactory, SpectatorService` from `@ngneat/spectator/vitest`; import `HttpClient, HttpResponse, provideHttpClient, withInterceptors` from `@angular/common/http`; import `HttpTestingController, provideHttpClientTesting` from `@angular/common/http/testing`; import `MockStore, provideMockStore` from `@ngrx/store/testing`; import `rateLimitInterceptor` from `./rate-limit.interceptor`; import `* as RateLimitActions` from `../../features/ingestion/store/rate-limit/rate-limit.actions`; declare `spectator`, `controller: HttpTestingController`, `store: MockStore` variables; wire `createServiceFactory({ service: HttpClient, providers: [provideHttpClient(withInterceptors([rateLimitInterceptor])), provideHttpClientTesting(), provideMockStore()] })`; add `beforeEach` calling `createService()`, injecting `HttpTestingController` and `MockStore`; add `afterEach` calling `controller.verify()` and `localStorage.clear()`

### US4 test cases — 429 Handling (P1 — write first)

- [X] T012 [US4] In `rate-limit.interceptor.spec.ts`, write `it('doit dispatcher rateLimitExceeded avec message et retryAfterSeconds quand le statut est 429')`: spy on `store.dispatch` with `vi.spyOn(store, 'dispatch')`; subscribe to `spectator.service.get('/api/test')`; flush `{ message: 'Rate limit dépassé', retryAfterSeconds: 60 }` with `{ status: 429, statusText: 'Too Many Requests' }`; assert `dispatchSpy` called with `RateLimitActions.rateLimitExceeded({ message: 'Rate limit dépassé', retryAfterSeconds: 60 })`

- [X] T013 [US4] In `rate-limit.interceptor.spec.ts`, write `it('doit utiliser retryAfterSeconds=60 par défaut quand le champ est absent du body 429')`: flush `{}` with status 429; assert dispatch called with `retryAfterSeconds: 60`

- [X] T014 [US4] In `rate-limit.interceptor.spec.ts`, write `it('doit retransmettre l\'erreur 429 sans la swallower')`: subscribe with both next and error callbacks; flush 429 response; assert error callback received an `HttpErrorResponse` with `status: 429`

- [X] T015 [US4] In `rate-limit.interceptor.spec.ts`, write `it('doit ne pas dispatcher rateLimitExceeded pour une réponse 200 normale')`: spy on `store.dispatch`; flush status 200; assert `dispatchSpy` NOT called with `rateLimitExceeded` (may be called with `updateRemainingTokens` if header present — use `not.toHaveBeenCalledWith(rateLimitExceeded(...))`)

### US2 test cases — Rate-Limit Header Injection (P2)

- [X] T016 [US2] In `rate-limit.interceptor.spec.ts`, write `it('doit ajouter le header X-User-Id quand userId est présent dans localStorage')`: `localStorage.setItem('userId', 'user-42')`; make a GET request; capture the forwarded request via `controller.expectOne(...)` and assert `req.request.headers.get('X-User-Id') === 'user-42'`; flush 200 to complete the request

- [X] T017 [US2] In `rate-limit.interceptor.spec.ts`, write `it('doit ne pas ajouter le header X-User-Id quand userId est absent de localStorage')`: make a GET request (no localStorage setup); assert `req.request.headers.has('X-User-Id') === false`; flush 200

- [X] T018 [US2] In `rate-limit.interceptor.spec.ts`, write `it('doit ne pas modifier l\'objet requête original (immutabilité)')`: `localStorage.setItem('userId', 'u1')`; capture the original `HttpRequest` reference before the interceptor runs and the cloned reference from `controller.expectOne`; assert they are different object references (clone was created); flush 200

### US3 test cases — Remaining-Token Tracking (P2)

- [X] T019 [US3] In `rate-limit.interceptor.spec.ts`, write `it('doit dispatcher updateRemainingTokens avec endpoint=upload pour une URL /api/v1/upload/...')`: spy on `store.dispatch`; flush `{}` with `{ status: 200, headers: { 'X-RateLimit-Remaining': '7' } }` for URL `/api/v1/upload/doc.pdf`; assert dispatch called with `RateLimitActions.updateRemainingTokens({ endpoint: 'upload', remaining: 7 })`

- [X] T020 [US3] In `rate-limit.interceptor.spec.ts`, write `it('doit dispatcher updateRemainingTokens avec endpoint=batch pour une URL /api/v1/upload/batch/...')`: same pattern with URL `/api/v1/upload/batch/123`; assert `endpoint: 'batch'`

- [X] T021 [US3] In `rate-limit.interceptor.spec.ts`, write `it('doit dispatcher updateRemainingTokens avec endpoint=search pour une URL /api/v1/search/...')`: URL `/api/v1/search/query`; assert `endpoint: 'search'`

- [X] T022 [US3] In `rate-limit.interceptor.spec.ts`, write `it('doit dispatcher updateRemainingTokens avec endpoint=delete pour une URL /api/v1/delete/...')`: URL `/api/v1/delete/doc-1`; assert `endpoint: 'delete'`

- [X] T023 [US3] In `rate-limit.interceptor.spec.ts`, write `it('doit dispatcher updateRemainingTokens avec endpoint=default pour une URL sans pattern reconnu')`: URL `/api/v1/other/resource`; assert `endpoint: 'default'`

- [X] T024 [US3] In `rate-limit.interceptor.spec.ts`, write `it('doit ne pas dispatcher updateRemainingTokens si le header X-RateLimit-Remaining est absent')`: spy on `store.dispatch`; flush 200 response WITHOUT the `X-RateLimit-Remaining` header; assert `dispatchSpy` NOT called with `updateRemainingTokens(...)`

### Validation

- [X] T025 Run `ng test --include="**/rate-limit.interceptor.spec.ts"` from `agentic-rag-ui/` — confirm all 13 tests pass; fix any failing test before proceeding

**Checkpoint**: US2, US3 and US4 complete. `rate-limit.interceptor.spec.ts` has 13 passing tests.

---

## Phase 5: Polish & Coverage Validation

**Purpose**: Enforce the 100% branch coverage gate required by Constitution Principle IX
and commit the complete Phase 2 test suite.

- [X] T026 Run `ng test --code-coverage --include="**/core/interceptors/**"` from `agentic-rag-ui/` — open `coverage/agentic-rag-ui/src/app/core/interceptors/duplicate-interceptor.ts.html` and verify **Branches: 100%** and **Statements: 100%**

- [X] T027 Run `ng test --code-coverage --include="**/core/interceptors/**"` — open `coverage/agentic-rag-ui/src/app/core/interceptors/rate-limit.interceptor.ts.html` and verify **Branches: 100%**; if the `X-RateLimit-Remaining`-on-error branch (deferred per spec clarification) is flagged as uncovered, document it as a known exception in `specs/011-phase2-interceptors/checklists/requirements.md`

- [X] T028 [P] Update `specs/011-phase2-interceptors/checklists/requirements.md` — mark all items `[x]` completed and add final note: "Phase 2 complete — 18 tests passing, 100% branch coverage on duplicate-interceptor.ts and rate-limit.interceptor.ts"

- [X] T029 [P] Run `ng test --include="**/core/interceptors/**"` one final time without `--code-coverage` to confirm clean pass — record test count in commit message

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Skipped — no blocking prerequisites
- **US1 (Phase 3)**: Depends on Setup completion — no dependency on Phase 4
- **US4+US2+US3 (Phase 4)**: Depends on Setup completion — independent of Phase 3 (different file)
- **Polish (Phase 5)**: Depends on BOTH Phase 3 AND Phase 4 being green

### User Story Dependencies

- **US1**: Start after Phase 1 — no dependency on any other story
- **US4**: Start after Phase 1 — no dependency on US1, US2, US3; write first within Phase 4
- **US2**: Start after T011 (harness in place)
- **US3**: Start after T011 (harness in place)

### Within Each Phase

- Harness task (T004 / T011) MUST be complete before individual `it()` tasks begin
- All `it()` tasks within a spec file share the same file — write sequentially in the described order
- Both spec files can be worked in parallel by different developers (T004 and T011 are [independent files])

### Parallel Opportunities

Phase 3 and Phase 4 can be executed in parallel (different files):

```bash
# Developer A — Phase 3
ng test --include="**/duplicate-interceptor.spec.ts" --watch

# Developer B — Phase 4
ng test --include="**/rate-limit.interceptor.spec.ts" --watch
```

---

## Implementation Strategy

### MVP First (US1 only — Phase 3)

1. Complete Phase 1: Setup (T001–T003)
2. Complete Phase 3: US1 (T004–T010)
3. **STOP and VALIDATE**: `ng test --include="**/duplicate-interceptor.spec.ts"` — 5 tests pass
4. Phase 2 interceptor tests are partially compliant; US1 demonstrates the test pattern

### Incremental Delivery

1. Setup (T001–T003) → baseline confirmed
2. US1 / Phase 3 (T004–T010) → `duplicate-interceptor.spec.ts` green ✅
3. US4 within Phase 4 (T011–T015) → 429 handling tests green ✅
4. US2 within Phase 4 (T016–T018) → header injection tests green ✅
5. US3 within Phase 4 (T019–T024) → token tracking tests green ✅
6. Validate Phase 4 (T025) → `rate-limit.interceptor.spec.ts` 13 tests green ✅
7. Polish (T026–T029) → 100% branch coverage confirmed ✅

### Parallel Team Strategy

With two developers available after Setup:

- **Developer A**: Phase 3 (T004 → T010) — `duplicate-interceptor.spec.ts`
- **Developer B**: Phase 4 (T011 → T025) — `rate-limit.interceptor.spec.ts`
- Both: Phase 5 (T026–T029) — coverage + commit after merge

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks in the same phase
- US labels map to spec.md user stories: US1=P1, US2=P2, US3=P2, US4=P1
- ALL test bodies must be fully implemented — no `{ ... }` stubs left in the committed spec
- Confirm each test FAILS before the production file exists (red-green rule)
- Actually, production files already exist — verify tests fail for the WRONG BEHAVIOUR first
  (e.g., a test that expects `isDuplicate: true` should fail if you temporarily break that line)
- The `getEndpointType` private function is exercised via T019–T023 — no direct test needed
- `localStorage.clear()` in `afterEach` handles US2 test isolation (T016 sets userId)
