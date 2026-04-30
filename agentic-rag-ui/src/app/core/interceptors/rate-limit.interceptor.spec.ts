// rate-limit.interceptor.spec.ts — Phase 2, User Stories 2 / 3 / 4
// Teste l'intercepteur directement via TestBed.runInInjectionContext() pour éviter
// les conflits entre inject(Store) et l'HTTP testing backend.

import { TestBed } from '@angular/core/testing';
import {
  HttpEventType,
  HttpRequest,
  HttpResponse,
  HttpErrorResponse,
  HttpHeaders,
} from '@angular/common/http';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { Observable, of, throwError } from 'rxjs';

import { rateLimitInterceptor } from './rate-limit.interceptor';
import * as RateLimitActions from '../../features/ingestion/store/rate-limit/rate-limit.actions';

describe('RateLimitInterceptor', () => {
  let store: MockStore;
  let localStorageMock: { getItem: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideMockStore()],
    });

    store = TestBed.inject(MockStore);

    // Remplace localStorage globalement pour éviter les conflits jsdom/vitest.
    // vi.spyOn(Storage.prototype, 'getItem') brise localStorage.getItem dans cet
    // environnement ; vi.stubGlobal remplace directement globalThis.localStorage.
    localStorageMock = { getItem: vi.fn().mockReturnValue(null) };
    vi.stubGlobal('localStorage', localStorageMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  /** Helper : appelle l'intercepteur dans le contexte d'injection du TestBed. */
  function runInterceptor<T>(
    req: HttpRequest<T>,
    nextFn: (r: HttpRequest<T>) => Observable<any>,
  ): Promise<{ value?: any; error?: any }> {
    return new Promise((resolve) => {
      TestBed.runInInjectionContext(() => {
        rateLimitInterceptor(req as any, nextFn as any).subscribe({
          next: (value: any) => resolve({ value }),
          error: (error: any) => resolve({ error }),
        });
      });
    });
  }

  // ─── US4 — 429 Handling (P1) ─────────────────────────────────────────────

  it('doit dispatcher rateLimitExceeded avec message et retryAfterSeconds quand le statut est 429', async () => {
    const dispatchSpy = vi.spyOn(store, 'dispatch');
    const req = new HttpRequest('GET', '/api/test');
    const httpError = new HttpErrorResponse({
      status: 429,
      error: { message: 'Rate limit dépassé', retryAfterSeconds: 60 },
    });

    await runInterceptor(req, () => throwError(() => httpError));

    expect(dispatchSpy).toHaveBeenCalledWith(
      RateLimitActions.rateLimitExceeded({
        message: 'Rate limit dépassé',
        retryAfterSeconds: 60,
      }),
    );
  });

  it('doit utiliser retryAfterSeconds=60 par défaut quand le champ est absent du body 429', async () => {
    const dispatchSpy = vi.spyOn(store, 'dispatch');
    const req = new HttpRequest('GET', '/api/test');
    const httpError = new HttpErrorResponse({ status: 429, error: {} });

    await runInterceptor(req, () => throwError(() => httpError));

    // Utiliser objectContaining directement comme matcher (pas imbriqué dans le créateur d'action)
    expect(dispatchSpy).toHaveBeenCalledWith(
      expect.objectContaining({ retryAfterSeconds: 60 }),
    );
  });

  it('doit retransmettre l\'erreur 429 sans la swallower', async () => {
    const req = new HttpRequest('GET', '/api/test');
    const httpError = new HttpErrorResponse({
      status: 429,
      error: { message: 'Rate limit dépassé', retryAfterSeconds: 30 },
    });

    const result = await runInterceptor(req, () => throwError(() => httpError));

    expect(result.error).toBeDefined();
    expect(result.error.status).toBe(429);
  });

  it('doit ne pas dispatcher rateLimitExceeded pour une réponse 200 normale', async () => {
    const dispatchSpy = vi.spyOn(store, 'dispatch');
    const req = new HttpRequest('GET', '/api/test');
    const response = new HttpResponse({ status: 200 });

    await runInterceptor(req, () => of(response));

    const exceededCalls = dispatchSpy.mock.calls.filter(
      (call) => (call[0] as any)?.type === '[Rate Limit] Exceeded',
    );
    expect(exceededCalls).toHaveLength(0);
  });

  it('doit retransmettre les erreurs non-429 sans dispatcher rateLimitExceeded (branche catchError FALSE)', async () => {
    const dispatchSpy = vi.spyOn(store, 'dispatch');
    const req = new HttpRequest('GET', '/api/test');
    const httpError = new HttpErrorResponse({ status: 500, error: 'Internal Server Error' });

    const result = await runInterceptor(req, () => throwError(() => httpError));

    expect(result.error).toBeDefined();
    expect(result.error.status).toBe(500);
    const exceededCalls = dispatchSpy.mock.calls.filter(
      (call) => (call[0] as any)?.type === '[Rate Limit] Exceeded',
    );
    expect(exceededCalls).toHaveLength(0);
  });

  // ─── US2 — Header Injection (P2) ─────────────────────────────────────────

  it('doit ajouter le header X-User-Id quand userId est présent dans localStorage', async () => {
    localStorageMock.getItem.mockReturnValue('user-42');
    const req = new HttpRequest('GET', '/api/test');
    let capturedReq: HttpRequest<any> | undefined;

    await runInterceptor(req, (r) => {
      capturedReq = r as HttpRequest<any>;
      return of(new HttpResponse({ status: 200 }));
    });

    expect(capturedReq?.headers.get('X-User-Id')).toBe('user-42');
  });

  it('doit ne pas ajouter le header X-User-Id quand userId est absent de localStorage', async () => {
    // getItemSpy retourne null par défaut (configuré dans beforeEach)
    const req = new HttpRequest('GET', '/api/test');
    let capturedReq: HttpRequest<any> | undefined;

    await runInterceptor(req, (r) => {
      capturedReq = r as HttpRequest<any>;
      return of(new HttpResponse({ status: 200 }));
    });

    expect(capturedReq?.headers.has('X-User-Id')).toBe(false);
  });

  it('doit ne pas modifier l\'objet requête original (immutabilité)', async () => {
    localStorageMock.getItem.mockReturnValue('user-immutable');
    const original = new HttpRequest('GET', '/api/test');
    let capturedReq: HttpRequest<any> | undefined;

    await runInterceptor(original, (r) => {
      capturedReq = r as HttpRequest<any>;
      return of(new HttpResponse({ status: 200 }));
    });

    // L'intercepteur clone la requête ; l'original n'a pas le header
    expect(original.headers.has('X-User-Id')).toBe(false);
    // Le clone transmis à next porte bien le header
    expect(capturedReq?.headers.get('X-User-Id')).toBe('user-immutable');
  });

  // ─── US3 — Remaining-Token Tracking (P2) ─────────────────────────────────

  it('doit dispatcher updateRemainingTokens avec endpoint=upload pour une URL /api/v1/upload/...', async () => {
    const dispatchSpy = vi.spyOn(store, 'dispatch');
    const req = new HttpRequest('GET', '/api/v1/upload/doc.pdf');
    const response = new HttpResponse({
      status: 200,
      headers: new HttpHeaders({ 'X-RateLimit-Remaining': '7' }),
    });

    await runInterceptor(req, () => of(response));

    expect(dispatchSpy).toHaveBeenCalledWith(
      RateLimitActions.updateRemainingTokens({ endpoint: 'upload', remaining: 7 }),
    );
  });

  it('doit dispatcher updateRemainingTokens avec endpoint=batch pour une URL /api/v1/upload/batch/...', async () => {
    const dispatchSpy = vi.spyOn(store, 'dispatch');
    const req = new HttpRequest('GET', '/api/v1/upload/batch/123');
    const response = new HttpResponse({
      status: 200,
      headers: new HttpHeaders({ 'X-RateLimit-Remaining': '5' }),
    });

    await runInterceptor(req, () => of(response));

    expect(dispatchSpy).toHaveBeenCalledWith(
      RateLimitActions.updateRemainingTokens({ endpoint: 'batch', remaining: 5 }),
    );
  });

  it('doit dispatcher updateRemainingTokens avec endpoint=search pour une URL /api/v1/search/...', async () => {
    const dispatchSpy = vi.spyOn(store, 'dispatch');
    const req = new HttpRequest('GET', '/api/v1/search/query');
    const response = new HttpResponse({
      status: 200,
      headers: new HttpHeaders({ 'X-RateLimit-Remaining': '20' }),
    });

    await runInterceptor(req, () => of(response));

    expect(dispatchSpy).toHaveBeenCalledWith(
      RateLimitActions.updateRemainingTokens({ endpoint: 'search', remaining: 20 }),
    );
  });

  it('doit dispatcher updateRemainingTokens avec endpoint=delete pour une URL /api/v1/delete/...', async () => {
    const dispatchSpy = vi.spyOn(store, 'dispatch');
    const req = new HttpRequest('GET', '/api/v1/delete/doc-1');
    const response = new HttpResponse({
      status: 200,
      headers: new HttpHeaders({ 'X-RateLimit-Remaining': '10' }),
    });

    await runInterceptor(req, () => of(response));

    expect(dispatchSpy).toHaveBeenCalledWith(
      RateLimitActions.updateRemainingTokens({ endpoint: 'delete', remaining: 10 }),
    );
  });

  it('doit dispatcher updateRemainingTokens avec endpoint=default pour une URL sans pattern reconnu', async () => {
    const dispatchSpy = vi.spyOn(store, 'dispatch');
    const req = new HttpRequest('GET', '/api/v1/other/resource');
    const response = new HttpResponse({
      status: 200,
      headers: new HttpHeaders({ 'X-RateLimit-Remaining': '30' }),
    });

    await runInterceptor(req, () => of(response));

    expect(dispatchSpy).toHaveBeenCalledWith(
      RateLimitActions.updateRemainingTokens({ endpoint: 'default', remaining: 30 }),
    );
  });

  it('doit ne pas dispatcher updateRemainingTokens pour un événement HTTP sans propriété headers (branche tap FALSE)', async () => {
    const dispatchSpy = vi.spyOn(store, 'dispatch');
    const req = new HttpRequest('GET', '/api/v1/upload/doc.pdf');
    // HttpSentEvent: événement intermédiaire sans propriété 'headers'
    const sentEvent = { type: HttpEventType.Sent };

    await runInterceptor(req, () => of(sentEvent as any));

    const tokenCalls = dispatchSpy.mock.calls.filter(
      (call) => (call[0] as any)?.type === '[Rate Limit] Update Remaining Tokens',
    );
    expect(tokenCalls).toHaveLength(0);
  });

  it('doit ne pas dispatcher updateRemainingTokens si le header X-RateLimit-Remaining est absent', async () => {
    const dispatchSpy = vi.spyOn(store, 'dispatch');
    const req = new HttpRequest('GET', '/api/v1/upload/doc.pdf');
    const response = new HttpResponse({ status: 200 }); // Pas de header X-RateLimit-Remaining

    await runInterceptor(req, () => of(response));

    const tokenCalls = dispatchSpy.mock.calls.filter(
      (call) => (call[0] as any)?.type === '[Rate Limit] Update Remaining Tokens',
    );
    expect(tokenCalls).toHaveLength(0);
  });
});
