<!--
SYNC IMPACT REPORT
==================
Version change: 1.0.0 → 1.1.0

Modified principles: none (backend principles I–V unchanged)

Constitution retitled:
  "NexRAG Backend — Test Constitution" → "NexRAG — Test Constitution"
  Rationale: constitution now governs both backend and frontend codebases.

Added sections:
  - Principles VI–X  (Angular 21 / TypeScript frontend test governance)
  - Frontend Testing Standards & Tooling (Spectator + Vitest + NgRx)
  - Frontend Development Workflow & Quality Gates (14-phase plan)

Removed sections: none

Templates requiring updates:
  ✅ .specify/memory/constitution.md — this file (updated)
  ⚠  .specify/templates/plan-template.md — Constitution Check section
     should reference backend Principles I–V and frontend Principles VI–X
     when producing plans for frontend features. Pending manual update.
  ⚠  .specify/templates/tasks-template.md — Task phase labels should map
     to the 14 frontend phases (agentic-ui-test-plan-speckit.md) when
     generating tasks for Angular features. Pending manual update.
  ⚠  .specify/templates/spec-template.md — FR/AC items for frontend
     features should reference the 14-phase plan. No structural change
     required; co-location convention documented here for reference.

Deferred TODOs: none — RATIFICATION_DATE retained from v1.0.0;
  LAST_AMENDED_DATE updated to today (2026-04-28).
-->

# NexRAG — Test Constitution

---

## Part A — Backend (Spring Boot / Java 21)

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

## Backend Testing Standards & Tooling

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

## Backend Development Workflow & Quality Gates

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

---

## Part B — Frontend (Angular 21 / TypeScript 5.9)

### VI. Angular Component Test Isolation (NON-NEGOTIABLE)

Every Angular test MUST use `@ngneat/spectator` factory functions — direct `TestBed`
configuration is FORBIDDEN outside spectator wrappers.

- **Components**: MUST use `createComponentFactory`; default to shallow rendering
  (`shallow: true`) unless the test explicitly verifies child-component interaction.
- **Services**: MUST use `createServiceFactory` for DI-backed services and
  `createHttpFactory` for services that consume `HttpClient`.
- **Pipes**: MUST use `createPipeFactory`; pipe logic MUST be testable without DOM.
- All service dependencies in component tests MUST be replaced with `mockProvider()` or
  `SpyObject<T>` — NEVER inject the real implementation unless tagged `[INTÉGRATION]`.
- Unit tests (Phases 1–12) MUST NOT start a real Angular application; integration tests
  (Phases 13–14) MAY use `RouterTestingModule` with a full route tree.
- Each spec file MUST be co-located with its source file (Angular CLI convention):
  `src/app/features/chat/store/chat.reducer.spec.ts` lives beside `chat.reducer.ts`.

**Rationale**: Spectator factories eliminate TestBed boilerplate and enforce consistent
dependency isolation. Co-location ensures specs are found and maintained alongside the code
they verify.

### VII. SOLID Principles Reflected in Angular/TypeScript Tests

Tests MUST mirror Angular SOLID boundaries — they MUST NOT bypass or flatten them.

- **SRP**: Each `*.spec.ts` file covers exactly one class or one clearly bounded
  responsibility (one reducer, one component, one service). No omnibus spec files.
- **OCP**: Adding a new component, pipe, or NgRx action MUST produce a new `*.spec.ts`
  without modifying any existing spec — the `MockProvider` pattern enables this cleanly.
- **LSP**: `SpyObject<T>` mocks for services MUST return `Observable`/`Signal` types
  matching the real contract; NEVER return a raw value where a stream is expected.
- **ISP**: Component test setups MUST mock only the direct `@Input()` bindings and injected
  services the component declares — no extra providers that the component does not use.
- **DIP**: All tested classes MUST receive dependencies via Angular DI; tests MUST NOT
  call `new SomeService()` directly inside spec files.

**Rationale**: Tests that reflect Angular component boundaries catch regressions in data
flow (Inputs/Outputs, store selectors) before they silently break the user interface.

### VIII. Naming & File Organisation Conventions (Frontend)

All TypeScript test artefacts MUST follow Angular CLI and project conventions.

- **Spec file name**: mirrors the source file name with `.spec.ts` suffix:
  `chat.reducer.ts` → `chat.reducer.spec.ts`,
  `upload-zone.component.ts` → `upload-zone.component.spec.ts`
- **Spec location**: MUST be co-located with the source file in the same directory.
- **`describe` label**: class name or feature name in English
  — e.g. `describe('ChatReducer', ...)`, `describe('UploadZoneComponent', ...)`
- **`it` / `test` label**: French imperative:
  `"doit [action] quand [condition]"`
  — e.g. `"doit ajouter le message utilisateur à la liste"`,
         `"doit rejeter les fichiers avec extension non autorisée"`
- **Integration tests**: prefixed with `[INTÉGRATION]` inside the `it` description:
  `"[INTÉGRATION] le flux complet : upload → progress → done"`
- `async`/`fakeAsync` usage: MUST be labelled in the description when timing is relevant:
  `"doit mettre à jour le compteur chaque seconde"` (with `fakeAsync(() => {...}))`)

**Rationale**: Consistent naming enables instant navigation, pattern-based filtering
(`ng test --include="**/features/chat/**"`), and enforces one-to-one traceability between
source classes and their specifications.

### IX. Coverage & Quality Gates (Frontend)

Coverage thresholds from `agentic-ui-test-plan-speckit.md` are the minimum floor; falling
below ANY threshold MUST block merge to `master`.

| Metric | Minimum Target |
|--------|---------------|
| Statements | ≥ 80 % |
| Branches | ≥ 75 % |
| Functions | ≥ 85 % |
| Lines | ≥ 80 % |

- Every `it()` / `test()` listed in `agentic-ui-test-plan-speckit.md` MUST be implemented;
  stub-only specs (`it('...', () => { ... })` with empty bodies) are non-conforming.
- "Happy path only" is insufficient: every public component output, store action, and HTTP
  method MUST have at least one error/edge-case test alongside its success-path test.
- Safety-critical flows — XSS sanitisation in `MarkdownPipe`, rate-limit interception,
  duplicate-request suppression — MUST achieve **100 % branch coverage**.
- The full test suite (all 14 phases) MUST complete within **2 minutes** on CI.

**Rationale**: The coverage floor prevents hidden edge cases in the streaming pipeline,
rate-limit handling, and NgRx state transitions from silently degrading user experience.

### X. NgRx & Real-Time Communication Contract Testing

State management and real-time channels MUST be tested with dedicated Angular/NgRx utilities.

- **Reducers**: MUST be tested as pure functions — invoke directly with `(state, action)`;
  no store module setup required.
- **Selectors**: MUST be tested by passing a mock `AppState` object and asserting
  the projected value; use `createSelector` projector functions for unit isolation.
- **Effects**: MUST use `provideMockActions` (from `@ngrx/effects/testing`) and
  `provideMockStore` (from `@ngrx/store/testing`); NEVER use a real store or real API in
  effect unit tests.
- **SSE (StreamingApiService)**: MUST mock `EventSource` at the window level; no real
  server connection in unit tests.
- **WebSocket (STOMP)**: MUST stub `Client` from `@stomp/stompjs`; no real SockJS
  connection in unit tests.
- Real-time integration tests (Phase 13–14) MAY use a lightweight in-process mock server
  if the test explicitly validates end-to-end data flow.

**Rationale**: NgRx utilities (`provideMockStore`, `provideMockActions`) isolate each
layer of the store without bootstrapping the full Angular application, keeping tests fast
and deterministic.

## Frontend Testing Standards & Tooling

**Framework stack** — fixed for this project; substitutions require a MAJOR version amendment:

| Tool | Version | Role |
|------|---------|------|
| `@ngneat/spectator` | `^22.1.0` | Component/service/pipe/http factory wrappers |
| Vitest | `^4.0.8` | Test runner (replaces Karma/Jasmine) |
| Angular Testing Utilities | Angular 21 BOM | `fakeAsync`, `tick`, `flush`, `ComponentFixture` |
| `@ngrx/store/testing` | `^21.0.1` | `provideMockStore` for unit tests |
| `@ngrx/effects/testing` | `^21.0.1` | `provideMockActions` for effect tests |
| Angular Router Testing | Angular 21 BOM | `RouterTestingModule` for routing tests |

**14-phase implementation map** (source: `agentic-ui-test-plan-speckit.md`):

| Phase | Module Scope | Type | Priority |
|-------|-------------|------|----------|
| 1 | `core/models` — crud, ingestion, streaming | Unit (pure TS) | Critical |
| 2 | `core/interceptors` — duplicate, rate-limit | Unit HTTP | Critical |
| 3 | `core/services` — http, crud, ingestion, streaming, ws, ws-progress, notification, voice | Unit + HTTP | Critical |
| 4 | `shared/pipes` + `shared/directives` — highlight, markdown | Unit (pipe isolated) | High |
| 5 | `shared/components/toast-container` | Component | High |
| 6 | `features/chat/store` — actions, reducer, selectors, effects | NgRx unit | Critical |
| 7 | `features/chat/components` — interface, input, item, voice-button | Component + integration DOM | High |
| 8 | `features/chat/pages` + resolvers | Page + routing | Medium |
| 9 | `features/ingestion/store` — crud, ingestion, progress, rate-limit | NgRx unit | Critical |
| 10 | `features/ingestion/components` — 8 components | Component + integration DOM | High |
| 11 | `features/ingestion/pages` — upload-page | Page | Medium |
| 12 | `features/management` — store, components, page | Component + NgRx | Medium |
| 13 | `pages/workspace` | Integration (routing) | High |
| 14 | `app.component`, `app.routes`, `app.config` | Integration (app root) | High |

**Target totals** (from `agentic-ui-test-plan-speckit.md`): ~52 spec files,
~204 unit tests, ~17 integration tests.

## Frontend Development Workflow & Quality Gates

**Test-First flow** — mandatory for all new `*.spec.ts` files:

1. Write the complete `*.spec.ts` with all `it()` / `test()` calls and labels.
2. Run the suite and confirm every new test **fails** (red phase): `npm test`.
3. Implement or adjust production code until every new test **passes** (green phase).
4. Refactor while all tests remain green.
5. Verify coverage gates (Principle IX) with `ng test --code-coverage` before committing.

**Commit discipline**:
- Each test phase (1–14) MUST be committed as an independent unit; phases MUST NOT be bundled.
- Commit message format: `test(phase-N): add <SpecFileName> — <brief description>`
  — e.g. `test(phase-6): add chat.reducer.spec — SendMessage / StreamComplete scenarios`
- A test-only commit MUST NOT modify production Angular code unless strictly required for
  testability (e.g. adding an `@Output()` to enable event-emission testing).

**Architecture invariants enforced by tests**:
- NgRx effect tests MUST mock the service layer; they MUST NOT reach real HTTP endpoints
  (facade-delegation invariant mirrors backend Principle II).
- Component tests MUST NOT import `StoreModule.forRoot(...)` — use `provideMockStore` only.
- `MarkdownPipe` and `HighlightPipe` tests MUST include an explicit XSS sanitisation
  scenario verifying that `<script>` tags are neutralised (security compliance gate).
- The `agentic-ui-test-plan-speckit.md` phase order defines the implementation sequence;
  phases MUST NOT be skipped or reordered without an amendment to this constitution.

**Filtering by phase** (run subset of tests during development):

```bash
ng test --include="**/core/models/**"        # Phase 1
ng test --include="**/core/interceptors/**"  # Phase 2
ng test --include="**/core/services/**"      # Phase 3
ng test --include="**/shared/**"             # Phases 4–5
ng test --include="**/features/chat/**"      # Phases 6–8
ng test --include="**/features/ingestion/**" # Phases 9–11
ng test --include="**/features/management/**" # Phase 12
ng test --include="**/pages/workspace/**"    # Phase 13
ng test --include="**/app.*"                 # Phase 14
```

---

## Governance

This constitution supersedes all prior informal testing conventions for the NexRAG
platform (backend and frontend). Every developer MUST review it before beginning a new
test phase in either codebase.

**Amendment procedure**:
1. Open a PR that modifies `.specify/memory/constitution.md` exclusively.
2. Increment the version number according to the versioning policy below.
3. Update the Sync Impact Report HTML comment at the top of this file.
4. Check that dependent templates (plan, spec, tasks) still align; update them if needed.
5. Obtain at least one approving review before merging.

**Versioning policy**:
- **MAJOR** (x.0.0): Removal of a principle, change to coverage thresholds, incompatible
  rename of naming conventions, or removal of a mandatory tooling item.
- **MINOR** (1.x.0): New principle added, new phase added to a test plan map, new mandatory
  tooling item, or material expansion of any section.
- **PATCH** (1.0.x): Wording clarifications, typo corrections, example updates, non-semantic
  refinements that do not alter the rules.

**Compliance review**: The lead developer MUST verify coverage gate compliance
(Principles IV and IX) before each phase begins. Non-compliant modules MUST be listed
explicitly in the PR description and resolved before the phase is considered complete.

**Version**: 1.1.0 | **Ratified**: 2026-03-26 | **Last Amended**: 2026-04-28
