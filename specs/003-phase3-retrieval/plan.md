# Implementation Plan: Phase 3 — Retrieval Pipeline

**Branch**: `003-phase3-retrieval` | **Date**: 2026-03-28 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/003-phase3-retrieval/spec.md`

## Summary

Write the full JUnit 5 / Mockito unit test suite for the NexRAG retrieval module (Phase 3 of the 9-phase test plan). All 10 production classes under `service/rag/retrieval/` already exist; this plan covers test design, mock strategies, and acceptance-criterion mapping — no production code changes are anticipated.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 3.4.2, JUnit 5 (Jupiter), Mockito, AssertJ, LangChain4j 1.0.0-beta1
**Storage**: pgvector (EmbeddingStore — mocked in unit tests), Redis (not used in retrieval phase)
**Testing**: JUnit 5 Jupiter · Mockito · AssertJ · Spring Boot Test (no Testcontainers for Phase 3)
**Target Platform**: JVM / Spring Boot backend service
**Project Type**: Unit test suite (backend service)
**Performance Goals**: Every unit test < 500 ms; all Phase 3 tests combined < 30 s
**Constraints**: ≥ 80% line + branch coverage per module; zero real network calls; MockitoExtension on every spec class
**Scale/Scope**: 10 `*Spec.java` classes, ~50–70 `@Test` methods covering all Phase 3 ACs

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I — Test Isolation & Independence | ✅ PASS | All classes use `@ExtendWith(MockitoExtension.class)`; `@BeforeEach` resets state; no shared mutable fields; no real I/O |
| II — SOLID Design in Tests | ✅ PASS | One `*Spec.java` per production class; `@InjectMocks` + `@Mock` constructor injection pattern; LSP-compliant mocks |
| III — Naming & Organisation | ✅ PASS | `<Class>Spec.java` naming; `src/test/java/com/exemple/nexrag/service/rag/retrieval/...` mirrors production; French `@DisplayName` |
| IV — Coverage & Quality Gates | ✅ PASS | ≥ 80% floor required; every AC-8.x / AC-9.x / AC-10.x maps to at least one `@Test`; both success + failure paths required |
| V — Integration & Contract Testing | ✅ PASS | Phase 3 is unit-only; Testcontainers deferred to Phase 9; no live endpoints or API keys |

No violations detected. No Complexity Tracking required.

## Project Structure

### Documentation (this feature)

```text
specs/003-phase3-retrieval/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
nex-rag/src/test/java/com/exemple/nexrag/service/rag/
└── retrieval/
    ├── query/
    │   ├── QueryTransformerServiceSpec.java   ← US-8 / AC-8.1
    │   └── QueryRouterServiceSpec.java        ← US-8 / AC-8.2 AC-8.3
    ├── retriever/
    │   ├── TextVectorRetrieverSpec.java       ← US-9 / AC-9.1 AC-9.3
    │   ├── BM25RetrieverSpec.java             ← US-9 / AC-9.1 AC-9.3
    │   ├── ImageVectorRetrieverSpec.java      ← US-9 / AC-9.1 AC-9.3
    │   └── ParallelRetrieverServiceSpec.java  ← US-9 / AC-9.1 AC-9.2 AC-9.3
    ├── reranker/
    │   └── CrossEncoderRerankerSpec.java      ← US-10 / AC-10.1
    ├── aggregator/
    │   └── ContentAggregatorServiceSpec.java  ← US-10 / AC-10.1 AC-10.2
    ├── injector/
    │   └── ContentInjectorServiceSpec.java    ← US-10 / AC-10.3
    └── RetrievalAugmentorOrchestratorSpec.java ← Full pipeline smoke tests
```

**Structure Decision**: Single-module Java backend. Test tree mirrors production package tree exactly under `src/test/java/com/exemple/nexrag/service/rag/retrieval/` (Principle III). No frontend or infrastructure directories involved.
