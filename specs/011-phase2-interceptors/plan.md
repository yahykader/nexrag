# Implementation Plan: Phase 2 — HTTP Interceptors Test Suite

**Branch**: `011-phase2-interceptors` | **Date**: 2026-04-28 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/011-phase2-interceptors/spec.md`

---

## Summary

Write two co-located Vitest spec files for the NexRAG frontend's functional HTTP interceptors:
`duplicate-interceptor.spec.ts` (5 test cases — 409 error enrichment) and
`rate-limit.interceptor.spec.ts` (13 test cases — header injection, remaining-token dispatch,
429 handling). Both files use `createServiceFactory` from `@ngneat/spectator/vitest` with the
modern Angular 21 `provideHttpClient(withInterceptors([fn])) + provideHttpClientTesting()`
pattern. The rate-limit spec additionally wires `provideMockStore` for NgRx dispatch
assertions. Target: 100 % branch coverage on both production files.

---

## Technical Context

**Language/Version**: TypeScript 5.9 / Angular 21
**Primary Dependencies**: `@ngneat/spectator ^22.1.0`, `@ngrx/store/testing ^21.0.1`,
  `@angular/common` (provideHttpClient, provideHttpClientTesting, HttpTestingController)
**Storage**: N/A — no persistent storage; `localStorage` used only in `rateLimitInterceptor`
  for `userId` header injection
**Testing**: `@angular/build:unit-test` + Vitest globals (already configured in
  `tsconfig.spec.json` and `angular.json`)
**Target Platform**: Browser (jsdom in Vitest; `@angular/build:unit-test` runner)
**Project Type**: Angular 21 standalone SPA — frontend module of NexRAG platform
**Performance Goals**: Phase 2 suite completes in under 5 seconds on developer workstation
**Constraints**: 100 % branch coverage on both interceptor files (Constitution Principle IX);
  `createHttpFactory` is prohibited (conflicts with `withInterceptors` — see research.md);
  no real HTTP calls (all requests flushed via `HttpTestingController`)
**Scale/Scope**: 2 spec files, ~18 test cases total (5 + 13)

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Status |
|-----------|-------|--------|
| **VI** — Angular Component Test Isolation | `createServiceFactory` used; no direct `TestBed` config; `mockProvider`/`provideMockStore` for all dependencies | ✅ PASS |
| **VII** — SOLID in TS Tests | One spec file per interceptor (SRP); `SpyObject`/`MockStore` honour Observable contracts (LSP); only direct deps injected (ISP); no `new SomeService()` (DIP) | ✅ PASS |
| **VIII** — Naming Conventions | Files: `*.spec.ts` co-located; `describe` in English; `it` labels in French imperative `"doit [action] quand [condition]"` | ✅ PASS |
| **IX** — Coverage & Quality Gates | Both files targeted at 100 % branch (safety-critical path); happy path + error + edge cases covered for every branch | ✅ PASS |
| **X** — NgRx Contract Testing | `provideMockStore` used; `store.dispatch` spied via `vi.spyOn`; no real store reducer bootstrapped | ✅ PASS |

**No violations. Plan may proceed.**

---

## Project Structure

### Documentation (this feature)

```text
specs/011-phase2-interceptors/
├── plan.md          ← this file
├── research.md      ← Phase 0 output
├── data-model.md    ← Phase 1 output
├── quickstart.md    ← Phase 1 output
└── tasks.md         ← Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code (Angular frontend)

```text
agentic-rag-ui/src/app/core/interceptors/
├── duplicate-interceptor.ts             (existing — do NOT modify)
├── duplicate-interceptor.spec.ts        ← CREATE: 5 test cases
├── rate-limit.interceptor.ts            (existing — do NOT modify)
└── rate-limit.interceptor.spec.ts       ← CREATE: 13 test cases
```

**Structure Decision**: Co-located spec files (Angular CLI convention). No new directories
required. Both spec files are pure additions — no production code is modified.

---

## Phase 0 — Research (complete)

All unknowns resolved. See [research.md](research.md) for full decision log.

| Unknown | Resolution |
|---------|-----------|
| Spectator factory for functional interceptors | `createServiceFactory<HttpClient>` + `provideHttpClientTesting()` |
| `createHttpFactory` compatibility | ❌ Conflicts with `withInterceptors` — use `createServiceFactory` instead |
| NgRx store in interceptor tests | `provideMockStore` + `vi.spyOn(store, 'dispatch')` |
| `localStorage` isolation | `afterEach(() => localStorage.clear())` |
| Private `getEndpointType` coverage | Test indirectly via dispatched action `endpoint` field |
| Spectator Vitest entry point | Import from `@ngneat/spectator/vitest` |

---

## Phase 1 — Design & Contracts (complete)

See [data-model.md](data-model.md) for entity definitions.
No external API contracts exposed — interceptors are internal pipeline components.

### Test setup blueprint

#### `duplicate-interceptor.spec.ts` — test harness

```ts
import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { duplicateInterceptor } from './duplicate-interceptor';

describe('DuplicateInterceptor', () => {
  let spectator: SpectatorService<HttpClient>;
  let controller: HttpTestingController;

  const createService = createServiceFactory({
    service: HttpClient,
    providers: [
      provideHttpClient(withInterceptors([duplicateInterceptor])),
      provideHttpClientTesting(),
    ],
  });

  beforeEach(() => {
    spectator = createService();
    controller = spectator.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  // 5 test cases — see spec.md US1
});
```

#### `rate-limit.interceptor.spec.ts` — test harness

```ts
import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { rateLimitInterceptor } from './rate-limit.interceptor';
import * as RateLimitActions from '../../features/ingestion/store/rate-limit/rate-limit.actions';

describe('RateLimitInterceptor', () => {
  let spectator: SpectatorService<HttpClient>;
  let controller: HttpTestingController;
  let store: MockStore;

  const createService = createServiceFactory({
    service: HttpClient,
    providers: [
      provideHttpClient(withInterceptors([rateLimitInterceptor])),
      provideHttpClientTesting(),
      provideMockStore(),
    ],
  });

  beforeEach(() => {
    spectator = createService();
    controller = spectator.inject(HttpTestingController);
    store = spectator.inject(MockStore);
  });

  afterEach(() => {
    controller.verify();
    localStorage.clear();
  });

  // 13 test cases — see spec.md US2 + US3 + US4
});
```

### HTTP flush pattern for error responses

```ts
// Flush a 409 error
const req = controller.expectOne('/api/test');
req.flush(
  { filename: 'doc.pdf', batchId: 'batch-1', existingBatchId: 'batch-0',
    message: 'Ce fichier existe déjà' },
  { status: 409, statusText: 'Conflict' }
);
```

### HTTP flush pattern for responses with rate-limit header

```ts
// Flush 200 with X-RateLimit-Remaining header
const req = controller.expectOne('/api/v1/upload/doc.pdf');
req.flush({}, {
  status: 200,
  statusText: 'OK',
  headers: { 'X-RateLimit-Remaining': '7' }
});
```

### NgRx dispatch assertion pattern

```ts
const dispatchSpy = vi.spyOn(store, 'dispatch');
// ... flush response ...
expect(dispatchSpy).toHaveBeenCalledWith(
  RateLimitActions.updateRemainingTokens({ endpoint: 'upload', remaining: 7 })
);
```

---

## Implementation sequence

1. **`duplicate-interceptor.spec.ts`** — no external dependencies; implement first as a
   warmup. All 5 cases use only `controller.flush` and `observable.subscribe` error assertion.

2. **`rate-limit.interceptor.spec.ts`** — depends on NgRx mock setup. Implement US2 (header
   injection) first (simplest), then US3 (token tracking — 6 cases), then US4 (429 — 4 cases).

3. **Coverage verification** — run `ng test --code-coverage --include="**/core/interceptors/**"`
   and confirm both files reach 100 % branch coverage.

---

## Complexity Tracking

> No constitution violations. No complexity tracking required.
