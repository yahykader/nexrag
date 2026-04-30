# Research: Phase 2 — HTTP Interceptors Test Suite

**Branch**: `011-phase2-interceptors`
**Date**: 2026-04-28
**Input**: `spec.md` + Angular 21 / Spectator v22 / Vitest source inspection

---

## Decision 1 — Test runner integration

**Decision**: Use `@angular/build:unit-test` builder (already configured) with Vitest globals.

**Rationale**: `angular.json` already has `"test": { "builder": "@angular/build:unit-test" }`.
`tsconfig.spec.json` already has `"types": ["vitest/globals"]`, so `describe`, `it`,
`beforeEach`, `afterEach`, `vi`, `expect` are all available as globals — no imports needed.
`@ngneat/spectator` ships a Vitest-specific build (`ngneat-spectator-vitest.mjs`) that is
picked up automatically.

**Alternatives considered**:
- Karma/Jasmine — deprecated, not installed, not applicable.
- Jest — not installed; Vitest is the project's chosen runner.

---

## Decision 2 — Spectator factory for functional interceptors

**Decision**: Use `createServiceFactory<HttpClient>` (NOT `createHttpFactory`) with:
```
providers: [
  provideHttpClient(withInterceptors([interceptorFn])),
  provideHttpClientTesting()
]
```

**Rationale**: Source inspection of `@ngneat/spectator` v22 revealed that `createHttpFactory`
internally calls `initialHttpModule`, which pushes `HttpClientTestingModule` onto the imports
array. `HttpClientTestingModule` is **deprecated** in Angular 21 and, critically, conflicts
with `provideHttpClient(withInterceptors([...]))`. Both attempt to provide `HttpClient`; using
them together breaks the `HttpTestingController` backend hook.

The correct Angular 21 approach is the functional provider pair:
- `provideHttpClient(withInterceptors([fn]))` — registers the interceptor in the DI pipeline
- `provideHttpClientTesting()` — swaps in the testing backend (returns `Provider[]`)

`createServiceFactory` accepts `providers: any[]`, does NOT pre-import
`HttpClientTestingModule`, and allows Spectator's DI injection (`spectator.inject(Token)`)
for retrieving `HttpTestingController`. This is fully Spectator-compliant per
Constitution Principle VI.

**`HttpTestingController` retrieval**:
```ts
let controller: HttpTestingController;
beforeEach(() => {
  spectator = createService();
  controller = spectator.inject(HttpTestingController);
});
afterEach(() => controller.verify());
```

**Alternatives considered**:
- `createHttpFactory` — rejected; uses deprecated `HttpClientTestingModule` which conflicts
  with functional interceptor registration.
- Plain `TestBed.configureTestingModule` — rejected; violates Constitution Principle VI
  (Spectator-first mandate).

---

## Decision 3 — NgRx store mocking for `rateLimitInterceptor`

**Decision**: Use `provideMockStore({ initialState })` from `@ngrx/store/testing` inside
the `createServiceFactory` providers array.

**Rationale**: `rateLimitInterceptor` calls `inject(Store)` at runtime to dispatch actions.
`provideMockStore` replaces the real `@ngrx/store` with a mock that:
- Accepts `dispatch` spy assertions via `store.dispatch`
- Does not require any reducer/effects setup
- Is compatible with Spectator's provider system

Store dispatch assertions:
```ts
const store = spectator.inject(MockStore);
const dispatchSpy = vi.spyOn(store, 'dispatch');
// ... after flushing HTTP response ...
expect(dispatchSpy).toHaveBeenCalledWith(
  rateLimitExceeded({ message: 'Rate limit dépassé', retryAfterSeconds: 60 })
);
```

**Alternatives considered**:
- `StoreModule.forRoot(reducers)` — rejected; bootstraps real reducer, adds unnecessary
  complexity and violates Constitution Principle X (effects/reducers tested in isolation).
- Manual `Store` class mock — rejected; `provideMockStore` is the canonical NgRx testing
  utility and produces better error messages.

---

## Decision 4 — `localStorage` isolation

**Decision**: `afterEach(() => localStorage.clear())` in `rate-limit.interceptor.spec.ts`.

**Rationale**: Vitest runs specs in a jsdom environment where `localStorage` is a real
`Storage` object shared across the same spec file's test cases. Without cleanup,
`localStorage.setItem('userId', ...)` in one test bleeds into the next. `localStorage.clear()`
is the simplest idiomatic reset that requires no spy infrastructure.

**Alternatives considered**:
- `vi.spyOn(Storage.prototype, 'getItem')` — more complex; requires `mockRestore()` in
  `afterEach` and does not reset real values set in other tests.
- Vitest `--isolate` flag — too coarse; isolates at file level, not test level.

---

## Decision 5 — `getEndpointType` testing strategy

**Decision**: Test `getEndpointType` indirectly through the dispatched `updateRemainingTokens`
action payload. No direct unit test of the private function.

**Rationale**: `getEndpointType` is not exported from `rate-limit.interceptor.ts`. Testing
it directly would require either exporting it (a production-code change for test purposes,
violating the "minimal changes for testability" rule) or using module internals. The function
has five deterministic URL-matching branches, all of which can be covered via the interceptor's
observable pipeline by using URLs that trigger each branch.

**Coverage**: Each of the 5 endpoint types (`upload`, `batch`, `delete`, `search`, `default`)
maps to one test case in US3 that flushes an HTTP response with the `X-RateLimit-Remaining`
header and asserts the dispatched `endpoint` value. Combined with the 200-success and
missing-header tests, all `getEndpointType` branches are reachable.

---

## Decision 6 — Vitest import source for Spectator

**Decision**: Import from `@ngneat/spectator/vitest`.

**Rationale**: Spectator v22 ships separate entry points per test runner:
- `@ngneat/spectator` — base, framework-agnostic
- `@ngneat/spectator/vitest` — includes Vitest-specific matchers and auto-setup

Using the Vitest entry ensures custom matchers (`toHaveClass`, `toBeDisabled`, etc.) are
available and the Spectator lifecycle hooks integrate correctly with Vitest's `beforeEach`/
`afterEach` without needing manual TestBed reset.

---

## Summary Table

| Unknown | Decision | Key Constraint |
|---------|----------|----------------|
| Test runner | `@angular/build:unit-test` + Vitest globals | Already configured |
| Interceptor test setup | `createServiceFactory` + `provideHttpClient` + `provideHttpClientTesting` | `createHttpFactory` conflicts with `withInterceptors` |
| NgRx mocking | `provideMockStore` + `vi.spyOn(store, 'dispatch')` | No real reducer/store needed |
| localStorage isolation | `afterEach(() => localStorage.clear())` | jsdom shares storage per file |
| Private `getEndpointType` | Test indirectly via dispatched action payload | Not exported |
| Spectator entry | `@ngneat/spectator/vitest` | Vitest-specific matchers |
