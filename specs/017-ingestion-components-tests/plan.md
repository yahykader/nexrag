# Implementation Plan: PHASE 10 — Ingestion Components Test Suite

**Branch**: `017-ingestion-components-tests` | **Date**: 2026-05-04 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/017-ingestion-components-tests/spec.md`

## Summary

Write 8 co-located Vitest/Spectator spec files for every component in `src/app/features/ingestion/components/`. Each spec verifies UI rendering, store-driven state, event emissions, and `@ViewChild` modal wiring using mocked NgRx store state and real child modal components as standalone imports. One minor production code change is required: `DeleteAllButtonComponent` must receive a second disabled guard driven by `selectAllUploads` (empty-list condition confirmed in spec clarification).

## Technical Context

**Language/Version**: TypeScript 5.9 / Angular 21  
**Primary Dependencies**: `@ngneat/spectator ^22.1.0`, `@ngrx/store/testing ^21.0.1`, `@ngrx/effects/testing ^21.0.1`, Vitest `^4.0.8`  
**Storage**: N/A — all specs use `provideMockStore`; no real persistence  
**Testing**: Vitest `^4.0.8` + `@ngneat/spectator ^22.1.0`  
**Target Platform**: Node.js (Vitest test environment, jsdom browser emulation)  
**Project Type**: Angular 21 frontend — component test suite  
**Performance Goals**: Each of 8 spec files completes in under 5 seconds; full Phase 10 suite under 40 seconds  
**Constraints**: Zero real HTTP or WebSocket calls; zero real Angular router; Bootstrap JS (`window.bootstrap`) mocked in modal tests  
**Scale/Scope**: 8 spec files, ~35 unit tests (32 from test plan + 3 from clarifications Q1/Q2/Q3)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against **Part B — Frontend Principles VI–X** of the NexRAG Test Constitution v1.1.0.

| Principle | Gate | Status |
|-----------|------|--------|
| VI — Component Test Isolation | `createComponentFactory` required; direct `TestBed` forbidden; `mockProvider` for services | **PASS** — FR-010 mandates Spectator factories throughout. `@ViewChild` child modals imported as real standalone deps (explicit exception in Principle VI: "unless the test explicitly verifies child-component interaction"). |
| VII — SOLID in Tests | One spec per component; `mockProvider` / `SpyObject` for dependencies | **PASS** — 8 spec files, one per component. No omnibus files. |
| VIII — Naming Conventions | `.component.spec.ts` suffix; co-located; `describe` in English; `it` in French imperative | **PASS** — all 8 files follow `upload-zone.component.spec.ts` pattern co-located with source. |
| IX — Coverage Gates | ≥80% statements, ≥75% branches, ≥85% functions, ≥80% lines | **PASS** — SC-003/SC-004 targets meet or exceed thresholds. Safety-critical path: none in this phase (no XSS/rate-limit pipeline, covered Phase 2–3). |
| X — NgRx & Real-Time | `provideMockStore` for components; no real store; WebSocket stubbed | **PASS** — FR-001 is explicit. No effects tested here (Phase 9). No real SSE/WS. |

**No gate violations. No complexity justification required.**

> **Note on production code change**: `DeleteAllButtonComponent` currently reads only `selectCrudLoading`. Spec clarification (Q3) requires it to also read `selectAllUploads` and disable the button when the list is empty. This is a legitimate UX guard — not test-only logic — and is included as Task 1 in the implementation.

## Project Structure

### Documentation (this feature)

```text
specs/017-ingestion-components-tests/
├── plan.md              ← this file
├── research.md          ← Phase 0 output
├── data-model.md        ← Phase 1 output
├── quickstart.md        ← Phase 1 output
└── tasks.md             ← Phase 2 output (/speckit.tasks — not created here)
```

### Source Code (repository root: `agentic-rag-ui/`)

```text
src/app/features/ingestion/components/
├── upload-zone/
│   ├── upload-zone.component.ts                 (exists — no changes)
│   └── upload-zone.component.spec.ts            ← NEW  (6 tests)
├── upload-item/
│   ├── upload-item.component.ts                 (exists — no changes)
│   └── upload-item.component.spec.ts            ← NEW  (9 tests incl. clarifications)
├── progress-panel/
│   ├── progress-panel.component.ts              (exists — no changes)
│   └── progress-panel.component.spec.ts         ← NEW  (4 tests)
├── delete-all-button/
│   ├── delete-all-button.component.ts           ← MODIFY  (add selectAllUploads guard)
│   └── delete-all-button.component.spec.ts      ← NEW  (4 tests incl. clarification)
├── delete-all-modal/
│   ├── delete-all-modal.component.ts            (exists — no changes)
│   └── delete-all-modal.component.spec.ts       ← NEW  (4 tests)
├── delete-batch-modal/
│   ├── delete-batch-modal.component.ts          (exists — no changes)
│   └── delete-batch-modal.component.spec.ts     ← NEW  (3 tests)
├── rate-limit-indicator/
│   ├── rate-limit-indicator.component.ts        (exists — no changes)
│   └── rate-limit-indicator.component.spec.ts   ← NEW  (3 tests)
└── rate-limit-toast/
    ├── rate-limit-toast.component.ts            (exists — no changes)
    └── rate-limit-toast.component.spec.ts       ← NEW  (2 tests)
```

**Structure Decision**: Angular CLI co-location convention — each `.spec.ts` lives in the same directory as its source file, enabling pattern-based test filtering (`npm test -- --include="**/features/ingestion/components/**"`).

## Complexity Tracking

> Only one item — justified:

| Item | Why Needed | Simpler Alternative Rejected Because |
|------|-----------|--------------------------------------|
| Production change in `DeleteAllButtonComponent` | Spec Q3 clarification confirmed: button must be disabled when upload list is empty — this UX guard is absent from current code | Leaving it out makes SC-006 ("one test per status") invalid and leaves a real usability gap where users can click Delete All on an empty system |
