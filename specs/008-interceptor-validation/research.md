# Research: PHASE 8 — Interceptor & Validation

**Branch**: `008-interceptor-validation` | **Date**: 2026-04-04

---

## Decision 1: Mocking the Bucket4j `ProxyManager<String>` chain

**Decision**: Mock `ProxyManager<String>`, `RemoteBucketBuilder<String>`, `Bucket`, and `ConsumptionProbe` as four independent Mockito `@Mock` fields, chained via `when(...).thenReturn(...)` in `@BeforeEach`.

**Rationale**: `RateLimitService.check()` calls `proxyManager.builder().build(key, config)` which returns a `Bucket`, then calls `bucket.tryConsumeAndReturnRemaining(1)` which returns a `ConsumptionProbe`. The return type of `proxyManager.builder()` is `io.github.bucket4j.distributed.proxy.RemoteBucketBuilder<K>` — **not** an inner `Builder` interface. Using `@Mock RemoteBucketBuilder<String>` with a suppressed unchecked warning is the correct approach.

**Mock setup pattern** (in `@BeforeEach`):
```java
when(proxyManager.builder()).thenReturn(remoteBucketBuilder);
when(remoteBucketBuilder.build(anyString(), any())).thenReturn(bucket);
when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
```

**Alternatives considered**:
- Real Bucket4j in-memory bucket: couples the test to Bucket4j internals; makes TTL/refill timing non-deterministic.
- `@SpringBootTest` with embedded Redis: violates Principle I (unit tests must not call real infrastructure); belongs in Phase 9.

---

## Decision 2: Testing `HandlerInterceptor.preHandle()` without a Spring context

**Decision**: Instantiate `RateLimitInterceptor` via `@InjectMocks` with `@ExtendWith(MockitoExtension.class)`. Use `MockHttpServletRequest` / `MockHttpServletResponse` from `spring-test` directly — no `MockMvc`, no `@WebMvcTest`, no `@SpringBootTest`.

**Rationale**: `preHandle(request, response, handler)` is a plain method call on a POJO. It does not require the Spring DispatcherServlet. `MockHttpServletRequest` lets us set method, URI, and headers; `MockHttpServletResponse` lets us assert status and response headers. This keeps the test in < 500 ms (constitution Principle I).

**`ObjectMapper` handling**: Mock `ObjectMapper` and stub `writeValueAsString(any())` to return a fixed JSON string. The test scope for the interceptor is HTTP status codes and response headers — not JSON serialization, which has its own unit. This avoids the only `throws` clause in `preHandle` caused by `ObjectMapper`.

**handler argument**: Pass `new Object()` — `RateLimitInterceptor.preHandle` never uses the handler parameter.

**Alternatives considered**:
- `@WebMvcTest` slice: registers the full web layer; overkill for a single method; adds 2–3 seconds startup time; unnecessary for this class.

---

## Decision 3: Magic bytes in `FileSignatureValidator` tests

**Decision**: Use `new MockMultipartFile("file", "name.ext", "mime", new byte[]{...})` with explicit byte literals. For oversized-signature scenarios, override `getBytes()` in an anonymous subclass.

**Rationale**: `FileSignatureValidator.validateSignature()` calls `file.getBytes()` and reads the raw byte array. `MockMultipartFile` stores bytes in memory and returns them faithfully from `getBytes()` — no filesystem required.

**Key byte sequences** used in tests (from production `FILE_SIGNATURES` map):

| Format | Magic bytes (hex) | Literal |
|--------|-------------------|---------|
| PDF | `25 50 44 46` | `{0x25, 0x50, 0x44, 0x46, 0x2D}` |
| PNG | `89 50 4E 47 0D 0A 1A 0A` | as above |
| ZIP / DOCX / XLSX / PPTX | `50 4B 03 04` | `{0x50, 0x4B, 0x03, 0x04, 0x14, 0x00}` |
| EXE (MZ) | `4D 5A` | `{0x4D, 0x5A, 0x00, 0x01}` |
| OLE (DOC/XLS) | `D0 CF 11 E0` | `{(byte)0xD0, (byte)0xCF, 0x11, (byte)0xE0}` |

**Short-file scenario**: provide `new byte[]{0x25}` (1 byte) declared as `.pdf` — triggers the "file too short" branch (< 4 expected bytes for PDF).

**Alternatives considered**:
- Real files on disk: violates Principle I (no filesystem I/O in unit tests).
- Reflection to access `FILE_SIGNATURES` map: fragile; couples test to private state.

---

## Decision 4: Log output assertion strategy

**Decision**: Do **not** assert on log output in Phase 8 unit tests. Verify observable behavior (HTTP status, return values, exception messages) only.

**Rationale**: FR-007b requires WARN-level log entries, but the constitution (Principle IV) specifies coverage targets in terms of branch and line coverage, not log output. Capturing Logback `ListAppender` entries adds test fragility (depends on logger name, log level, and Logback configuration) with low regression-detection value compared to asserting the actual rejection behavior. If log assertions are later mandated, they belong in a dedicated observability phase.

**What IS asserted in lieu of log output**:
- `preHandle()` return value (`true`/`false`)
- `MockHttpServletResponse.getStatus()` (200/429)
- Response headers (`X-RateLimit-Remaining`, `Retry-After`, `X-RateLimit-Reset`)
- Mockito `verify()` calls on collaborators
- Thrown exception type and message for validators

**Alternatives considered**:
- `@ExtendWith` + Logback `ListAppender`: feasible but adds ~10 lines of boilerplate per class for low-value assertions.
- SLF4J test helpers (`slf4j-test`): requires an extra test dependency not in the project's BOM.

---

## Decision 5: `FileValidator` — no new test file needed

**Decision**: `FileValidatorSpec.java` already exists and covers all ACs in US-22 for `FileValidator`. It must **not** be modified or re-created. The only validation classes requiring new test files are `FileSignatureValidator` and `AudioFileValidator`.

**Rationale**: Constitution Principle II (SRP) — one `*Spec.java` per production class. `FileValidatorSpec` maps 1:1 to `FileValidator`. Creating a second spec or merging test logic would violate SRP and create duplicate coverage.

**Confirmed coverage in existing `FileValidatorSpec`**:
- null file → `IllegalArgumentException("Fichier vide ou absent")`
- empty file (0 bytes) → same
- blank filename → `IllegalArgumentException("Nom de fichier absent")`
- size = `MAX_FILE_SIZE_BYTES` → passes (boundary)
- size = `MAX_FILE_SIZE_BYTES + 1` → `IllegalArgumentException` with size in MB
- `.exe` extension → `IllegalArgumentException` mentioning type
- valid batch → passes
- null/empty batch → `IllegalArgumentException("Aucun fichier fourni")`
- batch with oversized file → propagates `IllegalArgumentException`
