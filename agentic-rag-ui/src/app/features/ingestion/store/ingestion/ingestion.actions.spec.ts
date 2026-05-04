import { describe, it, expect } from 'vitest';
import * as IngestionActions from './ingestion.actions';

describe('IngestionActions', () => {
  it('uploadFileAsync doit avoir le type [Ingestion] Upload File Async', () => {
    const file = new File(['content'], 'test.pdf');
    const action = IngestionActions.uploadFileAsync({ fileId: 'f1', file });
    expect(action.type).toBe('[Ingestion] Upload File Async');
    expect(action.fileId).toBe('f1');
  });

  it('uploadFileAsyncAccepted doit avoir le type [Ingestion] Upload File Async Accepted', () => {
    const response = { accepted: true, batchId: 'b1', filename: 'test.pdf', message: 'ok', statusUrl: '/status', duplicate: false };
    const action = IngestionActions.uploadFileAsyncAccepted({ fileId: 'f1', response });
    expect(action.type).toBe('[Ingestion] Upload File Async Accepted');
    expect(action.response.batchId).toBe('b1');
  });

  it('uploadFileRateLimited doit avoir le type [Ingestion] Upload File Rate Limited', () => {
    const action = IngestionActions.uploadFileRateLimited({ fileId: 'f1', retryAfterSeconds: 60, message: 'Rate limit' });
    expect(action.type).toBe('[Ingestion] Upload File Rate Limited');
    expect(action.retryAfterSeconds).toBe(60);
    expect(action.message).toBe('Rate limit');
  });

  it('uploadFileDuplicate doit avoir le type [Ingestion] Upload File Duplicate', () => {
    const action = IngestionActions.uploadFileDuplicate({ fileId: 'f1', batchId: 'b1', existingBatchId: 'b-old', message: 'Doublon' });
    expect(action.type).toBe('[Ingestion] Upload File Duplicate');
    expect(action.existingBatchId).toBe('b-old');
  });

  it('addFilesToUpload doit avoir le type [Ingestion] Add Files To Upload', () => {
    const files = [new File(['content'], 'file1.pdf')];
    const action = IngestionActions.addFilesToUpload({ files });
    expect(action.type).toBe('[Ingestion] Add Files To Upload');
    expect(action.files).toHaveLength(1);
  });

  it('clearCompletedUploads doit avoir le type [Ingestion] Clear Completed Uploads', () => {
    const action = IngestionActions.clearCompletedUploads();
    expect(action.type).toBe('[Ingestion] Clear Completed Uploads');
  });

  it('toggleUploadMode doit avoir le type [Ingestion] Toggle Upload Mode', () => {
    const action = IngestionActions.toggleUploadMode();
    expect(action.type).toBe('[Ingestion] Toggle Upload Mode');
  });

  it('uploadFileSuccess doit avoir le type [Ingestion] Upload File Success', () => {
    const response = {
      success: true, batchId: 'b1', filename: 'test.pdf', fileSize: 100,
      textEmbeddings: 1, imageEmbeddings: 0, durationMs: 500, streamingUsed: false,
      message: 'ok', duplicate: false
    };
    const action = IngestionActions.uploadFileSuccess({ fileId: 'f1', response });
    expect(action.type).toBe('[Ingestion] Upload File Success');
  });

  it('removeUpload doit avoir le type [Ingestion] Remove Upload', () => {
    const action = IngestionActions.removeUpload({ fileId: 'f1' });
    expect(action.type).toBe('[Ingestion] Remove Upload');
    expect(action.fileId).toBe('f1');
  });

  it('loadStrategies doit avoir le type [Ingestion] Load Strategies', () => {
    const action = IngestionActions.loadStrategies();
    expect(action.type).toBe('[Ingestion] Load Strategies');
  });
});
