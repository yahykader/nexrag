# Quickstart: Phase 1 — Ingestion Foundation Tests

**Branch**: `001-phase1-ingestion-foundation` | **Date**: 2026-03-26

---

## Prerequisites

- Java 21 installed (`java --version` should show `21.x`)
- Maven wrapper present (`nex-rag/mvnw`)
- No running services required — all tests are unit tests (no Redis, no ClamAV)

---

## Run All Phase 1 Tests

```bash
cd nex-rag/

# Run all Spec classes in the ingestion package
./mvnw test -Dtest="**/ingestion/**/*Spec" -pl .
```

Expected output: `BUILD SUCCESS` with `Tests run: ~65, Failures: 0, Errors: 0`

---

## Run a Single Test Class

```bash
# File validation
./mvnw test -Dtest=FileValidatorSpec

# Antivirus guard
./mvnw test -Dtest=AntivirusGuardSpec

# Deduplication store (read + write)
./mvnw test -Dtest=DeduplicationStoreSpec

# Text normalisation
./mvnw test -Dtest=TextNormalizerSpec
```

---

## Run a Single Test Method

```bash
./mvnw test -Dtest="FileValidatorSpec#shouldRejectFileExceedingMaxSize"
```

---

## Check Coverage Gates

```bash
# Runs tests + JaCoCo coverage verification (80% gate + 100% on critical classes)
./mvnw verify -pl .
```

Fails with `BUILD FAILURE` if:
- Any ingestion package is below 80% line or branch coverage
- `AntivirusGuard`, `HashComputer`, or `DeduplicationService` are below 100% branch coverage

Coverage HTML report: `nex-rag/target/site/jacoco/index.html`

---

## Verify Test Isolation

```bash
# Run tests in randomised order to confirm no ordering dependency
./mvnw test -Dtest="**/ingestion/**/*Spec" -Dsurefire.runOrder=random
```

All tests MUST pass regardless of execution order.

---

## Verify Performance Budget

```bash
# Run with timing output — any test > 500ms is a violation of Constitution Principle I
./mvnw test -Dtest="**/ingestion/**/*Spec" -Dsurefire.printSummary=true 2>&1 | grep -E "Time elapsed"
```

---

## Confirm Test-First (Red Phase)

Before implementing a production class, run its spec and confirm **all tests fail**:

```bash
# Example: before implementing AntivirusGuard
./mvnw test -Dtest=AntivirusGuardSpec
# Expected: COMPILATION ERROR or test failures — this is correct (red phase)
```

---

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `NullPointerException` in `@InjectMocks` | Production class uses field injection, not constructor | Refactor to constructor injection |
| `ServerSocket` test hangs | `ClamAvSocketClientSpec` reply thread not started | Check `@BeforeEach` virtual thread setup |
| Coverage gate fails | New branch added to production class without test | Add failure-path test for the new branch |
| `SocketTimeoutException` in test | Stub server not sending response before `timeoutMs` | Ensure reply thread starts before `scan()` is called |
