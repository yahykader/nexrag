# Research: Phase 5 — Voice Transcription & RAG Observability

**Feature**: `005-phase5-voice-metrics`  
**Date**: 2026-04-01  
**Status**: Complete — all NEEDS CLARIFICATION resolved

---

## R-001 — Unit-Testing `WhisperService` given `@PostConstruct` + `@Value`

**Decision**: Test only the validation paths in unit specs; cover the happy path via WireMock integration spec.

**Rationale**: `WhisperService.openAiService` is a private field populated in `@PostConstruct init()` using an injected `@Value("${openai.api.key}")` string. Mockito `@InjectMocks` cannot trigger `@PostConstruct`, so `openAiService` remains `null` after injection. Tests that require `openAiService` (i.e., the happy-path transcription call) must therefore run as integration tests against a WireMock stub. The validation path (`validateAudio`) is exercised before `openAiService` is ever touched, making it fully unit-testable. The `@Value` field `apiKey` is injected via `ReflectionTestUtils.setField(service, "apiKey", "test-key")` — the same pattern already used in `AntivirusGuardSpec`.

**Alternatives considered**:
- Extract `OpenAiService` creation to a `@Bean` factory and inject it via constructor → would require production code change; deferred (out of scope for test-only phase).
- Use `@SpringBootTest(webEnvironment = NONE)` + `@TestPropertySource` → heavier, slower, classified as integration test.

**Affected specs**: `WhisperServiceSpec.java` (unit), `WhisperServiceIntegrationSpec.java` (integration, Phase 9 or future).

---

## R-002 — `SimpleMeterRegistry` for `RAGMetrics` Unit Tests

**Decision**: Use `io.micrometer.core.instrument.simple.SimpleMeterRegistry` — no Spring context needed.

**Rationale**: `RAGMetrics` takes `MeterRegistry` via constructor injection. `SimpleMeterRegistry` is an in-memory, zero-dependency implementation from the Micrometer BOM already present in the project. It allows asserting `counter.count()` and `gauge.value()` synchronously without any infrastructure. Pattern:
```java
MeterRegistry registry = new SimpleMeterRegistry();
RAGMetrics metrics = new RAGMetrics(registry);
```
Counters are queried after the fact: `registry.counter("rag_ingestion_files_total", "strategy", "pdf", "status", "success").count()`.

**Alternatives considered**:
- `PrometheusMeterRegistry` (used in production) → requires Prometheus dependency and scraping setup; overkill for unit tests.
- Mockito mock of `MeterRegistry` → complex stubbing chain; SimpleMeterRegistry is simpler and more realistic.

---

## R-003 — Mocking `RateLimitService` (Bucket4j + Redis-backed `ProxyManager`)

**Decision**: Mock `RateLimitService` at the facade/controller boundary using Mockito; do not test Bucket4j internals in unit specs.

**Rationale**: `RateLimitService` wraps `ProxyManager<String>` (Redis-backed Bucket4j). Unit tests of `VoiceFacade` or `VoiceController` must mock `RateLimitService` and stub `checkVoiceLimit(userId)` to return either `RateLimitResult.allowed(9)` or `RateLimitResult.blocked(30)`. A dedicated `RateLimitServiceSpec.java` (if it doesn't exist yet) would mock `ProxyManager` directly. This is consistent with how `RateLimitService` is used across ingestion controllers.

**For the voice endpoint**: `RateLimitService` will need a `checkVoiceLimit(String userId)` method backed by a `voiceBucketConfig` (10 tokens/min). This mirrors `checkUploadLimit` — same pattern, same bucket configuration style.

**Alternatives considered**:
- Full integration test with Redis Testcontainer → appropriate only for Phase 9 end-to-end.
- In-process Bucket4j without ProxyManager → breaks the existing distributed rate-limit architecture.

---

## R-004 — Resilience4j Retry for Whisper API Calls

**Decision**: Configure a Resilience4j `@Retry` annotation on `WhisperService.callWhisperApi()` with `maxAttempts=3`, exponential backoff `1s → 10s`, multiplier `2.0` — consistent with existing retry config in `application.yml`.

**Rationale**: The project already declares Resilience4j for circuit breakers (visionAI, Redis, ClamAV) with a shared retry policy (3 attempts, 1s→10s, multiplier=2.0) in `application.yml`. The voice transcription retry must reuse that configuration rather than introduce a new one. Unit testing retry behaviour is not appropriate in a unit spec (it requires real timing or clock stubbing); the spec validates only that the correct exception type is thrown after exhausting retries, by stubbing the failure directly.

**Alternatives considered**:
- Manual retry loop in `WhisperService` → duplicates existing Resilience4j infrastructure.
- Spring `@Retryable` (spring-retry) → different library, project uses Resilience4j consistently.

---

## R-005 — WireMock Stubbing for Whisper API (Integration Path)

**Decision**: Stub the Whisper HTTP endpoint via WireMock (`POST /v1/audio/transcriptions`) in integration specs; no real API key used.

**Rationale**: The `OpenAiService` from `com.theokanning.openai` sends `POST https://api.openai.com/v1/audio/transcriptions` as a multipart request. WireMock (already in the project for `OpenAiStreamingClientSpec`) can intercept this by pointing `OpenAiService` at `http://localhost:{wiremock.port}`. The happy-path test stubs a `200 OK` response with body `{"text": "Bonjour monde"}`. Timeout and error tests stub `500` responses or use WireMock's `withFixedDelay`.

**Stub shape**:
```
POST /v1/audio/transcriptions
Content-Type: multipart/form-data
→ 200 { "text": "Bonjour monde" }
```

**Alternatives considered**:
- `@SpringBootTest` with real Whisper key → violates Constitution Principle V (no live endpoints).
- Reflectively replace `openAiService` field → fragile, depends on private field name.

---

## R-006 — Audio Privacy: Log Masking Strategy

**Decision**: Log only audio byte count and filename (no content); transcript text replaced with `[TRANSCRIPTION MASQUÉE — N chars]` placeholder in log output.

**Rationale**: Spec clarification Q5 established that audio and transcripts are sensitive. Looking at the existing `WhisperService` code, the debug log already prints transcript content: `log.debug("📝 [Whisper] Transcription brute : '{}'", transcript)`. This must be changed to `log.debug("📝 [Whisper] Transcription brute : [{} chars]", transcript.length())`. The success log `log.info("✅ [Whisper] Transcription réussie — {} caractères : '{}'`, ...)` must also be masked. In test specs, log masking is validated indirectly by asserting the service returns the correct transcript while the log output is never asserted on content.

**Alternatives considered**:
- Field-level `@JsonIgnore` → only relevant for serialization, not logging.
- MDC filtering → complex, cross-cutting; simple string replacement in the log call is sufficient.
