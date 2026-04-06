# Quickstart: PHASE 8 — Interceptor & Validation Tests

**Branch**: `008-interceptor-validation` | **Date**: 2026-04-04

---

## Prerequisites

- Java 21 installed
- Maven wrapper available (`./mvnw`) from `nex-rag/`
- No infrastructure required (unit tests only — all dependencies mocked)

---

## Run All Phase 8 Tests

```bash
cd nex-rag

./mvnw test -Dtest="RateLimitInterceptorSpec,RateLimitServiceSpec,FileSignatureValidatorSpec,AudioFileValidatorSpec,FileValidatorSpec"
```

---

## Run by Sub-Group

```bash
# Rate limiting interceptor & service only
./mvnw test -Dtest="RateLimitInterceptorSpec,RateLimitServiceSpec"

# Validation layer only (all 3 validators)
./mvnw test -Dtest="FileSignatureValidatorSpec,AudioFileValidatorSpec,FileValidatorSpec"

# Single class
./mvnw test -Dtest="RateLimitInterceptorSpec"
./mvnw test -Dtest="RateLimitServiceSpec"
./mvnw test -Dtest="FileSignatureValidatorSpec"
./mvnw test -Dtest="AudioFileValidatorSpec"

# Single method
./mvnw test -Dtest="RateLimitInterceptorSpec#shouldReturn429WithJsonBodyWhenUploadLimitExceeded"
```

---

## Run Full Phase 8 + Coverage Check

```bash
./mvnw test jacoco:report -Dtest="RateLimitInterceptorSpec,RateLimitServiceSpec,FileSignatureValidatorSpec,AudioFileValidatorSpec,FileValidatorSpec"

# Coverage report at:
# nex-rag/target/site/jacoco/index.html
```

---

## Test File Locations

```
src/test/java/com/exemple/nexrag/
├── validation/
│   ├── FileValidatorSpec.java            ✅ already exists
│   ├── FileSignatureValidatorSpec.java   ⬜ to create
│   └── AudioFileValidatorSpec.java       ⬜ to create
└── service/rag/interceptor/
    ├── RateLimitInterceptorSpec.java     ⬜ to create
    └── RateLimitServiceSpec.java         ⬜ to create
```

---

## Expected Test Counts (minimum)

| Spec class | Min test methods | Key assertions |
|-----------|-----------------|----------------|
| `RateLimitInterceptorSpec` | 7 | preHandle return value, HTTP 429, response headers, OPTIONS bypass, routing, IP fallback |
| `RateLimitServiceSpec` | 4 | allowed result, blocked result, fail-open, Redis key format |
| `FileSignatureValidatorSpec` | 7 | dangerous ext, signature mismatch, txt skip, detectRealType, validateComplete, DOCX-ZIP equiv, short file |
| `AudioFileValidatorSpec` | 4 | null, empty, oversized, valid |
| `FileValidatorSpec` | 10 | already implemented — do not modify |

---

## Constitution Compliance Checklist

Before committing Phase 8:

- [ ] Each new `*Spec.java` covers exactly one production class (SRP — Principle II)
- [ ] All `@DisplayName` values are in French imperative: `"DOIT [action] quand [condition]"` (Principle III)
- [ ] `@ExtendWith(MockitoExtension.class)` on every new spec class (Principle I)
- [ ] No real Redis, filesystem I/O, or network calls in any test (Principle I)
- [ ] All production classes receive dependencies via constructor injection; tests use `@InjectMocks` / `@Mock` only (Principle II DIP)
- [ ] Line + branch coverage ≥ 80% for `interceptor` and `validation` packages (Principle IV)
- [ ] Every AC in US-21, US-22, US-23 maps to at least one `@Test` method (Principle IV)
- [ ] Commit message format: `test(phase-8): add <ClassName>Spec — <brief description>` (Workflow)

---

## Commit Sequence

```bash
# One commit per Spec class (constitution: phases committed as independent units)
git add src/test/java/com/exemple/nexrag/service/rag/interceptor/RateLimitInterceptorSpec.java
git commit -m "test(phase-8): add RateLimitInterceptorSpec — limitation de débit par endpoint"

git add src/test/java/com/exemple/nexrag/service/rag/interceptor/RateLimitServiceSpec.java
git commit -m "test(phase-8): add RateLimitServiceSpec — quotas Bucket4j avec fail-open Redis"

git add src/test/java/com/exemple/nexrag/validation/FileSignatureValidatorSpec.java
git commit -m "test(phase-8): add FileSignatureValidatorSpec — validation magic bytes et extensions dangereuses"

git add src/test/java/com/exemple/nexrag/validation/AudioFileValidatorSpec.java
git commit -m "test(phase-8): add AudioFileValidatorSpec — validation taille fichier audio"
```
