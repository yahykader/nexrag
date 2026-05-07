# Tasks: PHASE 14 — App Root Integration Tests

**Input**: Design documents from `/specs/019-app-root-tests/`
**Prerequisites**: plan.md ✅ | spec.md ✅ | research.md ✅ | data-model.md ✅ | quickstart.md ✅

**Tests**: All tasks in this phase ARE the test implementation (Phase 14 is a test-writing phase).

**Organization**: Tasks grouped by user story. Each story produces one independent, runnable spec file.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Maps to user story from spec.md (US1, US2, US3)
- Exact file paths included in all task descriptions

---

## Phase 1: Setup

**Purpose**: Remove the existing raw-TestBed stub so it does not conflict with the new Spectator-based files.

- [x] T001 Delete `src/app/app.spec.ts` (stub uses raw TestBed without Spectator — superseded by the 3 new spec files created in US1/US2/US3)

**Checkpoint**: `src/app/app.spec.ts` no longer exists. `npm test -- --run` completes with no reference to the deleted file.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Confirm the Spectator/Vitest import path used in prior phases also resolves correctly at the app-root level before writing tests.

**⚠️ CRITICAL**: Run this verification before any spec file is created to avoid discovering import issues mid-implementation.

- [x] T002 Verify Spectator vitest import resolves at app root — add a temporary one-liner import check in a scratch REPL or confirm by inspecting `node_modules/@ngneat/spectator/vitest` exists in `agentic-rag-ui/node_modules/@ngneat/spectator/`
- [x] T003 Confirm `RouterTestingModule` is available from `@angular/router/testing` by checking it is re-exported in the Angular 21 BOM (inspect `node_modules/@angular/router/testing/index.d.ts`)

**Checkpoint**: All imports confirmed resolvable — US1/US2/US3 can now be implemented in parallel.

---

## Phase 3: User Story 1 — Application Bootstrap Verification (Priority: P1) 🎯 MVP

**Goal**: Verify `AppComponent` renders its shell correctly (router-outlet, navbar, toast container) with a mock store, and that the full provider chain bootstraps without injection errors.

**Independent Test**: `npm test -- --run src/app/app.component.spec.ts` passes all 5 tests (4 unit + 1 integration).

### Implementation for User Story 1

- [x] T004 [US1] Create `src/app/app.component.spec.ts` with Spectator factory setup: `createComponentFactory({ component: AppComponent, imports: [RouterTestingModule], providers: [provideMockStore({})], shallow: true })`
- [x] T005 [P] [US1] Implement test US1.1 in `src/app/app.component.spec.ts`: `it('doit créer l\'application', ...)` — assert `spectator.component` is truthy
- [x] T006 [P] [US1] Implement test US1.2 in `src/app/app.component.spec.ts`: `it('doit contenir un <router-outlet>', ...)` — assert `spectator.query('router-outlet')` is truthy
- [x] T007 [P] [US1] Implement test US1.3 in `src/app/app.component.spec.ts`: `it('doit afficher le <app-toast-container>', ...)` — assert `spectator.query('app-toast-container')` is truthy
- [x] T008 [P] [US1] Implement test US1.4 in `src/app/app.component.spec.ts`: `it('doit afficher les liens Workspace et Management dans la navbar', ...)` — query all `a.nav-link` elements and assert `routerLink` values contain `/workspace` and `/management`
- [x] T009 [US1] Implement test US1.5 in `src/app/app.component.spec.ts`: `it('[INTÉGRATION] doit se bootstrapper sans erreur d\'injection avec les vrais providers', fakeAsync(async () => {...}))` — configure TestBed with real `provideStore`, `provideEffects`, `provideHttpClient(withInterceptors([...]))`, `provideAnimations`, `provideMarkdown` and `RouterTestingModule.withRoutes(routes)`; assert component and router-outlet render (see research.md R-04 for full pattern)
- [x] T010 [US1] Run `npm test -- --run --reporter=verbose src/app/app.component.spec.ts` and confirm all 5 tests pass (4 green unit + 1 green integration, zero failures)

**Checkpoint**: `app.component.spec.ts` fully passing — User Story 1 independently verified. ✅

---

## Phase 4: User Story 2 — Navigation Routing Correctness (Priority: P2)

**Goal**: Verify all top-level routes resolve correctly — root redirect, workspace lazy load, management regression guard, and wildcard fallback — using the real Angular router in a test environment. One integration test confirms the full redirect chain with no mocks.

**Independent Test**: `npm test -- --run src/app/app.routes.spec.ts` passes all 5 tests (4 unit + 1 integration).

### Implementation for User Story 2

- [x] T011 [US2] Create `src/app/app.routes.spec.ts` with `TestBed.configureTestingModule({ imports: [RouterTestingModule.withRoutes(routes)] })` setup; inject `Router` and `Location` in `beforeEach`; call `router.initialNavigation()` in `beforeEach`
- [x] T012 [P] [US2] Implement test US2.1 in `src/app/app.routes.spec.ts`: `it('doit rediriger / vers /workspace', fakeAsync(() => {...}))` — `router.navigate([''])`, `tick()`, assert `location.path() === '/workspace'`
- [x] T013 [P] [US2] Implement test US2.2 in `src/app/app.routes.spec.ts`: `it('doit charger WorkspaceComponent pour /workspace', fakeAsync(() => {...}))` — `router.navigate(['/workspace'])`, `flush()`, assert `location.path() === '/workspace'`
- [x] T014 [P] [US2] Implement test US2.3 in `src/app/app.routes.spec.ts`: `it('doit rediriger /management (route inactive) vers /workspace', fakeAsync(() => {...}))` — `router.navigate(['/management'])`, `tick()`, assert `location.path() === '/workspace'` (regression guard — fails if route is silently re-activated)
- [x] T015 [P] [US2] Implement test US2.4 in `src/app/app.routes.spec.ts`: `it('doit rediriger une route inconnue vers /workspace via le wildcard', fakeAsync(() => {...}))` — `router.navigate(['/unknown-path'])`, `tick()`, assert `location.path() === '/workspace'`
- [x] T016 [US2] Implement test US2.5 in `src/app/app.routes.spec.ts`: `it('[INTÉGRATION] doit naviguer de / vers /workspace avec le vrai router', fakeAsync(() => {...}))` — `router.navigate([''])`, `flush()`, assert both `location.path() === '/workspace'` and `router.url === '/workspace'` (see research.md R-05)
- [x] T017 [US2] Run `npm test -- --run --reporter=verbose src/app/app.routes.spec.ts` and confirm all 5 tests pass (4 green unit + 1 green integration, zero failures)

**Checkpoint**: `app.routes.spec.ts` fully passing — User Story 2 independently verified. ✅

---

## Phase 5: User Story 3 — Application Configuration Integrity (Priority: P3)

**Goal**: Verify that all required global providers (router, 5 store slices, 5 effect classes, HTTP client with 2 interceptors) are present in `appConfig.providers` via static JSON serialisation inspection — no TestBed or application bootstrap required.

**Independent Test**: `npm test -- --run src/app/app.config.spec.ts` passes all 4 tests (4 unit, zero integration).

### Implementation for User Story 3

- [x] T018 [US3] Create `src/app/app.config.spec.ts` with static serialisation setup: `import { appConfig } from './app.config'; const serialized = JSON.stringify(appConfig.providers);` at describe-scope (see research.md R-03 for rationale and minification risk note)
- [x] T019 [P] [US3] Implement test US3.1 in `src/app/app.config.spec.ts`: `it('doit inclure provideRouter()', ...)` — assert `serialized` contains the string `'workspace'` (route path marker embedded in EnvironmentProviders serialisation)
- [x] T020 [P] [US3] Implement test US3.2 in `src/app/app.config.spec.ts`: `it('doit inclure provideStore() avec les 5 slices', ...)` — assert `serialized` contains `'ingestion'`, `'progress'`, `'crud'`, `'rateLimit'`, `'chat'` (reducer key names)
- [x] T021 [P] [US3] Implement test US3.3 in `src/app/app.config.spec.ts`: `it('doit inclure provideEffects() avec les 5 classes d\'effets', ...)` — assert `serialized` contains `'IngestionEffects'`, `'ProgressEffects'`, `'CrudEffects'`, `'RateLimitEffects'`, `'ChatEffects'` (class names preserved in non-minified test bundles)
- [x] T022 [P] [US3] Implement test US3.4 in `src/app/app.config.spec.ts`: `it('doit inclure provideHttpClient() avec les intercepteurs', ...)` — assert `serialized` contains `'duplicateInterceptor'` and `'rateLimitInterceptor'` (function names from `core/interceptors/`)
- [x] T023 [US3] Run `npm test -- --run --reporter=verbose src/app/app.config.spec.ts` and confirm all 4 tests pass (zero failures)

**Checkpoint**: `app.config.spec.ts` fully passing — User Story 3 independently verified. ✅

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Full suite validation, coverage gate verification, and commit.

- [x] T024 Run the full Phase 14 suite together: `npm test -- --run --reporter=verbose --include="src/app/app.*.spec.ts"` and confirm all 14 tests pass (12 unit + 2 integration, zero failures, zero skipped)
- [x] T025 [P] Run coverage report for the 3 source files: `npm test -- --run --coverage --include="src/app/app.*.spec.ts"` — verify all 4 thresholds for `app.component.ts`, `app.routes.ts`, `app.config.ts`: statements ≥ 80%, branches ≥ 75%, functions ≥ 85%, lines ≥ 80%
- [ ] T026 Stage and commit the 3 new spec files and the stub deletion: `git add src/app/app.component.spec.ts src/app/app.routes.spec.ts src/app/app.config.spec.ts && git rm src/app/app.spec.ts && git commit -m "test(phase-14): add app.component/routes/config specs — shell, routing, provider coverage"`

**Checkpoint**: All 14 Phase 14 tests green, coverage gates satisfied, committed. 🎉

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 (stub removed) — BLOCKS Phase 3/4/5 start
- **US1 (Phase 3)**: Depends on Phase 2 completion
- **US2 (Phase 4)**: Depends on Phase 2 completion — can run in parallel with US1/US3 after Phase 2
- **US3 (Phase 5)**: Depends on Phase 2 completion — can run in parallel with US1/US2 after Phase 2
- **Polish (Phase 6)**: Depends on Phase 3 + Phase 4 + Phase 5 completion

### User Story Dependencies

- **US1 (P1)**: No dependency on US2 or US3 — completely independent
- **US2 (P2)**: No dependency on US1 or US3 — completely independent (different file, different testing utility)
- **US3 (P3)**: No dependency on US1 or US2 — static import only, no test runner setup required

### Within Each User Story

- T004/T011/T018 (spec file skeleton) MUST complete before parallel test tasks in that story
- T005–T008 / T012–T015 / T019–T022 (individual `it()` implementations) can run in parallel once the skeleton exists
- T009/T016 (integration tests) depend on the unit test skeleton being in place
- Verification tasks (T010/T017/T023) MUST run after all `it()` implementations for their story

### Parallel Opportunities

- After Phase 2: US1, US2, and US3 can all be implemented in parallel (3 independent files)
- Within US1: T005, T006, T007, T008 can be written in parallel (same file, non-overlapping `it()` blocks)
- Within US2: T012, T013, T014, T015 can be written in parallel (same file, non-overlapping `it()` blocks)
- Within US3: T019, T020, T021, T022 can be written in parallel (same file, non-overlapping `it()` blocks)

---

## Parallel Execution Examples

### US1, US2, US3 in Parallel (after T003 completes)

```bash
# Terminal 1 — US1: AppComponent
npx code "src/app/app.component.spec.ts"  # implement T004-T009

# Terminal 2 — US2: AppRoutes
npx code "src/app/app.routes.spec.ts"     # implement T011-T016

# Terminal 3 — US3: AppConfig
npx code "src/app/app.config.spec.ts"     # implement T018-T022
```

### Running Individual Story Suites

```bash
# US1 only
npm test -- --run --reporter=verbose src/app/app.component.spec.ts

# US2 only
npm test -- --run --reporter=verbose src/app/app.routes.spec.ts

# US3 only
npm test -- --run --reporter=verbose src/app/app.config.spec.ts

# All Phase 14
npm test -- --run --reporter=verbose --include="src/app/app.*.spec.ts"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Delete stub
2. Complete Phase 2: Verify imports
3. Complete Phase 3 (T004–T010): `app.component.spec.ts`
4. **STOP and VALIDATE**: `npm test -- --run src/app/app.component.spec.ts` → 5/5 green
5. The shell bootstrap is verified — highest-risk test is done

### Incremental Delivery

1. Phase 1 + Phase 2 → Foundation ready
2. Phase 3 (US1) → AppComponent verified (MVP)
3. Phase 4 (US2) → Routing verified (add routing coverage)
4. Phase 5 (US3) → Config verified (add provider coverage)
5. Phase 6 → Full suite green + committed

### Parallel Team Strategy

With 3 developers, after Phase 2:
- Dev A: US1 (T004–T010) — `app.component.spec.ts`
- Dev B: US2 (T011–T017) — `app.routes.spec.ts`
- Dev C: US3 (T018–T023) — `app.config.spec.ts`
- All three merge → Phase 6 (T024–T026) together

---

## Notes

- `[P]` tasks within a user story share the same spec file — "parallel" means independent `it()` blocks that can be written in any order without merge conflict (all add to the same file)
- The `[INTÉGRATION]` tests (T009, T016) MUST use `fakeAsync` + `flush()` to drain Promise microtasks from lazy component loading
- `provideStoreDevtools` MUST be excluded from T009's real-provider list — it accesses browser extensions that fail in jsdom
- If static inspection tests (T019–T022) fail with unexpected serialisation output, inspect `JSON.stringify(appConfig.providers)` in a scratch test to identify the actual markers (see research.md R-03 risk note)
- The management route regression guard (T014) is expected to fail only if someone uncomments the management route — that is the correct behaviour when Phase 12 activates it
- Delete `app.spec.ts` (T001) BEFORE creating the new files to avoid IDE confusion from having both stubs and real tests open simultaneously
