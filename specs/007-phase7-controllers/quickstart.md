# Quickstart: Phase 7 — Controllers Unit Tests (MockMvc)

**Branch**: `007-phase7-controllers` | **Date**: 2026-04-02

## Prerequisites

- Java 21 installed (`java -version`)
- Maven wrapper present (`nex-rag/mvnw`)
- Phase 6 (Facade) tests passing on `master`

## Before Writing Tests — Required Production Change (Task T0)

`StreamingRequest.query` needs `@NotBlank` before `StreamingAssistantControllerSpec` (Task T2)
can validate the empty-query edge case. Apply this change first:

**File**: `nex-rag/src/main/java/com/exemple/nexrag/service/rag/streaming/model/StreamingRequest.java`

1. Add import: `import jakarta.validation.constraints.NotBlank;`
2. Annotate the `query` field: `@NotBlank private String query;`
3. Add `@Valid` to `streamPost()` parameter in `StreamingAssistantController`:
   `public SseEmitter streamPost(@Valid @RequestBody StreamingRequest request)`
4. Add import to controller: `import jakarta.validation.Valid;`

Verify the change compiles before proceeding to Task T2:
```bash
cd nex-rag
./mvnw compile -q
```

## Running All Phase 7 Tests

```bash
cd nex-rag

# Run all Phase 7 controller specs
./mvnw test -Dtest="MultimodalIngestionControllerSpec,StreamingAssistantControllerSpec,MultimodalCrudControllerSpec,MetricsControllerSpec,VoiceControllerSpec,WebSocketStatsControllerSpec"

# Run a single spec class
./mvnw test -Dtest=MultimodalIngestionControllerSpec

# Run a single test method
./mvnw test -Dtest=MultimodalIngestionControllerSpec#shouldReturn202ForAsyncUpload
```

## Running with Coverage Report

```bash
cd nex-rag
./mvnw test jacoco:report -Dtest="MultimodalIngestionControllerSpec,StreamingAssistantControllerSpec,MultimodalCrudControllerSpec,MetricsControllerSpec,VoiceControllerSpec,WebSocketStatsControllerSpec"

# Coverage report location:
# nex-rag/target/site/jacoco/index.html
```

Open `target/site/jacoco/index.html` in a browser and verify:
- All six controller classes show ≥ 80% line and branch coverage
- `MultimodalIngestionController`: all 11 endpoint methods covered
- `StreamingAssistantController`: GET stream, POST stream, cancel, health, empty-query rejection covered

## Test Class Locations

| Spec Class | Location |
|---|---|
| `MultimodalIngestionControllerSpec` | `src/test/java/com/exemple/nexrag/service/rag/controller/` |
| `StreamingAssistantControllerSpec` | `src/test/java/com/exemple/nexrag/service/rag/controller/` |
| `MetricsControllerSpec` | `src/test/java/com/exemple/nexrag/service/rag/controller/` |
| `VoiceControllerSpec` | `src/test/java/com/exemple/nexrag/service/rag/controller/` |
| `WebSocketStatsControllerSpec` | `src/test/java/com/exemple/nexrag/service/rag/controller/` |
| `MultimodalCrudControllerSpec` | `src/test/java/com/exemple/nexrag/controller/` |

## Standard Test Class Structure

```java
@DisplayName("Spec : MultimodalIngestionController — API d'ingestion REST")
@WebMvcTest(MultimodalIngestionController.class)
@Import(IngestionExceptionHandler.class)            // only for Ingestion/Crud/Voice controllers
class MultimodalIngestionControllerSpec {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IngestionFacade ingestionFacade;        // the controller's single dependency

    @Autowired
    private ObjectMapper objectMapper;

    // US-1 / AC sync upload — success
    @Test
    @DisplayName("DOIT retourner 200 pour un upload synchrone valide")
    void shouldReturn200ForSyncUpload() throws Exception {
        when(ingestionFacade.uploadSync(any(), any()))
            .thenReturn(IngestionResponse.builder().success(true).build());

        mockMvc.perform(multipart("/api/v1/ingestion/upload")
                .file(new MockMultipartFile("file", "doc.pdf",
                                            "application/pdf", "data".getBytes())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    // Exception mapping via @ControllerAdvice
    @Test
    @DisplayName("DOIT retourner 409 quand une DuplicateFileException est levée")
    void shouldReturn409WhenDuplicateFileExceptionThrown() throws Exception {
        when(ingestionFacade.uploadSync(any(), any()))
            .thenThrow(new DuplicateFileException("batch-abc"));

        mockMvc.perform(multipart("/api/v1/ingestion/upload")
                .file(new MockMultipartFile("file", "dup.pdf",
                                            "application/pdf", "data".getBytes())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.duplicate").value(true));
    }
}
```

## MetricsController — @TestConfiguration Pattern

```java
@DisplayName("Spec : MetricsController — endpoints métriques et health")
@WebMvcTest(MetricsController.class)
@Import(MetricsControllerSpec.TestMetricsConfig.class)
class MetricsControllerSpec {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class TestMetricsConfig {
        @Bean
        PrometheusMeterRegistry prometheusMeterRegistry() {
            PrometheusMeterRegistry registry =
                new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
            registry.timer("rag.query.duration");
            registry.counter("rag.queries.total");
            registry.counter("rag.queries.success");
            AtomicLong activeConns = new AtomicLong(0);
            registry.gauge("rag.connections.active", activeConns, AtomicLong::get);
            return registry;
        }
    }

    @Test
    @DisplayName("DOIT retourner text/plain pour le scrape Prometheus")
    void shouldReturnPlainTextForPrometheus() throws Exception {
        mockMvc.perform(get("/api/actuator/prometheus"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
            .andExpect(content().string(not(emptyString())));
    }
}
```

## Commit Message Convention

```
test(phase-7): add MultimodalIngestionControllerSpec — routing et exception mapping ingestion
test(phase-7): add StreamingAssistantControllerSpec — SSE Content-Type et annulation flux
test(phase-7): add MultimodalCrudControllerSpec — CRUD embeddings et not-found advice
test(phase-7): add MetricsControllerSpec — scrape Prometheus et résumé métriques
test(phase-7): add VoiceControllerSpec — transcription audio et gestion erreurs voice
test(phase-7): add WebSocketStatsControllerSpec — statistiques sessions et cleanup
```

## Definition of Done for Phase 7

- [ ] Task T0: `@NotBlank` added to `StreamingRequest.query`; `@Valid` added to `streamPost()`
- [ ] Task T1: `MultimodalIngestionControllerSpec` — all 16 scenarios pass (13 happy + 3 exception)
- [ ] Task T2: `StreamingAssistantControllerSpec` — all 5 scenarios pass
- [ ] Task T3: `MultimodalCrudControllerSpec` — all 10 scenarios pass (8 happy + 2 exception)
- [ ] Task T4: `MetricsControllerSpec` — all 3 scenarios pass
- [ ] Task T5: `VoiceControllerSpec` — all 6 scenarios pass (4 happy + 2 exception)
- [ ] Task T6: `WebSocketStatsControllerSpec` — all 7 scenarios pass
- [ ] All 6 controller classes achieve ≥ 80% line + branch coverage
- [ ] All 47 `@Test` methods pass in under 500 ms each
- [ ] No `@MockBean` of a service or repository class — facade/manager only
