# Feature Specification: Chat Store NgRx Tests

**Feature Branch**: `013-chat-store-ngrx`  
**Created**: 2026-04-30  
**Status**: Draft  
**Input**: User description: "read agentic-ui-test-plan-speckit.md file and create a specification from PHASE 6 — src/app/features/chat/store"

## Clarifications

### Session 2026-04-30

- Q: How should the reducer behave when load-history-success arrives and messages already exist in the list? → A: Replace — discard current messages and populate the list entirely with the history payload.
- Q: What happens when a send-message action is dispatched while streaming is already in progress? → A: Cancel and restart — cancel the in-flight stream and begin the new message immediately.
- Q: What should the reducer do when a receive-chunk action arrives after stream-complete has been dispatched? → A: Ignore silently — state remains unchanged, no error raised.
- Q: Should the streaming effect retry on failure before dispatching stream-error? → A: No retry — dispatch stream-error immediately on first failure; user resends manually.
- Q: Is the chat scoped to a specific conversation ID, or is there a single active session? → A: Single active conversation — no conversation ID in state or API calls.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Reducer State Correctness (Priority: P1)

A developer working on the chat feature needs confidence that the chat state transitions are correct and predictable. When any action is dispatched, the reducer must produce the expected next state — from the initial blank state through message accumulation, streaming updates, and error handling.

**Why this priority**: The reducer is the single source of truth for chat state. Bugs here propagate to every component that reads from the store. It is the highest-risk piece of logic and must be verified first.

**Independent Test**: Can be fully tested in isolation by dispatching each action against the reducer and asserting on the resulting state object. Delivers verified state logic before any UI or side-effect work begins.

**Acceptance Scenarios**:

1. **Given** no prior state exists, **When** the store initialises, **Then** the state contains an empty message list, streaming is inactive, and no error is present.
2. **Given** the current state has no messages, **When** a user sends a message, **Then** the user's message appears as the last entry in the message list.
3. **Given** the assistant is streaming a response, **When** a new content chunk arrives, **Then** the last message in the list is updated with the accumulated content.
4. **Given** streaming is in progress, **When** a stream-complete signal is received, **Then** the streaming flag is set to inactive.
5. **Given** the chat contains messages, **When** a clear-chat action is dispatched, **Then** the message list is emptied and streaming is inactive.
6. **Given** the store is empty, **When** a successful history-load response arrives, **Then** all historical messages are present in the message list in their original order.
7. **Given** any state, **When** a set-error action is dispatched, **Then** the error field contains the provided message.

---

### User Story 2 - Selector Data Access (Priority: P2)

A developer building chat UI components needs reliable selectors that extract the correct slice of state without introducing their own logic errors. Selectors must return accurate values for all combinations of state shape.

**Why this priority**: Selectors are consumed by every component connected to the chat store. An incorrect selector silently returns stale or wrong data, causing UI bugs that are hard to trace back to the store.

**Independent Test**: Can be fully tested by constructing mock state objects and asserting that each selector returns the expected derived value. Verifies data access contracts independently of the reducer.

**Acceptance Scenarios**:

1. **Given** a state with two messages, **When** the all-messages selector is called, **Then** it returns a list of exactly two messages.
2. **Given** a state where streaming is inactive, **When** the is-streaming selector is called, **Then** it returns false.
3. **Given** a state with three messages, **When** the last-message selector is called, **Then** it returns the third message.
4. **Given** a state with no error, **When** the error selector is called, **Then** it returns null.
5. **Given** a state with an error message, **When** the error selector is called, **Then** it returns the stored error string.

---

### User Story 3 - Effects Async Orchestration (Priority: P3)

A developer integrating the streaming chat API needs confidence that the NgRx effects correctly bridge the async streaming service with the store. Each chunk must trigger the right action, completion must be signalled, and failures must be handled gracefully.

**Why this priority**: Effects contain the most complex async logic in the store layer. Streaming and history-loading bugs produce visible user-facing failures (missing messages, frozen UI, uncaught errors).

**Independent Test**: Can be fully tested with mocked service dependencies and a mock action stream, verifying the sequence of dispatched actions without any real network calls.

**Acceptance Scenarios**:

1. **Given** a send-message action is dispatched, **When** the effect processes it, **Then** the streaming service is invoked with the message content.
2. **Given** the streaming service emits content chunks, **When** each chunk arrives, **Then** a receive-chunk action is dispatched for every chunk received.
3. **Given** the streaming service signals completion, **When** the done flag is received, **Then** a stream-complete action is dispatched exactly once.
4. **Given** the streaming service throws an error, **When** the failure occurs, **Then** a stream-error action is dispatched and streaming does not remain active.
5. **Given** a load-history action is dispatched, **When** the effect processes it, **Then** the history API service is called and a load-history-success action is dispatched with the returned messages.

---

### User Story 4 - Action Type and Payload Safety (Priority: P4)

A developer consuming chat actions in components, effects, or tests needs to verify that each action creator produces an object with the exact expected type string and payload shape, preventing runtime dispatch failures caused by typos or payload mismatches.

**Why this priority**: Action type strings and payload shapes form the contract between different layers of the application. Incorrect types break reducer matching and logging. This is low-risk individually but costly if wrong.

**Independent Test**: Can be fully tested by calling each action creator with sample inputs and asserting on the resulting object's type and payload properties.

**Acceptance Scenarios**:

1. **Given** a send-message action is created with message content, **When** the action object is inspected, **Then** the type string matches the expected pattern and the content field equals the provided input.
2. **Given** a receive-chunk action is created with partial content, **When** the action object is inspected, **Then** the chunk payload is present and correctly typed.
3. **Given** a stream-complete action is created, **When** the action object is inspected, **Then** the type string is correct and no unexpected payload is present.
4. **Given** a set-error action is created with an error message, **When** the action object is inspected, **Then** the error field equals the provided string.

---

### Edge Cases

- When a receive-chunk action arrives after stream-complete has already been dispatched, the reducer ignores it silently — state remains unchanged and no error is raised.
- When load-history-success is dispatched and messages already exist in the list, the reducer replaces all current messages with the history payload (no append, no merge).
- When a send-message action is dispatched while streaming is already in progress, the in-flight stream is cancelled and the new message begins streaming immediately (cancel-and-restart).
- How does the system handle a set-error action with an empty string or null value?
- What happens when the streaming service emits zero chunks before signalling completion?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The chat reducer MUST return a valid initial state when no prior state is provided, containing an empty message list, an inactive streaming flag, and a null error field.
- **FR-002**: The chat reducer MUST append a user message to the end of the message list when a send-message action is processed.
- **FR-003**: The chat reducer MUST update the content of the most recent assistant message when a receive-chunk action is processed. If `isStreaming` is false at the time of dispatch, the reducer MUST ignore the action and leave state unchanged.
- **FR-004**: The chat reducer MUST set the streaming flag to inactive when a stream-complete action is processed.
- **FR-005**: The chat reducer MUST reset the message list to empty when a clear-chat action is processed.
- **FR-006**: The chat reducer MUST discard all current messages and replace the message list entirely with the payload from a load-history-success action, regardless of how many messages were previously in the list.
- **FR-007**: The chat reducer MUST store the provided error string when a set-error action is processed.
- **FR-008**: Each selector MUST return the correct slice of state given any valid state shape.
- **FR-009**: The send-message effect MUST invoke the streaming service and dispatch a receive-chunk action for every content chunk emitted.
- **FR-010**: The send-message effect MUST dispatch a stream-complete action when the streaming service signals the end of the response.
- **FR-011**: The send-message effect MUST dispatch a stream-error action immediately on the first failure from the streaming service, without retrying. The streaming flag MUST be inactive after the error is dispatched; the user resends manually.
- **FR-012**: The load-history effect MUST call the history API service and dispatch load-history-success with the returned messages on success.
- **FR-015**: The send-message effect MUST cancel any in-flight streaming request when a new send-message action arrives while streaming is active, then immediately begin streaming the new message (cancel-and-restart semantics).
- **FR-013**: Each action creator MUST produce an object with the correct type string and the exact payload shape defined in the action interface.
- **FR-014**: All test files MUST cover a minimum of 80% of statements and 85% of functions within the tested store files.

### Key Entities

- **ChatMessage**: Represents a single message in the conversation; has a unique identifier, a role (user or assistant), content text, and a timestamp. No conversation ID is stored — there is one active session at a time.
- **ChatState**: The full state shape managed by the store; contains an ordered list of ChatMessage objects, a streaming-active boolean, and a nullable error string. No conversation ID field is required.
- **ChatAction**: A typed event object that describes a state-changing intent; has a string type identifier and an optional payload.
- **ChatSelector**: A pure function that derives a specific value or sub-set of data from the full ChatState; must be memoized and side-effect-free.
- **ChatEffect**: A side-effect handler that listens for specific actions, calls external services, and dispatches result actions; must be observable-based and independently testable.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 18 planned unit tests for the chat store pass without failures in the CI environment.
- **SC-002**: Statement coverage for the chat store files (`chat.reducer.ts`, `chat.selectors.ts`, `chat.effects.ts`, `chat.actions.ts`, `chat.state.ts`) reaches at least 80%.
- **SC-003**: Function coverage for the same store files reaches at least 85%.
- **SC-004**: Branch coverage for reducer state transitions reaches at least 75%, ensuring all action-handling branches are exercised.
- **SC-005**: Each test file runs independently in under 2 seconds, confirming no hidden async dependencies or unresolved observables.
- **SC-006**: Zero flaky tests — all 18 tests produce consistent results across 5 consecutive runs without any infrastructure changes.
- **SC-007**: Every reducer action handler, every selector, and every effect is covered by at least one dedicated test case.

## Assumptions

- The chat store files (`chat.actions.ts`, `chat.reducer.ts`, `chat.selectors.ts`, `chat.effects.ts`, `chat.state.ts`) already exist or will be scaffolded before tests are written.
- Tests are written using the Spectator + Jasmine/Karma toolchain already configured in the project, with `provideMockStore` and `provideMockActions` for NgRx isolation.
- No real network calls, WebSocket connections, or backend services are used in Phase 6 tests; all external dependencies are mocked.
- The `AppState` shape is defined and stable enough for selectors to reference the `chat` slice by key.
- A shared `test-helpers.ts` utility providing `mockMessage` factory and `mockStore` helper is available or will be created as part of this phase.
- The streaming service emits an observable of chunk objects; the final chunk carries an `isDone: true` flag to signal completion.
- There is a single active chat conversation at a time; the store and all API calls are session-global with no conversation ID parameter.
- Vitest (as referenced in CLAUDE.md) or Karma is the test runner; either is acceptable since the tests themselves are framework-agnostic assertions.
