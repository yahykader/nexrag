# Feature Specification: Phase 8 — Chat Pages & Resolvers Test Coverage

**Feature Branch**: `015-chat-pages-resolvers`  
**Created**: 2026-05-03  
**Status**: Draft  
**Input**: User description: "read agentic-ui-test-plan-speckit.md file and create a specification for the PHASE 8 — `src/app/features/chat/pages` + `resolvers`"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Chat Route Pre-Initialization via Resolver (Priority: P1)

When a user navigates to the chat section of the application, the system must guarantee a conversation context exists before the page renders. A route resolver checks the current state — if no active conversation is found, one is automatically created. The page then renders with a valid conversation already in place.

**Why this priority**: Without a functioning resolver, the chat page can render in an empty, broken state. This is the entry guard for the entire chat experience and must be verified first.

**Independent Test**: Can be tested by running the resolver in isolation with a mocked store, asserting that it dispatches `createConversation` when no active conversation exists and skips the dispatch when one does.

**Acceptance Scenarios**:

1. **Given** no active conversation exists in the store, **When** the resolver runs before route activation, **Then** the `createConversation` action is dispatched and the resolver completes successfully.
2. **Given** an active conversation already exists in the store, **When** the resolver runs, **Then** `createConversation` is NOT dispatched and the resolver completes without side effects.
3. **Given** the resolver runs (with or without dispatching), **When** its observable is subscribed to, **Then** it emits exactly once and completes — confirming the route guard never blocks navigation indefinitely.

---

### User Story 2 - Chat Page Renders Store-Connected Conversation List (Priority: P1)

A user opens the chat page and sees a sidebar listing all their previous conversations, with the currently active one visually highlighted. The page subscribes to live store data so the list updates automatically when conversations are created or deleted elsewhere.

**Why this priority**: The conversation list is the primary navigation surface of the chat page — it is visible on every page load and connects directly to the store state.

**Independent Test**: Can be tested by mounting `ChatPageComponent` with a mock store pre-populated with conversations, then asserting that the sidebar renders the correct number of items and highlights the active one.

**Acceptance Scenarios**:

1. **Given** the chat page mounts, **When** the component initializes, **Then** it subscribes to the `conversations$` observable from the NgRx store.
2. **Given** the chat page mounts, **When** the component initializes, **Then** it subscribes to the `activeConversationId$` observable from the NgRx store.
3. **Given** the store contains two conversations, **When** the template renders, **Then** two conversation items appear in the sidebar.
4. **Given** the store has no conversations, **When** the template renders, **Then** an empty-state indicator is shown in the sidebar.
5. **Given** the page is rendered, **When** the template is inspected, **Then** the `app-chat-interface` child component is present in the main content area.

---

### User Story 3 - Conversation Management Actions from Sidebar (Priority: P2)

A user can manage their conversations directly from the sidebar: start a new one, switch to an existing one, or delete one they no longer need. Each action triggers the correct state change in the application.

**Why this priority**: These are the three primary interactions a user has with the sidebar. Each one dispatches a distinct NgRx action and must be verified independently.

**Independent Test**: Can be tested by simulating button clicks and confirming the expected NgRx action was dispatched, without needing a live backend.

**Acceptance Scenarios**:

1. **Given** the sidebar is expanded, **When** the user clicks the "Nouveau" button, **Then** the `createConversation` action is dispatched to the store.
2. **Given** a list of conversations is displayed, **When** the user clicks on a conversation item, **Then** the `setActiveConversation` action is dispatched with the correct `conversationId`.
3. **Given** a conversation has a delete button, **When** the user clicks delete and `ConfirmationService` returns a positive result, **Then** the `deleteConversation` action is dispatched with the correct `conversationId`.
4. **Given** a conversation has a delete button, **When** the user clicks delete and `ConfirmationService` returns a negative result, **Then** the `deleteConversation` action is NOT dispatched.

---

### User Story 4 - Sidebar Collapse / Expand Toggle (Priority: P3)

A user can collapse the sidebar to maximize the chat area, then expand it again. When collapsed, conversation titles and delete buttons are hidden, but an icon-only mode and the toggle button remain accessible.

**Why this priority**: This is a UX convenience feature — chat works fully with or without it — but the toggle state must be verifiable to prevent regressions.

**Independent Test**: Can be tested by calling `toggleSidebar()` on the component and asserting the `sidebarCollapsed` boolean flips and the corresponding CSS class is applied to the sidebar element.

**Acceptance Scenarios**:

1. **Given** the sidebar is expanded (`sidebarCollapsed = false`), **When** `toggleSidebar()` is called, **Then** `sidebarCollapsed` becomes `true` and the `collapsed` CSS class is applied.
2. **Given** the sidebar is collapsed (`sidebarCollapsed = true`), **When** `toggleSidebar()` is called again, **Then** `sidebarCollapsed` becomes `false` and the sidebar expands.

---

### Edge Cases

- What happens when the resolver runs but the store selector emits multiple times — only the first emission (`take(1)`) should be used.
- How does the system handle a cancelled deletion — `stopPropagation` must still be called, `ConfirmationService` returns a negative result, and `deleteConversation` must NOT be dispatched.
- What happens when `conversations$` emits an empty array — the empty-state block must appear and the `ngFor` loop must render zero items.
- How does the component handle cleanup when the user navigates away — subscriptions from `async` pipe are automatically managed, but any manual subscriptions must be unsubscribed on `OnDestroy`.

## Clarifications

### Session 2026-05-03

- Q: How should the confirmation gate be handled for testing? → A: Refactor `onDeleteConversation` to use an injectable `ConfirmationService` before writing tests; the service is then mocked via `mockProvider` in specs.
- Q: Which isolation strategy should be used for `ChatPageComponent` unit tests? → A: Declare a minimal stub `@Component({ selector: 'app-chat-interface', template: '' })` in each test file; replace the real import in `overrideComponent` or via `imports` array override.
- Q: Should implementing `ngOnDestroy` + a cleanup test be in scope for this phase? → A: Out of scope — all current store bindings use the `async` pipe which self-cleans; no `OnDestroy` implementation or test is required unless a manual subscription is introduced.
- Q: Should each acceptance scenario become its own `it()` block, or be grouped to match the original plan count? → A: One `it()` per acceptance scenario; FR-013 updated to 14 unit tests + 2 integration tests.
- Q: What should the test for resolver completion (US1 scenario 3) assert? → A: Assert that the resolver observable emits once and completes, confirming the route guard does not block navigation.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The `ChatResolver` MUST read the active conversation ID from the NgRx store before route activation.
- **FR-002**: The `ChatResolver` MUST dispatch `createConversation` when no active conversation is found.
- **FR-003**: The `ChatResolver` MUST NOT dispatch `createConversation` when an active conversation already exists.
- **FR-004**: The `ChatResolver` MUST emit exactly once and complete without error, regardless of whether `createConversation` was dispatched — the test MUST verify observable completion, not store state.
- **FR-005**: The `ChatPageComponent` MUST subscribe to the conversations list from the store upon initialization.
- **FR-006**: The `ChatPageComponent` MUST subscribe to the active conversation ID from the store upon initialization.
- **FR-007**: The `ChatPageComponent` MUST render the `app-chat-interface` element in its main content area; this MUST be verified using a stub component so that selector presence — not child behaviour — is what is asserted.
- **FR-008**: The `ChatPageComponent` MUST dispatch `createConversation` when the user clicks the "Nouveau" button.
- **FR-009**: The `ChatPageComponent` MUST dispatch `setActiveConversation` with the correct ID when the user clicks a conversation item.
- **FR-010**: The `ChatPageComponent` MUST dispatch `deleteConversation` only after an injectable `ConfirmationService` returns a confirmed result; `onDeleteConversation` MUST be refactored to call this service instead of `window.confirm()` directly.
- **FR-011**: The `ChatPageComponent` MUST display an empty-state indicator when the conversations list is empty.
- **FR-012**: The `ChatPageComponent` MUST toggle the `sidebarCollapsed` boolean and apply the `collapsed` CSS class accordingly.
- **FR-013**: The test suite MUST provide one `it()` block per acceptance scenario — 11 unit tests for `ChatPageComponent` (US2: 5, US3: 4, US4: 2) + 3 unit tests for `ChatResolver` (US1: 3) + 2 integration tests = **16 tests total**.

### Key Entities

- **Conversation**: An entity with `id`, `title`, `messages[]`, and `updatedAt` — listed in the sidebar, with one marked as active at any time.
- **ChatResolver**: A route guard that reads store state and ensures a conversation context exists before the page renders.
- **Chat NgRx Slice**: The reactive state source providing `conversations` and `activeConversationId` streams to the page component.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 14 unit tests for `ChatPageComponent` and `ChatResolver` pass on every run, with zero flaky failures.
- **SC-002**: Both integration tests — `[INTÉGRATION] doit inclure ChatInterfaceComponent` and `[INTÉGRATION] dispatch complet page + store` — pass consistently.
- **SC-003**: Statement coverage for `features/chat/pages/` and `features/chat/resolvers/` reaches ≥ 80%, branch coverage ≥ 75%, function coverage ≥ 85%.
- **SC-004**: The full Phase 8 test suite completes in under 10 seconds.
- **SC-005**: No test relies on real network calls, real router navigation, or a real NgRx store — all dependencies are mocked or stubbed.

## Assumptions

- `ngOnDestroy` and subscription-cleanup tests are explicitly out of scope for this phase: all store bindings in `ChatPageComponent` use the `async` pipe, which self-manages teardown. If a manual `subscribe()` is introduced in a future phase, an `OnDestroy` test MUST be added at that time.
- The `ChatResolver` resolves to `Observable<void>`, not a boolean or a redirect — the error-redirect scenario from the original test plan does not match the current implementation and is excluded from this phase's scope.
- The Vitest test runner (not Karma/Jasmine) is used throughout `agentic-rag-ui`, consistent with `agentic-rag-ui/CLAUDE.md`.
- `@ngneat/spectator` (v22+) and `@ngrx/store/testing` (`provideMockStore`) are already installed — no additional packages are required.
- `ChatInterfaceComponent` is isolated in unit tests by declaring a minimal stub component (`@Component({ selector: 'app-chat-interface', template: '' })`) and replacing the real import via `overrideComponent` or the `imports` array — `NO_ERRORS_SCHEMA` is explicitly avoided to preserve selector-match verification.
- `onDeleteConversation` will be refactored to inject a `ConfirmationService` before tests are written; tests mock this service via `mockProvider(ConfirmationService)`, eliminating any dependency on the JSDOM `window.confirm` global.
