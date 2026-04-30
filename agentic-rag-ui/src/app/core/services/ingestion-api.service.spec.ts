// ingestion-api.service.spec.ts — Phase 3, User Story 2
// Vérifie les contrats HTTP de IngestionApiService (FormData + propagation d'erreurs)

import { createHttpFactory, SpectatorHttp } from '@ngneat/spectator/vitest';
import { HttpTestingController } from '@angular/common/http/testing';
import { IngestionApiService } from './ingestion-api.service';

describe('IngestionApiService', () => {
  let spectator: SpectatorHttp<IngestionApiService>;
  let controller: HttpTestingController;

  const createHttp = createHttpFactory(IngestionApiService);

  beforeEach(() => {
    spectator = createHttp();
    controller = spectator.controller;
  });

  afterEach(() => {
    controller.verify();
  });

  // ─── US2 — AC1 ────────────────────────────────────────────────────────────

  it('doit envoyer un FormData sans batchId quand uploadFile est appelé sans batchId', () => {
    const file = new File(['content'], 'doc.pdf', { type: 'application/pdf' });
    spectator.service.uploadFile(file).subscribe();

    const req = controller.expectOne('/api/v1/ingestion/upload');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeInstanceOf(FormData);
    expect(req.request.body.get('file')).toBe(file);
    expect(req.request.body.has('batchId')).toBe(false);
    req.flush({
      success: true,
      batchId: 'b1',
      filename: 'doc.pdf',
      fileSize: 100,
      textEmbeddings: 5,
      imageEmbeddings: 0,
      durationMs: 200,
      streamingUsed: false,
      message: 'ok',
      duplicate: false,
    });
  });

  // ─── US2 — AC2 ────────────────────────────────────────────────────────────

  it('doit inclure batchId dans le FormData quand fourni à uploadFile', () => {
    const file = new File(['content'], 'doc.pdf', { type: 'application/pdf' });
    spectator.service.uploadFile(file, 'batch-7').subscribe();

    const req = controller.expectOne('/api/v1/ingestion/upload');
    expect(req.request.body.get('batchId')).toBe('batch-7');
    req.flush({
      success: true,
      batchId: 'batch-7',
      filename: 'doc.pdf',
      fileSize: 100,
      textEmbeddings: 5,
      imageEmbeddings: 0,
      durationMs: 200,
      streamingUsed: false,
      message: 'ok',
      duplicate: false,
    });
  });

  // ─── US2 — AC3 ────────────────────────────────────────────────────────────

  it('doit appeler POST /api/v1/ingestion/upload/async pour uploadFileAsync', () => {
    const file = new File(['data'], 'img.png', { type: 'image/png' });
    spectator.service.uploadFileAsync(file).subscribe();

    const req = controller.expectOne('/api/v1/ingestion/upload/async');
    expect(req.request.method).toBe('POST');
    req.flush({
      accepted: true,
      batchId: 'b2',
      filename: 'img.png',
      message: 'accepted',
      statusUrl: '/api/v1/ingestion/status/b2',
      duplicate: false,
    });
  });

  // ─── US2 — AC4 ────────────────────────────────────────────────────────────

  it('doit envoyer plusieurs fichiers sous la clé "files" pour uploadBatchAsync', () => {
    const f1 = new File(['a'], 'a.pdf', { type: 'application/pdf' });
    const f2 = new File(['b'], 'b.pdf', { type: 'application/pdf' });
    spectator.service.uploadBatchAsync([f1, f2]).subscribe();

    const req = controller.expectOne('/api/v1/ingestion/upload/batch/async');
    expect(req.request.method).toBe('POST');
    const body: FormData = req.request.body;
    const files = body.getAll('files');
    expect(files).toHaveLength(2);
    expect(files[0]).toBe(f1);
    expect(files[1]).toBe(f2);
    req.flush({
      accepted: true,
      batchId: 'b3',
      filename: 'batch',
      message: 'accepted',
      statusUrl: '/api/v1/ingestion/status/b3',
      duplicate: false,
    });
  });

  // ─── US2 — AC5 ────────────────────────────────────────────────────────────

  it('doit propager l\'erreur observable quand le serveur répond 422', () => {
    const file = new File(['bad'], 'bad.pdf', { type: 'application/pdf' });
    let capturedError: any;

    spectator.service.uploadFile(file).subscribe({
      next: () => { throw new Error('aucune réponse success attendue'); },
      error: (err) => (capturedError = err),
    });

    controller
      .expectOne('/api/v1/ingestion/upload')
      .flush({ message: 'Unprocessable' }, { status: 422, statusText: 'Unprocessable Entity' });

    expect(capturedError).toBeDefined();
    expect(capturedError.status).toBe(422);
  });

  // ─── US2 — AC6 ────────────────────────────────────────────────────────────

  it('doit appeler GET /api/v1/ingestion/status/:id pour getBatchStatus', () => {
    spectator.service.getBatchStatus('batch-99').subscribe();

    const req = controller.expectOne('/api/v1/ingestion/status/batch-99');
    expect(req.request.method).toBe('GET');
    req.flush({
      found: true,
      batchId: 'batch-99',
      textEmbeddings: 3,
      imageEmbeddings: 1,
      totalEmbeddings: 4,
      message: 'ok',
    });
  });

  // ─── US2 — AC7 ────────────────────────────────────────────────────────────

  it('doit appeler DELETE /api/v1/ingestion/rollback/:id pour rollbackBatch', () => {
    spectator.service.rollbackBatch('batch-55').subscribe();

    const req = controller.expectOne('/api/v1/ingestion/rollback/batch-55');
    expect(req.request.method).toBe('DELETE');
    req.flush({ success: true, message: 'rolled back' });
  });
});
