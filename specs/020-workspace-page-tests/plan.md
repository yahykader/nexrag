# Implementation Plan: Workspace Page Integration Tests

**Branch**: `020-workspace-page-tests` | **Date**: 2026-05-07 | **Spec**: [spec.md](spec.md)  
**Input**: Feature specification from `/specs/020-workspace-page-tests/spec.md`

## Summary

Add `workspace.component.spec.ts` (6 unit + 3 integration tests) for the `WorkspaceComponent` shell page. Because the component has no logic, the work splits into two parts: (1) a one-line production template fix — adding `<app-toast-container>` to the workspace HTML and importing `ToastContainerComponent` — and (2) the spec itself, which verifies layout structure via CSS-class queries, explicit child-selector presence, and a full-store integration scenario using the project's established `mockFullIngestionState` + `buildChatState` helpers.

## Technical Context

**Language/Version**: TypeScript 5.9 / Angular 21  
**Primary Dependencies**: `@ngneat/spectator/vitest ^22.1.0`, `@ngrx/store/testing ^21.0.1`, Vitest `^4.0.8`  
**Storage**: N/A — no persistence; `provideMockStore` only in integration tests  
**Testing**: Vitest + `@ngneat/spectator/vitest` (`createComponentFactory`, `overrideComponents`)  
**Target Platform**: Browser — Angular SPA at `localhost:4200`  
**Project Type**: Angular frontend — test spec for a shell page  
**Performance Goals**: Spec completes in under 5 seconds in CI (isolated component, no HTTP/WebSocket)  
**Constraints**: Explicit `@Component` stubs required (`NO_ERRORS_SCHEMA` prohibited); all five store slices seeded in integration mock store  
**Scale/Scope**: 1 spec file, 9 tests (6 unit + 3 integration), 1 production file modified

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| VI — Angular Test Isolation | ✅ PASS | `createComponentFactory` used; `overrideComponents` for stubs; no real Angular app bootstrapped in unit tests |
| VII — SOLID in Tests | ✅ PASS | One spec file, one component; no existing specs modified |
| VIII — Naming Conventions | ✅ PASS | File: `workspace.component.spec.ts` co-located; `describe` in English; `it()` in French; `[INTÉGRATION]` prefix |
| IX — Coverage Gates | ✅ PASS | 100% expected (`workspace.component.ts` has no logic); no stub-only tests |
| X — NgRx & Real-Time Contract | ✅ PASS | `provideMockStore` in integration; no `StoreModule.forRoot`; no real WebSocket |

No violations. Complexity Tracking section omitted.

## Project Structure

### Documentation (this feature)

```text
specs/020-workspace-page-tests/
├── plan.md              ← this file
├── research.md          ← Phase 0 output
├── data-model.md        ← Phase 1 output
├── quickstart.md        ← Phase 1 output
└── tasks.md             ← Phase 2 output (/speckit.tasks — not created here)
```

### Source Code (files touched)

```text
src/app/pages/workspace/
├── workspace.component.ts        ← MODIFY: add ToastContainerComponent import
├── workspace.component.html      ← MODIFY: add <app-toast-container> element
└── workspace.component.spec.ts   ← CREATE: 6 unit + 3 integration tests
```

**Structure Decision**: Single-file addition co-located with the source component (Angular CLI convention). No new directories needed. The production changes are minimal (2 lines across 2 files) and bundled with the tests as decided in clarification Q1.
