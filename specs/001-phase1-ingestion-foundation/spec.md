# Feature Specification: Phase 1 — Ingestion Foundation Tests
# (Utilitaires, Sécurité & Déduplication)

**Feature Branch**: `001-phase1-ingestion-foundation`
**Created**: 2026-03-26
**Status**: Draft
**Source**: `nexrag-test-plan-speckit.md` — Phase 1
**Test type**: Unit tests — JUnit 5 · Mockito · AssertJ · WireMock
**Target package**: `src/test/java/com/exemple/nexrag/service/rag/ingestion/`

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — File Validation & Type Detection (Priority: P1)

The ingestion system must reject invalid files before any processing occurs.
This covers three independent safety checks: MIME-type detection by magic bytes,
file-size enforcement, and allowlist-based extension filtering. A fourth responsibility,
metadata sanitisation, removes sensitive EXIF data from accepted files.

**Why this priority**: First line of defence — if invalid or oversized files slip through,
every downstream component (antivirus, strategy, embedding) is exposed to unsafe input.

**Independent Test**: Can be fully tested by calling `FileValidator.validate()` and
`FileTypeDetector.detect()` in isolation with mock inputs and stubbed configuration.
Delivers complete validation coverage without any other component.

**Acceptance Scenarios**:

1. **Given** a file whose bytes are `text/plain` but whose filename ends in `.pdf`,
   **When** `FileTypeDetector.detect()` is called,
   **Then** the returned MIME type is `text/plain` (magic bytes win over extension).

2. **Given** the maximum allowed size is 1 024 bytes,
   **When** `FileValidator.validate()` is called with a file of 2 048 bytes,
   **Then** a `FileSizeExceededException` is thrown containing the actual file size.

3. **Given** the allowed extension list is `[pdf, docx, txt]`,
   **When** `FileValidator.validate()` is called with a file named `malware.exe`,
   **Then** an `InvalidFileTypeException` is thrown.

4. **Given** a JPEG image containing GPS EXIF metadata,
   **When** `MetadataSanitizer.sanitize()` is called,
   **Then** the returned bytes contain no GPS EXIF fields.

---

### User Story 2 — File Deduplication (Priority: P1)

The ingestion system must detect and reject binary-identical files to avoid indexing
the same content twice in the vector store. Detection is based on a SHA-256 hash of the
file's raw bytes, persisted in Redis.

**Why this priority**: Duplicate ingestion inflates the vector store and produces
redundant embeddings that degrade retrieval precision and waste API credits.

**Independent Test**: `HashComputer`, `DeduplicationService`, and `DeduplicationStore`
can each be tested in isolation using Mockito to stub Redis interactions, delivering
idempotent deduplication verification without a live Redis instance.

**Acceptance Scenarios**:

1. **Given** two files with identical byte content,
   **When** `HashComputer.compute()` is called on each,
   **Then** both produce the same SHA-256 hex string.

2. **Given** a hash already present in the deduplication store,
   **When** `DeduplicationService.isDuplicate()` is called with the same hash,
   **Then** the method returns `true`.

3. **Given** `DeduplicationService.isDuplicate()` is called multiple times with the same hash,
   **When** each call completes,
   **Then** the store is queried each time with no side effects (no extra writes, no exceptions).

4. **Given** a new file hash that is not yet present in the store,
   **When** `DeduplicationStore.save(hash)` is called,
   **Then** the Redis backend receives exactly one write command with that hash
   (verified via Mockito interaction assertion).

---

### User Story 3 — Text Chunk Deduplication (Priority: P2)

The ingestion system must detect duplicate text chunks within a processing session
to prevent the embedding service from being called with the same text twice.
A fast in-memory local cache covers the session; Redis is the durable fallback.

**Why this priority**: Redundant embedding calls cost money and slow ingestion.
Session-level deduplication eliminates the most common duplicates with near-zero latency.

**Independent Test**: `TextNormalizer`, `TextDeduplicationService`, and `TextLocalCache`
can be tested without Redis by stubbing the remote store, delivering session deduplication
coverage independently of any infrastructure.

**Acceptance Scenarios**:

1. **Given** the inputs `"Hello World"` and `"  hello world  "`,
   **When** `TextNormalizer.normalize()` is called on each,
   **Then** both return the same normalised string (lowercase, trimmed, no diacritics).

2. **Given** a chunk already registered in the current session,
   **When** `TextDeduplicationService.isDuplicate()` is called with the same chunk,
   **Then** the method returns `true` without throwing any exception.

3. **Given** `TextLocalCache` contains several entries,
   **When** `TextLocalCache.clear()` is called,
   **Then** the cache is empty and subsequent lookups return `false`,
   regardless of any Redis state.

---

### User Story 4 — Antivirus Security Guard (Priority: P1)

The ingestion system must scan every uploaded file with ClamAV before processing begins.
Any file triggering an INFECTED response must be rejected immediately.
If ClamAV is unreachable, ingestion must be blocked (fail-secure).

**Why this priority**: Security-critical path — a virus that bypasses the guard can be
processed, chunked, embedded, and stored, then potentially extracted later via retrieval.

**Independent Test**: `ClamAvSocketClient`, `ClamAvResponseParser`, and `AntivirusGuard`
can be tested with socket stubs and response string fixtures; `ClamAvHealthScheduler`
with a mocked client and a stubbed scheduler clock.

**Acceptance Scenarios**:

1. **Given** ClamAV returns `stream: Eicar-Test-Signature FOUND`,
   **When** `AntivirusGuard.assertClean()` is called,
   **Then** a `VirusFoundException` is thrown containing the threat name.

2. **Given** ClamAV returns `stream: OK`,
   **When** `AntivirusGuard.assertClean()` is called,
   **Then** no exception is thrown and execution continues normally.

3. **Given** the ClamAV socket is unreachable (connection refused),
   **When** `AntivirusGuard.assertClean()` is called,
   **Then** an `AntivirusUnavailableException` is thrown (fail-secure behaviour).

4. **Given** `ClamAvResponseParser.parse()` receives `"stream: OK"`,
   **When** parsing completes,
   **Then** the returned status is `CLEAN`.

5. **Given** `ClamAvResponseParser.parse()` receives `"stream: Eicar FOUND"`,
   **When** parsing completes,
   **Then** the returned status is `INFECTED` with the threat name extracted.

6. **Given** the ClamAV socket connects but no response arrives within the configured timeout,
   **When** `AntivirusGuard.assertClean()` is called,
   **Then** an `AntivirusUnavailableException` is thrown (fail-secure, same as connection refused).

---

### Edge Cases

- What happens when `FileValidator` receives a `null` or zero-byte file?
  → Must throw `InvalidFileTypeException` immediately; no `NullPointerException` allowed.
- What happens when `HashComputer` is called on an empty file (0 bytes)?
  → Must return the deterministic SHA-256 digest of an empty byte array.
- What happens when `DeduplicationStore` cannot reach Redis?
  → Must propagate a typed exception; must not swallow it silently.
- What happens when `TextNormalizer` receives a string of only whitespace?
  → Must return an empty string, not throw.
- What happens when `ClamAvResponseParser` receives an unrecognised response?
  → Must return status `ERROR` and include the raw response in the result.
- What happens when `ClamAvHealthScheduler` detects ClamAV is down during a health check?
  → Must log a warning and update health status; must not throw or crash the scheduler.
- What happens when the ClamAV socket connects but never responds (scan stall)?
  → `AntivirusGuard` MUST throw `AntivirusUnavailableException` after the configurable
  socket timeout elapses (fail-secure). Unit tests stub the timeout via `ClamAvProperties`.

---

## Clarifications

### Session 2026-03-26

- Q: How should `ClamAvSocketClient` be exercised in unit tests given it uses a raw TCP socket (INSTREAM), not HTTP? → A: Option A — `ClamAvSocketClient` is declared as an interface; `AntivirusGuardSpec` injects it via Mockito. `ClamAvSocketClientSpec` uses a plain Java `ServerSocket` stub (no external framework). WireMock remains in `pom.xml` for HTTP-facing phases only.
- Q: Should Phase 1 tests verify the hash write path (`save()`), or defer it to Phase 2? → A: Option A — Phase 1 tests both read AND write. `DeduplicationStoreSpec` verifies both `exists(hash)` and `save(hash)` with Mockito-stubbed Redis; `DeduplicationServiceSpec` verifies the full read+write flow.
- Q: What is the lifetime of a `TextLocalCache` instance (session boundary)? → A: Option A — One instance per ingestion batch. Tests instantiate a fresh `TextLocalCache` via `new TextLocalCache()` inside `@BeforeEach`; no shared state between test methods.
- Q: When ClamAV connects but stalls with no response, what should `AntivirusGuard` do? → A: Option A — Throw `AntivirusUnavailableException` after a configurable socket timeout (fail-secure). Timeout value comes from `ClamAvProperties`; unit tests stub it to a small value (e.g., 100 ms).
- Q: Does `MetadataSanitizer` handle only images or also PDF/DOCX document metadata? → A: Option A — Images only (JPEG/PNG EXIF) in Phase 1. PDF/DOCX metadata sanitisation is out of scope here; it is the responsibility of the respective ingestion strategies in Phase 2.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-1.1**: The file type detector MUST identify the real MIME type by inspecting
  magic bytes, independent of the filename extension or declared content type.
- **FR-1.2**: The file validator MUST reject files exceeding the configured maximum size,
  throwing an exception that includes the actual byte count in its message.
- **FR-1.3**: The file validator MUST reject files whose extension is absent from the
  configured allowlist, throwing a typed exception.
- **FR-1.4**: The metadata sanitiser MUST strip sensitive EXIF fields (GPS location,
  device identifiers) from image files (JPEG/PNG) before they enter the processing pipeline.
  PDF and DOCX document metadata (author, company, revision history) are explicitly out of
  scope for Phase 1; those are handled by their respective ingestion strategies in Phase 2.
- **FR-2.1**: The hash computer MUST produce a deterministic SHA-256 hex digest;
  identical byte inputs MUST always yield identical outputs.
- **FR-2.2**: The file deduplication service MUST signal a duplicate when the computed
  hash matches an entry already present in the store.
- **FR-2.3**: The deduplication store MUST expose both a read operation (`exists(hash)`)
  and a write operation (`save(hash)`); both MUST be tested in Phase 1.
  Read operations MUST be idempotent; `save()` MUST be verifiable via Mockito interaction
  assertion on the Redis stub.
- **FR-3.1**: The text normaliser MUST convert input to lowercase, trim surrounding
  whitespace, and remove diacritical marks to produce a canonical form.
- **FR-3.2**: The text deduplication service MUST report a duplicate for any chunk
  already seen in the current session, without raising an error.
- **FR-3.3**: The local text cache MUST be clearable independently of the Redis store;
  clearing it MUST NOT alter any Redis-stored entries.
- **FR-4.1**: The ClamAV socket client MUST be declared as an interface; its concrete
  implementation transmits file bytes to the daemon using the INSTREAM protocol.
  `AntivirusGuard` MUST depend on the interface (DIP); unit tests for the guard mock the
  interface via Mockito. `ClamAvSocketClientSpec` uses a plain Java `ServerSocket` stub.
- **FR-4.2**: The ClamAV response parser MUST classify responses as `CLEAN`, `INFECTED`,
  or `ERROR` based on the daemon reply string.
- **FR-4.3**: The antivirus guard MUST block ingestion when the result is `INFECTED`,
  when the daemon is unreachable (connection refused), or when the scan stalls beyond a
  configurable socket timeout — all three cases throw `AntivirusUnavailableException`
  (fail-secure). The timeout is read from `ClamAvProperties` and stubbed in unit tests.
- **FR-4.4**: The ClamAV health scheduler MUST periodically verify daemon availability
  and update an observable health status without throwing on connection failure.

### Key Entities

- **FileValidationProperties**: Configuration holding `maxSizeBytes` and `allowedExtensions`;
  provided as a Mockito stub in unit tests.
- **HashComputer**: Pure, stateless function — takes `byte[]`, returns SHA-256 hex `String`.
- **ClamAvScanResult**: Value object encapsulating scan `status` (`CLEAN / INFECTED / ERROR`)
  and an optional `threatName` string.
- **DeduplicationStore**: Redis-backed store for SHA-256 hashes of ingested files;
  interacted with only through its interface in unit tests.
- **TextLocalCache**: Batch-scoped in-memory cache of normalised text chunk hashes —
  one instance per ingestion batch. Tests instantiate it fresh via `new TextLocalCache()`
  inside `@BeforeEach`; no shared instance between test methods.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 14 unit test classes listed in Phase 1 of the test plan are implemented
  and pass, with no modifications to existing production classes beyond those required for
  testability (e.g., extracting an interface).
- **SC-002**: Every Acceptance Criterion (AC-1.1 through AC-4.3) from
  `nexrag-test-plan-speckit.md` maps to at least one `@Test` method carrying a
  `@DisplayName` in French imperative form (`"DOIT … quand …"`).
- **SC-003**: Line and branch coverage for `ingestion/util`, `ingestion/security`, and
  `ingestion/deduplication` packages reaches ≥ 80 % as measured at build time.
- **SC-004**: The safety-critical classes — `AntivirusGuard`, `HashComputer`, and
  `DeduplicationService` — each reach 100 % branch coverage.
- **SC-005**: Every unit test completes in under 500 ms when run individually;
  no test makes a real network call or opens a real socket.
- **SC-006**: Each test class runs and passes independently when executed alone,
  confirming full test isolation.

---

## Assumptions

- Production classes (`FileValidator`, `AntivirusGuard`, `HashComputer`, etc.) already exist
  or will be created inside `com.exemple.nexrag.service.rag.ingestion` following the
  package structure defined in `CLAUDE.md`.
- All production classes under test use constructor injection for their dependencies,
  enabling `@InjectMocks` / `@Mock` without field-reflection workarounds.
- `FileValidationProperties` is a Spring configuration record whose values are provided
  through Mockito stubs in unit tests; no Spring context is loaded.
- ClamAV socket communication is fully encapsulated in `ClamAvSocketClient`; neither
  `AntivirusGuard` nor `ClamAvResponseParser` open sockets directly.
- `TextLocalCache` is batch-scoped (one instance per ingestion batch, not a Spring singleton).
  Tests create a fresh instance via `new TextLocalCache()` in `@BeforeEach`.
- PDF and DOCX document metadata sanitisation is out of scope for Phase 1; it belongs
  to `PdfIngestionStrategy` and `DocxIngestionStrategy` tested in Phase 2.
- WireMock (`wiremock-jre8`) is declared as a `<scope>test</scope>` dependency in
  `pom.xml` but is NOT used in Phase 1 unit tests. ClamAV socket tests use a plain Java
  `ServerSocket` stub. WireMock is reserved for HTTP-facing phases (Phase 7 controllers,
  Phase 9 end-to-end integration).
