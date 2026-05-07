# Feature Specification: PHASE 14 — App Root Integration Tests

**Feature Branch**: `019-app-root-tests`
**Created**: 2026-05-06
**Status**: Draft
**Input**: User description: "read agentic-ui-test-plan-speckit.md file and create a specification for PHASE 14 — `src/app` (Application Root)"

## Clarifications

### Session 2026-05-06

- Q: Should `app.routes.spec.ts` cover child route activation (`/workspace/chat`, `/workspace/upload`) or only top-level routes? → A: Phase 14 covers only top-level routes (`/` redirect, `/workspace`, `**` fallback); child routes are Phase 13 (workspace) responsibility.
- Q: Should Phase 14 test the commented-out `/management` route? → A: Yes — add a test asserting that navigating to `/management` triggers the wildcard redirect to `/workspace`, pinning the current behavior as a regression guard.
- Q: Should SC-004 apply all four project-standard coverage metrics or statement-only? → A: All four — statements ≥ 80%, branches ≥ 75%, functions ≥ 85%, lines ≥ 80%.
- Q: Which 2 scenarios qualify as integration tests (no mocks, real provider chain)? → A: (I) Full app bootstrap with real store/router/HTTP providers — assert shell renders without injection errors. (II) Real-router navigation `/` → assert user lands on `/workspace` with `WorkspaceComponent` active.
- Q: How should `app.config.spec.ts` verify provider presence? → A: Static inspection of the `appConfig.providers` array (no TestBed, no bootstrapping); the Q4-I integration test (full bootstrap) handles runtime correctness.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Application Bootstrap Verification (Priority: P1)

A developer verifies that the application root component assembles correctly — navigation bar is visible, a router outlet is present, and the global toast notification area is active — so that users always have a predictable shell regardless of which page they navigate to.

**Why this priority**: The application root is the entry point. If the shell does not render correctly, every feature built on top is broken. This is the highest-risk regression point.

**Independent Test**: Can be fully tested by mounting `AppComponent` in isolation with a mock store and confirming the DOM contains a `<router-outlet>`, a navigation bar, and a `<app-toast-container>`.

**Acceptance Scenarios**:

1. **Given** the application has been bootstrapped, **When** the root component is rendered, **Then** the application shell is created without errors.
2. **Given** the root component is rendered, **When** the DOM is inspected, **Then** a `<router-outlet>` element is present.
3. **Given** the root component is rendered, **When** the DOM is inspected, **Then** the `<app-toast-container>` element is present for global notifications.
4. **Given** the root component is rendered, **When** the navigation bar is inspected, **Then** links to `/workspace` and `/management` are rendered.
5. **[INTEGRATION]** **Given** `AppComponent` is mounted with the real router, store, and HTTP providers (no mocks), **When** the application initialises, **Then** the shell renders without any provider injection errors.

---

### User Story 2 - Navigation Routing Correctness (Priority: P2)

A developer verifies that every declared URL route in the application resolves to the correct destination page — including the root redirect, all feature pages, and the wildcard fallback — so that users are never sent to a blank or broken page.

**Why this priority**: Broken routes are invisible until a user hits them. Route coverage gaps discovered post-deployment cause user-facing failures that are hard to debug. Covering all routes in tests prevents silent routing regressions.

**Independent Test**: Can be fully tested using the Angular router testing utilities to navigate to each path and assert which component is activated or which redirect fires.

**Acceptance Scenarios**:

1. **Given** a user navigates to `/`, **When** the router resolves the path, **Then** the user is redirected to `/workspace`.
2. **Given** a user navigates to `/workspace`, **When** the router resolves the path, **Then** the `WorkspaceComponent` is lazy-loaded and activated.
3. **Given** a user navigates to `/management` (route currently inactive), **When** the router resolves the path, **Then** the wildcard route fires and the user is redirected to `/workspace`.
4. **Given** a user navigates to an unknown URL, **When** the router resolves the path, **Then** the user is redirected to `/workspace` via the wildcard route.
5. **[INTEGRATION]** **Given** the application is bootstrapped with the real router (no router test doubles), **When** the user navigates to `/`, **Then** the router redirects to `/workspace` and `WorkspaceComponent` becomes the active routed component.

*Note: Child route scenarios (`/workspace/chat`, `/workspace/upload`) are covered in Phase 13 (WorkspaceComponent spec), not here.*

---

### User Story 3 - Application Configuration Integrity (Priority: P3)

A developer verifies that all required global providers — router, HTTP client with interceptors, state management store slices, effects, and animations — are declared in the application configuration so that no feature breaks due to missing providers at runtime.

**Why this priority**: Missing providers produce cryptic runtime injection errors that are hard to trace. Verifying the configuration statically catches provider omissions before they surface in feature tests.

**Independent Test**: Can be fully tested by statically inspecting the `appConfig.providers` array and asserting each required provider entry is present — no `TestBed` or DOM rendering required. Runtime correctness is guaranteed by the integration test in User Story 1.

**Acceptance Scenarios**:

1. **Given** the `appConfig.providers` array is inspected statically, **When** its entries are examined, **Then** a `provideRouter()` entry referencing the application routes is present.
2. **Given** the `appConfig.providers` array is inspected statically, **When** its entries are examined, **Then** a `provideStore()` entry listing all 5 state slices (`ingestion`, `progress`, `crud`, `rateLimit`, `chat`) is present.
3. **Given** the `appConfig.providers` array is inspected statically, **When** its entries are examined, **Then** a `provideEffects()` entry listing all 5 effect classes (`IngestionEffects`, `ProgressEffects`, `CrudEffects`, `RateLimitEffects`, `ChatEffects`) is present.
4. **Given** the `appConfig.providers` array is inspected statically, **When** its entries are examined, **Then** a `provideHttpClient()` entry including both the duplicate-detection and rate-limit interceptors is present.

---

### Edge Cases

- What happens when the application is bootstrapped without the NgRx store providers — do components that inject store selectors fail gracefully or throw?
- How does the router behave when the `WorkspaceComponent` lazy chunk fails to load (network error during lazy import)?
- What happens if a user bookmarks a deep URL (e.g., `/workspace/chat`) and navigates directly — does the route resolve correctly on fresh load?
- How does the wildcard route `**` interact with the workspace child router outlet — does an unknown child path fall through to the application-level wildcard?
- What happens if both the duplicate interceptor and the rate-limit interceptor are triggered on the same request?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The application root MUST render a persistent top navigation bar containing links to the `Workspace` and `Management` sections on every page.
- **FR-002**: The application root MUST include a `<router-outlet>` so feature pages are injected into the shell at runtime.
- **FR-003**: The application root MUST render a global toast notification container (`<app-toast-container>`) outside the router outlet so toasts persist across navigation.
- **FR-004**: The URL path `/` MUST automatically redirect users to `/workspace` on application load.
- **FR-005**: The URL path `/workspace` MUST lazy-load and activate the `WorkspaceComponent` without a full page reload.
- **FR-006**: Any URL not matched by a declared top-level route MUST redirect users to `/workspace` via a wildcard fallback route.
- **FR-006a**: The currently inactive `/management` path MUST resolve via the wildcard fallback to `/workspace`, confirming no silent route re-activation goes undetected.
- **FR-007**: The application configuration MUST register `provideRouter()` with the complete route definition array.
- **FR-008**: The application configuration MUST register `provideHttpClient()` with both the duplicate-detection interceptor and the rate-limit interceptor applied globally.
- **FR-009**: The application configuration MUST register `provideStore()` with all five state slices: `ingestion`, `progress`, `crud`, `rateLimit`, and `chat`.
- **FR-010**: The application configuration MUST register `provideEffects()` with all five effect classes: `IngestionEffects`, `ProgressEffects`, `CrudEffects`, `RateLimitEffects`, and `ChatEffects`.
- **FR-011**: The application configuration MUST register `provideAnimations()` so Angular Material components animate correctly.
- **FR-012**: The application configuration MUST register `provideMarkdown()` so markdown rendering is available application-wide in chat messages.

### Key Entities

- **AppComponent**: The root shell component — responsible for the top navigation bar, the primary router outlet, and the global toast container. Stateless: it carries no business logic.
- **AppRoutes**: The top-level route definition array — declares the root redirect, the workspace lazy route, and the wildcard fallback. Child routes within `/workspace` are delegated to `WorkspaceComponent`.
- **AppConfig**: The application-level provider configuration — wires together routing, HTTP, state management, animations, and third-party providers. All providers must be present for the application to function.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 13 tests in Phase 14 (11 unit + 2 integration) pass with zero failures.
- **SC-002**: The `AppComponent` renders its shell (navbar + router-outlet + toast container) without console errors during test execution.
- **SC-003**: Every declared route resolves to its target component or redirect in under 100ms during test execution.
- **SC-004**: Coverage for `app.component.ts`, `app.routes.ts`, and `app.config.ts` meets all four project-standard thresholds: statements ≥ 80%, branches ≥ 75%, functions ≥ 85%, lines ≥ 80%.
- **SC-005**: No missing provider injection errors appear in test output — all 5 store slices and 5 effect classes are confirmed present in the configuration.
- **SC-006**: The wildcard route correctly handles 100% of unknown URL patterns tested (at minimum: `/unknown`, `/fake/path`, `/does-not-exist`).

## Assumptions

- Child routes within `/workspace` (chat, upload) are defined inside `WorkspaceComponent`'s own route configuration, not at the application root level — their tests belong exclusively to Phase 13 (workspace spec). Phase 14 tests only the three top-level routes: `/`, `/workspace`, and `**`.
- The `management` top-level route is intentionally commented out in the current codebase pending Phase 12 completion. Phase 14 includes a regression guard test asserting that `/management` currently resolves via the wildcard to `/workspace`; this test is expected to be updated (not removed) when Phase 12 activates the route.
- The wildcard `**` route redirects to `/workspace` rather than a dedicated 404 page — this is the current production behavior and the expected test assertion.
- All NgRx store slices and effects implemented in phases 013–018 are correctly exported and importable; this phase does not re-test their internal behavior.
- The Vitest test runner is already configured and operational for the project (not Karma/Jasmine).
- `provideMockStore` from `@ngrx/store/testing` is available and will be used to avoid real store initialization in `AppComponent` unit tests.
- The `app.config.spec.ts` unit tests verify provider presence by statically inspecting the `appConfig.providers` array — no `TestBed` or application bootstrap. Runtime wiring correctness is delegated to the User Story 1 integration test (full bootstrap, no mocks).
- `provideStoreDevtools()` is present in the config but is not a mandatory requirement to assert in tests (it is a developer tooling concern, not a production feature).
