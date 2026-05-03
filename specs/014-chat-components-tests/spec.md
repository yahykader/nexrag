# Feature Specification: Phase 7 — Chat Components Test Suite

**Feature Branch**: `014-chat-components-tests`  
**Created**: 2026-05-02  
**Status**: Draft  
**Input**: User description: "read agentic-ui-test-plan-speckit.md file and create a specification from PHASE 7 — `src/app/features/chat/components`"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Chat Interface Rendering & Store Integration (Priority: P1)

A developer verifying the `ChatInterfaceComponent` can confirm that the main chat container correctly renders the message list from the NgRx store, shows a loading spinner during active streaming, and auto-scrolls to the bottom when a new message arrives. They can also verify that the `MessageInputComponent` and `VoiceButtonComponent` child components are present in the rendered template.

**Why this priority**: This is the top-level container for the entire chat feature. If it fails to render or connect to state, all downstream components are untestable and the user-facing chat experience breaks entirely.

**Independent Test**: Can be fully tested by mounting the component with a mock store containing predefined messages and streaming state, then asserting DOM content — without real network calls or routing.

**Acceptance Scenarios**:

1. **Given** the store holds 3 messages and `isStreaming=false`, **When** `ChatInterfaceComponent` mounts, **Then** all 3 messages are rendered in the message list.
2. **Given** `isStreaming=true` in the store, **When** the component renders, **Then** a spinner/loading indicator is visible.
3. **Given** the message list is not at the bottom, **When** a new message is added to the store, **Then** the scroll position moves to the bottom of the list.
4. **Given** the component is mounted, **When** the template is inspected, **Then** both `MessageInputComponent` and `VoiceButtonComponent` are present as child elements.
5. **Given** a user submits a message via the input, **When** the submit event fires, **Then** the `SendMessage` action is dispatched to the NgRx store.

---

### User Story 2 - Message Input Behavior & Validation (Priority: P1)

A developer testing `MessageInputComponent` verifies that a user can only submit non-empty messages, that Enter triggers submission while Shift+Enter does not, and that the input is cleared and disabled at appropriate times.

**Why this priority**: The message input is the primary user interaction point. Incorrect validation or keyboard behavior causes user frustration and incorrect action dispatching.

**Independent Test**: Can be fully tested by mounting `MessageInputComponent` with `@Input()` bindings for `isDisabled` and listening to the `messageSent` output event — no store required.

**Acceptance Scenarios**:

1. **Given** the input field is empty, **When** the Submit button is rendered, **Then** the button is disabled.
2. **Given** the user types a message, **When** they click the Submit button, **Then** the `messageSent` event emits with the message content.
3. **Given** the user types a message and presses Enter, **When** the keydown event fires, **Then** `messageSent` is emitted and the input is cleared.
4. **Given** the user presses Shift+Enter, **When** the keydown event fires, **Then** `messageSent` is NOT emitted (newline behavior only).
5. **Given** `isDisabled=true` (streaming active), **When** the component renders, **Then** the input field and submit button are both disabled.
6. **Given** a message is submitted, **When** the submission completes, **Then** the input field value is reset to empty.

---

### User Story 3 - Message Item Display & Content Formatting (Priority: P2)

A developer testing `MessageItemComponent` verifies that each message is displayed with the correct CSS role class, that assistant messages receive markdown rendering, that messages with a search term show highlighted text, and that a formatted timestamp is shown.

**Why this priority**: Message items are the primary content of the chat UI. Incorrect role styling or missing markdown rendering degrades content readability; these are regression-prone once pipes are involved.

**Independent Test**: Can be fully tested by mounting `MessageItemComponent` with `@Input()` bindings for `message` (role, content, timestamp) and `searchTerm`, then asserting DOM classes and inner HTML.

**Acceptance Scenarios**:

1. **Given** a message with `role='user'`, **When** `MessageItemComponent` renders it, **Then** the host element carries the CSS class `user`.
2. **Given** a message with `role='assistant'`, **When** `MessageItemComponent` renders it, **Then** the host element carries the CSS class `assistant`.
3. **Given** an assistant message with markdown content, **When** the component renders, **Then** the markdown pipe converts it to formatted HTML (e.g., `**bold**` → `<strong>bold</strong>`).
4. **Given** a `searchTerm` input is provided, **When** the component renders, **Then** the matching term in the message content is wrapped with a highlight marker.
5. **Given** a message with a valid timestamp, **When** the component renders, **Then** a relative time string is displayed (e.g., "just now", "2 min ago", "1 hour ago").
6. **Given** a message whose content contains a `<script>` tag or an inline event handler, **When** the component renders the content through the markdown pipe, **Then** no executable script element is present in the DOM (see FR-017).

---

### User Story 4 - Voice Input Button Lifecycle (Priority: P3)

A developer testing `VoiceButtonComponent` verifies that clicking the button starts speech recognition, that an animation is shown during active listening, that the recognized transcript is emitted as an output event, and that an error state is shown when the browser does not support the speech API.

**Why this priority**: Voice input is an enhancement feature. The core chat works without it, but its tests must ensure it degrades gracefully on unsupported browsers and does not break the component tree.

**Independent Test**: Can be fully tested by mounting `VoiceButtonComponent` with a mocked `VoiceService`, simulating clicks and observing `voiceTranscript` output events and DOM state.

**Acceptance Scenarios**:

1. **Given** the user clicks the voice button, **When** the click event fires, **Then** `VoiceService.startListening()` is called.
2. **Given** `VoiceService.isListening$` emits `true`, **When** the component renders, **Then** a pulsing animation class is applied to the button.
3. **Given** speech recognition completes, **When** `VoiceService` emits a transcript, **Then** the `voiceTranscript` output event emits with the recognized text.
4. **Given** the browser does not support the speech recognition API, **When** the component initializes, **Then** an error or unsupported-browser indicator is displayed.

---

### Edge Cases

- What happens when the message list is empty (no messages in the store) — `ChatInterfaceComponent` must render an empty state gracefully without errors.
- What happens when the user submits whitespace-only input — `MessageInputComponent` must treat it as empty and keep the submit button disabled.
- What happens when a message has `null` or `undefined` content — `MessageItemComponent` must not throw and must render safely.
- What happens when markdown content contains potential XSS markup — the markdown pipe must sanitize output before rendering (formalized as FR-017 with an explicit test case).
- What happens when `VoiceService` throws immediately on `startListening()` — `VoiceButtonComponent` must catch the error and display an error state without crashing.
- What happens when a new message arrives while the list is already at the bottom — auto-scroll must still fire without visual jank.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The test suite MUST cover all four components in `src/app/features/chat/components`: `ChatInterfaceComponent`, `MessageInputComponent`, `MessageItemComponent`, and `VoiceButtonComponent`.
- **FR-002**: Each component spec MUST include a creation smoke test asserting the component instance exists.
- **FR-003**: `ChatInterfaceComponent` tests MUST verify NgRx store integration by providing a mock store with predefined state and asserting DOM output.
- **FR-004**: `ChatInterfaceComponent` tests MUST include at least one integration scenario that dispatches a `SendMessage` action and verifies store interaction. Child components (`MessageInputComponent`, `VoiceButtonComponent`) MUST be declared as stubs (shallow rendering) so the test isolates the parent's store wiring only.
- **FR-005**: `MessageInputComponent` tests MUST verify all submit paths: click, Enter key, and Shift+Enter (negative case).
- **FR-006**: `MessageInputComponent` tests MUST verify the disabled state while streaming is active.
- **FR-007**: `MessageItemComponent` tests MUST verify role-based CSS class application for both `user` and `assistant` roles.
- **FR-008**: `MessageItemComponent` tests MUST verify that the markdown pipe is applied to assistant messages and produces formatted HTML output.
- **FR-009**: `MessageItemComponent` tests MUST verify that the highlight pipe is applied when a `searchTerm` is provided.
- **FR-016**: `MessageItemComponent` tests MUST verify that the timestamp renders as a relative time string ("just now", "2 min ago", "1 hour ago"). A timestamp of ≤60 seconds ago MUST display "just now"; older timestamps MUST display a minutes- or hours-ago string.
- **FR-017**: `MessageItemComponent` tests MUST include an explicit XSS sanitization test: given a message whose content contains a raw `<script>` tag or inline event handler (e.g., `<img onerror="alert(1)">`), the rendered DOM MUST NOT contain an executable script element — the injected markup must be stripped or escaped by the markdown pipe before insertion.
- **FR-010**: `VoiceButtonComponent` tests MUST mock `VoiceService` and verify that `startListening()` is called on button click.
- **FR-011**: `VoiceButtonComponent` tests MUST verify the `voiceTranscript` output event emits correctly when recognition completes.
- **FR-012**: `VoiceButtonComponent` tests MUST cover the unsupported-browser error display.
- **FR-013**: All component tests MUST use `createComponentFactory` from `@ngneat/spectator` with `mockProvider` / `SpyObject` for dependencies — no real service instances. For `ChatInterfaceComponent`, child components are declared as stubs (shallow rendering); each child component's behaviour is verified exclusively within its own dedicated spec file.
- **FR-014**: NgRx-connected tests MUST use `provideMockStore` from `@ngrx/store/testing`.
- **FR-015**: The test suite MUST achieve a minimum of 22 unit test cases and 4 integration test cases across the four components.

### Key Entities

- **Message**: Represents a single chat turn with `id`, `role` (`user` | `assistant`), `content` (string), and `timestamp`. Drives both the store state and the `MessageItemComponent` input.
- **ChatState**: NgRx slice containing `messages[]`, `isStreaming` (boolean), and `error` (string | null). The source of truth for `ChatInterfaceComponent` rendering.
- **VoiceTranscript**: The string output produced by `VoiceService` upon successful speech recognition, emitted by `VoiceButtonComponent` as an output event.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 4 component spec files are created and pass with zero failures when the test suite is run.
- **SC-002**: At least 22 unit test cases and 4 integration test cases are implemented and passing across the four component specs.
- **SC-003**: Statement coverage for `src/app/features/chat/components/**` reaches ≥ 80% as reported by the coverage tool.
- **SC-004**: Function coverage for the same scope reaches ≥ 85%.
- **SC-005**: No test relies on a real network call, real NgRx store, or real browser speech API — all dependencies are mocked, verifiable by running the suite in isolation.
- **SC-006**: The test suite completes in under 30 seconds in CI, ensuring it does not block the pipeline.
- **SC-007**: Every component input/output binding is exercised by at least one test case, leaving no untested public API surface.

## Assumptions

- The four components (`ChatInterfaceComponent`, `MessageInputComponent`, `MessageItemComponent`, `VoiceButtonComponent`) already exist in `src/app/features/chat/components/` with their implementation complete, as this phase is test-only.
- The NgRx chat store (actions, reducer, selectors, effects) tested in Phase 6 is stable and its public API will not change during Phase 7.
- `MarkdownPipe` and `HighlightPipe` from `src/app/shared/pipes/` are already implemented and available for import into component specs.
- `VoiceService` from `src/app/core/services/` already exists with a stable public API including `startListening()`, `stopListening()`, and `isListening$` observable.
- `@ngneat/spectator ^22.1.0` and `@ngrx/store/testing ^21.0.1` are already installed (confirmed in CLAUDE.md active technologies for phase 013).
- Tests are written for **Vitest** (`npm test`), not Jasmine/Karma (`ng test`). Async utilities use `vi.useFakeTimers()` and `vi.advanceTimersByTime()`. The source test-plan document (`agentic-ui-test-plan-speckit.md`) references Jasmine/Karma but was written before the project migrated to Vitest.
- Mobile-specific behavior (touch events, mobile keyboard) is out of scope for this phase.
- Accessibility testing (ARIA labels, keyboard navigation, screen reader compatibility, axe-core audits) is explicitly out of scope for Phase 7. It is deferred to a dedicated accessibility audit phase.

## Clarifications

### Session 2026-05-02

- Q: Which test runner is in use for Phase 7 frontend tests — Vitest or Jasmine/Karma? → A: Vitest (`npm test`); the test plan document is outdated on this point.
- Q: Should `ChatInterfaceComponent` integration tests render child components as real instances (deep) or stubs (shallow)? → A: Shallow — child components are declared as stubs; parent store wiring is tested in isolation.
- Q: What format should the timestamp in `MessageItemComponent` use? → A: Relative time — "just now" (≤60 s), "2 min ago", "1 hour ago".
- Q: Should Phase 7 tests include accessibility assertions (ARIA, keyboard navigation)? → A: Out of scope — deferred to a dedicated accessibility audit phase.
- Q: Should XSS sanitization by the markdown pipe be a formal Functional Requirement? → A: Yes — added as FR-017 with an explicit acceptance scenario in User Story 3.
