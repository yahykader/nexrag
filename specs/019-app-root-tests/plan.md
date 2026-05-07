# Implementation Plan: PHASE 14 — App Root Integration Tests

**Branch**: `019-app-root-tests` | **Date**: 2026-05-06 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/019-app-root-tests/spec.md`

## Summary

Write 3 co-located Vitest/Spectator spec files for the Angular application root (`src/app/`): `app.component.spec.ts`, `app.routes.spec.ts`, and `app.config.spec.ts`. The suite verifies shell assembly (router-outlet, navbar, toast container), top-level route resolution (root redirect, workspace lazy load, management regression guard, wildcard fallback), and provider configuration completeness (5 store slices, 5 effect classes, dual HTTP interceptors). Two integration tests run with the real Angular provider chain and real router — no mocks. The existing stub `app.spec.ts` is replaced by the three new files. Total: 13 tests (11 unit + 2 integration). No production code changes required.

## Technical Context

**Language/Version**: TypeScript 5.9 / Angular 21
**Primary Dependencies**: `@ngneat/spectator ^22.1.0`, `@ngrx/store/testing ^21.0.1`, Vitest `^4.0.8`, `RouterTestingModule` (Angular 21 BOM)
**Storage**: N/A — `provideMockStore` for unit tests; real `appConfig` providers for integration tests
**Testing**: Vitest `^4.0.8` + `@ngneat/spectator ^22.1.0` + Angular Router Testing utilities
**Target Platform**: Node.js (Vitest test environment, jsdom browser emulation)
**Project Type**: Angular 21 SPA — application root test suite (Phase 14 of 14)
**Performance Goals**: All 13 tests complete in under 15 seconds; each unit test under 500ms
**Constraints**: Unit tests use `provideMockStore`; NO real HTTP or WebSocket calls; `app.config.spec.ts` inspects `appConfig.providers` statically without TestBed; integration tests use real `appConfig` providers (minus `provideStoreDevtools`)
**Scale/Scope**: 3 spec files, 13 tests (11 unit + 2 integration)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against **Part B — Frontend Principles VI–X** of the NexRAG Test Constitution v1.1.0.

| Principle | Gate Requirement | Status |
|-----------|-----------------|--------|
| VI — Component Test Isolation | `createComponentFactory` for AppComponent unit tests; `shallow: true` to stub child components (RouterOutlet, ToastContainer); real providers only in `[INTÉGRATION]` tests | **PASS** — FR-001/US1 mandates Spectator factories. Principle VI explicitly allows real providers in Phase 13–14 integration tests. `app.config.spec.ts` uses no TestBed at all (static inspection). |
| VII — SOLID in Tests | One spec per source file; no omnibus files | **PASS** — 3 spec files, each covering exactly one source artefact. |
| VIII — Naming Conventions | `.spec.ts` suffix; co-located; `describe` in English; `it` in French imperative; `[INTÉGRATION]` prefix on integration tests | **PASS** — filenames follow Angular CLI convention; 2 integration tests carry `[INTÉGRATION]` prefix. |
| IX — Coverage Gates | ≥80% statements, ≥75% branches, ≥85% functions, ≥80% lines | **PASS** — SC-004 sets all four thresholds. AppComponent, AppRoutes, AppConfig have minimal conditional logic; 11 unit + 2 integration tests achieve comprehensive branch coverage. |
| X — NgRx & Real-Time | `provideMockStore` in component unit tests; real store only in `[INTÉGRATION]` | **PASS** — `app.component.spec.ts` unit tests use `provideMockStore({})`; the bootstrap integration test uses the real `provideStore()` from `appConfig`. |

**No gate violations. No complexity justification required.**

## Project Structure

### Documentation (this feature)

```text
specs/019-app-root-tests/
├── plan.md              ← this file
├── research.md          ← Phase 0 output
├── data-model.md        ← Phase 1 output
├── quickstart.md        ← Phase 1 output
└── tasks.md             ← Phase 2 output (/speckit.tasks — not created here)
```

### Source Code (repository root: `agentic-rag-ui/`)

```text
src/app/
├── app.component.ts           (exists — no changes required)
├── app.component.html         (exists — no changes required)
├── app.component.scss         (exists — no changes required)
├── app.component.spec.ts      ← NEW (5 tests: 4 unit + 1 integration)
│
├── app.routes.ts              (exists — no changes required)
├── app.routes.spec.ts         ← NEW (5 tests: 4 unit + 1 integration)
│
├── app.config.ts              (exists — no changes required)
├── app.config.spec.ts         ← NEW (3 unit tests — static provider inspection)
│
└── app.spec.ts                ← DELETE (existing stub, superseded by the 3 new files)
```

**Structure Decision**: Angular CLI co-location — all 3 spec files live beside their source counterparts in `src/app/`. The existing `app.spec.ts` stub (2 raw TestBed tests, no Spectator) is deleted; its 2 scenarios (`create the app`, `correct title`) migrate into `app.component.spec.ts` under Spectator.

## Complexity Tracking

> No violations. No entries required.
