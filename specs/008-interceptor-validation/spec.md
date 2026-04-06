# Feature Specification: PHASE 8 — Interceptor & Validation

**Feature Branch**: `008-interceptor-validation`
**Created**: 2026-04-04
**Status**: Draft
**Input**: User description: "read nexrag-test-plan-speckit.md and create a specification for the PHASE 8 — Interceptor & Validation"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Rate Limiting per Client and Endpoint (Priority: P1)

As a platform operator, I want the system to automatically cap the number of requests each client can make per endpoint category per minute, so that no single client can overwhelm the service or degrade performance for others.

**Why this priority**: Rate limiting is a foundational defense against abuse and unintentional overload. Without it, a misbehaving client can starve all other users. It must be enforced at the entry point before any processing occurs.

**Independent Test**: Can be fully tested by sending repeated requests from the same client identity and verifying the system returns a rejection response after the configured threshold is reached, with a retry-wait indicator included.

**Acceptance Scenarios**:

1. **Given** a client has reached the upload request quota for the current minute, **When** they attempt another upload, **Then** the system rejects the request immediately and indicates how long to wait before retrying.
2. **Given** a client sends a cross-origin preflight check, **When** the request arrives, **Then** the system permits it without counting it against any quota.
3. **Given** a client's quota is exhausted, **When** sufficient time has passed for at least one token to refill in the rolling 60-second window, **Then** the system allows the next request and the remaining count reflects the refilled token.
4. **Given** a client does not provide an explicit identity header, **When** they send requests, **Then** the system identifies them by their network address (respecting proxy forwarding) and applies rate limiting accordingly.
5. **Given** the rate limiting store is temporarily unavailable, **When** a request arrives, **Then** the system allows the request through rather than blocking legitimate traffic.
6. **Given** a request is allowed under quota, **When** the response is sent, **Then** it includes a header indicating how many requests remain in the current window.

---

### User Story 2 - File Validation Before Processing (Priority: P2)

As a system, I want to reject invalid or dangerous files at the entry boundary before any processing begins, so that malformed, oversized, or prohibited file types never enter the ingestion pipeline.

**Why this priority**: Validating files early prevents wasted computation, storage pollution, and security exposure. It is the second line of defense after rate limiting in the ingestion flow.

**Independent Test**: Can be fully tested by submitting files with various invalid properties (missing, empty, oversized, blocked extension) and confirming each is rejected with a specific, descriptive error message before any ingestion work occurs.

**Acceptance Scenarios**:

1. **Given** a client uploads a valid document file within the size limit with an allowed extension, **When** the system validates it, **Then** the file is accepted and proceeds to ingestion.
2. **Given** a client sends a request with no file attached, **When** the system validates it, **Then** the request is rejected with a message indicating the file is missing.
3. **Given** a client uploads a file that exceeds the maximum allowed size, **When** the system validates it, **Then** the request is rejected with a message stating the actual size and the configured limit.
4. **Given** a client uploads a file with a prohibited extension (e.g. an executable type), **When** the system validates it, **Then** the request is rejected immediately with a message identifying the blocked type.
5. **Given** a batch upload contains at least one invalid file, **When** the system validates the batch, **Then** the entire batch is rejected immediately upon detecting the first invalid file, with a message describing that file's specific violation (fail-fast — subsequent files are not inspected).
6. **Given** a client submits an audio file for transcription that exceeds 25 MB, **When** the system validates it, **Then** the request is rejected with a message indicating the file size received and the limit.

---

### User Story 3 - File Content Signature Verification (Priority: P3)

As a security-conscious platform, I want to verify that each uploaded file's actual byte content matches its declared type, so that disguised or spoofed files cannot bypass extension-based filters.

**Why this priority**: An attacker can rename a malicious file (e.g. an executable) to appear as a document. Content-level verification closes this bypass gap. It is prioritized below basic validation since basic checks handle the common cases first.

**Independent Test**: Can be fully tested by submitting files with mismatched content and extensions (e.g. an image file saved as `.pdf`) and verifying they are rejected, while legitimately-typed files with matching signatures are accepted.

**Acceptance Scenarios**:

1. **Given** a client uploads a file whose content signature matches its declared extension, **When** the system verifies the signature, **Then** the file passes and proceeds.
2. **Given** a client uploads a file whose actual content does not match the declared extension, **When** the system verifies the signature, **Then** the request is rejected with a message identifying the mismatch.
3. **Given** a client uploads an executable file disguised as a document, **When** the system checks the extension category, **Then** the request is rejected immediately as a dangerous type without needing to inspect content.
4. **Given** a client uploads a text-based file (e.g. `.txt`, `.csv`) that has no known binary signature, **When** the system attempts signature verification, **Then** the check is skipped and the file is accepted based on other validation rules.
5. **Given** a client uploads a modern Office document (.docx or .xlsx), **When** the system verifies the signature, **Then** it correctly recognises their shared internal container format and accepts them.
6. **Given** a client uploads a file too short to contain its expected signature, **When** the system attempts to read the signature bytes, **Then** the request is rejected with a message indicating the file is too short to be a valid instance of the declared type.

---

### Edge Cases

- What happens when a file has a valid extension but its byte content is shorter than the required signature length?
- When `X-User-Id`, session attribute, and `X-Forwarded-For`/`X-Real-IP` are all absent, the system falls back to the direct remote address of the connection. The leftmost IP in `X-Forwarded-For` is trusted as-is; IP spoofing via this header is out of scope and delegated to network-level controls.
- A batch upload with multiple invalid files fails fast on the first invalid file detected; the system does not inspect the remaining files or accumulate multiple errors.
- What happens when the rate limiting store is unavailable at request time — is any error surfaced to the client or is the request silently allowed?
- How does signature verification handle DOCX, XLSX, and PPTX when they share the same internal container format as ZIP archives?

## Requirements *(mandatory)*

### Functional Requirements

**Rate Limiting**

- **FR-001**: The system MUST enforce per-client, per-endpoint-category request quotas using a rolling 60-second window; tokens refill continuously at a constant rate rather than resetting all at once on a fixed clock boundary.
- **FR-001b**: Quota counters MUST be shared across all running service instances; a client's total requests across all nodes counts against a single global quota, preventing limit multiplication in multi-instance deployments.
- **FR-002**: The system MUST resolve client identity in priority order: explicit client header (`X-User-Id`) → authenticated session attribute → network address; when falling back to network address, the system MUST use the leftmost (first) IP in `X-Forwarded-For` if present, then `X-Real-IP`, then the direct remote address. No trusted-proxy filtering is applied; spoofing prevention is delegated to network-level controls.
- **FR-003**: When a client exceeds their quota, the system MUST reject the request and include the wait duration before the next request is allowed.
- **FR-004**: The system MUST apply distinct, independently-tracked quotas per endpoint category: file upload, batch upload, file deletion, search, and general requests.
- **FR-005**: The system MUST bypass all quota checks for cross-origin preflight requests.
- **FR-006**: When the quota store is unreachable, the system MUST allow requests to proceed (fail-open) to avoid blocking legitimate traffic.
- **FR-007**: Every allowed response MUST include a header indicating the number of remaining requests available in the current window.
- **FR-007b**: Every rate-limit block MUST produce a structured log entry at WARN level identifying the client, the endpoint category, and the retry wait duration. Every file validation rejection MUST produce a structured log entry at WARN level identifying the file and the specific violation. No metric counters are required in this phase.

**File Validation**

- **FR-008**: The system MUST reject files that are null, empty (zero bytes), or have no filename.
- **FR-009**: The system MUST reject files exceeding the configured maximum size, with a message reporting the actual size and the limit.
- **FR-010**: The system MUST reject files whose extension appears on the blocked list, with a message identifying the blocked type.
- **FR-011**: The system MUST reject batch upload requests that contain no files; when a batch contains invalid files, the system MUST fail fast — stopping at the first invalid file and returning that file's violation as the error, without inspecting the remaining files.
- **FR-012**: The system MUST reject audio files submitted for transcription that exceed 25 MB, with a message indicating the size received.

**File Signature Verification**

- **FR-013**: The system MUST block any file whose extension is classified as dangerous, regardless of its content.
- **FR-014**: For formats with known binary signatures, the system MUST compare the file's opening bytes against the expected signature for the declared extension.
- **FR-015**: If the content signature does not match the declared extension, the system MUST reject the file with a message identifying the mismatch.
- **FR-016**: For formats without a known binary signature (text-based types), the system MUST skip signature verification and accept the file on other criteria alone.
- **FR-017**: The system MUST treat modern Office document formats (.docx, .xlsx, .pptx) as sharing the same valid internal container format for signature matching.
- **FR-018**: The system MUST reject files too short to contain their expected signature, with a message indicating the file is too short.

### Key Entities

- **Client Identity**: The entity making requests; resolved from header, session, or network address; used as the quota tracking key.
- **Rate Limit Quota**: A configurable threshold (requests per minute) tied to an endpoint category and a client identity; state is stored in a shared, distributed store so the limit is enforced globally across all service instances.
- **Rate Limit Result**: Outcome of a quota check — allowed (with remaining count) or blocked (with retry wait duration).
- **File Constraint**: The set of rules a file must satisfy to be accepted: non-empty, named, within size limit, allowed extension.
- **File Signature**: The expected byte pattern at the start of a file for a given declared type, used to verify the actual content matches the extension.
- **Validation Result**: The outcome of a signature check — valid or invalid, carrying the detected real type and any error description.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of requests exceeding the per-client-per-endpoint quota are rejected before any downstream processing is triggered.
- **SC-002**: Every rate-limit rejection response includes a retry-wait value, enabling automated clients to back off correctly.
- **SC-003**: 100% of cross-origin preflight requests are permitted without any quota deduction.
- **SC-004**: 100% of files with blocked extensions are rejected before any ingestion work begins.
- **SC-005**: 100% of files whose byte content does not match their declared extension are detected and rejected.
- **SC-006**: Text-based file formats without a known binary signature pass validation without false rejection.
- **SC-007**: When the quota store is unavailable, zero legitimate requests are blocked as a direct result of the outage (fail-open guarantee); the failure is recorded as a log entry at ERROR level.
- **SC-009**: Every rate-limit block and every file validation rejection produces a WARN-level log entry; no rejection occurs silently.
- **SC-008**: All validation rejections include a human-readable message identifying the specific reason for rejection.

## Assumptions

- Rate limit quotas (upload: 10/min, batch: 5/min, delete: 20/min, search: 50/min, default: 30/min) are already configured; this spec covers enforcement and test coverage, not initial configuration.
- The blocked extension list for basic validation (exe, bat, cmd, msi, com, scr, vbs, ps1, sh) and for signature verification (exe, dll, bat, sh, cmd, vbs, ps1, jar) is stable for this phase.
- The 25 MB audio file size limit is a fixed constraint imposed by the upstream transcription service and is not configurable in this phase.
- Signature verification applies only to formats with well-known binary magic bytes; text-based formats (txt, csv, json, xml, md) are explicitly excluded by design.
- Rate limiting operates independently per endpoint category; a client blocked on uploads can still make search requests within the search quota.
- The `FileValidatorSpec` test class already exists and covers basic file validation scenarios (US-22); new test classes required are `RateLimitInterceptorSpec`, `RateLimitServiceSpec`, `FileSignatureValidatorSpec`, and `AudioFileValidatorSpec`.
- Modern Office formats (docx, xlsx, pptx) share a ZIP-based container signature; signature verification must account for this equivalence to avoid false rejections.

## Clarifications

### Session 2026-04-04

- Q: When a batch upload contains multiple invalid files, what failure reporting strategy should the system use? → A: Fail-fast — stop at the first invalid file, return a single error identifying that file's violation; do not inspect remaining files.
- Q: How is the 1-minute quota window measured — fixed-calendar, rolling, or token-bucket? → A: Rolling window (sliding 60-second span) — tokens refill continuously at a constant rate; no hard reset on a fixed clock boundary.
- Q: Is quota enforcement per-node or shared globally across all service instances? → A: Global — quota counters are shared across all instances via a central store; a client's requests across all nodes count against one shared limit.
- Q: Which IP value in `X-Forwarded-For` should be trusted as the canonical client address? → A: Leftmost (first) IP — no trusted-proxy filtering; spoofing prevention is delegated to network-level controls.
- Q: Should rate-limit blocks and validation rejections produce observable signals beyond the HTTP response? → A: Log entries only — every block/rejection MUST produce a structured WARN-level log entry; no metric counters required in this phase.
