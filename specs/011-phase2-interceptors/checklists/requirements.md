# Specification Quality Checklist: Phase 2 — HTTP Interceptors Test Suite

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-04-28
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- All 8 FR items map directly to acceptance scenarios in User Stories 1–4
- Edge cases explicitly document the `null`-body 409, non-integer `X-RateLimit-Remaining`,
  and unavailable localStorage scenarios
- SC-002 and SC-003 target 100% branch coverage (safety-critical per Constitution Principle IX)
- 5 clarifications applied (session 2026-04-28):
  1. `duplicateInterceptor` scope locked to 409 enrichment; in-flight dedup out of scope
  2. Retry-after-backoff dropped from `rateLimitInterceptor` test scope
  3. Test setup: `createServiceFactory` (Spectator) for `duplicate-interceptor.spec.ts`;
     `TestBed.runInInjectionContext()` direct invocation for `rate-limit.interceptor.spec.ts`
     (required because `inject(Store)` inside the functional interceptor cannot run in the
     HTTP testing chain in `@angular/build:unit-test` + Vitest environment)
  4. `updateRemainingTokens` tested on 200 / missing-header only; 4xx/5xx deferred
  5. `localStorage` isolation via `vi.stubGlobal('localStorage', ...)` + `vi.unstubAllGlobals()`
     (`vi.spyOn(Storage.prototype, 'getItem')` breaks `localStorage.getItem` in this jsdom env)

## Phase 2 Implementation Complete — 2026-04-28

**20 tests passing** (2 app + 5 duplicate + 13 rate-limit), 0 failures.

Known technical decisions:
- `rate-limit.interceptor.spec.ts` uses `TestBed.runInInjectionContext()` to invoke the
  interceptor directly, bypassing the HTTP testing backend. This is necessary because
  `inject(Store)` inside an `HttpInterceptorFn` does not run in an injectable context when
  dispatched through `HttpClient` in the Angular unit test environment (jsdom + Vitest).
- Production bug fixed: `duplicate-interceptor.ts` line 22 — `existingBatchId` now defaults
  to `null` (not `undefined`) when both `existingBatchId` and `batchId` fields are absent.
