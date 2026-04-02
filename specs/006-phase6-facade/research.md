# Research: Phase 6 — Facade Unit Tests

**Branch**: `006-phase6-facade` | **Date**: 2026-04-02

---

## Decision 1: Reactive Stream Testing (StreamingFacadeImpl)

**Decision**: Use `reactor-test` (`StepVerifier`) to assert `Flux<StreamingEvent>` behaviour.

**Rationale**: `StepVerifier` is the standard Project Reactor testing utility and is already
on the classpath via `spring-boot-starter-webflux`. It supports asserting on emitted items,
completion, and error signals in a synchronous, blocking manner — compatible with the
Constitution's 500 ms unit-test budget.

**Pattern**:
```java
// Happy path — non-empty stream
when(streamingOrchestrator.stream(any())).thenReturn(Flux.just(contentEvent));
StepVerifier.create(facade.stream(query))
    .expectNextCount(1)
    .verifyComplete();

// Error → ERROR event, no exception propagated
when(streamingOrchestrator.stream(any())).thenReturn(Flux.error(new RuntimeException("boom")));
StepVerifier.create(facade.stream(query))
    .expectNextMatches(e -> e.type() == EventType.ERROR)
    .verifyComplete();

// Empty result — valid success
when(streamingOrchestrator.stream(any())).thenReturn(Flux.empty());
StepVerifier.create(facade.stream(query))
    .verifyComplete();  // completes with 0 items — no error
```

**Alternatives considered**:
- `TestSubscriber` (raw Reactor): more verbose, lower-level — rejected.
- Blocking `.collectList().block()`: works for small streams but hides backpressure semantics — rejected.

---

## Decision 2: Mocking Flux-Returning Methods with Mockito

**Decision**: Use standard `when(mock.method(any())).thenReturn(Flux.just(...))` — no special setup needed.

**Rationale**: Mockito stubs return values, not subscribers. `Flux.just(...)`, `Flux.empty()`,
and `Flux.error(...)` are cold publishers; they are safe to return from stubs and will behave
correctly each time `StepVerifier` subscribes.

**Key rule**: Never use `Flux.create()` or hot publishers in mock returns — they introduce
thread-safety issues in test teardown. Stick to `Flux.just()`, `Flux.empty()`, `Flux.error()`.

---

## Decision 3: Fail-Closed Pattern for DuplicateChecker

**Decision**: When the underlying `DeduplicationService` throws a `DeduplicationStoreException`
(or any `RuntimeException` representing store unreachability), `DuplicateChecker.isDuplicate()`
catches it and returns `true` (treat as duplicate).

**Rationale**: Failing closed (return `true`) prevents unknown-state content from entering the
embedding pipeline, which would create duplicate vectors and degrade RAG retrieval quality.
This matches the Constitution Principle IV requirement for 100 % branch coverage on
safety-critical deduplication paths.

**Test pattern**:
```java
when(deduplicationService.isDuplicate(any())).thenThrow(new DeduplicationStoreException("Redis down"));

boolean result = checker.isDuplicate(hash);

assertThat(result).isTrue();  // fail closed
```

**Alternatives considered**:
- Fail open (return `false`): rejected — risks duplicate embeddings.
- Propagate exception to caller: rejected — caller (IngestionFacadeImpl) would need to handle it separately; violates single-responsibility for the checker.

---

## Decision 4: VoiceFacadeImpl — WhisperService Integration

**Decision**: `VoiceFacadeImpl` wraps `WhisperService.transcribeAudio()` (Phase 5). It returns
a `VoiceTranscriptionResult` carrying either the transcription string or a structured error
reason. The facade catches `IllegalArgumentException` (empty/null audio) and
`WhisperUnavailableException` (service down) and converts them to result objects.

**Rationale**: Phase 5 established that `WhisperService` throws `IllegalArgumentException` for
empty audio and returns `false` from `isAvailable()` when the API key is blank. The facade
must translate these into `VoiceTranscriptionResult` without propagating exceptions — consistent
with the error-handling contract established for `IngestionFacadeImpl` and `StreamingFacadeImpl`.

**Test mock setup**:
```java
// Happy path
when(whisperService.transcribeAudio(validBytes)).thenReturn("transcription text");

// Invalid input — WhisperService throws IAE
when(whisperService.transcribeAudio(new byte[0])).thenThrow(new IllegalArgumentException("empty audio"));

// Service unavailable — WhisperService throws or isAvailable() returns false
when(whisperService.isAvailable()).thenReturn(false);
```

---

## Decision 5: IngestionFacadeImpl — Antivirus Unavailability

**Decision**: When `AntivirusGuard` throws `AntivirusUnavailableException`, `IngestionFacadeImpl`
catches it and returns `IngestionResult` with status `REJECTED` and reason `ANTIVIRUS_UNAVAILABLE`.

**Rationale**: Confirmed in clarification session 2026-04-02 (Q1). Consistent with the
fail-safe principle — do not ingest when the security gate is broken.

**Test pattern**:
```java
when(antivirusGuard.scan(any())).thenThrow(new AntivirusUnavailableException("ClamAV down"));

IngestionResult result = facade.ingest(file, metadata);

assertThat(result.getStatus()).isEqualTo(IngestionStatus.REJECTED);
assertThat(result.getReason()).isEqualTo("ANTIVIRUS_UNAVAILABLE");
verifyNoInteractions(ingestionOrchestrator);
```
