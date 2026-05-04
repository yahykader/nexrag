import { describe, it, expect } from 'vitest';
import {
  selectUploads,
  selectPendingUploads,
  selectActiveUploads,
  selectCompletedUploads,
  selectUploadMode,
  selectRateLimitedUploads,
  selectRateLimitedCount
} from './ingestion.selectors';
import { initialState } from './ingestion.state';
import { mockUploadFile } from '../../../../test-helpers';

describe('IngestionSelectors', () => {
  const baseState = { ingestion: initialState };

  it('selectUploads doit retourner le tableau d\'uploads', () => {
    const uploads = [mockUploadFile({ id: 'f1' })];
    const state = { ingestion: { ...initialState, uploads } };
    expect(selectUploads(state as any)).toEqual(uploads);
  });

  it('selectPendingUploads doit filtrer les uploads avec status pending', () => {
    const uploads = [
      mockUploadFile({ id: 'f1', status: 'pending' }),
      mockUploadFile({ id: 'f2', status: 'uploading' }),
    ];
    const state = { ingestion: { ...initialState, uploads } };
    const pending = selectPendingUploads(state as any);
    expect(pending).toHaveLength(1);
    expect(pending[0].id).toBe('f1');
  });

  it('selectActiveUploads doit filtrer les uploads avec status uploading', () => {
    const uploads = [
      mockUploadFile({ id: 'f1', status: 'pending' }),
      mockUploadFile({ id: 'f2', status: 'uploading' }),
    ];
    const state = { ingestion: { ...initialState, uploads } };
    const active = selectActiveUploads(state as any);
    expect(active).toHaveLength(1);
    expect(active[0].id).toBe('f2');
  });

  it('selectCompletedUploads doit retourner success, error, duplicate mais PAS rate-limited', () => {
    const uploads = [
      mockUploadFile({ id: 'f1', status: 'success' }),
      mockUploadFile({ id: 'f2', status: 'error' }),
      mockUploadFile({ id: 'f3', status: 'duplicate' }),
      mockUploadFile({ id: 'f4', status: 'rate-limited' }),
      mockUploadFile({ id: 'f5', status: 'pending' }),
    ];
    const state = { ingestion: { ...initialState, uploads } };
    const completed = selectCompletedUploads(state as any);
    expect(completed).toHaveLength(3);
    expect(completed.map(u => u.id)).toEqual(['f1', 'f2', 'f3']);
  });

  it('selectUploadMode doit retourner async par défaut', () => {
    expect(selectUploadMode(baseState as any)).toBe('async');
  });

  it('selectRateLimitedUploads doit filtrer les uploads avec status rate-limited', () => {
    const uploads = [
      mockUploadFile({ id: 'f1', status: 'uploading' }),
      mockUploadFile({ id: 'f2', status: 'rate-limited' }),
    ];
    const state = { ingestion: { ...initialState, uploads } };
    const rateLimited = selectRateLimitedUploads(state as any);
    expect(rateLimited).toHaveLength(1);
    expect(rateLimited[0].id).toBe('f2');
  });
});
