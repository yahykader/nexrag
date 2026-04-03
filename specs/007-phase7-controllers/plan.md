# Implementation Plan: Phase 7 — Controllers Unit Tests (MockMvc)

**Branch**: `007-phase7-controllers` | **Date**: 2026-04-02 | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `specs/007-phase7-controllers/spec.md`

## Summary

Implement six `@WebMvcTest` unit test classes covering all REST controllers in the NexRAG
backend: `MultimodalIngestionController`, `StreamingAssistantController`,
`MultimodalCrudController`, `VoiceController`, `MetricsController`, and
`WebSocketStatsController`. Each test class mocks the controller's immediate dependency
(facade or manager), imports the relevant `@ControllerAdvice` handler where one exists, and
disables Spring Security filters. One minimal production code change is required:
`@NotBlank` must be added to `StreamingRequest.query` to enable bean-validation-driven HTTP
400 for the empty-query edge case. The suite must reach ≥ 80 % line + branch coverage on all
six controller classes within 500 ms per test method.

## Technical Context

**Language/Version**: Java 21  
**Primary Dependencies**: JUnit 5 (Jupiter), Mockito, AssertJ, Spring Boot Test (MockMvc, `@WebMvcTest`), Jakarta Bean Validation (`@NotBlank`)  
**Storage**: N/A — all dependencies mocked; no real infrastructure in unit tests  
**Testing**: `@WebMvcTest` + `@MockBean` for facades/managers; `@TestConfiguration` for `PrometheusMeterRegistry`; `@AutoConfigureMockMvc(addFilters=false)` to disable filters  
**Target Platform**: Maven Surefire (Spring Boot 3.4.2 BOM)  
**Project Type**: Unit test suite — controller (HTTP routing) layer  
**Performance Goals**: Each test method < 500 ms (Constitution Principle I)  
**Constraints**: No real network calls; Spring Security not on classpath (confirmed); `@ControllerAdvice` beans imported only where a dedicated handler exists (Ingestion, Crud, Voice)  
**Scale/Scope**: 6 `*Spec.java` classes, ~38 `@Test` methods total; 1 production code change (`@NotBlank` on `StreamingRequest.query`)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Gate | Status |
|-----------|------|--------|
| I — Isolation | Every test < 500 ms; `@WebMvcTest` slice starts only the web layer; no shared mutable state between test methods; `@BeforeEach` resets mocks via `@MockBean` lifecycle | ✅ Pass |
| II — SOLID | One `*Spec.java` per production controller; `@MockBean` for the single facade/manager dependency; `@ControllerAdvice` beans imported only when a real advice class exists | ✅ Pass |
| III — Naming | `<TestedClassName>Spec.java`; `@DisplayName` on class + every `@Test`; French `"DOIT … quand …"` wording (Constitution §III) | ✅ Pass |
| IV — Coverage | ≥ 80 % line + branch on all 6 controllers; every FR (FR-001–FR-017) + edge case maps to at least one `@Test`; happy-path AND failure-path covered per public method | ✅ Pass — enforced by design |
| V — Integration | Phase 7 is pure unit (MockMvc slice); no Testcontainers, no WireMock; end-to-end controller-to-service flow deferred to Phase 9 | ✅ Pass — N/A |

No violations to justify. Complexity Tracking table omitted.

## Project Structure

### Documentation (this feature)

```text
specs/007-phase7-controllers/
├── plan.md              ← this file
├── research.md          ← Phase 0 output
├── data-model.md        ← Phase 1 output
├── quickstart.md        ← Phase 1 output
└── tasks.md             ← Phase 2 output (/speckit.tasks — not yet created)
```

### Source Code (repository root)

```text
nex-rag/src/
├── main/java/com/exemple/nexrag/
│   ├── service/rag/controller/              ← 5 production controllers under test
│   │   ├── MultimodalIngestionController.java
│   │   ├── StreamingAssistantController.java
│   │   ├── MetricsController.java
│   │   ├── VoiceController.java
│   │   └── WebSocketStatsController.java
│   ├── controller/                          ← 1 production controller (different package)
│   │   └── MultimodalCrudController.java
│   ├── advice/                              ← @ControllerAdvice handlers (imported in 3 test slices)
│   │   ├── IngestionExceptionHandler.java
│   │   ├── CrudExceptionHandler.java
│   │   └── VoiceExceptionHandler.java
│   └── service/rag/streaming/model/
│       └── StreamingRequest.java            ← PRODUCTION CHANGE: add @NotBlank to query field
│
└── test/java/com/exemple/nexrag/
    ├── service/rag/controller/              ← 5 test classes (mirrors production package)
    │   ├── MultimodalIngestionControllerSpec.java   ← Task T1
    │   ├── StreamingAssistantControllerSpec.java    ← Task T2
    │   ├── MetricsControllerSpec.java               ← Task T4
    │   ├── VoiceControllerSpec.java                 ← Task T5
    │   └── WebSocketStatsControllerSpec.java        ← Task T6
    └── controller/                          ← 1 test class (mirrors production package)
        └── MultimodalCrudControllerSpec.java        ← Task T3
```

**Structure Decision**: Single Spring Boot project (`nex-rag/`). Test sources mirror the
production package tree exactly (Constitution Principle III). `MultimodalCrudController`
lives in `com.exemple.nexrag.controller` (not `service.rag.controller`) per its source file;
its test class is placed in the corresponding test package.

## Implementation Phases

### Phase 0 — Research (complete → see research.md)

Resolved decisions:
- `@WebMvcTest` slice setup and filter disabling strategy (Spring Security absent from classpath)
- `@ControllerAdvice` import pattern for `@WebMvcTest` slices
- `MetricsController` test setup: `@WebMvcTest` + `@TestConfiguration` providing `PrometheusMeterRegistry`
- `StreamingRequest.query` bean validation gap and required `@NotBlank` production change
- `MultimodalCrudController` package discrepancy and test placement

### Phase 1 — Design & Contracts (complete → see data-model.md, quickstart.md)

Deliverables:
- `data-model.md` — mock contracts for all 6 dependencies, exception-mapping table, `@TestConfiguration` blueprint
- `quickstart.md` — how to run and verify Phase 7 tests
- No `contracts/` directory — controllers expose HTTP endpoints already documented via Swagger (`/swagger-ui.html`); no new external interface contract needed for this test phase

### Phase 2 — Tasks

Generated by `/speckit.tasks`. Each task = one `*Spec.java` class (+ one shared production change).

| Task | Spec Class | ACs Covered | Priority | Note |
|------|-----------|-------------|----------|------|
| T0 | `StreamingRequest.java` (production change) | FR-006, edge-case empty-query | P0 | Add `@NotBlank` to `query` field before T2 |
| T1 | `MultimodalIngestionControllerSpec` | US-1 scenarios 1–13, FR-001–005, FR-017, edge cases | P1 | Import `IngestionExceptionHandler` |
| T2 | `StreamingAssistantControllerSpec` | US-2 scenarios 1–5, FR-006–007 | P2 | Depends on T0 (bean validation) |
| T3 | `MultimodalCrudControllerSpec` | US-3 scenarios 1–7, FR-008–010, FR-017 | P3 | Import `CrudExceptionHandler`; different package |
| T4 | `MetricsControllerSpec` | US-5 scenarios 1–3, FR-012–013 | P4 | `@TestConfiguration` for registry |
| T5 | `VoiceControllerSpec` | US-4 scenarios 1–4, FR-011, FR-017 | P3 | Import `VoiceExceptionHandler` |
| T6 | `WebSocketStatsControllerSpec` | US-6 scenarios 1–7, FR-014–015 | P4 | No advice bean; mock `WebSocketSessionManager` |
