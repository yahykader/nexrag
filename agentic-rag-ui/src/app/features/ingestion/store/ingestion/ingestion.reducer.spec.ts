import { describe, it, expect } from 'vitest';
import { ingestionReducer } from './ingestion.reducer';
import { initialState } from './ingestion.state';
import * as IngestionActions from './ingestion.actions';
import * as CrudActions from '../crud/crud.actions';
import * as ProgressActions from '../progress/progress.actions';
import { mockUploadFile, mockUploadProgress } from '../../../../test-helpers';

describe('IngestionReducer', () => {
  it('doit retourner l\'état initial', () => {
    const state = ingestionReducer(undefined, { type: '@@INIT' });
    expect(state).toEqual(initialState);
  });

  it('addFilesToUpload doit ajouter des fichiers avec status pending', () => {
    const files = [new File(['content'], 'test.pdf')];
    const state = ingestionReducer(initialState, IngestionActions.addFilesToUpload({ files }));
    expect(state.uploads).toHaveLength(1);
    expect(state.uploads[0].status).toBe('pending');
    expect(state.stats.total).toBe(1);
  });

  it('uploadFileAsync doit passer un upload à status uploading et incrémenter activeUploads', () => {
    const withUpload = ingestionReducer(
      { ...initialState, uploads: [mockUploadFile({ id: 'f1' })] },
      IngestionActions.uploadFileAsync({ fileId: 'f1', file: new File(['c'], 'test.pdf') })
    );
    const upload = withUpload.uploads.find(u => u.id === 'f1');
    expect(upload?.status).toBe('uploading');
    expect(withUpload.activeUploads).toBe(1);
  });

  it('uploadFileRateLimited doit passer status à rate-limited et incrémenter stats.rateLimited', () => {
    const withUpload = { ...initialState, uploads: [mockUploadFile({ id: 'f1', status: 'uploading', })] };
    const state = ingestionReducer(withUpload, IngestionActions.uploadFileRateLimited({
      fileId: 'f1', retryAfterSeconds: 60, message: 'Rate limit'
    }));
    const upload = state.uploads.find(u => u.id === 'f1');
    expect(upload?.status).toBe('rate-limited');
    expect(upload?.retryAfterSeconds).toBe(60);
    expect(state.stats.rateLimited).toBe(1);
  });

  it('uploadFileRateLimited doit être idempotent (isAlreadyRateLimited guard)', () => {
    const withRateLimited = { ...initialState, uploads: [mockUploadFile({ id: 'f1', status: 'rate-limited' })], stats: { ...initialState.stats, rateLimited: 1 } };
    const state = ingestionReducer(withRateLimited, IngestionActions.uploadFileRateLimited({
      fileId: 'f1', retryAfterSeconds: 30, message: 'Rate limit 2'
    }));
    expect(state.stats.rateLimited).toBe(1);
  });

  it('clearCompletedUploads doit ne garder que pending et uploading', () => {
    const withMultiple = {
      ...initialState,
      uploads: [
        mockUploadFile({ id: 'p1', status: 'pending' }),
        mockUploadFile({ id: 'u1', status: 'uploading' }),
        mockUploadFile({ id: 's1', status: 'success' }),
        mockUploadFile({ id: 'e1', status: 'error' }),
        mockUploadFile({ id: 'd1', status: 'duplicate' }),
        mockUploadFile({ id: 'r1', status: 'rate-limited' }),
      ]
    };
    const state = ingestionReducer(withMultiple, IngestionActions.clearCompletedUploads());
    expect(state.uploads).toHaveLength(2);
    expect(state.uploads.map(u => u.id)).toEqual(['p1', 'u1']);
  });

  it('toggleUploadMode doit basculer entre async et sync', () => {
    const syncState = ingestionReducer(initialState, IngestionActions.toggleUploadMode());
    expect(syncState.uploadMode).toBe('sync');
    const asyncState = ingestionReducer(syncState, IngestionActions.toggleUploadMode());
    expect(asyncState.uploadMode).toBe('async');
  });

  it('CrudActions.deleteBatch doit supprimer les uploads correspondants au batchId', () => {
    const withUploads = {
      ...initialState,
      uploads: [
        mockUploadFile({ id: 'f1', batchId: 'batch-1' }),
        mockUploadFile({ id: 'f2', batchId: 'batch-2' }),
      ]
    };
    const state = ingestionReducer(withUploads, CrudActions.deleteBatch({ batchId: 'batch-1' }));
    expect(state.uploads).toHaveLength(1);
    expect(state.uploads[0].id).toBe('f2');
  });

  it('CrudActions.deleteAllFilesSuccess doit vider le tableau uploads', () => {
    const withUploads = {
      ...initialState,
      uploads: [mockUploadFile({ id: 'f1' }), mockUploadFile({ id: 'f2' })]
    };
    const response = { success: true, deletedCount: 2, message: 'ok' };
    const state = ingestionReducer(withUploads, CrudActions.deleteAllFilesSuccess({ response }));
    expect(state.uploads).toHaveLength(0);
  });

  it('ProgressActions.progressUpdate COMPLETED doit passer status à success et progress à 100', () => {
    const withUpload = {
      ...initialState,
      uploads: [mockUploadFile({ id: 'f1', status: 'uploading', batchId: 'batch-1' })]
    };
    const progress = mockUploadProgress({ batchId: 'batch-1', stage: 'COMPLETED' });
    const state = ingestionReducer(withUpload, ProgressActions.progressUpdate({ progress }));
    const upload = state.uploads.find(u => u.id === 'f1');
    expect(upload?.status).toBe('success');
    expect(upload?.progress).toBe(100);
  });

  it('ProgressActions.progressUpdate ERROR doit passer status à error', () => {
    const withUpload = {
      ...initialState,
      uploads: [mockUploadFile({ id: 'f1', status: 'uploading', batchId: 'batch-1' })]
    };
    const progress = mockUploadProgress({ batchId: 'batch-1', stage: 'ERROR', message: 'Échec traitement' });
    const state = ingestionReducer(withUpload, ProgressActions.progressUpdate({ progress }));
    const upload = state.uploads.find(u => u.id === 'f1');
    expect(upload?.status).toBe('error');
    expect(upload?.error).toBe('Échec traitement');
  });

  it('uploadFileDuplicate doit stocker existingBatchId et passer status à duplicate', () => {
    const withUpload = {
      ...initialState,
      uploads: [mockUploadFile({ id: 'f1', status: 'uploading' })]
    };
    const state = ingestionReducer(withUpload, IngestionActions.uploadFileDuplicate({
      fileId: 'f1', batchId: 'b-new', existingBatchId: 'b-old', message: 'Doublon'
    }));
    const upload = state.uploads.find(u => u.id === 'f1');
    expect(upload?.status).toBe('duplicate');
    expect(upload?.existingBatchId).toBe('b-old');
    expect(state.stats.duplicates).toBe(1);
  });

  it('uploadFileSuccess doit passer status à success et stocker response', () => {
    const withUpload = {
      ...initialState,
      uploads: [mockUploadFile({ id: 'f1', status: 'uploading' })],
      activeUploads: 1
    };
    const response = {
      success: true, batchId: 'b1', filename: 'test.pdf', fileSize: 100,
      textEmbeddings: 2, imageEmbeddings: 0, durationMs: 500, streamingUsed: false,
      message: 'ok', duplicate: false
    };
    const state = ingestionReducer(withUpload, IngestionActions.uploadFileSuccess({ fileId: 'f1', response }));
    const upload = state.uploads.find(u => u.id === 'f1');
    expect(upload?.status).toBe('success');
    expect(upload?.progress).toBe(100);
    expect(state.stats.success).toBe(1);
    expect(state.activeUploads).toBe(0);
  });
});
