# Implementation Plan: Phase 8 — Chat Pages & Resolvers Test Coverage

**Branch**: `015-chat-pages-resolvers` | **Date**: 2026-05-04 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/015-chat-pages-resolvers/spec.md`

## Summary

Build 2 Vitest + Spectator spec files for `ChatPageComponent` (page-level container with conversation sidebar) and `ChatResolver` (route guard). Before tests can be written, `onDeleteConversation` in `ChatPageComponent` must be refactored to inject a new `ConfirmationService` instead of calling `window.confirm()` directly — required for JSDOM compatibility in Vitest. Research (Phase 0) confirms: the EntityAdapter initialState shape for `provideMockStore`, the Spectator `overrideComponent` pattern for the `ChatInterfaceComponent` stub, and the subscription-callback approach for testing `Observable<void>` completion. Total deliverable: 1 new service, 1 modified component, 2 new spec files, 14 unit tests + 2 integration tests.

## Technical Context

**Language/Version**: TypeScript 5.9 / Angular 21 (standalone components throughout)  
**Primary Dependencies**: `@ngneat/spectator ^22.1.0`, `@ngrx/store/testing ^21.0.1`, Vitest `^4.0.8`, `@ngrx/store ^21.0.1`, `@ngrx/entity ^21.0.1`  
**Storage**: N/A — no persistence in unit tests  
**Testing**: Vitest (`npm test`), `createComponentFactory` / `createServiceFactory` from `@ngneat/spectator`, `provideMockStore` from `@ngrx/store/testing`  
**Target Platform**: Web browser — Angular SPA (desktop)  
**Project Type**: Web application — Angular frontend page + resolver test suite (Phase 8 of 14)  
**Performance Goals**: Full Phase 8 suite ≤ 10 s in CI; individual test methods < 500 ms (Constitution Principle IX)  
**Constraints**: No real HTTP, no real NgRx Store, no real router in unit tests; `ChatInterfaceComponent` replaced by explicit stub; `ConfirmationService` mocked via `mockProvider`; new `ConfirmationService` production class required before tests  
**Scale/Scope**: 2 spec files, 1 new service, 1 modified component; 14 unit tests + 2 integration tests = 16 tests total

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Part B — Frontend Principles (Applicable to Phase 8)

| Principle | Requirement | Status |
|-----------|-------------|--------|
| VI — Test Isolation | `createComponentFactory`; `mockProvider(ConfirmationService)` + `provideMockStore`; no real DI | **PASS** — planned |
| VI — Shallow rendering | Stub `ChatInterfaceComponent` via explicit `@Component` stub (Clarification Q2) | **PASS** — confirmed |
| VI — Co-location | Spec files placed beside source `.ts` files in the same directory | **PASS** — planned |
| VII — SRP | One `*.spec.ts` per class: `chat-page.component.spec.ts`, `chat.resolver.spec.ts` | **PASS** — 2 separate spec files |
| VII — DIP | All deps via Angular DI; no `new ConfirmationService()` in spec files | **PASS** — planned |
| VIII — Naming | `describe` in English; `it()` labels in French imperative; `[INTÉGRATION]` prefix | **PASS** — planned |
| IX — Coverage | ≥ 80 % statements, ≥ 75 % branches, ≥ 85 % functions (SC-003) | **PASS** — planned |
| IX — No stub-only tests | All `it()` bodies fully implemented | **PASS** — planned |
| X — NgRx | `provideMockStore` only; no `StoreModule.forRoot()` | **PASS** — planned |

**Note on production change**: Extracting `window.confirm` into `ConfirmationService` is a minimal code change strictly required for testability — explicitly permitted by the constitution ("unless strictly required for testability").

**No gate violations. Complexity Tracking not required.**

## Project Structure

### Documentation (this feature)

```text
specs/015-chat-pages-resolvers/
├── plan.md              ← this file
├── research.md          ← Phase 0 output
├── data-model.md        ← Phase 1 output
├── quickstart.md        ← Phase 1 output
├── contracts/
│   ├── chat-page.contract.md
│   └── chat-resolver.contract.md
└── tasks.md             ← Phase 2 output (via /speckit.tasks)
```

### Source Code

```text
agentic-rag-ui/src/app/
├── core/services/
│   └── confirmation.service.ts             ← TO CREATE (new injectable; wraps window.confirm)
│
└── features/chat/
    ├── pages/chat-page/
    │   ├── chat-page.component.ts          ← MODIFY (inject ConfirmationService)
    │   ├── chat-page.component.html        ← no change
    │   ├── chat-page.component.scss        ← no change
    │   └── chat-page.component.spec.ts     ← TO CREATE (11 unit + 2 integration)
    └── resolvers/
        ├── chat.resolver.ts                ← no change
        └── chat.resolver.spec.ts           ← TO CREATE (3 unit)
```

**Structure Decision**: Single Angular project (`agentic-rag-ui/`) — Option 2 (web application). Spec files are co-located beside their source files per Constitution Principle VI and Angular CLI convention.
