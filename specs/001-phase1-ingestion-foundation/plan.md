# Implementation Plan: Phase 1 — Ingestion Foundation Tests

**Branch**: `001-phase1-ingestion-foundation` | **Date**: 2026-03-26 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/001-phase1-ingestion-foundation/spec.md`

## Summary

Build 14 JUnit 5 unit test classes covering the NexRAG ingestion foundation layer:
file validation & MIME-type detection, SHA-256 file deduplication, text-chunk
deduplication, and ClamAV antivirus scanning. All tests use Mockito for dependency
injection, AssertJ for assertions, and a plain Java `ServerSocket` stub for the
ClamAV socket client spec. No Spring context is loaded in any Phase 1 test.
Coverage gates: ≥80% line+branch per module, 100% branch on `AntivirusGuard`,
`HashComputer`, and `DeduplicationService`.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: JUnit 5 (Jupiter) · Mockito · AssertJ · Spring Boot Test · JaCoCo Maven Plugin
**Storage**: Redis (Mockito-stubbed; no real Redis in unit tests)
**Testing**: `./mvnw test` (Maven Surefire) · JaCoCo for coverage gates
**Target Platform**: Spring Boot 3.4.2 JVM backend, Maven build
**Project Type**: unit test suite (pure test artifact — no new production code unless interface extraction is required for testability)
**Performance Goals**: every `@Test` method MUST complete in under 500 ms
**Constraints**: no Spring context loaded (`@ExtendWith(MockitoExtension.class)` only) · no real network calls · no real Redis · no real ClamAV socket
**Scale/Scope**: 14 test classes · ~65 test methods · 4 user stories · 6 edge cases

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Gate | Status |
|-----------|------|--------|
| I — Test Isolation & Independence | Each test uses `@BeforeEach` fresh instances; `MockitoExtension` resets mocks automatically; no shared mutable state | ✅ PASS |
| II — SOLID Design Reflected in Tests | `ClamAvSocketClient` is an interface; all classes use constructor injection; each `*Spec.java` covers exactly one production class | ✅ PASS |
| III — Naming & Organisation | All classes end in `Spec.java`; `@DisplayName` in French imperative form; package tree mirrors production | ✅ PASS |
| IV — Coverage & Quality Gates | JaCoCo configured with ≥80% line+branch per module; 100% branch on `AntivirusGuard`, `HashComputer`, `DeduplicationService`; CI gate active | ✅ PASS |
| V — Integration & Contract Testing | Phase 1 is unit-only; Testcontainers and real Redis/pgvector deferred to Phase 9; WireMock reserved for Phase 7+ | ✅ PASS |

No violations. Complexity Tracking section not required.

## Project Structure

### Documentation (this feature)

```text
specs/001-phase1-ingestion-foundation/
├── plan.md              # This file
├── research.md          # Phase 0 — library decisions & protocol details
├── data-model.md        # Phase 1 — interfaces, records, exception contracts
├── quickstart.md        # Phase 1 — how to run and verify Phase 1 tests
├── contracts/
│   ├── file-validation-contract.md
│   ├── deduplication-contract.md
│   └── antivirus-contract.md
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code (repository root)

```text
nex-rag/
└── src/
    └── test/
        └── java/
            └── com/exemple/nexrag/service/rag/
                └── ingestion/
                    ├── util/
                    │   ├── FileTypeDetectorSpec.java        # US-1 / FR-1.1
                    │   ├── FileValidatorSpec.java           # US-1 / FR-1.2, FR-1.3
                    │   ├── MetadataSanitizerSpec.java       # US-1 / FR-1.4 (images only)
                    │   ├── FileUtilsSpec.java               # US-1 utilities
                    │   └── InMemoryMultipartFileSpec.java   # test helper
                    ├── deduplication/
                    │   ├── file/
                    │   │   ├── HashComputerSpec.java        # US-2 / FR-2.1 (100% branch)
                    │   │   ├── DeduplicationServiceSpec.java # US-2 / FR-2.2 (100% branch)
                    │   │   └── DeduplicationStoreSpec.java  # US-2 / FR-2.3 (read + write)
                    │   └── text/
                    │       ├── TextNormalizerSpec.java      # US-3 / FR-3.1
                    │       ├── TextDeduplicationServiceSpec.java # US-3 / FR-3.2
                    │       └── TextLocalCacheSpec.java      # US-3 / FR-3.3
                    └── security/
                        ├── ClamAvSocketClientSpec.java     # US-4 / FR-4.1 (ServerSocket stub)
                        ├── ClamAvResponseParserSpec.java   # US-4 / FR-4.2
                        ├── AntivirusGuardSpec.java         # US-4 / FR-4.3 (100% branch)
                        └── ClamAvHealthSchedulerSpec.java  # US-4 / FR-4.4
```

**Structure Decision**: Single Maven project (`nex-rag/`) — tests live under
`src/test/java/` mirroring the production package tree exactly (Constitution Principle III).
No new source directories or modules are created.
