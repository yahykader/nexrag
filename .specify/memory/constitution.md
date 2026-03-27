<!--
SYNC IMPACT REPORT
==================
Version change: N/A (blank template) → 1.0.0 (initial ratification)

Modified principles: none — initial fill from template

Added sections:
  - Core Principles (5 principles: I–V)
  - Testing Standards & Tooling
  - Development Workflow & Quality Gates
  - Governance

Removed sections: none (template HTML comments stripped after fill)

Templates requiring updates:
  - ✅ .specify/memory/constitution.md — this file
  - ⚠  .specify/templates/plan-template.md — "Constitution Check" section uses
       generic gate text; should reference Principles I–V by name when producing
       a plan for this project.
  - ⚠  .specify/templates/tasks-template.md — phase structure (Setup / Foundational
       / Story phases) aligns well; verify that Phase labels map to the 9 test phases
       defined in Principle V when generating tasks for this project.
  - ⚠  .specify/templates/spec-template.md — no structural change required; French
       @DisplayName convention is documented here for reference.

Deferred TODOs: none — RATIFICATION_DATE set to today (first-run date).
-->

# NexRAG Backend — Test Constitution

## Core Principles

### I. Test Isolation & Independence (NON-NEGOTIABLE)

Every test MUST be fully independent, repeatable, and free of side effects.

- Unit tests MUST complete in under 500 ms each; no real network calls or file-system
  I/O are allowed inside a unit test boundary.
- Each test class MUST set up and tear down its own state via `@BeforeEach` / `@AfterEach`;
  no shared mutable fields between test methods.
- Tests MUST NOT rely on execution order; every test MUST pass when run in isolation.
- Mockito mocks MUST be reset automatically — use `@ExtendWith(MockitoExtension.class)` on
  every unit test class; never share a mock across test methods without explicit reset.
- Integration tests MAY depend on real infrastructure only when started via Testcontainers
  (ephemeral, reproducible containers); never point at a shared or production instance.

**Rationale**: Order-dependent or environment-coupled tests mask real regressions and make
CI unreliable. A test suite that cannot run in any order is a liability, not a safety net.

### II. SOLID Design Reflected in Tests

Tests MUST validate SOLID boundaries — they MUST NOT bypass or flatten them.

- **SRP**: Each `*Spec.java` class covers exactly one production class or one responsibility;
  no omnibus test files that touch multiple unrelated classes.
- **OCP**: Adding a new `IngestionStrategy` MUST produce a new `*Spec.java` file without
  modifying any existing spec — mirroring the `IngestionConfig` auto-discovery contract.
- **LSP**: Any mock that stands in for a strategy or service interface MUST honour the same
  behavioural contract as the real implementation (same exceptions, same return semantics).
- **ISP**: Constructor injection under test MUST accept only the interfaces the class declares;
  tests MUST NOT inject wider collaborators than the class actually needs.
- **DIP**: Production classes under test MUST receive all dependencies via constructor
  injection; tests inject mocks via `@InjectMocks` / `@Mock` only — no field-reflection hacks.

**Rationale**: Tests that mirror SOLID boundaries act as living documentation of each class's
responsibility and catch design regressions before they reach code review.

### III. Naming & Organisation Conventions

All test artefacts MUST follow a consistent, discoverable naming scheme.

- **Unit spec class**: `<TestedClassName>Spec.java`
  — e.g. `FileValidatorSpec.java`, `AntivirusGuardSpec.java`, `TextChunkerSpec.java`
- **Integration spec class**: `<FeatureName>IntegrationSpec.java`
  — e.g. `IngestionPipelineIntegrationSpec.java`
- **Test root**: `src/test/java/com/exemple/nexrag/service/rag/<module>/`
  mirrors the production package tree exactly; no flat test packages.
- `@DisplayName` MUST appear on every test class and every `@Test` method.
  Wording convention (French, imperative):
  `"DOIT [action] quand [condition]"` — e.g. `"DOIT lever FileSizeExceededException quand fichier > maxSize"`
- All display names, log messages inside tests, and assertion messages MUST be written in
  **French**, consistent with the production codebase convention defined in `CLAUDE.md`.

**Rationale**: Predictable, searchable names enable instant discovery and enforce a strict
one-to-one mapping between production classes and their specifications.

### IV. Coverage & Quality Gates

Coverage is a minimum floor enforced in CI — it is not a vanity metric.

- Line + branch coverage: **≥ 80 %** per module; CI MUST fail a build below this threshold.
- Every Acceptance Criterion (`AC-x.y`) listed in `nexrag-test-plan-speckit.md` MUST map
  to at least one `@Test` method; untested ACs are non-conforming.
- Safety-critical paths — `AntivirusGuard`, file/text deduplication, `RollbackExecutor` —
  MUST achieve **100 % branch coverage**; partial coverage here is a blocker, not a warning.
- "Happy path only" coverage is insufficient: every public method MUST have at least one
  failure-path or edge-case test alongside its success-path test.
- A green build that falls below any coverage threshold MUST block merge to `master`.

**Rationale**: The 80 % floor prevents hidden branches in the ingestion and retrieval pipelines
from silently causing data loss, virus bypass, or duplicate indexing in production.

### V. Integration & Contract Testing

Integration specs MUST use isolated, reproducible infrastructure — never live endpoints.

- All integration specs MUST be annotated with `@SpringBootTest` and use Testcontainers for
  every infrastructure dependency (PostgreSQL/pgvector, Redis, ClamAV).
- External HTTP services (OpenAI API, ClamAV HTTP daemon) MUST be stubbed with WireMock
  inside integration tests; no real API keys or live hosts may be used.
- Redis integration tests MUST spin up a `redis:7` Testcontainers instance; an in-memory
  fake (e.g. `EmbeddedRedis`) is NOT an acceptable substitute.
- pgvector tests MUST use a `pgvector/pgvector:pg16` Testcontainers image.
- The end-to-end pipeline (Phase 9 of the test plan) MUST be covered by at least one spec
  exercising the full flow: upload → antivirus scan → deduplication → strategy selection
  → embedding → vector store → retrieval.
- The 9-phase test plan from `nexrag-test-plan-speckit.md` defines the implementation order
  and MUST be followed; phases MUST NOT be skipped or reordered without an amendment to this
  constitution.

**Rationale**: Infrastructure mocks diverge silently from real behaviour. Testcontainers
eliminates that divergence at the cost of slightly slower integration suites — an acceptable
trade-off for a safety-critical ingestion pipeline.

## Testing Standards & Tooling

**Framework stack** — fixed for this project; substitutions require a MAJOR version amendment:

| Tool | Version / Scope | Role |
|------|----------------|------|
| JUnit 5 (Jupiter) | Spring Boot BOM | Test runner and lifecycle |
| Mockito | Spring Boot BOM | Unit-level mocking |
| AssertJ | Spring Boot BOM | Fluent, readable assertions |
| Testcontainers | Spring Boot BOM | Real infra in integration tests |
| WireMock (`wiremock-jre8`) | Explicit `<scope>test</scope>` | External HTTP stubbing |
| Spring Boot Test | Spring Boot BOM | `@SpringBootTest` and MockMvc slices |
| MockMvc | Spring Boot BOM | Controller-layer unit tests (Phase 7) |

**9-phase implementation map** (source: `nexrag-test-plan-speckit.md`):

| Phase | Module Scope | Type | Priority |
|-------|-------------|------|----------|
| 1 | `ingestion` — util, security, deduplication, cache | Unit | Critical |
| 2 | `ingestion` — strategy, analyzer, compression, tracker | Unit | Critical |
| 3 | `retrieval` — query, reranker, aggregator, injector | Unit | Critical |
| 4 | `streaming` — SSE, WebSocket STOMP | Unit | High |
| 5 | `voice` + `metrics` | Unit | High |
| 6 | `facade` — IngestionFacade, CrudFacade | Unit | High |
| 7 | `controller` — MockMvc | Unit | Medium |
| 8 | `interceptor` + `validation` | Unit | Medium |
| 9 | End-to-end ingestion + retrieval pipeline | Integration | Critical |

**Performance budget**: unit tests (Phases 1–8) MUST average under 500 ms per test method;
the full integration suite (Phase 9) MUST complete within 3 minutes on CI.

## Development Workflow & Quality Gates

**Test-First flow** — mandatory for all new `*Spec.java` classes:

1. Write the complete `*Spec.java` with all `@Test` methods and `@DisplayName` labels.
2. Run the suite and confirm every new test **fails** (red phase).
3. Implement or adjust production code until every new test **passes** (green phase).
4. Refactor production code while all tests remain green.
5. Verify the coverage gate (Principle IV) before committing.

**Commit discipline**:
- Each test phase (1–9) MUST be committed as an independent unit; phases MUST NOT be bundled.
- Commit message format: `test(phase-N): add <ClassName>Spec — <brief description>`
  — e.g. `test(phase-1): add AntivirusGuardSpec — ClamAV virus detection scenarios`
- A test-only commit MUST NOT modify production code unless strictly necessary for
  testability (e.g. extracting an interface to enable constructor injection).

**Architecture invariants enforced by tests**:
- `IngestionOrchestrator` MUST NOT be modified when a new `IngestionStrategy` is registered;
  its spec MUST pass unchanged after the addition (OCP compliance gate).
- All Redis key strings MUST originate from the `constant/` package; any test that uses a raw
  string Redis key is non-conforming and MUST be refactored before merge.
- MockMvc controller tests MUST mock the facade layer only; they MUST NOT reach service or
  repository classes directly (facade-delegation invariant from `CLAUDE.md`).

## Governance

This constitution supersedes all prior informal testing conventions for the NexRAG backend.
Every developer MUST review it before beginning a new test phase.

**Amendment procedure**:
1. Open a PR that modifies `.specify/memory/constitution.md` exclusively.
2. Increment the version number according to the versioning policy below.
3. Update the Sync Impact Report HTML comment at the top of this file.
4. Check that dependent templates (plan, spec, tasks) still align; update them if needed.
5. Obtain at least one approving review before merging.

**Versioning policy**:
- **MAJOR** (x.0.0): Removal of a principle, change to coverage thresholds, incompatible
  rename of naming conventions, or removal of a mandatory tooling item.
- **MINOR** (1.x.0): New principle added, new phase added to the 9-phase map, new mandatory
  tooling item, or material expansion of any section.
- **PATCH** (1.0.x): Wording clarifications, typo corrections, example updates, non-semantic
  refinements that do not alter the rules.

**Compliance review**: The lead developer MUST verify coverage gate compliance (Principle IV)
before each phase begins. Non-compliant modules MUST be listed explicitly in the PR description
and resolved before the phase is considered complete.

**Version**: 1.0.0 | **Ratified**: 2026-03-26 | **Last Amended**: 2026-03-26
