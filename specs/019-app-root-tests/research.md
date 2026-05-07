# Research: PHASE 14 — App Root Integration Tests

**Branch**: `019-app-root-tests` | **Date**: 2026-05-06

---

## R-01: Testing AppComponent with Spectator + RouterOutlet

**Decision**: Use `createComponentFactory` with `imports: [RouterTestingModule]` and `shallow: true`.

**Rationale**: `AppComponent` imports `RouterOutlet`, `RouterLink`, and `RouterLinkActive` — three Angular router directives that require a router context. `RouterTestingModule` provides a lightweight in-memory router that satisfies these directives without launching a real navigation stack. `shallow: true` stubs `<app-toast-container>` and prevents its providers (NotificationService, etc.) from leaking into the unit test boundary. `provideMockStore({})` covers the NgRx DI requirement.

**Alternatives considered**:
- `NO_ERRORS_SCHEMA`: rejected — silently swallows structural errors, masking template mistakes.
- Real `AppModule`/`appConfig`: rejected for unit tests — triggers full provider chain, violating Principle VI isolation.
- `overrideComponent` to remove router directives: rejected — overly invasive and brittle.

**Implementation pattern**:
```typescript
const createComponent = createComponentFactory({
  component: AppComponent,
  imports: [RouterTestingModule],
  providers: [provideMockStore({})],
  shallow: true,
});
```

---

## R-02: Testing app.routes.ts — Redirect and Lazy-Load Assertions

**Decision**: Use `TestBed.configureTestingModule` with `RouterTestingModule.withRoutes(routes)`, inject `Router` and `Location`, and wrap navigations in `fakeAsync` + `tick()/flush()`.

**Rationale**: `RouterTestingModule.withRoutes(routes)` registers the real application routes in a test-safe in-memory router. Redirect tests are synchronous after `tick()`. Lazy-loaded components (`loadComponent`) require `flush()` to drain the Promise microtask queue. `Location.path()` gives the resolved path after navigation; `router.getCurrentNavigation()` or `router.url` gives the active URL.

**Alternatives considered**:
- Spectator's `createComponentFactory`: overkill — routes don't belong to a component under test.
- Real `appConfig` router: brings full provider chain, conflicts with unit test isolation; reserved for the `[INTÉGRATION]` test only.
- Mocking `Router`: defeats the purpose — we need the real router to test redirects.

**Implementation pattern**:
```typescript
// Unit test
TestBed.configureTestingModule({
  imports: [RouterTestingModule.withRoutes(routes)],
});
const router = TestBed.inject(Router);
const location = TestBed.inject(Location);

it('doit rediriger / vers /workspace', fakeAsync(() => {
  router.navigate(['']);
  tick();
  expect(location.path()).toBe('/workspace');
}));
```

**Lazy-load pattern** — WorkspaceComponent uses `loadComponent`:
```typescript
it('doit charger WorkspaceComponent pour /workspace', fakeAsync(() => {
  router.navigate(['/workspace']);
  flush();  // drains loadComponent Promise
  expect(location.path()).toBe('/workspace');
  // verify active route component via router.routerState.snapshot
}));
```

---

## R-03: Static Inspection of appConfig.providers

**Decision**: Import `appConfig` directly and inspect `appConfig.providers` using JSON serialisation pattern-matching for known identifiers.

**Rationale**: Angular's `provideRouter()`, `provideStore()`, `provideEffects()`, and `provideHttpClient()` return `EnvironmentProviders` — opaque objects with an internal `ɵproviders` array. Direct reference equality (`===`) does not work because each factory call creates a new object. The most practical no-TestBed approach is to serialise the providers array and search for known string markers. In development/test mode (no minification), function names and reducer key strings are preserved in serialisation. The integration test (R-04) validates actual runtime correctness; this static check validates that providers were not accidentally removed from the config.

**Alternatives considered**:
- `TestBed.configureTestingApplication(appConfig)` + `TestBed.inject(Store)`: More reliable but violates the agreed "no TestBed" constraint for `app.config.spec.ts`.
- Reference equality after calling factories with same args: fails — `provideStore({...})` creates a new object each call.
- Counting `appConfig.providers.length`: Too fragile; breaks on any provider addition.

**Implementation pattern**:
```typescript
import { appConfig } from './app.config';

describe('AppConfig', () => {
  const serialized = JSON.stringify(appConfig.providers);

  it('doit inclure provideRouter()', () => {
    // Routes array is embedded in the EnvironmentProviders serialisation
    expect(serialized).toContain('workspace');  // route path marker
  });

  it('doit inclure provideStore() avec les 5 slices', () => {
    expect(serialized).toContain('ingestion');
    expect(serialized).toContain('progress');
    expect(serialized).toContain('crud');
    expect(serialized).toContain('rateLimit');
    expect(serialized).toContain('chat');
  });

  it('doit inclure provideEffects() avec les 5 classes d\'effets', () => {
    expect(serialized).toContain('IngestionEffects');
    expect(serialized).toContain('ProgressEffects');
    expect(serialized).toContain('CrudEffects');
    expect(serialized).toContain('RateLimitEffects');
    expect(serialized).toContain('ChatEffects');
  });

  it('doit inclure provideHttpClient() avec les intercepteurs', () => {
    expect(serialized).toContain('duplicateInterceptor');
    expect(serialized).toContain('rateLimitInterceptor');
  });
});
```

**Risk note**: This approach relies on non-minified serialisation. Tests MUST run in development mode (default for `npm test`). If a future migration minifies test bundles, these assertions must be replaced with `TestBed`-based injection tests.

---

## R-04: Integration Test — Full Bootstrap with Real Providers

**Decision**: Use `TestBed.configureTestingModule` importing `AppComponent` with `appConfig.providers` (excluding `provideStoreDevtools` which is dev-only), then create the component and assert it renders without errors.

**Rationale**: This is the single test that validates the complete provider wiring at runtime. By excluding `provideStoreDevtools` (it uses `localStorage` and Redux DevTools extension checks that fail in jsdom), all other real providers can be activated. `RouterTestingModule` replaces `provideRouter` to prevent real URL navigation in jsdom. The key assertion: `fixture.componentInstance` is truthy and `fixture.nativeElement` contains `<router-outlet>`.

**Implementation pattern**:
```typescript
// [INTÉGRATION] test in app.component.spec.ts
it('[INTÉGRATION] doit se bootstrapper sans erreur d\'injection avec les vrais providers',
  fakeAsync(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent, RouterTestingModule.withRoutes(routes)],
      providers: [
        // real providers from appConfig, minus devtools
        provideStore({ ingestion: ingestionReducer, progress: progressReducer,
                       crud: crudReducer, rateLimit: rateLimitReducer, chat: chatReducer }),
        provideEffects([IngestionEffects, ProgressEffects, CrudEffects,
                        RateLimitEffects, ChatEffects]),
        provideHttpClient(withInterceptors([duplicateInterceptor, rateLimitInterceptor])),
        provideAnimations(),
        provideMarkdown(),
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(AppComponent);
    flush();
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
    expect(fixture.nativeElement.querySelector('router-outlet')).toBeTruthy();
  })
);
```

---

## R-05: Integration Test — Real Router Navigation `/` → `/workspace`

**Decision**: Use `TestBed.configureTestingModule` with `RouterTestingModule.withRoutes(routes)` and navigate from root, asserting final URL is `/workspace`.

**Rationale**: This validates the redirect chain end-to-end using the real Angular router (not a mock). No `AppComponent` mounting required — the router can be tested standalone. `fakeAsync + flush()` drains both the redirect microtask and any lazy-load promise.

**Implementation pattern**:
```typescript
// [INTÉGRATION] test in app.routes.spec.ts
it('[INTÉGRATION] doit naviguer de / vers /workspace avec le vrai router', fakeAsync(() => {
  TestBed.configureTestingModule({
    imports: [RouterTestingModule.withRoutes(routes)],
  });
  const router = TestBed.inject(Router);
  const location = TestBed.inject(Location);

  router.initialNavigation();
  router.navigate(['']);
  flush();

  expect(location.path()).toBe('/workspace');
}));
```

---

## R-06: Migrating app.spec.ts Stub

**Decision**: Delete `app.spec.ts` and migrate its 2 tests into `app.component.spec.ts` under Spectator.

**Rationale**: `app.spec.ts` uses raw `TestBed` (not Spectator), violating Principle VI. Its two tests (`should create the app`, `should have the correct title`) are absorbed by the Spectator-based `app.component.spec.ts` as `doit créer l'application` and re-expressed through the component instance check.

**Migration map**:
| app.spec.ts (old) | app.component.spec.ts (new) |
|---|---|
| `should create the app` | `doit créer l'application` (US1.1) |
| `should have the correct title` | Absorbed into US1.1 — title is an internal property, not a user-visible concern; no dedicated test needed |
