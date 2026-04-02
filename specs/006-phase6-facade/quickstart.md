# Quickstart: Phase 6 — Facade Unit Tests

**Branch**: `006-phase6-facade` | **Date**: 2026-04-02

---

## Prerequisites

- Java 21 installed (`java -version`)
- Maven wrapper present (`nex-rag/mvnw`)
- No infrastructure required — all dependencies are mocked

---

## Run all Phase 6 tests

```bash
cd nex-rag
./mvnw test -Dtest="IngestionFacadeImplSpec,CrudFacadeImplSpec,StreamingFacadeImplSpec,VoiceFacadeImplSpec,DuplicateCheckerSpec"
```

---

## Run a single spec class

```bash
./mvnw test -Dtest=IngestionFacadeImplSpec
./mvnw test -Dtest=CrudFacadeImplSpec
./mvnw test -Dtest=StreamingFacadeImplSpec
./mvnw test -Dtest=VoiceFacadeImplSpec
./mvnw test -Dtest=DuplicateCheckerSpec
```

---

## Run a single test method

```bash
./mvnw test -Dtest="IngestionFacadeImplSpec#shouldReturnSuccessForValidFile"
```

---

## Check coverage (JaCoCo)

```bash
./mvnw test jacoco:report
# Report → nex-rag/target/site/jacoco/index.html
```

Coverage gate: **≥ 80 % line + branch** for the `facade` module.  
`DuplicateChecker` specifically: **100 % branch coverage** required.

---

## Expected test results

| Spec Class | Test Methods | All pass? |
|-----------|-------------|-----------|
| `IngestionFacadeImplSpec` | 4 | ✅ |
| `CrudFacadeImplSpec` | 3 | ✅ |
| `StreamingFacadeImplSpec` | 3 | ✅ |
| `VoiceFacadeImplSpec` | 3 | ✅ |
| `DuplicateCheckerSpec` | 3 | ✅ |
| **Total** | **16** | ✅ |

---

## Test locations

```
nex-rag/src/test/java/com/exemple/nexrag/service/rag/facade/
├── IngestionFacadeImplSpec.java
├── CrudFacadeImplSpec.java
├── StreamingFacadeImplSpec.java
├── VoiceFacadeImplSpec.java
└── DuplicateCheckerSpec.java
```

---

## Commit format (Constitution Principle — Development Workflow)

```
test(phase-6): add IngestionFacadeImplSpec — orchestration et gestion erreurs antivirus
test(phase-6): add CrudFacadeImplSpec — pagination et suppression avec embeddings
test(phase-6): add StreamingFacadeImplSpec — flux RAG et conversion erreurs
test(phase-6): add VoiceFacadeImplSpec — transcription vocale et erreurs WhisperService
test(phase-6): add DuplicateCheckerSpec — détection doublon et fail-closed store inaccessible
```

Each class is committed independently (Constitution Principle — Commit discipline).
