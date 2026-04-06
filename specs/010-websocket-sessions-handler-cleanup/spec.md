
# Feature Specification: PHASE 10 — WebSocket : Sessions, Handler & Cleanup

**Feature Branch**: `PHASE-10-WebSocket-Sessions-Handler-Cleanup`
**Created**: 2026-04-06
**Status**: Draft
**Input**: User description: "read nexrag-test-plan-speckit.md and create a specification for the PHASE 10 — WebSocket : Sessions, Handler & Cleanup"

## User Scenarios & Testing *(mandatory)*

### User Story 1 — WebSocket Session Lifecycle Management (Priority: P1)

As the platform system, I want to manage the full lifecycle of WebSocket connections — registration, activity tracking, and deregistration — so that the set of active connections is always accurate and consistent.

**Why this priority**: Correct session tracking is the foundation for all WebSocket behaviour. Routing messages, broadcasting, and cleanup all depend on the session registry being accurate. Errors at this layer cascade into every higher-level feature.

**Independent Test**: Can be fully tested by registering a session, verifying it is recorded as active, performing activity updates, then unregistering and confirming the session is no longer present and the active count reflects reality.

**Acceptance Scenarios**:

1. **Given** a new WebSocket connection is opened, **When** the session is registered, **Then** it appears in the active session registry and the active session count increments by one.
2. **Given** a registered session is closed, **When** the session is unregistered, **Then** it is removed from all tracking structures, marked as disconnected, and the active count decrements by one.
3. **Given** a session receives two messages, **When** activity is recorded for each, **Then** the message count for that session equals two and the last-activity timestamp is updated.
4. **Given** a session is associated with a conversation identifier, **When** the identifier is stored and then retrieved, **Then** the retrieved value matches exactly what was stored.
5. **Given** a session identifier that was never registered, **When** the conversation identifier is retrieved, **Then** the result is null (no entry exists).
6. **Given** a session that is actively registered, **When** the session object itself is retrieved by identifier, **Then** the returned object is the exact instance that was registered.

---

### User Story 2 — Statistics and Inactive Session Cleanup (Priority: P1)

As a platform operator, I want to inspect real-time session statistics and trigger cleanup of sessions that have been disconnected beyond a configured threshold, so that I can monitor system health and prevent resource leaks.

**Why this priority**: Without cleanup, stale session entries accumulate indefinitely, consuming memory and distorting health metrics. Statistics visibility allows ops teams to detect anomalies before they affect users.

**Independent Test**: Can be fully tested by registering sessions, simulating disconnection, and verifying that cleanup with a near-zero threshold removes stale entries while cleanup with a very large threshold preserves them. Statistics can be validated against known state.

**Acceptance Scenarios**:

1. **Given** no sessions have ever been registered, **When** statistics are requested, **Then** all numeric fields (total, active, messages, averages, duration) are zero.
2. **Given** two activity updates have been applied to a single session, **When** statistics are requested, **Then** the total message count is two and the average messages per session is 2.0.
3. **Given** two sessions are registered, **When** the set of active session identifiers is requested, **Then** both identifiers are present in the result and no extra identifiers appear.
4. **Given** a session has been disconnected and the inactivity threshold is set to a negative value, **When** cleanup runs, **Then** the session is removed from the session registry.
5. **Given** a session has been disconnected and the inactivity threshold is set to the maximum possible value, **When** cleanup runs, **Then** the session is retained in the registry (threshold not yet exceeded).

---

### User Story 3 — WebSocket Handler Connection Lifecycle and Message Routing (Priority: P2)

As a generic WebSocket handler, I want to register incoming connections, route messages by type, reply with appropriate responses, and clean up when connections close or encounter transport errors, so that every connection event is handled predictably.

**Why this priority**: The handler is the first processing layer for all WebSocket traffic. Correct lifecycle and routing behaviour is required before any application-level logic (RAG assistant, broadcast) can function.

**Independent Test**: Can be fully tested without a running server by simulating connection open/close/error events and constructing text messages in different formats, then asserting on the outgoing message payloads and active session counts.

**Acceptance Scenarios**:

1. **Given** a client opens a WebSocket connection, **When** the connection-established event fires, **Then** the session count increments and the application hook for new connections is invoked.
2. **Given** a connected client sends a ping message, **When** the handler processes it, **Then** a pong message containing a timestamp is sent back to the same session.
3. **Given** a connected client sends a pong message, **When** the handler processes it, **Then** no reply is sent; the message is acknowledged silently.
4. **Given** a connected client sends a message with no type field, **When** the handler processes it, **Then** an error response with code `MISSING_TYPE` is returned to the sender.
5. **Given** a connected client sends a message that is not valid JSON, **When** the handler processes it, **Then** an error response with code `PROCESSING_ERROR` is returned to the sender.
6. **Given** a client closes the connection normally, **When** the connection-closed event fires, **Then** the session count decrements and the application hook for closed connections is invoked.
7. **Given** a client connection drops due to a transport error, **When** the error event fires, **Then** the session is removed from the registry and the transport-error hook is invoked.
8. **Given** two sessions are active and a broadcast is requested, **When** the broadcast executes, **Then** the message is delivered to both sessions.
9. **Given** a string longer than the configured truncation limit, **When** truncated, **Then** the result is the truncation-limit characters followed by `"..."`.
10. **Given** a string shorter than the truncation limit, **When** truncated, **Then** the string is returned unchanged; given a null input, the result is null.
11. **Given** per-session data is stored under a key, **When** that key is read back, **Then** the value matches what was stored.

---

### User Story 4 — RAG Assistant Message Routing over WebSocket (Priority: P2)

As a RAG platform client, I want to establish a conversation session, send natural language queries, and cancel active streams via WebSocket messages, so that I can interact with the assistant in real time without polling.

**Why this priority**: This is the primary user-facing interaction layer. Correct message routing and response formatting determine the entire conversational experience.

**Independent Test**: Can be fully tested without a live AI backend by injecting mock messages of each type and asserting on the outgoing response structure, conversation identifier generation, and session-manager call counts.

**Acceptance Scenarios**:

1. **Given** a client establishes a WebSocket connection, **When** the connection is accepted, **Then** the session is registered as anonymous and a `connected` message containing the session identifier and a timestamp is sent to the client.
2. **Given** a connected client closes the connection normally, **When** the closed event fires, **Then** the session is unregistered from the session manager.
3. **Given** a connected client's connection drops due to transport error, **When** the error event fires, **Then** the session is unregistered from the session manager.
4. **Given** a client sends an `init` message with a user identifier, **When** it is processed, **Then** a new conversation identifier prefixed with `conv_` is generated, stored against the session, and a `conversation_created` message containing the user identifier and conversation identifier is returned.
5. **Given** a client sends a `query` message with non-empty text and an active conversation, **When** it is processed, **Then** a `query_received` message containing the query text and the active conversation identifier is returned.
6. **Given** a client sends a `query` message with no text field, **When** it is processed, **Then** an error with code `MISSING_QUERY` is returned.
7. **Given** a client sends a `query` message with a blank text field (spaces only), **When** it is processed, **Then** an error with code `MISSING_QUERY` is returned.
8. **Given** a client sends a `cancel` message, **When** it is processed, **Then** a `cancelled` message with the text `"Stream annulé"` is returned.
9. **Given** a client sends a message of an unrecognised type, **When** it is processed, **Then** an error with code `UNKNOWN_TYPE` is returned.
10. **Given** any message is received over WebSocket, **When** it is processed, **Then** the session manager's activity tracker is updated exactly once for that session.
11. **Given** a `query` message arrives but no conversation has been initialised for the session, **When** the `query_received` message is composed, **Then** the conversation identifier field is an empty string.
12. **Given** two active sessions and a broadcast-to-all call, **When** the broadcast executes, **Then** both sessions receive the message.

---

### User Story 5 — Scheduled WebSocket Maintenance Tasks (Priority: P3)

As a platform operator, I want inactive WebSocket sessions to be purged automatically on a schedule and current statistics to be logged periodically, so that resources are reclaimed without manual intervention.

**Why this priority**: Automated maintenance prevents resource exhaustion under sustained load. Logging statistics on a schedule provides continuous visibility without requiring active monitoring queries.

**Independent Test**: Can be fully tested by mocking the session manager and properties, invoking the scheduled methods directly, and verifying the correct delegated calls are made with the correct argument values.

**Acceptance Scenarios**:

1. **Given** the inactivity threshold is configured to 3 600 000 milliseconds, **When** the cleanup task runs, **Then** it delegates to the session manager with exactly that threshold value.
2. **Given** the log-statistics task is invoked, **When** it runs, **Then** it retrieves statistics from the session manager exactly once.
3. **Given** the cleanup task runs, **When** it executes, **Then** it reads the active session count both before and after the cleanup to compute the number of sessions removed.
4. **Given** the inactivity threshold changes, **When** the cleanup task next runs, **Then** the updated threshold value is passed through; the threshold is always read fresh from configuration and never cached in the task.

---

### Edge Cases

- A session identifier that was never registered must not cause errors when queried — all lookup operations must return neutral values (null, false, zero) rather than throwing exceptions.
- Message handling on a closed or closing session must not propagate as an unhandled exception; delivery failures must be logged and silently swallowed at the handler level.
- Concurrent registration and deregistration of the same session identifier must not corrupt the active session count or leave orphaned entries.
- A `query` message with a text field present but containing only whitespace characters must be treated identically to a missing text field.
- A cleanup run with a threshold of zero or negative must remove all disconnected sessions regardless of when they disconnected.
- During a broadcast, a `sendMessage` failure on one session must not abort delivery to subsequent sessions — the failing session is logged and skipped, and broadcast continues (skip-and-continue, not fail-all).

## Requirements *(mandatory)*

### Functional Requirements

**Session Lifecycle Management**

- **FR-001**: The system MUST register a WebSocket session into two concurrent structures — an identity-keyed session map and an active-connections registry — at the moment the connection is established.
- **FR-002**: The system MUST remove a session from both structures and invoke the disconnection marker when the connection is closed or encounters a transport error.
- **FR-003**: The system MUST update the message count and last-activity timestamp for a session on every incoming message.
- **FR-004**: The system MUST store and retrieve a conversation identifier bound to a session; retrieval for an unknown session MUST return null without throwing.
- **FR-005**: The system MUST return the registered session object when queried by session identifier.

**Statistics and Cleanup**

- **FR-006**: The system MUST compute session statistics covering: total sessions ever registered, currently active sessions, total messages across all sessions, average messages per session, and average connection duration.
- **FR-007**: The system MUST remove sessions that have been marked disconnected for longer than the supplied threshold (in milliseconds) when cleanup is invoked.
- **FR-008**: The system MUST return the complete set of currently active session identifiers on demand.

**WebSocket Handler — Lifecycle and Routing**

- **FR-009**: The system MUST register each new connection on the `afterConnectionEstablished` event and invoke the `onConnectionEstablished` application hook.
- **FR-010**: The system MUST respond to a `ping` message type with a `pong` message containing a current timestamp.
- **FR-011**: The system MUST silently acknowledge a `pong` message type without sending any reply.
- **FR-012**: The system MUST return an error with code `MISSING_TYPE` when a well-formed JSON message lacks the `type` field.
- **FR-013**: The system MUST return an error with code `PROCESSING_ERROR` when an incoming message is not valid JSON.
- **FR-014**: The system MUST remove the session and invoke the `onConnectionClosed` hook on normal connection closure.
- **FR-015**: The system MUST remove the session and invoke the `onTransportError` hook on transport error.
- **FR-016**: The system MUST attempt delivery of a broadcast message to every session currently present in the active registry; if delivery to one session fails, the failure MUST be logged and that session skipped while delivery continues to all remaining sessions.
- **FR-017**: The system MUST truncate strings exceeding a configured character limit by appending `"..."` and MUST return null when given a null input.
- **FR-018**: The system MUST support storing and retrieving arbitrary key-value data scoped to a session identifier.

**RAG Assistant Controller**

- **FR-019**: On connection, the system MUST register the session as `"anonymous"` and send a `connected` message with the session identifier and current timestamp.
- **FR-020**: On connection close or transport error, the system MUST unregister the session from the session manager.
- **FR-021**: An `init` message MUST generate a unique conversation identifier prefixed with `conv_`, associate it with the session, and return a `conversation_created` message carrying both the user identifier and the conversation identifier.
- **FR-022**: A `query` message with non-empty, non-blank text MUST produce a `query_received` message containing the query text and the active conversation identifier (empty string if none is set).
- **FR-023**: A `query` message with absent or blank text MUST produce an error with code `MISSING_QUERY`.
- **FR-024**: A `cancel` message MUST produce a `cancelled` message with the fixed text `"Stream annulé"`.
- **FR-025**: A message of any unrecognised type MUST produce an error with code `UNKNOWN_TYPE`.
- **FR-026**: Every incoming message, regardless of type, MUST trigger exactly one activity update on the session manager for the sender's session.
- **FR-027**: `broadcastToAll` MUST attempt delivery to every session present in the controller's active session map; a delivery failure on one session MUST be logged and skipped while delivery continues to all remaining sessions.

**Scheduled Maintenance**

- **FR-028**: The cleanup task MUST read the inactivity threshold from configuration on every execution and pass it unmodified to the session manager's cleanup method.
- **FR-029**: The cleanup task MUST read the active session count before and after cleanup to log the number of sessions removed.
- **FR-030**: The statistics task MUST invoke the session manager's statistics method exactly once per execution and log the result.

**Observability — Connection Lifecycle**

- **FR-031**: Every connection-established and normal connection-closed event MUST produce a log entry at INFO level identifying the session identifier.
- **FR-032**: Every transport-error event MUST produce a log entry at WARN level identifying the session identifier and the error cause.

### Key Entities

- **WebSocket Session**: A single active client connection identified by a unique session identifier; carries metadata including registration time, last-activity timestamp, message count, and optional conversation identifier.
- **Session Registry**: The dual-map structure tracking active connections; must remain consistent across registration, deregistration, and concurrent updates.
- **Session Statistics**: A snapshot of aggregate metrics: total sessions, active sessions, total messages, average messages per session, average connection duration.
- **Connection Lifecycle Event**: One of four events — established, closed, transport error, message received — each triggering defined state transitions in the registry.
- **Message Envelope**: The JSON wrapper for all WebSocket traffic; at minimum contains a `type` field used for routing; additional fields are type-specific.
- **Conversation Context**: A transient identifier (`conv_`-prefixed) binding a WebSocket session to an ongoing RAG conversation; created on `init`, read on subsequent queries.
- **Cleanup Task**: A scheduled process that reads a configurable inactivity threshold and delegates stale-session removal to the session manager.
- **Inactivity Threshold**: A duration in milliseconds read from configuration; sessions disconnected longer than this value are eligible for removal.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of WebSocket session registration and deregistration operations leave the active session count in a consistent state — no phantom sessions, no negative counts.
- **SC-002**: Activity updates are reflected in session statistics without loss — two updates to one session always yield a message count of exactly two and an average of 2.0.
- **SC-003**: Every `ping` message receives a `pong` reply; zero `pong` messages trigger any outgoing reply.
- **SC-004**: 100% of messages with a missing or unrecognised type field receive a typed error response identifying the specific violation code.
- **SC-005**: 100% of malformed (non-JSON) messages receive a `PROCESSING_ERROR` response without causing an unhandled exception.
- **SC-006**: Inactive session cleanup removes exactly the sessions disconnected beyond the threshold and preserves all others — zero false removals, zero false retentions.
- **SC-007**: Every `query` message with blank or absent text produces a `MISSING_QUERY` error; zero such messages silently pass through.
- **SC-008**: The scheduled cleanup task always passes the configuration-sourced threshold to the session manager — zero hardcoded threshold values in the task code.
- **SC-009**: Every incoming WebSocket message triggers activity tracking exactly once — no double-counting, no missed updates.
- **SC-010**: Broadcast operations attempt delivery to every currently registered active session; a delivery failure on one session produces a log entry and does not prevent remaining sessions from receiving the message.
- **SC-011**: Every connection-established and normal connection-closed event produces exactly one INFO-level log entry; every transport-error event produces exactly one WARN-level log entry identifying the session and error cause.

## Assumptions

- The four classes under test (`WebSocketSessionManager`, `WebSocketHandler`, `WebSocketAssistantController`, `WebSocketCleanupTask`) are already implemented in the production codebase under `com.exemple.nexrag.websocket`; this phase covers unit test coverage only, not new feature implementation.
- All four test classes (`WebSocketSessionManagerSpec`, `WebSocketHandlerSpec`, `WebSocketAssistantControllerSpec`, `WebSocketCleanupTaskSpec`) are reported as already implemented in the test plan; the spec validates completeness and alignment with the documented acceptance criteria.
- No additional Maven dependencies are required — `spring-boot-starter-test` (JUnit 5 + Mockito + AssertJ) and `spring-boot-starter-websocket` already declared in `pom.xml` are sufficient.
- The test classes reside in `src/test/java/com/exemple/nexrag/websocket/` (same package as production classes) to allow access to `protected` methods on `WebSocketHandler` without a Spring context.
- The inactivity threshold default value used in test scenarios is 3 600 000 ms (1 hour), matching the value referenced in the test plan.
- The `WebSocketProperties` configuration class exposes `getInactiveThresholdMs()` returning a configurable long value; this is mocked in unit tests.
- The `conv_` prefix for conversation identifiers is a fixed convention and is not configurable.
- Activity tracking (`updateActivity`) is a fire-and-forget operation with no return value; tests verify it is called the correct number of times via mock verification.
- Thread safety of the concurrent maps is an implementation concern; tests validate logical correctness under sequential execution — concurrency stress tests are out of scope for this phase.

## Clarifications

### Session 2026-04-06

- Q: When `sendMessage` fails for one session during a broadcast, what is the required behavior for the remaining sessions? → A: Skip and continue — log the failure for the failing session, then deliver to all remaining sessions.
- Q: Should connection-established, connection-closed, and transport-error events each produce a log entry, and at what level? → A: INFO for established and normal closure, WARN for transport error.
