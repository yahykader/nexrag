# Feature Specification: Workspace Page Integration Tests

**Feature Branch**: `020-workspace-page-tests`  
**Created**: 2026-05-07  
**Status**: Draft  
**Input**: User description: "PHASE 13 - src/app/pages/workspace integration tests for the main workspace shell page"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Workspace Shell Renders Correctly (Priority: P1)

A developer running the test suite needs confidence that the `WorkspaceComponent` initializes properly and renders the expected two-panel layout (upload on the left, chat on the right). This is the foundation for all other tests — if the component cannot be created, nothing else is reliable.

**Why this priority**: All other workspace tests depend on the component mounting without errors. This is the smoke test that gates the entire suite.

**Independent Test**: Run the workspace spec in isolation; if the component factory succeeds and the layout containers are present in the DOM, this story is fully verified.

**Acceptance Scenarios**:

1. **Given** the WorkspaceComponent has all its child components mocked, **When** the test creates the component, **Then** the component instance is non-null and no console errors appear.
2. **Given** the component is mounted, **When** the DOM is inspected, **Then** a sidebar element containing the upload panel is present on the left side of the layout.
3. **Given** the component is mounted, **When** the DOM is inspected, **Then** a main element containing the chat panel is present on the right side of the layout.

---

### User Story 2 - Child Feature Pages Are Embedded (Priority: P1)

A developer needs assurance that the workspace shell correctly embeds the `UploadPage` and `ChatPage` feature components — verifying that the routing shell fulfils its contract of composing the two features side by side.

**Why this priority**: The workspace's only responsibility is to compose its two child pages. If either child is missing, the application's core dual-panel UX is broken.

**Independent Test**: Mount the component with shallow mocks for the child pages; confirm that each child component selector appears exactly once in the rendered output.

**Acceptance Scenarios**:

1. **Given** the component is mounted with mocked child pages, **When** the template is rendered, **Then** the upload page element appears inside the sidebar region.
2. **Given** the component is mounted with mocked child pages, **When** the template is rendered, **Then** the chat page element appears inside the main region.

---

### User Story 3 - Toast Notifications Are Available Globally (Priority: P2)

A developer needs to confirm that the workspace shell hosts the global toast outlet so that notifications triggered by either the upload or chat feature are displayed correctly at the workspace level.

**Why this priority**: Without the toast container at the workspace level, user feedback from HTTP errors, rate-limit warnings, and upload confirmations is silently swallowed.

**Independent Test**: Mount the component and verify the toast container element is present; trigger a mock notification and confirm it appears in the DOM.

**Acceptance Scenarios**:

1. **Given** the component is mounted, **When** the DOM is inspected, **Then** the toast container element is present in the workspace layout.
2. **Given** a success notification is dispatched, **When** the toast container processes it, **Then** a toast with a success style appears within the workspace.
3. **Given** an error notification is dispatched, **When** the toast container processes it, **Then** a toast with an error style appears within the workspace.

---

### User Story 4 - Full Upload-to-Chat Integration Flow (Priority: P3)

A developer needs an integration-level scenario that exercises the interaction between the upload panel and the chat panel within the same workspace: uploading a document triggers a progress event that updates the ingestion store, which the chat panel can subsequently query.

**Why this priority**: Unit tests verify each child component in isolation; this integration test validates that the shared NgRx store correctly connects the two panels without interference.

**Independent Test**: Use `provideMockStore` with a realistic initial state; simulate an upload completion action and verify the store state reflects the completed upload, ensuring both panels would reflect the change.

**Acceptance Scenarios**:

1. **Given** the workspace is mounted with a mock store containing a pending upload, **When** the upload-complete action is dispatched, **Then** the ingestion store reflects the success status.
2. **Given** the workspace is mounted, **When** a rate-limit action is dispatched, **Then** both the rate-limit indicator (upload side) and any chat input restrictions respond correctly.
3. **Given** the workspace is mounted, **When** the component is destroyed, **Then** no active subscriptions remain and no memory leaks occur.

---

### Edge Cases

- What happens when both child components fail to load (missing providers in test setup)?
- How does the layout behave if the sidebar or main panel has zero content height?
- What happens when a toast notification arrives before the toast container has fully initialized?
- How does the component behave if the NgRx store is not provided in the test environment?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The test suite MUST verify that `WorkspaceComponent` can be instantiated without errors when all child dependencies are mocked.
- **FR-002**: The test suite MUST confirm that an element with CSS class `.workspace-sidebar` is rendered in the DOM (queried by class, not element tag, for resilience to tag refactors).
- **FR-003**: The test suite MUST confirm that an element with CSS class `.workspace-main` is rendered in the DOM (queried by class, not element tag, for resilience to tag refactors).
- **FR-003b**: The test suite MUST verify that the `.workspace-sidebar` element contains the upload page child selector.
- **FR-003c**: The test suite MUST verify that the `.workspace-main` element contains the chat page child selector.
- **FR-004**: The test suite MUST verify that `UploadPageComponent` is embedded within the sidebar region.
- **FR-005**: The test suite MUST verify that `ChatPageComponent` is embedded within the main region.
- **FR-006**: This phase MUST add `<app-toast-container>` to the `workspace.component.html` template and import `ToastContainerComponent` in the component, then the test suite MUST verify the toast container is present in the DOM and displays notifications.
- **FR-007**: The test suite MUST include at least one integration scenario that validates cross-feature NgRx store interactions within the workspace.
- **FR-008**: The test suite MUST verify that no subscription leaks occur when the component is destroyed.
- **FR-009**: Unit tests MUST use explicit stub components (minimal `@Component` declarations with matching selectors and empty templates) for `UploadPageComponent` and `ChatPageComponent` — `NO_ERRORS_SCHEMA` is prohibited to preserve selector-regression detection.
- **FR-010**: Integration tests MUST use a mock store seeded with all five slices at their respective `initialState` values: `ingestion`, `progress`, `rateLimit`, `chat`, and `crud` — matching the real application bootstrap.

### Key Entities

- **WorkspaceComponent**: The shell page that composes the upload (left) and chat (right) panels within a full-viewport two-column layout.
- **UploadPageComponent** (child): The ingestion feature page rendered in the left sidebar; verified by selector presence in tests.
- **ChatPageComponent** (child): The conversational AI feature page rendered in the main area; verified by selector presence in tests.
- **ToastContainerComponent** (global outlet): Displays notifications from any feature within the workspace; must be present at the workspace level.
- **NgRx Store** (shared state): The single store connecting the ingestion and chat feature slices; exercised in integration scenarios.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 6 unit-level tests pass with zero failures when child components are mocked (4 structural + 2 CSS-class verification tests).
- **SC-002**: All 3 integration scenarios pass with a mock store, confirming cross-feature state propagation works correctly.
- **SC-003**: Test execution for the workspace spec completes in under 5 seconds in CI (isolated component, no real HTTP or WebSocket).
- **SC-004**: Zero subscription leaks are detected across all test runs (verified by the destroy lifecycle test).
- **SC-005**: The workspace spec achieves 100% statement and branch coverage for `workspace.component.ts`, given the component contains no logic beyond composition.

## Clarifications

### Session 2026-05-07

- Q: Is adding `<app-toast-container>` to the workspace template in scope for this test phase, or a separate prerequisite? → A: In scope — this phase adds the template change and tests it together.
- Q: Which isolation strategy should unit tests use for child components? → A: Explicit stub components (minimal `@Component` declarations with matching selectors) — `NO_ERRORS_SCHEMA` prohibited.
- Q: What should the 2 additional unit tests (beyond the 4 listed in the test plan) cover? → A: Layout CSS classes verified separately (`.workspace-sidebar` and `.workspace-main` class presence).
- Q: Which store slices must be seeded in the integration test mock store? → A: All five slices — `ingestion`, `progress`, `rateLimit`, `chat`, `crud`.
- Q: Should structural tests query elements by CSS class or HTML tag? → A: CSS class names — stable across tag refactors.

## Assumptions

- The `WorkspaceComponent` itself contains no business logic — it is purely a layout shell; accordingly, the test plan targets structural and integration concerns rather than state manipulation within the component itself.
- Adding `<app-toast-container>` to the workspace template and importing `ToastContainerComponent` is **in scope for this phase** — this is a template fix bundled with the test work, not a separate prerequisite.
- Child components (`UploadPageComponent`, `ChatPageComponent`) are stubbed using explicit minimal `@Component` stubs for unit tests — `NO_ERRORS_SCHEMA` is not used so that selector typos surface as failures.
- The mock store in integration tests will be seeded with all five slices — `ingestion`, `progress`, `rateLimit`, `chat`, and `crud` — each at their respective `initialState` values, matching real application bootstrap.
- Vitest is the test runner (not Karma/Jasmine), and `@ngneat/spectator/vitest` bindings are used throughout, consistent with all other phases.
- No routing or resolver behavior is tested at this layer — that belongs to Phase 14 (`app.routes`).
