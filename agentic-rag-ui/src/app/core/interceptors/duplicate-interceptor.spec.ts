// duplicate-interceptor.spec.ts — Phase 2, User Story 1
// Vérifie que duplicateInterceptor enrichit les erreurs 409 et laisse passer les autres

import { createServiceFactory, SpectatorService } from '@ngneat/spectator/vitest';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { duplicateInterceptor } from './duplicate-interceptor';

describe('DuplicateInterceptor', () => {
  let spectator: SpectatorService<HttpClient>;
  let controller: HttpTestingController;

  const createService = createServiceFactory({
    service: HttpClient,
    providers: [
      provideHttpClient(withInterceptors([duplicateInterceptor])),
      provideHttpClientTesting(),
    ],
  });

  beforeEach(() => {
    spectator = createService();
    controller = spectator.inject(HttpTestingController);
  });

  afterEach(() => {
    controller.verify();
  });

  // ─── US1 — AC1 ────────────────────────────────────────────────────────────

  it('doit enrichir l\'erreur avec isDuplicate=true, status=409 et data normalisée quand le serveur répond 409', () => {
    let capturedError: any;

    spectator.service.get('/api/test').subscribe({
      next: () => { throw new Error('aucune réponse success attendue'); },
      error: (err) => (capturedError = err),
    });

    controller.expectOne('/api/test').flush(
      {
        filename: 'document.pdf',
        batchId: 'batch-001',
        existingBatchId: 'batch-000',
        message: 'Ce fichier existe déjà',
      },
      { status: 409, statusText: 'Conflict' },
    );

    expect(capturedError).toBeDefined();
    expect(capturedError.isDuplicate).toBe(true);
    expect(capturedError.status).toBe(409);
    expect(capturedError.data.filename).toBe('document.pdf');
    expect(capturedError.data.batchId).toBe('batch-001');
    expect(capturedError.data.existingBatchId).toBe('batch-000');
    expect(capturedError.data.message).toBe('Ce fichier existe déjà');
    expect(capturedError.originalError).toBeDefined();
  });

  // ─── US1 — AC2 ────────────────────────────────────────────────────────────

  it('doit utiliser "Unknown" pour filename quand le champ est absent du body 409', () => {
    let capturedError: any;

    spectator.service.get('/api/test').subscribe({
      error: (err) => (capturedError = err),
    });

    controller.expectOne('/api/test').flush(
      {},
      { status: 409, statusText: 'Conflict' },
    );

    expect(capturedError.data.filename).toBe('Unknown');
  });

  // ─── US1 — AC3 ────────────────────────────────────────────────────────────

  it('doit utiliser null pour batchId et existingBatchId quand les champs sont absents du body 409', () => {
    let capturedError: any;

    spectator.service.get('/api/test').subscribe({
      error: (err) => (capturedError = err),
    });

    controller.expectOne('/api/test').flush(
      {},
      { status: 409, statusText: 'Conflict' },
    );

    expect(capturedError.data.batchId).toBeNull();
    expect(capturedError.data.existingBatchId).toBeNull();
  });

  // ─── US1 — AC4 ────────────────────────────────────────────────────────────

  it('doit laisser passer la réponse 200 sans transformation ni erreur', () => {
    let capturedResponse: any;
    let capturedError: any;

    spectator.service.get('/api/test').subscribe({
      next: (res) => (capturedResponse = res),
      error: (err) => (capturedError = err),
    });

    controller.expectOne('/api/test').flush({ ok: true }, { status: 200, statusText: 'OK' });

    expect(capturedResponse).toEqual({ ok: true });
    expect(capturedError).toBeUndefined();
  });

  // ─── US1 — AC5 ────────────────────────────────────────────────────────────

  it('doit retransmettre l\'erreur originale sans modification quand le statut est 500', () => {
    let capturedError: any;

    spectator.service.get('/api/test').subscribe({
      error: (err) => (capturedError = err),
    });

    controller.expectOne('/api/test').flush(
      { error: 'Internal Server Error' },
      { status: 500, statusText: 'Internal Server Error' },
    );

    // L'intercepteur ne doit pas enrichir les erreurs non-409
    expect(capturedError.isDuplicate).toBeUndefined();
    expect(capturedError.status).toBe(500);
  });
});
