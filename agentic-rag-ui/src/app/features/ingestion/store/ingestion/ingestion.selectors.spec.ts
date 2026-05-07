import { describe, it, expect } from 'vitest';
import {
  selectUploads,
  selectPendingUploads,
  selectActiveUploads,
  selectCompletedUploads,
  selectUploadMode,
  selectRateLimitedUploads,
} from './ingestion.selectors';
import { initialState } from './ingestion.state';
import { mockUploadFile } from '../../../../test-helpers';

describe('IngestionSelectors', () => {

  // ── Directs (dépendent de selectIngestionState) → projector reçoit feature state ──

  it("selectUploads doit retourner le tableau d'uploads", () => {
    const uploads = [mockUploadFile({ id: 'f1' })];
    expect(selectUploads.projector({ ...initialState, uploads })).toEqual(uploads);
  });

  it('selectUploadMode doit retourner async par défaut', () => {
    expect(selectUploadMode.projector(initialState)).toBe('async');
  });

  it('selectRateLimitedUploads doit filtrer les uploads avec status rate-limited', () => {
    const uploads = [
      mockUploadFile({ id: 'f1', status: 'uploading' }),
      mockUploadFile({ id: 'f2', status: 'rate-limited' }),
    ];
    // ⚠️ dépend de selectIngestionState (pas selectUploads) → feature state
    const rateLimited = selectRateLimitedUploads.projector({ ...initialState, uploads });
    expect(rateLimited).toHaveLength(1);
    expect(rateLimited[0].id).toBe('f2');
  });

  // ── Composés (dépendent de selectUploads) → projector reçoit UploadFile[] ──

  it('selectPendingUploads doit filtrer les uploads avec status pending', () => {
    const uploads = [
      mockUploadFile({ id: 'f1', status: 'pending' }),
      mockUploadFile({ id: 'f2', status: 'uploading' }),
    ];
    const pending = selectPendingUploads.projector(uploads);
    expect(pending).toHaveLength(1);
    expect(pending[0].id).toBe('f1');
  });

  it('selectActiveUploads doit filtrer les uploads avec status uploading', () => {
    const uploads = [
      mockUploadFile({ id: 'f1', status: 'pending' }),
      mockUploadFile({ id: 'f2', status: 'uploading' }),
    ];
    const active = selectActiveUploads.projector(uploads);
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
    const completed = selectCompletedUploads.projector(uploads);
    expect(completed).toHaveLength(3);
    expect(completed.map(u => u.id)).toEqual(['f1', 'f2', 'f3']);
  });
});