# Implementation Plan: PHASE 8 — Interceptor & Validation

**Branch**: `008-interceptor-validation` | **Date**: 2026-04-04 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/008-interceptor-validation/spec.md`

## Summary

Add unit test coverage for the rate-limiting and file-validation layer of the NexRAG backend. This phase targets four production classes — `RateLimitInterceptor`, `RateLimitService`, `FileSignatureValidator`, and `AudioFileValidator` — that currently have zero test coverage. `FileValidatorSpec` already exists and is not modified. All tests are pure unit tests using Mockito; no infrastructure is started.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: JUnit 5 (Jupiter), Mockito, AssertJ, Spring Boot Test (`MockHttpServletRequest`/`MockHttpServletResponse`), Bucket4j (`io.github.bucket4j:bucket4j_jdk17-lettuce`)
**Storage**: Redis (mocked via `ProxyManager<String>` Mockito mock — no real Redis in unit tests)
**Testing**: JUnit 5 + Mockito + AssertJ; `@ExtendWith(MockitoExtension.class)` on all new classes
**Target Platform**: JVM (Linux server / GCP); tests run locally and in CI
**Project Type**: web-service backend (Spring Boot 3.4.2)
**Performance Goals**: each test method < 500 ms (constitution Principle I)
**Constraints**: no real network calls, no real filesystem I/O, no real Redis; French `@DisplayName` on every class and method; log assertions NOT required in this phase (research Decision 4)
**Scale/Scope**: 4 new `*Spec.java` files; ~22 new `@Test` methods total across the 4 classes

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Principle | Status | Notes |
|------|-----------|--------|-------|
| Test isolation — no real infrastructure | I | ✅ PASS | All Redis/Bucket4j/ObjectMapper dependencies mocked |
| Unit tests < 500 ms | I | ✅ PASS | No I/O, no network; Mockito only |
| One `*Spec.java` per production class | II (SRP) | ✅ PASS | 4 new classes map 1:1 to 4 production classes |
| Constructor injection + `@InjectMocks` / `@Mock` only | II (DIP) | ✅ PASS | All production classes use `@RequiredArgsConstructor` |
| `<ClassName>Spec.java` naming | III | ✅ PASS | All new files follow convention |
| French `@DisplayName` on class + every `@Test` | III | ✅ PASS | Enforced in all spec examples |
| Package mirrors production tree | III | ✅ PASS | `service/rag/interceptor/` + `validation/` |
| ≥ 80% line+branch coverage per module | IV | ✅ PASS (target) | All public methods have success + failure paths |
| Every AC maps to ≥ 1 `@Test` | IV | ✅ PASS | Verified in data-model.md test mapping table |
| Integration tests use Testcontainers only | V | N/A | Phase 8 is unit tests only |

**Post-design re-check**: All gates still pass. No complexity violations. No `Complexity Tracking` section needed.

## Project Structure

### Documentation (this feature)

```text
specs/008-interceptor-validation/
├── plan.md              ✅ this file
├── research.md          ✅ Phase 0 output
├── data-model.md        ✅ Phase 1 output
├── quickstart.md        ✅ Phase 1 output
├── checklists/
│   └── requirements.md  ✅ all items pass
└── tasks.md             ⬜ Phase 2 output (/speckit.tasks — not yet created)
```

### Source Code

```text
nex-rag/src/
├── main/java/com/exemple/nexrag/
│   ├── service/rag/interceptor/
│   │   ├── RateLimitInterceptor.java      ✅ production (do not modify)
│   │   ├── RateLimitService.java          ✅ production (do not modify)
│   │   └── RateLimitResult.java           ✅ production (do not modify)
│   └── validation/
│       ├── FileValidator.java             ✅ production (do not modify)
│       ├── FileSignatureValidator.java    ✅ production (do not modify)
│       └── AudioFileValidator.java        ✅ production (do not modify)
│
└── test/java/com/exemple/nexrag/
    ├── service/rag/interceptor/
    │   ├── RateLimitInterceptorSpec.java  ⬜ CREATE
    │   └── RateLimitServiceSpec.java      ⬜ CREATE
    └── validation/
        ├── FileValidatorSpec.java         ✅ EXISTS — do not modify
        ├── FileSignatureValidatorSpec.java ⬜ CREATE
        └── AudioFileValidatorSpec.java    ⬜ CREATE
```

**Structure Decision**: Single backend project (`nex-rag/`). Test packages mirror production packages exactly per constitution Principle III. No frontend changes. No production code changes — this phase is test-only.

---

## Phase 0: Research

*Completed — see [research.md](research.md)*

**Key decisions:**

1. **Bucket4j mock chain**: Mock `ProxyManager<String>`, `RemoteBucketBuilder<String>`, `Bucket`, `ConsumptionProbe` as four `@Mock` fields; chain via `when(...).thenReturn(...)` in `@BeforeEach`. Never use real Bucket4j in-memory buckets (non-deterministic TTL).

2. **Interceptor testing**: `@ExtendWith(MockitoExtension.class)` + `MockHttpServletRequest` / `MockHttpServletResponse` — no `MockMvc`, no Spring context. Mock `ObjectMapper.writeValueAsString(any())` → fixed JSON string for 429 tests.

3. **Magic bytes testing**: `MockMultipartFile` with explicit byte-literal arrays. Short-file scenario: provide fewer bytes than the signature length. DOCX/XLSX equivalence: use ZIP header bytes (`50 4B 03 04`) with `.docx` extension.

4. **Log assertions**: Not required in this phase (research Decision 4). Assert HTTP behavior and return values only.

5. **`FileValidatorSpec` already complete**: Do not create, modify, or duplicate. All US-22 `FileValidator` ACs are already covered.

---

## Phase 1: Design & Contracts

### `RateLimitInterceptorSpec.java`

**Location**: `src/test/java/com/exemple/nexrag/service/rag/interceptor/`
**Class under test**: `RateLimitInterceptor(RateLimitService, ObjectMapper)`
**Mocks**: `@Mock RateLimitService`, `@Mock ObjectMapper`

| Test method | AC covered | Scenario |
|-------------|-----------|----------|
| `devraitRetourner429AvecCorpsJsonQuandLimiteUploadAtteinte` | US-21/AC-21.1 + AC-21.7 | `checkUploadLimit` returns `blocked(30)` → status 429, `Retry-After: 30`, `X-RateLimit-Remaining: 0` |
| `devraitAutoriserAvecHeaderRemainingQuandLimiteNonAtteinte` | US-21/AC-21.2 | `checkDefaultLimit` returns `allowed(29)` → status 200, `X-RateLimit-Remaining: 29` |
| `devraitCourtCircuiterOptionsEtIgnorerRateLimitService` | US-21/AC-21.3 | OPTIONS method → `preHandle()=true`, `verifyNoInteractions(rateLimitService)` |
| `devraitUtiliserPremierIpXForwardedForQuandPasDeUserId` | US-21/AC-21.4 | `X-Forwarded-For: 203.0.113.5, 10.0.0.1` → `checkDefaultLimit("203.0.113.5")` verified |
| `devraitPreferHeaderXUserIdAvantSessionEtIp` | US-21/AC-21.4 | `X-User-Id: user-42` + session → `checkDefaultLimit("user-42")` called, not session value |
| `devraitRouterSearchVersCheckSearchLimit` | US-21/AC-21.5 | URI `/api/search` → `checkSearchLimit(userId)` called |
| `devraitRouterDeleteFileVersCheckDeleteLimit` | US-21/AC-21.5 | `DELETE /api/file/abc` → `checkDeleteLimit(userId)` called |

**Import block**:
```java
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
```

---

### `RateLimitServiceSpec.java`

**Location**: `src/test/java/com/exemple/nexrag/service/rag/interceptor/`
**Class under test**: `RateLimitService(ProxyManager<String>, 5×Supplier<BucketConfiguration>)`
**Mocks**: `@Mock ProxyManager<String>`, `@Mock RemoteBucketBuilder<String>`, `@Mock Bucket`, `@Mock ConsumptionProbe`, 5× `@Mock Supplier<BucketConfiguration>`

| Test method | AC covered | Scenario |
|-------------|-----------|----------|
| `devraitRetournerAllowedAvecTokensRestantsQuandTokenConsomme` | US-21/AC-21.2 | `probe.isConsumed()=true`, `probe.getRemainingTokens()=9` → `RateLimitResult(allowed=true, remaining=9)` |
| `devraitRetournerBlockedAvecRetryAfterQuandLimiteAtteinte` | US-21/AC-21.1 | `probe.isConsumed()=false`, `probe.getNanosToWaitForRefill()=30_000_000_000L` → `RateLimitResult(allowed=false, retryAfter=30)` |
| `devraitRetournerAllowedZeroEnCasExceptionRedis` | US-21/AC-21.6 | `bucket.tryConsumeAndReturnRemaining(1)` throws `RuntimeException` → `RateLimitResult(allowed=true, remaining=0)` |
| `devraitConstruireCleRedisCorrectePourSearch` | US-21/AC-21.5 | `checkSearchLimit("user-99")` → `verify(bucketBuilder).build(eq("rate-limit:user-99:search"), any())` |

**Suppress warning note**: `@SuppressWarnings("unchecked")` required on `@Mock RemoteBucketBuilder<String>` field due to generic type erasure.

---

### `FileSignatureValidatorSpec.java`

**Location**: `src/test/java/com/exemple/nexrag/validation/`
**Class under test**: `FileSignatureValidator()` (no-arg constructor, `@Component`)
**No mocks** — plain instantiation in `@BeforeEach`

| Test method | AC covered | Scenario |
|-------------|-----------|----------|
| `devraitLeverSecurityExceptionPourExtensionDangereuse` | US-23/AC-23.1 | `.exe` → `SecurityException` containing "EXE" + "dangereuse" |
| `devraitLeverSecurityExceptionSiMagicBytesNonCorrespondants` | US-23/AC-23.2 | PNG bytes declared as `.pdf` → `SecurityException("Signature invalide...")` |
| `devraitIgnorerValidationSignaturePourExtensionsSansSignature` | US-23/AC-23.3 | `.txt` content → no exception |
| `devraitDecouvriTypePdfParMagicBytes` | US-23/AC-23.4 | `%PDF` bytes → `detectRealType()` returns `"pdf"` |
| `devraitRetournerValidationResultatInvalidePourExe` | US-23/AC-23.5 | `validateComplete(exeFile, "exe")` → `ValidationResult(isValid=false)` |
| `devraitAccepterDocxDontTypeDetecteEstZip` | US-23/AC-23.6 | ZIP bytes + `.docx` extension → `isExtensionMatching()=true` |
| `devraitLeverSecurityExceptionSiFichierTropCourt` | US-23/AC-23.7 | 1-byte file declared as `.pdf` (needs 4) → `SecurityException("trop court")` |

---

### `AudioFileValidatorSpec.java`

**Location**: `src/test/java/com/exemple/nexrag/validation/`
**Class under test**: `AudioFileValidator()` (no-arg constructor, `@Component`)
**No mocks** — plain instantiation in `@BeforeEach`

| Test method | AC covered | Scenario |
|-------------|-----------|----------|
| `devraitRejeterFichierAudioNull` | US-22/AC-22.10 | `null` → `IllegalArgumentException` containing "vide" |
| `devraitRejeterFichierAudioVide` | US-22/AC-22.10 | 0-byte file → `IllegalArgumentException` containing "vide" |
| `devraitRejeterFichierAudioDepassantLimite25Mo` | US-22/AC-22.10 | `getSize()=MAX_AUDIO_SIZE_BYTES+1` → `IllegalArgumentException` containing "25 MB" |
| `devraitAccepterFichierAudioValideEnDessousLimite` | US-22/AC-22.10 | 3-byte WAV file → no exception |

**`fileWithSize` helper**: anonymous `MockMultipartFile` subclass overriding `getSize()` and `isEmpty()` — same pattern used in `FileValidatorSpec`.

---

### Contracts

No external interface contracts are modified by this phase. Phase 8 is test-only; the interceptor and validators are internal components with no public API contract changes.

---

### Agent Context Update

Run after Phase 1 artifacts are complete:

```bash
cd "D:/Formation-DATA-2024/IA-Genrative/TP/NexRAG"
bash .specify/scripts/bash/update-agent-context.sh claude
```

---

## Acceptance Criteria Coverage Matrix

| AC | Test class | Test method (abbreviated) |
|----|-----------|--------------------------|
| US-21/AC-21.1 | `RateLimitInterceptorSpec` | `devraitRetourner429...LimiteUploadAtteinte` |
| US-21/AC-21.1 | `RateLimitServiceSpec` | `devraitRetournerBlockedAvecRetryAfter...` |
| US-21/AC-21.2 | `RateLimitInterceptorSpec` | `devraitAutoriserAvecHeaderRemaining...` |
| US-21/AC-21.2 | `RateLimitServiceSpec` | `devraitRetournerAllowedAvecTokensRestants...` |
| US-21/AC-21.3 | `RateLimitInterceptorSpec` | `devraitCourtCircuiterOptions...` |
| US-21/AC-21.4 | `RateLimitInterceptorSpec` | `devraitUtiliserPremierIpXForwardedFor...` |
| US-21/AC-21.4 | `RateLimitInterceptorSpec` | `devraitPreferHeaderXUserIdAvant...` |
| US-21/AC-21.5 | `RateLimitInterceptorSpec` | `devraitRouterSearchVers...` |
| US-21/AC-21.5 | `RateLimitInterceptorSpec` | `devraitRouterDeleteFileVers...` |
| US-21/AC-21.5 | `RateLimitServiceSpec` | `devraitConstruireCleRedisCorrecte...` |
| US-21/AC-21.6 | `RateLimitServiceSpec` | `devraitRetournerAllowedZeroEnCasExceptionRedis` |
| US-21/AC-21.7 | `RateLimitInterceptorSpec` | `devraitRetourner429AvecCorpsJson...` |
| US-22/AC-22.1–22.9 | `FileValidatorSpec` | ✅ already implemented (10 tests) |
| US-22/AC-22.10 | `AudioFileValidatorSpec` | 4 tests |
| US-23/AC-23.1 | `FileSignatureValidatorSpec` | `devraitLeverSecurityExceptionPourExtensionDangereuse` |
| US-23/AC-23.2 | `FileSignatureValidatorSpec` | `devraitLeverSecurityExceptionSiMagicBytesNonCorrespondants` |
| US-23/AC-23.3 | `FileSignatureValidatorSpec` | `devraitIgnorerValidationSignaturePourExtensionsSansSignature` |
| US-23/AC-23.4 | `FileSignatureValidatorSpec` | `devraitDecouvriTypePdfParMagicBytes` |
| US-23/AC-23.5 | `FileSignatureValidatorSpec` | `devraitRetournerValidationResultatInvalidePourExe` |
| US-23/AC-23.6 | `FileSignatureValidatorSpec` | `devraitAccepterDocxDontTypeDetecteEstZip` |
| US-23/AC-23.7 | `FileSignatureValidatorSpec` | `devraitLeverSecurityExceptionSiFichierTropCourt` |
