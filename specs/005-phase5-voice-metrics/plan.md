# Implementation Plan: Phase 5 — Voice Transcription & RAG Observability

**Branch**: `005-phase5-voice-metrics` | **Date**: 2026-04-01 | **Spec**: [spec.md](spec.md)  
**Input**: Feature specification from `specs/005-phase5-voice-metrics/spec.md`

---

## Summary

Phase 5 adds unit test coverage for two independent modules:

1. **Voice** (`service/rag/voice/`): `WhisperService` and `AudioTempFile` — audio validation, temp file lifecycle, and transcription error paths. The happy-path transcription call is deferred to a WireMock integration spec (Phase 9) because `openAiService` is created in `@PostConstruct` and cannot be injected by Mockito.

2. **Metrics** (`service/rag/metrics/`): `RAGMetrics` and `OpenAiEmbeddingService` — Micrometer counter/timer/gauge correctness using `SimpleMeterRegistry`, plus embedding API latency and error recording. Both classes accept all dependencies via constructor injection and are fully unit-testable with no Spring context.

Production code is **not modified** in this phase — test-only work. One exception: `WhisperService` log statements that print transcript content must be masked (FR-006d / R-006).

---

## Technical Context

**Language/Version**: Java 21 / Spring Boot 3.4.2  
**Primary Dependencies**: JUnit 5 (Jupiter), Mockito, AssertJ, Micrometer `SimpleMeterRegistry`, `ReflectionTestUtils` (Spring Test), Resilience4j (retry — existing config)  
**Storage**: N/A — both modules are stateless; no persistence in Phase 5  
**Testing**: JUnit 5 + Mockito + AssertJ (unit); WireMock (integration, Phase 9 scope)  
**Target Platform**: JVM / Linux server (same as existing backend)  
**Project Type**: Spring Boot web service — test phase only  
**Performance Goals**: Unit tests MUST complete in under 500 ms each (Constitution Principle I)  
**Constraints**: No real network calls in unit specs; no Spring context needed for metrics tests; `@Value` fields injected via `ReflectionTestUtils.setField`  
**Scale/Scope**: 4 new `*Spec.java` files; ~30–40 test methods total

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| **I — Isolation & Independence** | ✅ PASS | All unit tests use `@ExtendWith(MockitoExtension.class)`; `SimpleMeterRegistry` requires zero infrastructure; `AudioTempFile` tests use real `Files.write` (temp dir — allowed under 500 ms) |
| **II — SOLID in Tests** | ✅ PASS | One `*Spec.java` per production class; `WhisperService` → `WhisperServiceSpec`, `AudioTempFile` → `AudioTempFileSpec`, `RAGMetrics` → `RAGMetricsSpec`, `OpenAiEmbeddingService` → `OpenAiEmbeddingServiceSpec`; all deps injected via constructor |
| **III — Naming & Organisation** | ✅ PASS | `*Spec.java` naming; test root mirrors production package; French `@DisplayName` with `"DOIT [action] quand [condition]"` format |
| **IV — Coverage ≥ 80%** | ✅ PASS | All ACs (AC-13.1–13.5, AC-14.1–14.5) map to at least one `@Test`; every public method has at least one failure-path test |
| **V — Integration via Testcontainers/WireMock** | ✅ PASS | Happy-path transcription (AC-13.1, WireMock against Whisper API) deferred to Phase 9 integration spec; no live API keys in unit tests |

**Constitution Check Result: PASS — proceed to implementation.**

---

## Project Structure

### Documentation (this feature)

```
specs/005-phase5-voice-metrics/
├── plan.md          ✅ this file
├── research.md      ✅ Phase 0 output
├── data-model.md    ✅ Phase 1 output
├── quickstart.md    ✅ Phase 1 output
├── contracts/
│   └── voice-api.md ✅ Phase 1 output
└── tasks.md         ⬜ Phase 2 output (/speckit.tasks — not created here)
```

### Source Code — Test Files to Create

```
nex-rag/src/test/java/com/exemple/nexrag/service/rag/
├── voice/
│   ├── WhisperServiceSpec.java            ← NEW (US-1: validation paths)
│   └── AudioTempFileSpec.java             ← NEW (US-1: temp file lifecycle)
└── metrics/
    ├── RAGMetricsSpec.java                ← NEW (US-2: counters/gauges/timers)
    └── embedding/
        └── OpenAiEmbeddingServiceSpec.java ← NEW (US-3: embedding latency)
```

### Source Code — Production Files to Patch

```
nex-rag/src/main/java/com/exemple/nexrag/service/rag/voice/
└── WhisperService.java     — Mask transcript in log.debug() and log.info() (FR-006d)
```

**Structure Decision**: Single Spring Boot backend project — Option 1 (single project). New test files mirror existing production package tree exactly. One minimal production patch for log masking.

---

## Complexity Tracking

No Constitution violations — table not required.

---

## Phase 0: Research — Resolved

All unknowns resolved in [`research.md`](research.md). Summary:

| ID | Topic | Decision |
|---|---|---|
| R-001 | Unit-testing `WhisperService` (`@PostConstruct` + `@Value`) | Test validation paths only in unit spec; use `ReflectionTestUtils` for `apiKey`; happy path → WireMock integration |
| R-002 | `SimpleMeterRegistry` for `RAGMetrics` | Constructor-inject `new SimpleMeterRegistry()` — no Spring context needed |
| R-003 | Rate limit unit test (`ProxyManager` mock) | Mock `RateLimitService` at facade boundary; `checkVoiceLimit` to be added alongside existing endpoint methods |
| R-004 | Resilience4j retry for Whisper | Reuse existing `@Retry` config (3 attempts, 1s→10s, ×2.0); unit spec validates exception propagation only |
| R-005 | WireMock for Whisper API | `POST /v1/audio/transcriptions` stubbed; deferred to Phase 9 |
| R-006 | Log masking (FR-006d) | `log.debug` and `log.info` in `WhisperService` mask transcript to `[N chars]` |

---

## Phase 1: Design — Artifacts

### Data Model → [`data-model.md`](data-model.md)

Four entities documented:
- **AudioTranscription** — stateless value object, 25 MB limit, validation rules, privacy constraints
- **PipelineMetric** — full Micrometer meter inventory (20 meters across 5 pipeline layers)
- **CacheEvent** — hit/miss counter invariant
- **TempAudioFile** — transient file, `create`/`deleteSilently` lifecycle

### Interface Contracts → [`contracts/voice-api.md`](contracts/voice-api.md)

- `POST /api/v1/voice/transcribe` — multipart, 200/400/413/429/503 responses
- `GET /api/v1/voice/health` — availability status
- `GET /api/actuator/prometheus` — Micrometer scrape endpoint

### Quickstart → [`quickstart.md`](quickstart.md)

- Maven commands to run Phase 5 specs individually or together
- Test class patterns with code snippets (`ReflectionTestUtils`, `SimpleMeterRegistry`)
- Coverage gate command (`jacoco:report`)
- Commit message conventions

---

## Test Design Summary

### `WhisperServiceSpec.java`

| Test method | AC | What it verifies |
|---|---|---|
| `devraitLeverIllegalArgumentExceptionPourAudioVide` | AC-13.2 | `transcribeAudio(new byte[0], ...)` → `IllegalArgumentException` |
| `devraitLeverIllegalArgumentExceptionPourAudioNull` | AC-13.2 | `transcribeAudio(null, ...)` → `IllegalArgumentException` |
| `devraitLeverIllegalArgumentExceptionSiTaillDepasse25Mo` | FR-001 | `byte[26_214_401]` → `IllegalArgumentException` |
| `devraitRetournerFalseSiServiceNonInitialise` | AC-13.3 | `isAvailable()` → `false` (openAiService is null post-InjectMocks) |
| `devraitRetournerTrueSiApiKeyConfigure` | AC-13.3 | `ReflectionTestUtils` sets `apiKey` + `openAiService` → `isAvailable()` = `true` |
| `devraitNePasAppelerLApiPourAudioInvalide` | FR-001 | Verify `audioTempFile.create()` never called for invalid input |

### `AudioTempFileSpec.java`

| Test method | AC | What it verifies |
|---|---|---|
| `devraitCreerFichierTempAvecExtensionDuNomOriginal` | AC-13.4 | `create(bytes, "audio.mp3")` → file ends with `.mp3` |
| `devraitUtiliserExtensionParDefautSiAbsente` | AC-13.4 | `create(bytes, "audio")` → file ends with `.webm` |
| `devraitUtiliserExtensionParDefautSiNomNull` | AC-13.4 | `create(bytes, null)` → file ends with `.webm` |
| `devraitEcrireLesBytesCorrectementDansFichierTemp` | AC-13.4 | File content equals original bytes |
| `devraitSupprimerFichierSilencieusement` | AC-13.5 | `deleteSilently(file)` → file no longer exists |
| `devraitNePasLeverExceptionSiNullPasseADeleteSilently` | AC-13.5 | `deleteSilently(null)` → no exception |

### `RAGMetricsSpec.java`

| Test method | AC | What it verifies |
|---|---|---|
| `devraitIncrementerCompteurSuccesApresIngestionReussie` | AC-14.1 | Counter `rag_ingestion_files_total{strategy=pdf,status=success}` = 1.0 |
| `devraitIncrementerTotalFichiersTraites` | AC-14.1 | `getTotalFilesProcessed()` = 1 after one `recordIngestionSuccess` |
| `devraitSuivreIngestionsActivesViaStartEnd` | AC-14.2 | `startIngestion()` +1; `endIngestion()` back to 0 |
| `devraitIncrementerSeulementLeCompteurHit` | AC-14.3 | Hit counter = 1.0; miss meter absent from registry |
| `devraitIncrementerSeulementLeCompteurMiss` | AC-14.3 | Miss counter = 1.0; hit meter absent |
| `devraitAccumulerTokensGeneresCorrectement` | AC-14.5 | Two `recordGeneration` calls → `getTotalTokensGenerated()` = sum |
| `devraitNePasRecreeLeMeterSiDejaEnregistre` | FR-014 | Second `recordIngestionSuccess` → counter = 2.0, not a new registration |
| `devraitEnregistrerErreurIngestionSansToucherSucces` | FR-007/008 | Error counter increments; success counter stays at 0 |

### `OpenAiEmbeddingServiceSpec.java`

| Test method | AC | What it verifies |
|---|---|---|
| `devraitEnregistrerAppelAPIApresEmbeddingTexteReussi` | AC-14.4 | `ragMetrics.recordApiCall("embed_text", _)` called once |
| `devraitEnregistrerErreurAPIApresEchecEmbedding` | AC-14.4 | `ragMetrics.recordApiError("embed_text")` called; `RuntimeException` propagated |
| `devraitNePasEnregistrerSuccesEnCasDEchec` | AC-14.4 | `recordApiCall` NOT called when model throws |
| `devraitEnregistrerAppelAPIApresEmbeddingSegment` | AC-14.4 | `recordApiCall("embed_segment", _)` called for `embedSegment()` |
| `devraitEnregistrerAppelAPIApresEmbeddingBatch` | AC-14.4 | `recordApiCall("embed_batch", _)` called for `embedBatch()` |
