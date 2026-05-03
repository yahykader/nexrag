# Implementation Plan: Phase 7 — Chat Components Test Suite

**Branch**: `014-chat-components-tests` | **Date**: 2026-05-02 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/014-chat-components-tests/spec.md`

## Summary

Build 4 Vitest + Spectator spec files for the chat feature's existing Angular 21 standalone components: `ChatInterfaceComponent`, `MessageInputComponent`, `MessageItemComponent`, and `VoiceButtonComponent`. Research (Phase 0) resolves six spec-vs-implementation discrepancies uncovered during codebase exploration — including timestamp format, CSS class names, keyboard shortcut, and the actual voice API used — so that test assertions match the real component behaviour rather than the spec's assumptions.

## Technical Context

**Language/Version**: TypeScript 5.9 / Angular 21 (standalone components throughout)
**Primary Dependencies**: `@ngneat/spectator ^22.1.0`, `@ngrx/store/testing ^21.0.1`, Vitest, `ngx-markdown` (MarkdownModule), `@angular/forms` (FormsModule)
**Storage**: N/A — no persistence in unit tests
**Testing**: Vitest (`npm test`), `@ngneat/spectator` factories, `provideMockStore` from `@ngrx/store/testing`
**Target Platform**: Web browser — Angular SPA (desktop; mobile and touch events out of scope)
**Project Type**: Web application — Angular frontend component test suite (Phase 7 of 14)
**Performance Goals**: Full Phase 7 suite ≤ 30 s in CI; individual test methods < 500 ms (Constitution Principle IX)
**Constraints**: No real HTTP calls, no real NgRx Store, no real MediaRecorder, no real ngx-markdown rendering in unit tests — all replaced by mocks/SpyObject or stub declarations
**Scale/Scope**: 4 spec files co-located in `src/app/features/chat/components/`, ≥ 22 unit tests, ≥ 4 integration tests

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Part B — Frontend Principles (Applicable to Phase 7)

| Principle | Requirement | Status |
|-----------|-------------|--------|
| VI — Test Isolation | `createComponentFactory`; `mockProvider`/`SpyObject`; no real DI | **PASS** — planned |
| VI — Shallow rendering | Default `shallow: true`; children stubbed in ChatInterface tests | **PASS** — confirmed (Q2 clarification) |
| VI — Co-location | Spec files co-located beside source `.ts` files | **PASS** — planned |
| VII — SRP | One `*.spec.ts` per component; no omnibus files | **PASS** — 4 separate spec files |
| VII — DIP | No `new VoiceService()` inside specs; DI via Angular DI only | **PASS** — planned |
| VIII — Naming | `describe` in English, `it()` in French imperative, `[INTÉGRATION]` prefix | **PASS** — planned |
| IX — Coverage | ≥ 80 % statements, ≥ 75 % branches, ≥ 85 % functions | **PASS** — SC-003/SC-004 aligned |
| IX — XSS gate | Explicit sanitization test in MessageItemComponent spec | **PASS** — FR-017 added post-clarification |
| IX — No stub-only tests | All `it()` bodies fully implemented | **PASS** — planned |
| X — NgRx | `provideMockStore` only; no `StoreModule.forRoot()` | **PASS** — planned |

**No violations detected. Complexity Tracking not required.**

## Project Structure

### Documentation (this feature)

```text
specs/014-chat-components-tests/
├── plan.md              ← this file
├── research.md          ← Phase 0 output
├── data-model.md        ← Phase 1 output
├── quickstart.md        ← Phase 1 output
├── contracts/
│   ├── chat-interface.contract.md
│   ├── message-input.contract.md
│   ├── message-item.contract.md
│   └── voice-button.contract.md
└── tasks.md             ← Phase 2 output (via /speckit.tasks)
```

### Source Code

```text
agentic-rag-ui/src/app/features/chat/components/
├── chat-interface/
│   ├── chat-interface.component.ts
│   ├── chat-interface.component.html
│   ├── chat-interface.component.scss
│   └── chat-interface.component.spec.ts      ← TO CREATE
├── message-item/
│   ├── message-item.component.ts
│   ├── message-item.component.html
│   ├── message-item.component.scss
│   └── message-item.component.spec.ts        ← TO CREATE
├── message-input/
│   ├── message-input.component.ts
│   ├── message-input.component.html
│   ├── message-input.component.scss
│   └── message-input.component.spec.ts       ← TO CREATE
└── voice-control/
    ├── voice-button.component.ts
    ├── voice-button.component.html
    ├── voice-button.component.scss
    └── voice-button.component.spec.ts        ← TO CREATE
```

**Structure Decision**: Co-located spec files following Angular CLI convention and Constitution Principle VI. All 4 files are siblings of their respective component source files.
