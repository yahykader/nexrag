# Quickstart: Phase 5 Tests — Voice & Metrics

**Feature**: `005-phase5-voice-metrics`  
**Date**: 2026-04-01

---

## Prerequisites

- Java 21 + Maven Wrapper (`./mvnw`)
- No running infrastructure needed for unit tests (all dependencies mocked)
- For integration tests: Docker (WireMock started programmatically)

---

## Running Phase 5 Unit Tests

```bash
# From /nex-rag/

# Run all Phase 5 specs
./mvnw test -Dtest="WhisperServiceSpec,AudioTempFileSpec,RAGMetricsSpec,OpenAiEmbeddingServiceSpec"

# Run a single spec
./mvnw test -Dtest=WhisperServiceSpec

# Run a single test method
./mvnw test -Dtest=WhisperServiceSpec#devraitLeverIllegalArgumentExceptionPourAudioVide
```

---

## Expected Test File Locations

```
nex-rag/src/test/java/com/exemple/nexrag/service/rag/
├── voice/
│   ├── WhisperServiceSpec.java       ← US-1: transcription validation paths
│   └── AudioTempFileSpec.java        ← US-1: temp file lifecycle
└── metrics/
    ├── RAGMetricsSpec.java            ← US-2: counters, gauges, timers
    └── embedding/
        └── OpenAiEmbeddingServiceSpec.java  ← US-3: embedding latency tracking
```

---

## Test Patterns Used

### WhisperServiceSpec — inject `@Value` field

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("Spec : WhisperService — Transcription audio via Whisper")
class WhisperServiceSpec {

    @Mock WhisperProperties props;
    @Mock AudioTempFile     audioTempFile;
    @InjectMocks WhisperService service;

    @BeforeEach
    void injectApiKey() {
        // @Value fields not injected by Mockito — use ReflectionTestUtils
        ReflectionTestUtils.setField(service, "apiKey", "test-key-fake");
        when(props.getMinAudioBytes()).thenReturn(1_000);
    }
}
```

### RAGMetricsSpec — SimpleMeterRegistry (no Spring context)

```java
@DisplayName("Spec : RAGMetrics — Métriques centralisées du pipeline RAG")
class RAGMetricsSpec {

    MeterRegistry registry;
    RAGMetrics    metrics;

    @BeforeEach
    void setup() {
        registry = new SimpleMeterRegistry();
        metrics  = new RAGMetrics(registry);
    }
}
```

### OpenAiEmbeddingServiceSpec — mock EmbeddingModel + RAGMetrics

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("Spec : OpenAiEmbeddingService — Latence embedding par opération")
class OpenAiEmbeddingServiceSpec {

    @Mock EmbeddingModel embeddingModel;
    @Mock RAGMetrics     ragMetrics;
    @InjectMocks OpenAiEmbeddingService service;
}
```

---

## Coverage Gate

Run the full suite with coverage report:

```bash
./mvnw test jacoco:report
# Report: target/site/jacoco/index.html
# Gate: ≥ 80% line + branch coverage in voice/ and metrics/ packages
```

---

## Commit Message Convention (Phase 5)

```
test(phase-5): add WhisperServiceSpec — validation audio et gestion erreurs
test(phase-5): add AudioTempFileSpec — cycle de vie fichier temporaire
test(phase-5): add RAGMetricsSpec — compteurs et jauges pipeline RAG
test(phase-5): add OpenAiEmbeddingServiceSpec — latence et erreurs embedding
```
