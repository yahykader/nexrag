import { describe, it, expect } from 'vitest';
import * as CrudActions from './crud.actions';

describe('CrudActions', () => {
  it('deleteFile doit avoir le type [CRUD] Delete File', () => {
    const action = CrudActions.deleteFile({ embeddingId: 'emb-1', fileType: 'text' });
    expect(action.type).toBe('[CRUD] Delete File');
    expect(action.embeddingId).toBe('emb-1');
    expect(action.fileType).toBe('text');
  });

  it('deleteFileSuccess doit avoir le type [CRUD] Delete File Success', () => {
    const response = { success: true, deletedCount: 1, message: 'ok' };
    const action = CrudActions.deleteFileSuccess({ response });
    expect(action.type).toBe('[CRUD] Delete File Success');
    expect(action.response).toEqual(response);
  });

  it('deleteBatch doit avoir le type [CRUD] Delete Batch', () => {
    const action = CrudActions.deleteBatch({ batchId: 'batch-1' });
    expect(action.type).toBe('[CRUD] Delete Batch');
    expect(action.batchId).toBe('batch-1');
  });

  it('deleteBatchSuccess doit avoir le type [CRUD] Delete Batch Success', () => {
    const response = { success: true, deletedCount: 5, message: 'ok' };
    const action = CrudActions.deleteBatchSuccess({ response });
    expect(action.type).toBe('[CRUD] Delete Batch Success');
  });

  it('deleteAllFiles doit avoir le type [CRUD] Delete All Files', () => {
    const action = CrudActions.deleteAllFiles({ confirmation: 'DELETE_ALL' });
    expect(action.type).toBe('[CRUD] Delete All Files');
    expect(action.confirmation).toBe('DELETE_ALL');
  });

  it('deleteAllFilesSuccess doit avoir le type [CRUD] Delete All Files Success', () => {
    const response = { success: true, deletedCount: 10, message: 'ok' };
    const action = CrudActions.deleteAllFilesSuccess({ response });
    expect(action.type).toBe('[CRUD] Delete All Files Success');
  });

  it('checkDuplicate doit avoir le type [CRUD] Check Duplicate', () => {
    const file = new File(['content'], 'test.pdf', { type: 'application/pdf' });
    const action = CrudActions.checkDuplicate({ file });
    expect(action.type).toBe('[CRUD] Check Duplicate');
    expect(action.file).toBe(file);
  });

  it('clearAll doit avoir le type [CRUD] Clear All', () => {
    const action = CrudActions.clearAll();
    expect(action.type).toBe('[CRUD] Clear All');
  });
});
