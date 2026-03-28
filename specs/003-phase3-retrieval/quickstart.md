# Quickstart: Phase 3 — Retrieval Pipeline Tests

**Branch**: `003-phase3-retrieval` | **Date**: 2026-03-28

## Prerequisites

- Java 21 JDK installed and active (`java --version` should show 21.x)
- Maven wrapper available at `nex-rag/mvnw`
- Phase 1 and Phase 2 tests already passing (`./mvnw test` green)

## Run the Phase 3 Tests

```bash
# From the nex-rag/ directory

# Run the entire Phase 3 suite
./mvnw test -Dtest="QueryTransformerServiceSpec,QueryRouterServiceSpec,TextVectorRetrieverSpec,BM25RetrieverSpec,ImageVectorRetrieverSpec,ParallelRetrieverServiceSpec,CrossEncoderRerankerSpec,ContentAggregatorServiceSpec,ContentInjectorServiceSpec,RetrievalAugmentorOrchestratorSpec"

# Run a single spec class
./mvnw test -Dtest=ParallelRetrieverServiceSpec

# Run a single test method
./mvnw test -Dtest=ParallelRetrieverServiceSpec#devraitRetournerResultatsPartielsSiUnRetrieverTimeout

# Check coverage report (generated in target/site/jacoco/)
./mvnw test jacoco:report
open target/site/jacoco/index.html
```

## Test Class Pattern

Every Phase 3 `*Spec.java` follows this skeleton:

```java
@DisplayName("Spec : <ClassName> — <Rôle en français>")
@ExtendWith(MockitoExtension.class)
class <ClassName>Spec {

    // Mocks for all constructor dependencies
    @Mock private SomeDependency dependency;

    // Subject under test
    @InjectMocks private <ClassName> service;

    // Test-specific RetrievalConfig (real instance, not mock)
    private RetrievalConfig config;

    @BeforeEach
    void setUp() {
        config = buildTestConfig(); // See helpers below
    }

    @Test
    @DisplayName("DOIT <action> quand <condition>")
    void doit<Action>Quand<Condition>() {
        // Given
        // When
        // Then — use AssertJ: assertThat(...).isEqualTo(...)
    }
}
```

## Building a `RetrievalConfig` for Tests

Instead of mocking `RetrievalConfig`, construct a real instance:

```java
private RetrievalConfig buildTestConfig() {
    RetrievalConfig config = new RetrievalConfig();

    RetrievalConfig.QueryTransformerConfig qt = new RetrievalConfig.QueryTransformerConfig();
    qt.setEnabled(true);
    qt.setMethod("rule-based");
    qt.setMaxVariants(5);
    qt.setEnableSynonyms(true);
    qt.setEnableTemporalContext(true);
    config.setQueryTransformer(qt);

    RetrievalConfig.QueryRouterConfig qr = new RetrievalConfig.QueryRouterConfig();
    qr.setEnabled(true);
    qr.setDefaultStrategy("HYBRID");
    qr.setConfidenceThreshold(0.7);
    config.setQueryRouter(qr);

    RetrievalConfig.RetrieversConfig retrievers = new RetrievalConfig.RetrieversConfig();
    retrievers.setParallelTimeout(200L); // Short for tests
    // ... set text/image/bm25 sub-configs with topK and thresholds
    config.setRetrievers(retrievers);

    RetrievalConfig.AggregatorConfig agg = new RetrievalConfig.AggregatorConfig();
    agg.setRrfK(60);
    agg.setMaxCandidates(30);
    agg.setFinalTopK(10);
    config.setAggregator(agg);

    return config;
}
```

## Simulating a Retriever Timeout

```java
// In ParallelRetrieverServiceSpec — AC-9.2
when(bm25Retriever.retrieveAsync(any(), anyInt()))
    .thenAnswer(inv -> CompletableFuture.supplyAsync(() -> {
        Thread.sleep(1000); // Exceeds 200ms timeout
        return buildEmptyResult("bm25");
    }));

var results = service.retrieveParallel(List.of("test"), routingDecision);

// Only text results returned; no exception
assertThat(results).containsKey("text").doesNotContainKey("bm25");
```

## Simulating an Empty Context (AC FR-015)

```java
// ContentAggregatorServiceSpec — zero results edge case
when(textRetriever.retrieveAsync(any(), anyInt()))
    .thenReturn(CompletableFuture.completedFuture(buildEmptyResult("text")));
when(bm25Retriever.retrieveAsync(any(), anyInt()))
    .thenReturn(CompletableFuture.completedFuture(buildEmptyResult("bm25")));

var results = service.retrieveParallel(List.of("test"), hybridDecision);
var aggregated = aggregator.aggregate(results, "test");

// Empty context — no exception raised
assertThat(aggregated.getChunks()).isEmpty();
assertThat(aggregated.getFinalSelected()).isEqualTo(0);
```

## Coverage Verification

```bash
# Verify coverage gate (must be ≥ 80%)
./mvnw test jacoco:report jacoco:check

# Check Phase 3 module specifically
./mvnw test -Dtest="*retrieval*" jacoco:report
```

## Commit Convention (Constitution §III)

```
test(phase-3): add QueryTransformerServiceSpec — query expansion et fallback rule-based
test(phase-3): add ParallelRetrieverServiceSpec — retrieval parallèle et timeout graceful
```
