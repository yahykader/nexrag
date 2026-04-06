# Tasks: PHASE 8 — Interceptor & Validation

**Input**: Design documents from `/specs/008-interceptor-validation/`
**Prerequisites**: plan.md ✅ · spec.md ✅ · research.md ✅ · data-model.md ✅ · quickstart.md ✅

**Nature of work**: Test-only phase — all production classes already exist and must NOT be modified.
New files to create: `RateLimitInterceptorSpec.java`, `RateLimitServiceSpec.java`, `AudioFileValidatorSpec.java`, `FileSignatureValidatorSpec.java`.
Existing file to leave untouched: `FileValidatorSpec.java` (already covers all US-22 FileValidator ACs).

**Tests**: This phase IS the implementation — every task produces or validates test code.

**Organization**: Tasks grouped by user story to enable independent delivery of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no shared state)
- **[Story]**: User story from spec.md (US1 = Rate Limiting, US2 = File Validation, US3 = Signature Verification)

## Path Conventions

All test paths are under:
`nex-rag/src/test/java/com/exemple/nexrag/`

All production paths (reference only — do NOT modify) are under:
`nex-rag/src/main/java/com/exemple/nexrag/`

---

## Phase 1: Setup

**Purpose**: Confirm environment baseline before writing any new tests.

- [x] T001 Create interceptor test package directory `nex-rag/src/test/java/com/exemple/nexrag/service/rag/interceptor/` (mkdir or let IDE scaffold; must exist before T004/T005)
- [x] T002 Run `./mvnw test -Dtest="FileValidatorSpec"` from `nex-rag/` and confirm all 10 existing tests pass — establishes the green baseline before Phase 8 work begins

**Checkpoint**: Both existing test infrastructure and the FileValidatorSpec baseline are verified green.

---

## Phase 2: Foundational

**Purpose**: Verify that all Phase 8 production classes compile cleanly and that the Bucket4j mock chain imports are resolvable.

**⚠️ CRITICAL**: Confirm before writing any Spec class to avoid import errors mid-task.

- [x] T003 Run `./mvnw compile -pl nex-rag` and confirm zero errors for `RateLimitInterceptor`, `RateLimitService`, `RateLimitResult`, `FileValidator`, `FileSignatureValidator`, `AudioFileValidator` in `nex-rag/src/main/java/com/exemple/nexrag/`

**Checkpoint**: All production classes compile — Spec classes can now be created.

---

## Phase 3: User Story 1 — Rate Limiting (Priority: P1) 🎯 MVP

**Goal**: Full unit-test coverage for the rate-limiting layer (`RateLimitInterceptor` + `RateLimitService`), covering all 11 ACs in US-21.

**Independent Test**: `./mvnw test -Dtest="RateLimitInterceptorSpec,RateLimitServiceSpec"` must pass with 11 tests.

### Skeletons (create first — can be done in parallel)

- [x] T00X [P] [US1] Create `RateLimitInterceptorSpec.java` skeleton in `nex-rag/src/test/java/com/exemple/nexrag/service/rag/interceptor/RateLimitInterceptorSpec.java` — class annotation `@DisplayName("Spec : RateLimitInterceptor — Limitation de débit par endpoint")`, `@ExtendWith(MockitoExtension.class)`, fields `@Mock RateLimitService rateLimitService`, `@Mock ObjectMapper objectMapper`, `@InjectMocks RateLimitInterceptor interceptor`; `@BeforeEach` stub `when(objectMapper.writeValueAsString(any())).thenReturn("{\"error\":\"Too Many Requests\"}")`; no test methods yet
- [x] T00X [P] [US1] Create `RateLimitServiceSpec.java` skeleton in `nex-rag/src/test/java/com/exemple/nexrag/service/rag/interceptor/RateLimitServiceSpec.java` — class annotation `@DisplayName("Spec : RateLimitService — Gestion des quotas Bucket4j/Redis")`, `@ExtendWith(MockitoExtension.class)`, fields `@Mock ProxyManager<String> proxyManager`, `@SuppressWarnings("unchecked") @Mock RemoteBucketBuilder<String> remoteBucketBuilder`, `@Mock Bucket bucket`, `@Mock ConsumptionProbe probe`, five `@Mock Supplier<BucketConfiguration>` fields (`uploadConfig`, `batchConfig`, `deleteConfig`, `searchConfig`, `defaultConfig`); instantiate `RateLimitService` manually in `@BeforeEach` passing all 6 mocks; stub `when(proxyManager.builder()).thenReturn(remoteBucketBuilder)` and `when(remoteBucketBuilder.build(anyString(), any())).thenReturn(bucket)` in `@BeforeEach`

### Interceptor tests

- [x] T00X [US1] Add OPTIONS bypass test to `RateLimitInterceptorSpec`: method `devraitCourtCircuiterOptionsEtIgnorerRateLimitService()` — `MockHttpServletRequest("OPTIONS", "/api/upload")`, call `preHandle(req, res, new Object())`, assert `result` is `true`, call `verifyNoInteractions(rateLimitService)` — covers AC-21.3
- [x] T00X [US1] Add allowed-request test to `RateLimitInterceptorSpec`: method `devraitAutoriserAvecHeaderRemainingQuandLimiteNonAtteinte()` — `X-User-Id: user-1`, `GET /api/documents`, stub `checkDefaultLimit("user-1")` → `RateLimitResult.allowed(29L)`, assert `preHandle=true` and `res.getHeader("X-RateLimit-Remaining").equals("29")` — covers AC-21.2
- [x] T00X [US1] Add 429-block test to `RateLimitInterceptorSpec`: method `devraitRetourner429AvecCorpsJsonQuandLimiteUploadAtteinte()` — `X-User-Id: user-42`, `POST /api/upload`, stub `checkUploadLimit("user-42")` → `RateLimitResult.blocked(30L)`, assert `preHandle=false`, `res.getStatus()==429`, `res.getHeader("Retry-After").equals("30")`, `res.getHeader("X-RateLimit-Remaining").equals("0")` — covers AC-21.1 and AC-21.7
- [x] T00X [P] [US1] Add client-identity resolution tests to `RateLimitInterceptorSpec`: method `devraitUtiliserPremierIpXForwardedForQuandPasDeUserId()` — no `X-User-Id`, `X-Forwarded-For: 203.0.113.5, 10.0.0.1`, stub `checkDefaultLimit("203.0.113.5")` → `allowed(30L)`, use `verify(rateLimitService).checkDefaultLimit("203.0.113.5")`; method `devraitPreferHeaderXUserIdAvantIp()` — both `X-User-Id: user-99` and `X-Forwarded-For` present, verify `checkDefaultLimit("user-99")` is called — covers AC-21.4
- [x] T0XX [P] [US1] Add endpoint routing tests to `RateLimitInterceptorSpec`: method `devraitRouterSearchVersCheckSearchLimit()` — `POST /api/search`, verify `checkSearchLimit(any())` is called and `checkDefaultLimit` is never called; method `devraitRouterDeleteFileVersCheckDeleteLimit()` — `DELETE /api/file/abc-123`, verify `checkDeleteLimit(any())` is called; method `devraitRouterBatchVersCheckBatchLimit()` — `POST /api/upload/batch`, verify `checkBatchLimit(any())` is called — covers AC-21.5

### Service tests

- [x] T0XX [US1] Add token-consumed test to `RateLimitServiceSpec`: method `devraitRetournerAllowedAvecTokensRestantsQuandTokenConsomme()` — stub `bucket.tryConsumeAndReturnRemaining(1)` → `probe`, `probe.isConsumed()=true`, `probe.getRemainingTokens()=9L`, call `checkUploadLimit("user-1")`, assert `result.isAllowed()=true` and `result.getRemainingTokens()==9L` — covers AC-21.2
- [x] T0XX [US1] Add quota-exceeded test to `RateLimitServiceSpec`: method `devraitRetournerBlockedAvecRetryAfterQuandLimiteAtteinte()` — stub `probe.isConsumed()=false`, `probe.getNanosToWaitForRefill()=30_000_000_000L`, call `checkUploadLimit("user-1")`, assert `result.isAllowed()=false` and `result.getRetryAfterSeconds()==30L` — covers AC-21.1
- [x] T0XX [US1] Add fail-open test to `RateLimitServiceSpec`: method `devraitRetournerAllowedZeroEnCasExceptionRedis()` — stub `bucket.tryConsumeAndReturnRemaining(1)` to throw `new RuntimeException("Redis unavailable")`, call `checkUploadLimit("user-1")`, assert `result.isAllowed()=true` and `result.getRemainingTokens()==0` — covers AC-21.6
- [x] T0XX [US1] Add Redis-key-format test to `RateLimitServiceSpec`: method `devraitConstruireCleRedisCorrectePourEndpointSearch()` — stub `probe.isConsumed()=true`, call `checkSearchLimit("user-99")`, use `verify(remoteBucketBuilder).build(eq("rate-limit:user-99:search"), any())` — covers AC-21.5
- [x] T0XX [US1] Run `./mvnw test -Dtest="RateLimitInterceptorSpec,RateLimitServiceSpec"` from `nex-rag/` and confirm all 11 tests pass; fix any import or stub errors before proceeding

**Checkpoint**: US1 fully covered — rate limiting layer has 11 passing tests across 2 Spec classes.

---

## Phase 4: User Story 2 — File Validation (Priority: P2)

**Goal**: Full unit-test coverage for `AudioFileValidator`; `FileValidator` is already covered by the existing `FileValidatorSpec`. All 4 ACs in US-22 for audio validation must pass.

**Independent Test**: `./mvnw test -Dtest="AudioFileValidatorSpec,FileValidatorSpec"` must pass with 14 tests (4 new + 10 existing).

- [x] T0XX [P] [US2] Create `AudioFileValidatorSpec.java` skeleton in `nex-rag/src/test/java/com/exemple/nexrag/validation/AudioFileValidatorSpec.java` — class annotation `@DisplayName("Spec : AudioFileValidator — Validation des fichiers audio")`, `@ExtendWith(MockitoExtension.class)`, field `AudioFileValidator validator`, `@BeforeEach setUp()` instantiating `validator = new AudioFileValidator()`; no test methods yet
- [x] T0XX [US2] Add null and empty audio file rejection tests to `AudioFileValidatorSpec`: method `devraitRejeterFichierAudioNull()` — `assertThatIllegalArgumentException().isThrownBy(() -> validator.validate(null)).withMessageContaining("vide")`; method `devraitRejeterFichierAudioVide()` — `MockMultipartFile` with `new byte[0]`, same assertion — covers AC-22.10
- [x] T0XX [US2] Add oversized audio file rejection test to `AudioFileValidatorSpec`: method `devraitRejeterFichierAudioDepassantLimite25Mo()` — anonymous `MockMultipartFile` subclass overriding `getSize()` to return `VoiceConstants.MAX_AUDIO_SIZE_BYTES + 1` and `isEmpty()` to return `false`; assert `IllegalArgumentException` with message containing `"25 MB"` — covers AC-22.10
- [x] T0XX [US2] Add valid audio acceptance test to `AudioFileValidatorSpec`: method `devraitAccepterFichierAudioValideEnDessousLimite()` — `MockMultipartFile("file", "speech.wav", "audio/wav", new byte[]{1, 2, 3})`, assert `assertThatNoException().isThrownBy(() -> validator.validate(file))` — covers AC-22.10
- [x] T0XX [US2] Run `./mvnw test -Dtest="AudioFileValidatorSpec,FileValidatorSpec"` from `nex-rag/` and confirm all 14 tests pass

**Checkpoint**: US2 fully covered — audio validation has 4 passing tests; FileValidator baseline still green.

---

## Phase 5: User Story 3 — File Signature Verification (Priority: P3)

**Goal**: Full unit-test coverage for `FileSignatureValidator`, covering all 7 ACs in US-23 including dangerous extensions, magic bytes mismatch, unknown extension skip, type detection, validateComplete, DOCX-ZIP equivalence, and short-file guard.

**Independent Test**: `./mvnw test -Dtest="FileSignatureValidatorSpec"` must pass with 7 tests.

- [x] T0XX [P] [US3] Create `FileSignatureValidatorSpec.java` skeleton in `nex-rag/src/test/java/com/exemple/nexrag/validation/FileSignatureValidatorSpec.java` — class annotation `@DisplayName("Spec : FileSignatureValidator — Validation des magic bytes")`, `@ExtendWith(MockitoExtension.class)`, field `FileSignatureValidator validator`, `@BeforeEach setUp()` instantiating `validator = new FileSignatureValidator()`; no test methods yet
- [x] T0XX [US3] Add dangerous-extension test to `FileSignatureValidatorSpec`: method `devraitLeverSecurityExceptionPourExtensionDangereuse()` — `MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[]{0x4D, 0x5A, 0x00, 0x01})`, call `validator.validate(file, "exe")`, assert `assertThatThrownBy(...).isInstanceOf(SecurityException.class).hasMessageContainingIgnoringCase("EXE").hasMessageContaining("dangereuse")` — covers AC-23.1
- [x] T0XX [US3] Add magic-bytes-mismatch test to `FileSignatureValidatorSpec`: method `devraitLeverSecurityExceptionSiMagicBytesNonCorrespondants()` — PNG bytes `{(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}` in a file declared as `"pdf"`, call `validator.validate(file, "pdf")`, assert `SecurityException` with message containing `"Signature invalide"` — covers AC-23.2
- [x] T0XX [US3] Add unknown-extension skip test to `FileSignatureValidatorSpec`: method `devraitIgnorerValidationSignaturePourExtensionsSansSignature()` — `MockMultipartFile` with `"Hello world".getBytes()` and extension `"txt"`, call `validator.validate(file, "txt")`, assert `assertThatNoException().isThrownBy(...)` — covers AC-23.3
- [x] T0XX [US3] Add detectRealType test to `FileSignatureValidatorSpec`: method `devraitDecouvriTypePdfParMagicBytes()` — `MockMultipartFile` with bytes `{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31}` (`%PDF-1`), call `validator.detectRealType(file)`, assert result equals `"pdf"` — covers AC-23.4
- [x] T0XX [US3] Add validateComplete-invalid test to `FileSignatureValidatorSpec`: method `devraitRetournerValidationResultatInvalidePourExe()` — `MockMultipartFile` with MZ bytes and extension `"exe"`, call `validator.validateComplete(file, "exe")`, assert `result.isValid()` is `false` and `result.errorMessage()` is not blank — covers AC-23.5
- [x] T0XX [US3] Add DOCX-ZIP equivalence test to `FileSignatureValidatorSpec`: method `devraitAccepterDocxDontTypeDetecteEstZip()` — `MockMultipartFile` with ZIP bytes `{0x50, 0x4B, 0x03, 0x04, 0x14, 0x00}` and extension `"docx"`, call `validator.isExtensionMatching(file, "docx")`, assert result is `true` — covers AC-23.6
- [x] T0XX [US3] Add short-file test to `FileSignatureValidatorSpec`: method `devraitLeverSecurityExceptionSiFichierTropCourt()` — `MockMultipartFile` with `new byte[]{0x25}` (1 byte) declared as `"pdf"` (PDF signature needs 4 bytes), call `validator.validate(file, "pdf")`, assert `SecurityException` with message containing `"trop court"` — covers AC-23.7
- [x] T0XX [US3] Run `./mvnw test -Dtest="FileSignatureValidatorSpec"` from `nex-rag/` and confirm all 7 tests pass

**Checkpoint**: US3 fully covered — signature verification has 7 passing tests.

---

## Phase 6: Polish & Cross-Cutting

**Purpose**: Full-suite validation, coverage gate, and atomic commits per spec class.

- [x] T0XX [P] Run complete Phase 8 suite: `./mvnw test -Dtest="RateLimitInterceptorSpec,RateLimitServiceSpec,FileSignatureValidatorSpec,AudioFileValidatorSpec,FileValidatorSpec"` from `nex-rag/` and confirm all 32 tests pass (7 interceptor + 4 service + 7 signature + 4 audio + 10 file validator)
- [x] T0XX [P] Generate JaCoCo report: `./mvnw test jacoco:report -Dtest="RateLimitInterceptorSpec,RateLimitServiceSpec,FileSignatureValidatorSpec,AudioFileValidatorSpec,FileValidatorSpec"` from `nex-rag/`; open `nex-rag/target/site/jacoco/index.html` and verify ≥ 80% line and branch coverage for packages `service/rag/interceptor` and `validation`
- [x] T0XX Commit `RateLimitInterceptorSpec`: `git add nex-rag/src/test/java/com/exemple/nexrag/service/rag/interceptor/RateLimitInterceptorSpec.java && git commit -m "test(phase-8): add RateLimitInterceptorSpec — limitation de débit par endpoint"`
- [x] T0XX Commit `RateLimitServiceSpec`: `git add nex-rag/src/test/java/com/exemple/nexrag/service/rag/interceptor/RateLimitServiceSpec.java && git commit -m "test(phase-8): add RateLimitServiceSpec — quotas Bucket4j avec fail-open Redis"`
- [x] T0XX Commit `FileSignatureValidatorSpec`: `git add nex-rag/src/test/java/com/exemple/nexrag/validation/FileSignatureValidatorSpec.java && git commit -m "test(phase-8): add FileSignatureValidatorSpec — validation magic bytes et extensions dangereuses"`
- [x] T0XX Commit `AudioFileValidatorSpec`: `git add nex-rag/src/test/java/com/exemple/nexrag/validation/AudioFileValidatorSpec.java && git commit -m "test(phase-8): add AudioFileValidatorSpec — validation taille fichier audio"`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 completion
- **US1 (Phase 3)**: Depends on Phase 2 — BLOCKS nothing, but MVP priority
- **US2 (Phase 4)**: Depends on Phase 2 only — independent of US1
- **US3 (Phase 5)**: Depends on Phase 2 only — independent of US1 and US2
- **Polish (Phase 6)**: Depends on all desired stories being complete

### User Story Dependencies

- **US1 (P1)**: Starts after Phase 2 — no dependency on US2 or US3
- **US2 (P2)**: Starts after Phase 2 — no dependency on US1 or US3; `FileValidatorSpec` already exists
- **US3 (P3)**: Starts after Phase 2 — no dependency on US1 or US2

### Within Each User Story

- Skeleton task (T004/T005/T016/T021) MUST complete before test method tasks
- All test method tasks within a story can be added in any order
- Run-and-verify task (T015/T020/T029) MUST be last in each story phase

### Parallel Opportunities

- T004 and T005 are fully parallel (different files)
- T009 and T010 within US1 are parallel (different test methods, same file but no conflict if added sequentially)
- T016 (US2) and T021 (US3) skeleton creation are parallel with any US1 test method tasks
- T030 and T031 in Phase 6 are parallel (read-only operations)

---

## Parallel Example: User Story 1

```bash
# Run together (different files):
# Task T004: Create RateLimitInterceptorSpec skeleton
# Task T005: Create RateLimitServiceSpec skeleton

# Run together after T004 completes:
# Task T009: Add IP-resolution tests to RateLimitInterceptorSpec
# Task T010: Add endpoint routing tests to RateLimitInterceptorSpec

# Run together after T005 completes:
# Task T011: Add token-consumed test to RateLimitServiceSpec
# Task T012: Add quota-exceeded test to RateLimitServiceSpec
# Task T013: Add fail-open test to RateLimitServiceSpec
# Task T014: Add Redis-key-format test to RateLimitServiceSpec
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001–T002)
2. Complete Phase 2: Foundational (T003)
3. Complete Phase 3: US1 (T004–T015)
4. **STOP and VALIDATE**: `./mvnw test -Dtest="RateLimitInterceptorSpec,RateLimitServiceSpec"` — 11 tests green
5. Rate limiting is fully covered — proceed to US2 or stop here

### Incremental Delivery

1. Setup + Foundational → baseline confirmed
2. Add US1 (rate limiting) → validate 11 tests → commit 2 Spec classes
3. Add US2 (audio validation) → validate 14 tests → commit 1 Spec class
4. Add US3 (signature verification) → validate 7 tests → commit 1 Spec class
5. Polish → coverage gate ≥ 80% → all 32 tests green

### Parallel Team Strategy

After Phase 2 completes:
- Developer A: US1 (T004–T015)
- Developer B: US2 (T016–T020) + US3 (T021–T029)

All three stories are independently compilable and testable.

---

## Notes

- [P] tasks = different files or independent test methods, no shared mutable state
- [Story] label maps each task to its AC traceability row in `plan.md`
- **Do NOT modify** `FileValidatorSpec.java` — it already covers all US-22 FileValidator ACs
- **Do NOT modify** any production class — this is a test-only phase
- Skeleton tasks must precede test-method tasks in the same file
- Use `@SuppressWarnings("unchecked")` on `@Mock RemoteBucketBuilder<String>` (generic type erasure)
- All `@DisplayName` values must be in French imperative: `"DOIT [action] quand [condition]"`
- Commit one Spec class per commit (constitution Principle — phases as independent units)
