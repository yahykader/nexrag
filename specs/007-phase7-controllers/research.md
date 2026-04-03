# Research: Phase 7 — Controllers Unit Tests (MockMvc)

**Branch**: `007-phase7-controllers` | **Date**: 2026-04-02

## Decision Log

### D-01 — @WebMvcTest slice vs. full @SpringBootTest

**Decision**: Use `@WebMvcTest(ControllerClass.class)` for all six controller test classes.

**Rationale**: `@WebMvcTest` starts only the web layer (controllers, filters, converters,
`@ControllerAdvice`) without the full application context. This keeps each test class within
the 500 ms budget (Constitution Principle I) and isolates the HTTP-routing concern from
service/repository layers (Constitution Principle II — SRP). A full `@SpringBootTest` would
auto-configure datasources, Redis, and other infra that are irrelevant to routing validation.

**Alternatives considered**:
- `@SpringBootTest(webEnvironment=MOCK)` — rejected; too heavy for routing-only tests.
- Plain unit tests (instantiate controller directly) — rejected; loses HTTP layer validation
  (status codes, Content-Type headers, request binding).

---

### D-02 — Spring Security filter handling

**Decision**: Spring Security is **not on the classpath** (confirmed via `pom.xml` — no
`spring-boot-starter-security` or `spring-security-test` dependency). No security
configuration is needed.

**Rationale**: No `@AutoConfigureMockMvc(addFilters=false)` or `@WithMockUser` annotation is
required because there are no security filters to disable. All `@WebMvcTest` slices run
without any authentication context.

**Alternatives considered**:
- Adding `@AutoConfigureMockMvc(addFilters=false)` as a precaution — acceptable if the
  dependency is ever added, but unnecessary now. Rate-limiting filters (`Bucket4j`) are
  present but handled by `@WebMvcTest`'s selective bean loading — the `RateLimitInterceptor`
  is NOT a filter and is not loaded in the `@WebMvcTest` slice unless explicitly imported.

---

### D-03 — @ControllerAdvice inclusion strategy

**Decision**: Import `@ControllerAdvice` beans **only** for the three controllers that have a
dedicated handler:

| Controller | Advice Bean to Import |
|---|---|
| `MultimodalIngestionController` | `IngestionExceptionHandler` |
| `MultimodalCrudController` | `CrudExceptionHandler` |
| `VoiceController` | `VoiceExceptionHandler` |

`StreamingAssistantController`, `MetricsController`, and `WebSocketStatsController` have no
custom advice bean and require no import.

**Rationale**: `@RestControllerAdvice` beans are NOT automatically loaded by `@WebMvcTest` —
they must be explicitly imported via `@Import(AdviceClass.class)` on the test class. Without
the import, facade exceptions propagate as 500 errors and the exception-mapping scenarios
(clarification Q1) cannot be validated.

**Exception-to-HTTP mapping confirmed from source**:

| Exception | Handler | HTTP Status | Response Type |
|---|---|---|---|
| `DuplicateFileException` | `IngestionExceptionHandler` | 409 CONFLICT | `IngestionResponse` |
| `IllegalArgumentException` | `IngestionExceptionHandler` | 400 BAD REQUEST | `IngestionResponse` |
| `ResourceNotFoundException` | `IngestionExceptionHandler` | 404 NOT FOUND | `IngestionResponse` |
| `Exception` (catch-all) | `IngestionExceptionHandler` | 500 INTERNAL | `IngestionResponse` |
| `ResourceNotFoundException` | `CrudExceptionHandler` | 404 NOT FOUND | `DeleteResponse` |
| `IllegalArgumentException` | `CrudExceptionHandler` | 400 BAD REQUEST | `DeleteResponse` |
| `Exception` (catch-all) | `CrudExceptionHandler` | 500 INTERNAL | `DeleteResponse` |
| `IllegalArgumentException` | `VoiceExceptionHandler` | 400 BAD REQUEST | `TranscriptionResponse` |
| `Exception` (catch-all) | `VoiceExceptionHandler` | 500 INTERNAL | `TranscriptionResponse` |

**Alternatives considered**:
- Include all three advice beans in every test class — rejected; loads irrelevant beans and
  risks advice-bean ordering conflicts.

---

### D-04 — MetricsController test setup

**Decision**: Use `@WebMvcTest(MetricsController.class)` with a `@TestConfiguration` inner
class (or standalone class) that creates and registers a `PrometheusMeterRegistry` pre-loaded
with the four required meters.

**Rationale**: `@WebMvcTest` does not auto-configure Micrometer or Prometheus. Without a
registry bean, the `MetricsController` constructor throws a `NoSuchBeanDefinitionException`
at context startup. Providing the registry via `@TestConfiguration` is the lightest approach
— no `@SpringBootTest` needed.

**Required meters to register in the test configuration**:

```
rag.query.duration   → Timer
rag.queries.total    → Counter
rag.queries.success  → Counter
rag.connections.active → Gauge (long)
```

**Test configuration pattern**:
```java
@TestConfiguration
static class TestMetricsConfig {
    @Bean
    PrometheusMeterRegistry prometheusMeterRegistry() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        registry.timer("rag.query.duration");
        registry.counter("rag.queries.total");
        registry.counter("rag.queries.success");
        registry.gauge("rag.connections.active", new AtomicLong(0), AtomicLong::get);
        return registry;
    }
}
```

**Alternatives considered**:
- `@SpringBootTest(webEnvironment=MOCK)` — rejected; too heavy; starts full application context.
- `SimpleMeterRegistry` — rejected; `MetricsController` calls `prometheusRegistry.scrape()`
  which is a `PrometheusMeterRegistry`-specific method; a generic registry would not compile.

---

### D-05 — StreamingRequest bean validation gap

**Decision**: Add `@NotBlank` to `StreamingRequest.query` as a **required production code
change** (Task T0) before implementing `StreamingAssistantControllerSpec` (Task T2).

**Rationale**: `StreamingRequest.query` currently has no validation annotation. Without
`@NotBlank`, Spring MVC will not reject empty queries at the binding layer — the facade would
be called with an empty string, which is NOT the intended behaviour documented in FR-006 and
the empty-query edge case. Task T0 is therefore a prerequisite for Task T2.

**Also required**: `@Validated` (or `@Valid`) on the `@RequestBody StreamingRequest request`
parameter in `StreamingAssistantController.streamPost()` to activate constraint checking.

**Alternatives considered**:
- Validate in the facade and throw `IllegalArgumentException` — rejected per clarification
  Q5 (answer A chose bean validation as the mechanism).
- Leave as-is and skip the empty-query test — rejected; FR-006 mandates this behaviour.

---

### D-06 — MultimodalCrudController package placement

**Decision**: `MultimodalCrudControllerSpec.java` is placed in
`src/test/java/com/exemple/nexrag/controller/` (not `service/rag/controller/`), mirroring
the production class package `com.exemple.nexrag.controller`.

**Rationale**: Constitution Principle III requires test package to mirror production package
exactly. `MultimodalCrudController` is declared in `com.exemple.nexrag.controller` per its
source file (`package com.exemple.nexrag.controller;`), unlike the other five controllers
which live in `com.exemple.nexrag.service.rag.controller`.

**Impact**: `MultimodalCrudControllerSpec` must be in its own package directory. The
`@WebMvcTest(MultimodalCrudController.class)` annotation handles component scanning
independently of the test package location.
