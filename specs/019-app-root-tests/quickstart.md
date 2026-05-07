# Quickstart: PHASE 14 — App Root Integration Tests

**Branch**: `019-app-root-tests` | **Date**: 2026-05-06

---

## Prerequisites

All prior phases (011–018) must be committed. No new npm packages are required — all testing utilities are already installed.

```bash
# Verify test runner is operational
cd agentic-rag-ui
npm test -- --run   # run once, no watch mode
```

---

## Files to Create

| File | Location | Tests |
|------|----------|-------|
| `app.component.spec.ts` | `src/app/` | 5 (4 unit + 1 integration) |
| `app.routes.spec.ts` | `src/app/` | 5 (4 unit + 1 integration) |
| `app.config.spec.ts` | `src/app/` | 3 unit |

## File to Delete

```bash
# Remove the existing raw-TestBed stub (superseded by the 3 new spec files)
rm src/app/app.spec.ts
```

---

## Implementation Order

Work through the 3 spec files in this order (increasing complexity):

### Step 1 — `app.config.spec.ts` (static, no TestBed)

Simplest file — pure import and serialisation check. Write first to validate the provider markers before spending time on DOM tests.

```typescript
import { appConfig } from './app.config';

describe('AppConfig', () => {
  const serialized = JSON.stringify(appConfig.providers);

  it('doit inclure provideRouter()', () => {
    expect(serialized).toContain('workspace');
  });

  it('doit inclure provideStore() avec les 5 slices', () => {
    ['ingestion', 'progress', 'crud', 'rateLimit', 'chat'].forEach(slice => {
      expect(serialized).toContain(slice);
    });
  });

  it('doit inclure provideEffects() avec les 5 classes d\'effets', () => {
    ['IngestionEffects', 'ProgressEffects', 'CrudEffects',
     'RateLimitEffects', 'ChatEffects'].forEach(cls => {
      expect(serialized).toContain(cls);
    });
  });

  it('doit inclure provideHttpClient() avec les intercepteurs', () => {
    expect(serialized).toContain('duplicateInterceptor');
    expect(serialized).toContain('rateLimitInterceptor');
  });
});
```

Run: `npm test -- --run --reporter=verbose src/app/app.config.spec.ts`

---

### Step 2 — `app.routes.spec.ts` (router unit + 1 integration)

```typescript
import { TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Location } from '@angular/common';
import { RouterTestingModule } from '@angular/router/testing';
import { routes } from './app.routes';

describe('AppRoutes', () => {
  let router: Router;
  let location: Location;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RouterTestingModule.withRoutes(routes)],
    });
    router = TestBed.inject(Router);
    location = TestBed.inject(Location);
    router.initialNavigation();
  });

  it('doit rediriger / vers /workspace', fakeAsync(() => {
    router.navigate(['']);
    tick();
    expect(location.path()).toBe('/workspace');
  }));

  it('doit charger WorkspaceComponent pour /workspace', fakeAsync(() => {
    router.navigate(['/workspace']);
    flush();
    expect(location.path()).toBe('/workspace');
  }));

  it('doit rediriger /management (route inactive) vers /workspace', fakeAsync(() => {
    router.navigate(['/management']);
    tick();
    expect(location.path()).toBe('/workspace');
  }));

  it('doit rediriger une route inconnue vers /workspace via le wildcard', fakeAsync(() => {
    router.navigate(['/unknown-path']);
    tick();
    expect(location.path()).toBe('/workspace');
  }));

  it('[INTÉGRATION] doit naviguer de / vers /workspace avec le vrai router', fakeAsync(() => {
    router.navigate(['']);
    flush();
    expect(location.path()).toBe('/workspace');
    expect(router.url).toBe('/workspace');
  }));
});
```

Run: `npm test -- --run --reporter=verbose src/app/app.routes.spec.ts`

---

### Step 3 — `app.component.spec.ts` (Spectator unit + 1 integration)

```typescript
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { RouterTestingModule } from '@angular/router/testing';
import { provideMockStore } from '@ngrx/store/testing';
import { AppComponent } from './app.component';
// Integration imports (real providers):
import { fakeAsync, flush, TestBed } from '@angular/core/testing';
import { provideStore } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideMarkdown } from 'ngx-markdown';
import { routes } from './app.routes';
import { ingestionReducer } from './features/ingestion/store/ingestion/ingestion.reducer';
import { progressReducer } from './features/ingestion/store/progress/progress.reducer';
import { crudReducer } from './features/ingestion/store/crud/crud.reducer';
import { rateLimitReducer } from './features/ingestion/store/rate-limit/rate-limit.reducer';
import { chatReducer } from './features/chat/store/chat.reducer';
import { IngestionEffects } from './features/ingestion/store/ingestion/ingestion.effects';
import { ProgressEffects } from './features/ingestion/store/progress/progress.effects';
import { CrudEffects } from './features/ingestion/store/crud/crud.effects';
import { RateLimitEffects } from './features/ingestion/store/rate-limit/rate-limit.effects';
import { ChatEffects } from './features/chat/store/chat.effects';
import { duplicateInterceptor } from './core/interceptors/duplicate-interceptor';
import { rateLimitInterceptor } from './core/interceptors/rate-limit.interceptor';

describe('AppComponent', () => {
  const createComponent = createComponentFactory({
    component: AppComponent,
    imports: [RouterTestingModule],
    providers: [provideMockStore({})],
    shallow: true,
  });
  let spectator: Spectator<AppComponent>;

  beforeEach(() => spectator = createComponent());

  it('doit créer l\'application', () => {
    expect(spectator.component).toBeTruthy();
  });

  it('doit contenir un <router-outlet>', () => {
    expect(spectator.query('router-outlet')).toBeTruthy();
  });

  it('doit afficher le <app-toast-container>', () => {
    expect(spectator.query('app-toast-container')).toBeTruthy();
  });

  it('doit afficher les liens Workspace et Management dans la navbar', () => {
    const links = spectator.queryAll('a.nav-link');
    const hrefs = links.map(l => l.getAttribute('routerLink') ?? l.getAttribute('href'));
    expect(hrefs).toContain('/workspace');
    expect(hrefs).toContain('/management');
  });

  it('[INTÉGRATION] doit se bootstrapper sans erreur d\'injection avec les vrais providers',
    fakeAsync(async () => {
      await TestBed.configureTestingModule({
        imports: [AppComponent, RouterTestingModule.withRoutes(routes)],
        providers: [
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
});
```

Run: `npm test -- --run --reporter=verbose src/app/app.component.spec.ts`

---

## Run All Phase 14 Tests

```bash
# All 3 spec files at once
npm test -- --run --reporter=verbose --include="src/app/app.*.spec.ts"

# With coverage
npm test -- --run --coverage --include="src/app/app.*.spec.ts"
```

---

## Coverage Targets

| Metric | Target | Files |
|--------|--------|-------|
| Statements | ≥ 80% | `app.component.ts`, `app.routes.ts`, `app.config.ts` |
| Branches | ≥ 75% | Same |
| Functions | ≥ 85% | Same |
| Lines | ≥ 80% | Same |

---

## Commit Command

```bash
git add src/app/app.component.spec.ts \
        src/app/app.routes.spec.ts \
        src/app/app.config.spec.ts
git rm src/app/app.spec.ts
git commit -m "test(phase-14): add app.component/routes/config specs — shell, routing, provider coverage"
```

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| `NullInjectorError: No provider for Router` | Missing `RouterTestingModule` in createComponentFactory | Add `imports: [RouterTestingModule]` |
| `RouterTestingModule.withRoutes` not resolving lazy component | Missing `flush()` after `router.navigate()` | Replace `tick()` with `flush()` for lazy routes |
| Static inspection assertions fail | `appConfig.providers` serialises differently | Check that function names (e.g., `IngestionEffects`) appear in the non-minified output; ensure `npm test` runs in development mode |
| Integration test `provideStoreDevtools` error | DevTools tries to access localStorage/Redux extension | Exclude `provideStoreDevtools` from the integration test provider list |
| `app-toast-container` not found in shallow mode | Spectator `shallow: true` stubs child components by selector | Query the selector string `'app-toast-container'` (the Angular selector, not the tag name) |
