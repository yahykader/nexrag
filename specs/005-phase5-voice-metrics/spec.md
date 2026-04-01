# Feature Specification: Phase 5 — Voice Transcription & RAG Observability

**Feature Branch**: `005-phase5-voice-metrics`  
**Created**: 2026-04-01  
**Status**: Draft  
**Input**: User description: "create a specification for the PHASE 5 — Voice & Metrics"

## Clarifications

### Session 2026-04-01

- Q: When the transcription service is unreachable or times out, should the system retry automatically or fail immediately? → A: Retry with exponential backoff (3 attempts) before failing
- Q: What is the maximum allowed audio file size? → A: 25 MB (matches OpenAI Whisper upload limit)
- Q: Are transcription results persisted after the call, or is the service fully stateless? → A: Stateless — return transcript to caller, store nothing
- Q: Should voice transcription requests be rate-limited per user? → A: Yes — 10 requests/minute per user (consistent with upload endpoint)
- Q: Should audio content and transcripts be treated as sensitive/PII data? → A: Sensitive — transcripts masked in logs, raw audio bytes never logged

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Voice Input for RAG Queries (Priority: P1)

As an end user, I want to speak my question into a microphone and have the system convert my audio into text, so that I can use the RAG platform hands-free or when typing is inconvenient.

**Why this priority**: Voice transcription is the primary user-facing feature of this phase. Without it, users cannot interact with the RAG system via audio at all. It delivers standalone value as soon as a single audio file can be successfully converted to a query.

**Independent Test**: Can be fully tested by submitting a short audio recording and verifying that a readable text transcript is returned — independently of the rest of the RAG pipeline.

**Acceptance Scenarios**:

1. **Given** a user submits a valid audio recording (WAV, MP3, or WebM format), **When** the transcription service processes it, **Then** a non-empty text transcript is returned without error.
2. **Given** a user submits an empty audio file (0 bytes), **When** the transcription service attempts to process it, **Then** the request is rejected immediately with a clear error message indicating the audio is invalid.
3. **Given** a user submits a very short audio clip that contains only silence, **When** the transcription service processes it, **Then** the system logs a warning and returns an error indicating no speech was detected.
4. **Given** a user submits an audio file without a recognizable extension, **When** the system processes it, **Then** it defaults to a supported audio format without crashing.

---

### User Story 2 - RAG Pipeline Performance Observability (Priority: P2)

As a platform operator, I want real-time visibility into the performance of every stage of the RAG pipeline (ingestion, retrieval, generation, embedding, cache), so that I can detect degradations early and understand where bottlenecks occur.

**Why this priority**: Observability is foundational for operating the platform reliably in production. While it does not affect end-user features directly, it enables the ops team to prevent outages and optimize performance before users are impacted.

**Independent Test**: Can be fully tested by triggering pipeline operations (ingestion, queries, cache lookups) and verifying that the corresponding counters and timers are incremented correctly — without any dependency on the voice feature.

**Acceptance Scenarios**:

1. **Given** a document is successfully ingested, **When** the ingestion completes, **Then** the success counter for that document type is incremented and the total ingested file count increases by 1.
2. **Given** an ingestion fails, **When** the error is handled, **Then** the error counter is incremented with the failure type, while the success counter remains unchanged.
3. **Given** an embedding result is served from cache, **When** the cache lookup resolves, **Then** the cache hit counter is incremented and no new embedding call is recorded.
4. **Given** an embedding result is not in cache, **When** the system generates a new embedding, **Then** the cache miss counter and the embedding call counter are both incremented.
5. **Given** two parallel ingestion operations are active, **When** querying active ingestion count, **Then** the reported value is exactly 2; after both complete, the value returns to 0.
6. **Given** the LLM generates a response with 150 tokens, **When** generation completes, **Then** the total token counter increases by exactly 150.

---

### User Story 3 - Embedding Latency Tracking (Priority: P3)

As a platform operator, I want each external embedding API call to be individually timed and tracked, so that I can identify slow embedding requests and diagnose API degradation.

**Why this priority**: Embedding calls are a critical performance bottleneck. Tracking them individually allows root-cause analysis when overall query latency increases.

**Independent Test**: Can be tested by triggering embedding generation for a text snippet and verifying that a latency record and an API call counter exist for that operation — independently of ingestion or query pipelines.

**Acceptance Scenarios**:

1. **Given** a text is submitted for embedding generation, **When** the external call succeeds, **Then** a call duration record and a success counter are captured for that operation type.
2. **Given** a text is submitted for embedding generation, **When** the external call fails, **Then** an error counter is incremented for that operation type, no duration record is stored, and the error is propagated to the caller.

---

### Edge Cases

- What happens when an audio file is too large to process within the configured timeout?
- How does the system handle audio submitted in an unsupported codec?
- When the external transcription service is unavailable, the system retries up to 3 times with exponential backoff; if all attempts fail, an error is returned to the caller.
- How are metric counters handled if the monitoring backend is temporarily unreachable?
- The transcription service is stateless — each call is independent with no shared state, so concurrent submissions from different users are handled without conflict.
- How does the active-operations gauge behave if an ingestion crashes without a proper cleanup call?

## Requirements *(mandatory)*

### Functional Requirements

**Voice Transcription**

- **FR-001**: The system MUST reject audio input that is null, contains zero bytes, or exceeds 25 MB, returning a clear error message to the caller without making any external call.
- **FR-002**: The system MUST transcribe valid audio files and return a non-empty, trimmed text string.
- **FR-003**: The system MUST clean up any temporary files created during transcription, regardless of success or failure.
- **FR-004**: The system MUST report whether the transcription capability is currently available before accepting requests.
- **FR-005**: The system MUST resolve the audio file format from the original filename; when no extension is present, a default supported format MUST be assumed.
- **FR-006**: The system MUST propagate a descriptive error to the caller when the transcription service returns an empty or blank result.
- **FR-006b**: When the external transcription service is unreachable or times out, the system MUST retry the call up to 3 times with exponential backoff before propagating a failure to the caller.
- **FR-006c**: The voice transcription endpoint MUST enforce a rate limit of 10 requests per minute per user; requests exceeding this limit MUST be rejected with a clear rate-limit error before any external call is made.
- **FR-006d**: The system MUST treat audio content and transcripts as sensitive data — raw audio bytes MUST never appear in logs, and transcript text MUST be masked (e.g., truncated or replaced with a placeholder) in all application log output.

**RAG Observability**

- **FR-007**: The system MUST record a success counter and a duration measurement for every completed ingestion, tagged by document type.
- **FR-008**: The system MUST record an error counter tagged by document type and error category for every failed ingestion.
- **FR-009**: The system MUST maintain a real-time count of currently active ingestion operations and currently active query operations.
- **FR-010**: The system MUST record separate counters for cache hits and cache misses, tagged by cache scope.
- **FR-011**: The system MUST record a duration measurement and a success counter for each embedding generation call.
- **FR-012**: The system MUST record an error counter for each failed embedding generation call.
- **FR-013**: The system MUST accumulate the total number of tokens generated by the language model across all requests.
- **FR-014**: The system MUST record duration measurements for retrieval, reranking, query transformation, and full pipeline execution.

### Key Entities

- **AudioTranscription**: Represents a transcription request — input audio bytes (max 25 MB), original filename, language hint; output is the transcribed text string. Fully stateless: no result is stored after the call returns.
- **PipelineMetric**: A named measurement (counter, duration, or current value) associated with a specific stage and status tag in the RAG pipeline.
- **CacheEvent**: A cache interaction recording whether a lookup resulted in a hit or miss, tagged by the cache scope (e.g., embeddings).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Valid audio files of up to 10 minutes and up to 25 MB are transcribed and the result is returned to the caller within 30 seconds.
- **SC-002**: Invalid audio input (null, empty, exceeding 25 MB, or unsupported format) is rejected in under 50 milliseconds with a clear error message — no external call is made.
- **SC-003**: Temporary files created during transcription are always deleted after the call completes — 0 orphaned files after 1,000 consecutive transcription requests.
- **SC-004**: After any ingestion event, the corresponding counter is updated within 10 milliseconds — no metric is lost even under concurrent load.
- **SC-005**: The active-operations gauge reflects the correct count within 1 second of an operation starting or completing.
- **SC-006**: Cache hit and miss counters are always mutually exclusive — a single cache lookup increments exactly one of the two, never both.
- **SC-007**: The total tokens generated counter is consistent with the sum of all individual generation calls — zero discrepancy after 10,000 requests.
- **SC-008**: When a user exceeds 10 voice transcription requests per minute, subsequent requests are rejected in under 10 milliseconds — no external transcription call is made.

## Assumptions

- The external transcription service requires valid credentials configured at startup; the system does not manage credential rotation.
- Audio file formats supported are limited to those accepted by the external transcription service (WAV, MP3, MP4, WebM, and similar common formats).
- The timeout for transcription requests is configurable via application properties; the default is 30 seconds.
- Metric collection has negligible performance overhead (under 1 ms per record call) and does not require external network calls at record time.
- The monitoring backend (where metrics are scraped) may be temporarily unavailable without affecting the RAG pipeline — metric recording is fire-and-forget.
- Concurrent access to metric counters and gauges is thread-safe; no additional locking is required by callers.
- The transcription service is fully stateless — no transcript is stored or logged after the result is returned to the caller.
- Audio content and transcripts are treated as sensitive data: raw audio bytes must never appear in logs; transcript text must be masked in all log output (e.g., character count only, no content).
- Language hint for transcription is an ISO 639-1 code (e.g., "fr", "en"); if omitted, the transcription service auto-detects the language.
