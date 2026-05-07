import { TestBed } from '@angular/core/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { RouterTestingModule } from '@angular/router/testing';
import { provideStore } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { provideMockStore } from '@ngrx/store/testing';
import { provideMarkdown } from 'ngx-markdown';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { beforeEach, describe, expect, it } from 'vitest';

import { AppComponent } from './app.component';
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

  beforeEach(() => {
    spectator = createComponent();
  });

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
    const allText = links.map(l => l.textContent ?? '').join(' ');
    expect(allText.toLowerCase()).toContain('workspace');
    expect(allText.toLowerCase()).toContain('management');
  });

  it('[INTÉGRATION] doit se bootstrapper sans erreur d\'injection avec les vrais providers',
    async () => {
      TestBed.resetTestingModule();
      await TestBed.configureTestingModule({
        imports: [
          AppComponent,
          RouterTestingModule.withRoutes(routes),
        ],
        providers: [
          provideStore({
            ingestion: ingestionReducer,
            progress: progressReducer,
            crud: crudReducer,
            rateLimit: rateLimitReducer,
            chat: chatReducer,
          }),
          provideEffects([
            IngestionEffects,
            ProgressEffects,
            CrudEffects,
            RateLimitEffects,
            ChatEffects,
          ]),
          provideHttpClient(
            withInterceptors([duplicateInterceptor, rateLimitInterceptor])
          ),
          provideAnimations(),
          provideMarkdown(),
        ],
      }).compileComponents();

      const fixture = TestBed.createComponent(AppComponent);
      await fixture.whenStable();
      fixture.detectChanges();

      expect(fixture.componentInstance).toBeTruthy();
      expect(fixture.nativeElement.querySelector('router-outlet')).toBeTruthy();
    }
  );
});
