# Feature Specification: Phase 6 — Facade Unit Tests

**Feature Branch**: `006-phase6-facade`  
**Created**: 2026-04-02  
**Status**: Draft  
**Input**: User description: "read nexrag-test-plan-speckit.md and create specification from PHASE 6 — Facade"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ingestion Facade Orchestration (Priority: P1)

As a controller, I want to delegate document ingestion to a single facade, so that I have a clean, simple interface without needing to know the details of validation, virus scanning, deduplication, and storage.

**Why this priority**: This is the critical entry point for all document ingestion. Without a working ingestion facade, no knowledge base content can be added to the system. It is the foundation for all downstream RAG capabilities.

**Independent Test**: Can be fully tested by submitting a file to the ingestion facade and verifying the returned status (`SUCCESS`, `DUPLICATE`, or `REJECTED`) without invoking any real infrastructure.

**Acceptance Scenarios**:

1. **Given** a valid, non-duplicate, virus-free document, **When** the ingestion facade receives it, **Then** it returns a `SUCCESS` result containing a batch identifier.
2. **Given** a document whose content has already been ingested, **When** the ingestion facade receives it, **Then** it returns `DUPLICATE` and does not re-trigger the ingestion pipeline.
3. **Given** a document that is flagged by the antivirus scanner, **When** the ingestion facade receives it, **Then** it returns `REJECTED` with the rejection reason, and the document is not stored.

---

### User Story 2 - CRUD Facade for Document Management (Priority: P2)

As a controller, I want to list and delete ingested documents through a single facade, so that the knowledge base can be maintained without coupling the controller to persistence or embedding store details.

**Why this priority**: Document management (listing and deletion) is essential for maintaining the quality and relevance of the knowledge base. It is the second most important user-facing operation after ingestion.

**Independent Test**: Can be fully tested by requesting a paginated document list and verifying page size, and by deleting a document and verifying that the associated embeddings are removed.

**Acceptance Scenarios**:

1. **Given** a page size of N, **When** the CRUD facade lists documents, **Then** it returns exactly N documents per page.
2. **Given** a valid document identifier, **When** the CRUD facade deletes it, **Then** the embedding deletion operation is called with the correct identifier.
3. **Given** an identifier that does not exist in the system, **When** the CRUD facade attempts to retrieve or delete it, **Then** it raises a document-not-found error.

---

### User Story 3 - Streaming Facade for Conversational Queries (Priority: P3)

As a controller, I want to delegate streaming RAG query execution to a facade, so that controllers remain lightweight and error handling is centralised.

**Why this priority**: Streaming is the user-facing query mechanism, but it depends on the ingestion pipeline being populated first. Centralised error handling in the facade reduces duplication across controllers.

**Independent Test**: Can be fully tested by submitting a RAG query to the streaming facade and verifying that a stream of events is returned for valid queries, and that errors are converted to structured error events instead of propagating as exceptions.

**Acceptance Scenarios**:

1. **Given** a valid RAG query, **When** the streaming facade processes it, **Then** it returns a non-empty stream of events.
2. **Given** an orchestration error occurs mid-stream, **When** the streaming facade handles it, **Then** it emits a structured `ERROR` event and the stream terminates gracefully without throwing an exception to the caller.

---

### User Story 4 - Voice Transcription Facade (Priority: P3)

As a controller, I want to delegate audio transcription to a voice facade, so that I can offer voice-driven RAG queries without coupling the controller to transcription service details or audio handling concerns.

**Why this priority**: Voice interaction is a direct user-facing capability that shares the same priority level as streaming. The facade insulates the controller from audio validation, temporary file management, and transcription service availability.

**Independent Test**: Can be fully tested by submitting valid audio bytes to the voice facade and verifying a non-empty transcription string is returned, and by verifying that service errors are converted to structured responses without propagating exceptions.

**Acceptance Scenarios**:

1. **Given** valid audio bytes and the transcription service is available, **When** the voice facade processes the audio, **Then** it returns a non-empty, trimmed transcription string.
2. **Given** null or zero-length audio bytes, **When** the voice facade receives the request, **Then** it returns a structured error response indicating invalid input — no exception propagates to the caller.
3. **Given** the transcription service is unavailable, **When** the voice facade processes the request, **Then** it returns a structured error response indicating service unavailability — no exception propagates to the caller.

---

### User Story 5 - Duplicate Check as a Shared Concern (Priority: P4)

As the ingestion and CRUD facades, I want a shared duplicate-checking component, so that duplication detection logic is not repeated across multiple facades.

**Why this priority**: The `DuplicateChecker` is a cross-cutting concern reused by both ingestion and CRUD operations. Testing it independently ensures the shared logic is correct before it is exercised through the higher-level facades.

**Independent Test**: Can be fully tested by presenting previously seen and unseen content identifiers and verifying the correct boolean response.

**Acceptance Scenarios**:

1. **Given** a content hash that was registered in a previous ingestion, **When** the duplicate checker is asked, **Then** it returns `true`.
2. **Given** a content hash that has never been registered, **When** the duplicate checker is asked, **Then** it returns `false`.

---

### Edge Cases

- Zero-byte files are rejected by `FileValidator` (Phase 1) before the facade pipeline starts. The ingestion facade spec verifies delegation to the validator but does not own the zero-byte check directly.
- When the antivirus service is temporarily unavailable, the ingestion facade returns `REJECTED` with reason `ANTIVIRUS_UNAVAILABLE`; no exception propagates to the caller.
- What happens when pagination is requested with a page number beyond the last page?
- When the streaming orchestrator returns an empty result set, the stream completes normally with zero content events. The facade emits no special event; an empty stream is a valid success.
- When the duplicate checker store is unreachable, the checker fails closed: it returns `true` (treat as duplicate) to block ingestion. This prevents unknown-state content from entering the pipeline.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The ingestion facade MUST orchestrate the following steps in order: file validation → antivirus scan → duplicate detection → ingestion strategy execution.
- **FR-002**: The ingestion facade MUST return an `IngestionResult` containing a status (`SUCCESS`, `DUPLICATE`, or `REJECTED`) and relevant metadata (e.g., batch identifier or rejection reason).
- **FR-003**: The ingestion facade MUST short-circuit and return `DUPLICATE` without invoking the ingestion orchestrator when the duplicate checker identifies a known document.
- **FR-004**: The ingestion facade MUST return `REJECTED` with the virus identification reason when the antivirus scanner flags a document, and MUST also return `REJECTED` with reason `ANTIVIRUS_UNAVAILABLE` when the antivirus service is unreachable — no exception propagates to the caller in either case.
- **FR-005**: The CRUD facade MUST support paginated listing of ingested documents, returning the requested number of items per page.
- **FR-006**: The CRUD facade MUST delete a document by identifier and trigger removal of all associated embedding vectors.
- **FR-007**: The CRUD facade MUST raise a document-not-found error when an operation targets a non-existent document identifier.
- **FR-008**: The streaming facade MUST accept a RAG query and delegate execution to the streaming orchestrator, returning a reactive stream of events. An empty stream (zero content events) is a valid successful completion — no special event is emitted for empty results.
- **FR-009**: The streaming facade MUST convert orchestration exceptions into structured `ERROR` stream events so that no unchecked exception propagates to the caller.
- **FR-010**: The duplicate checker MUST be usable independently as a shared component by any facade that requires pre-ingestion duplicate detection. When the underlying store is unreachable, the checker MUST fail closed by returning `true` (treat as duplicate) to prevent unknown-state content from entering the pipeline.
- **FR-011**: The voice facade MUST delegate audio transcription to the transcription service, returning a non-empty trimmed string for valid audio input.
- **FR-012**: The voice facade MUST return a structured error response (not throw an exception) when the audio input is null or zero-length.
- **FR-013**: The voice facade MUST return a structured error response (not throw an exception) when the transcription service is unavailable.

### Key Entities

- **IngestionResult**: Represents the outcome of an ingestion request, carrying a status code (`SUCCESS`, `DUPLICATE`, `REJECTED`) and contextual metadata (batch ID or rejection reason).
- **DocumentPage**: A paginated view of ingested documents, containing items for the current page and total count metadata.
- **StreamingEvent**: A single event in the conversational streaming response, typed as a content fragment, metadata update, or error signal.
- **DuplicateChecker**: A shared lookup component that answers whether a given content identifier has already been processed by the system.
- **VoiceTranscriptionResult**: Represents the outcome of a voice transcription request, carrying either a transcription string (success) or a structured error reason (invalid input, service unavailable).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All five facade test classes (`IngestionFacadeImplSpec`, `CrudFacadeImplSpec`, `StreamingFacadeImplSpec`, `VoiceFacadeImplSpec`, `DuplicateCheckerSpec`) pass with zero test failures.
- **SC-002**: The facade module achieves a minimum of 80% line and branch coverage as measured by the project's coverage tooling.
- **SC-003**: Each unit test runs in under 500 ms in isolation, with no real network or database calls.
- **SC-004**: All three ingestion result statuses (`SUCCESS`, `DUPLICATE`, `REJECTED`) are covered by at least one passing acceptance scenario each.
- **SC-005**: The streaming facade's error-handling path is verified by at least one test confirming that orchestration errors produce `ERROR` events and do not propagate as exceptions.
- **SC-006**: The CRUD facade's pagination and deletion paths are each covered by at least one passing acceptance scenario, including the not-found error case.
- **SC-007**: The voice facade's happy path and both error paths (invalid input, service unavailable) are each covered by at least one passing acceptance scenario in `VoiceFacadeImplSpec`.

## Clarifications

### Session 2026-04-02

- Q: When the antivirus service is temporarily unavailable, does the ingestion facade throw an exception, return REJECTED, or fail open? → A: Return `REJECTED` with reason `ANTIVIRUS_UNAVAILABLE`; no exception propagates to the caller.
- Q: Does VoiceFacadeImpl need its own user story with dedicated acceptance scenarios, or does it share the streaming spec? → A: VoiceFacadeImpl gets a dedicated user story (US-4) with three scenarios: valid audio happy path, invalid/empty audio error, and transcription service unavailability error.
- Q: Who owns zero-byte file rejection — the ingestion facade or FileValidator (Phase 1)? → A: `FileValidator` (Phase 1) owns zero-byte rejection; the facade spec only verifies it correctly delegates to the validator.
- Q: When the streaming orchestrator returns an empty result set, does the facade emit a special event or complete normally? → A: Stream completes normally with zero content events; an empty stream is a valid success.
- Q: When the duplicate checker store is unreachable, does it fail open, fail closed, or propagate an exception? → A: Fail closed — return `true` (treat as duplicate) to block ingestion; no exception propagates.

## Assumptions

- The facades are pure service-layer orchestrators with no direct HTTP or WebSocket concerns — those belong to Phase 7 (Controllers).
- All dependencies of the facades (validators, antivirus guard, deduplication service, orchestrators, embedding store deleter) are mocked in unit tests using Mockito; no real infrastructure is needed.
- `VoiceFacadeImpl` delegates to `WhisperService` (introduced in Phase 5). It has its own dedicated user story (US-4) with three acceptance scenarios: valid audio happy path, invalid/empty audio error, and service unavailability error.
- The `DuplicateChecker` delegates to the same `DeduplicationService` established in Phase 1; its unit tests mock that dependency.
- Tests are authored following the project naming convention: `[ClassUnderTest]Spec.java`, located under `src/test/java/com/exemple/nexrag/service/rag/facade/`.
- The reactive streaming type used by `StreamingFacadeImpl` is Project Reactor's `Flux`; tests will use reactor-test utilities (`StepVerifier`) where needed.
