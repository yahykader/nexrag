import { Location } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { beforeEach, describe, expect, it } from 'vitest';

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

  it('doit rediriger / vers /workspace', async () => {
    await router.navigate(['']);
    expect(location.path()).toBe('/workspace');
  });

  it('doit charger WorkspaceComponent pour /workspace', async () => {
    await router.navigate(['/workspace']);
    expect(location.path()).toBe('/workspace');
  });

  it('doit rediriger /management (route inactive) vers /workspace via le wildcard', async () => {
    await router.navigate(['/management']);
    expect(location.path()).toBe('/workspace');
  });

  it('doit rediriger une route inconnue vers /workspace via le wildcard', async () => {
    await router.navigate(['/unknown-path']);
    expect(location.path()).toBe('/workspace');
  });

  it('[INTÉGRATION] doit naviguer de / vers /workspace avec le vrai router', async () => {
    await router.navigate(['']);
    expect(location.path()).toBe('/workspace');
    expect(router.url).toBe('/workspace');
  });
});
