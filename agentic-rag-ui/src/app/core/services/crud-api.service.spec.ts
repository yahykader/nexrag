// crud-api.service.spec.ts — Phase 3, User Story 1
// Vérifie les contrats HTTP de CrudApiService

import { createHttpFactory, SpectatorHttp } from '@ngneat/spectator/vitest';
import { HttpTestingController } from '@angular/common/http/testing';
import { CrudApiService } from './crud-api.service';

describe('CrudApiService', () => {
  let spectator: SpectatorHttp<CrudApiService>;
  let controller: HttpTestingController;

  const createHttp = createHttpFactory(CrudApiService);

  beforeEach(() => {
    spectator = createHttp();
    controller = spectator.controller;
  });

  afterEach(() => {
    controller.verify();
  });

  // ─── US1 — AC1 ────────────────────────────────────────────────────────────

  it('doit appeler DELETE /api/v1/crud/file/:id?type=text pour deleteFile', () => {
    spectator.service.deleteFile('emb-001', 'text').subscribe();

    const req = controller.expectOne('/api/v1/crud/file/emb-001?type=text');
    expect(req.request.method).toBe('DELETE');
    req.flush({ success: true, deletedCount: 1, message: 'ok' });
  });

  // ─── US1 — AC2 ────────────────────────────────────────────────────────────

  it('doit appeler DELETE /api/v1/crud/batch/:id/files pour deleteBatch', () => {
    spectator.service.deleteBatch('batch-42').subscribe();

    const req = controller.expectOne('/api/v1/crud/batch/batch-42/files');
    expect(req.request.method).toBe('DELETE');
    req.flush({ success: true, deletedCount: 5, message: 'ok' });
  });

  // ─── US1 — AC3 ────────────────────────────────────────────────────────────

  it('doit appeler DELETE avec body pour deleteTextBatch', () => {
    const ids = ['emb-1', 'emb-2'];
    spectator.service.deleteTextBatch(ids).subscribe();

    const req = controller.expectOne('/api/v1/crud/files/text/batch');
    expect(req.request.method).toBe('DELETE');
    expect(req.request.body).toEqual(ids);
    req.flush({ success: true, deletedCount: 2, message: 'ok' });
  });

  // ─── US1 — AC4 ────────────────────────────────────────────────────────────

  it('doit appeler DELETE avec body pour deleteImageBatch', () => {
    const ids = ['img-1', 'img-2'];
    spectator.service.deleteImageBatch(ids).subscribe();

    const req = controller.expectOne('/api/v1/crud/files/image/batch');
    expect(req.request.method).toBe('DELETE');
    expect(req.request.body).toEqual(ids);
    req.flush({ success: true, deletedCount: 2, message: 'ok' });
  });

  // ─── US1 — AC5 ────────────────────────────────────────────────────────────

  it('doit appeler DELETE /api/v1/crud/files/all?confirmation=... pour deleteAllFiles', () => {
    spectator.service.deleteAllFiles('CONFIRM_DELETE_ALL').subscribe();

    const req = controller.expectOne(
      '/api/v1/crud/files/all?confirmation=CONFIRM_DELETE_ALL',
    );
    expect(req.request.method).toBe('DELETE');
    req.flush({ success: true, deletedCount: 100, message: 'ok' });
  });

  // ─── US1 — AC6 ────────────────────────────────────────────────────────────

  it('doit envoyer un FormData en POST pour checkDuplicate', () => {
    const file = new File(['content'], 'test.pdf', { type: 'application/pdf' });
    spectator.service.checkDuplicate(file).subscribe();

    const req = controller.expectOne('/api/v1/crud/check-duplicate');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeInstanceOf(FormData);
    expect(req.request.body.get('file')).toBe(file);
    req.flush({ isDuplicate: false, filename: 'test.pdf', message: 'ok' });
  });

  // ─── US1 — AC7 ────────────────────────────────────────────────────────────

  it('doit appeler GET /api/v1/crud/batch/:id/info pour getBatchInfo', () => {
    spectator.service.getBatchInfo('batch-7').subscribe();

    const req = controller.expectOne('/api/v1/crud/batch/batch-7/info');
    expect(req.request.method).toBe('GET');
    req.flush({
      found: true,
      batchId: 'batch-7',
      textEmbeddings: 10,
      imageEmbeddings: 2,
      totalEmbeddings: 12,
      message: 'ok',
    });
  });

  // ─── US1 — AC8 ────────────────────────────────────────────────────────────

  it('doit appeler GET /api/v1/crud/stats/system pour getSystemStats', () => {
    spectator.service.getSystemStats().subscribe();

    const req = controller.expectOne('/api/v1/crud/stats/system');
    expect(req.request.method).toBe('GET');
    req.flush({ totalDocuments: 42 });
  });
});
