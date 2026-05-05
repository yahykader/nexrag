# Implementation Plan: PHASE 11 — Ingestion Upload Page Test Suite

**Branch**: `018-ingestion-pages-tests` | **Date**: 2026-05-05 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/018-ingestion-pages-tests/spec.md`

## Summary

Write 1 co-located Vitest/Spectator spec file for `UploadPageComponent` at `src/app/features/ingestion/pages/upload-page/`. The spec verifies page lifecycle (WebSocket connect/disconnect, strategy load dispatch), child component rendering and template bindings (`[disabled]` on upload zone, `*ngFor` upload lists), rate-limit banner visibility, mode toggle, progress panel conditional display, and 3 integration scenarios that simulate state evolution via `overrideSelector` + `refreshState()`. All 4 child components are imported as real standalone dependencies. No production code changes are required.

## Technical Context

**Language/Version**: TypeScript 5.9 / Angular 21  
**Primary Dependencies**: `@ngneat/spectator ^22.1.0`, `@ngrx/store/testing ^21.0.1`, Vitest `^4.0.8`  
**Storage**: N/A — all specs use `provideMockStore`; no real persistence  
**Testing**: Vitest `^4.0.8` + `@ngneat/spectator ^22.1.0`  
**Target Platform**: Node.js (Vitest test environment, jsdom browser emulation)  
**Project Type**: Angular 21 frontend — page-level test suite  
**Performance Goals**: Single spec file completes in under 10 seconds  
**Constraints**: Zero real HTTP or WebSocket calls; zero real Angular router; Bootstrap JS (`window.bootstrap`) mocked where child modals cascade  
**Scale/Scope**: 1 spec file, 10 tests (7 unit + 3 integration)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against **Part B — Frontend Principles VI–X** of the NexRAG Test Constitution v1.1.0.

| Principle | Gate | Status |
|-----------|------|--------|
| VI — Component Test Isolation | `createComponentFactory` required; direct `TestBed` forbidden; `mockProvider` for services | **PASS** — FR-001 mandates Spectator factories. Child components imported as real standalones per spec clarification Q1 (explicit exception in Principle VI: "unless the test explicitly verifies child-component interaction"). |
| VII — SOLID in Tests | One spec per component; `mockProvider` / `SpyObject` for dependencies | **PASS** — 1 spec file covering exactly `UploadPageComponent`. No omnibus files. |
| VIII — Naming Conventions | `.component.spec.ts` suffix; co-located; `describe` in English; `it` in French imperative; `[INTÉGRATION]` prefix on integration tests | **PASS** — file is `upload-page.component.spec.ts` co-located with source; 3 integration tests carry `[INTÉGRATION]` prefix. |
| IX — Coverage Gates | ≥80% statements, ≥75% branches, ≥85% functions, ≥80% lines | **PASS** — SC-003/SC-004 targets meet or exceed thresholds. Rate-limit interception (safety-critical) is covered at Phase 2/3; this phase covers rate-limit UI guard only. |
| X — NgRx & Real-Time | `provideMockStore` for components; no real store; WebSocket stubbed | **PASS** — FR-001 is explicit. No effects tested here. No real SSE/WS connection. |

**No gate violations. No complexity justification required.**

## Project Structure

### Documentation (this feature)

```text
specs/018-ingestion-pages-tests/
├── plan.md              ← this file
├── research.md          ← Phase 0 output
├── data-model.md        ← Phase 1 output
├── quickstart.md        ← Phase 1 output
└── tasks.md             ← Phase 2 output (/speckit.tasks — not created here)
```

### Source Code (repository root: `agentic-rag-ui/`)

```text
src/app/features/ingestion/pages/
└── upload-page/
    ├── upload-page.component.ts           (exists — no changes required)
    ├── upload-page.component.html         (exists — no changes required)
    ├── upload-page.component.scss         (exists — no changes required)
    └── upload-page.component.spec.ts      ← NEW (10 tests: 7 unit + 3 integration)

src/app/features/ingestion/components/testing/
└── ingestion-test.helpers.ts              (exists — reuse mockUploadFile, mockProgressState,
                                            mockRateLimitState, mockIngestionState)
```

**Structure Decision**: Angular CLI co-location convention — `upload-page.component.spec.ts` lives in the same directory as `upload-page.component.ts`, enabling pattern-based filtering (`npm test -- --include="**/features/ingestion/pages/**"`).

## Complexity Tracking

> No violations. No entries required.
