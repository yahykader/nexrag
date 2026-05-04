import { describe, it, expect } from 'vitest';
import { crudReducer } from './crud.reducer';
import { initialCrudState } from './crud.state';
import * as CrudActions from './crud.actions';

describe('CrudReducer', () => {
  it('doit retourner l\'état initial', () => {
    const state = crudReducer(undefined, { type: '@@INIT' });
    expect(state).toEqual(initialCrudState);
  });

  it('deleteFile doit passer loading à true et incrémenter activeDeleteOperations', () => {
    const action = CrudActions.deleteFile({ embeddingId: 'emb-1', fileType: 'text' });
    const state = crudReducer(initialCrudState, action);
    expect(state.loading).toBe(true);
    expect(state.activeDeleteOperations).toBe(1);
    expect(state.deleteOperations).toHaveLength(1);
    expect(state.deleteOperations[0].status).toBe('pending');
    expect(state.deleteOperations[0].targetId).toBe('emb-1');
  });

  it('deleteFileSuccess doit passer loading à false et décrémenter activeDeleteOperations', () => {
    const withOp = crudReducer(initialCrudState, CrudActions.deleteFile({ embeddingId: 'emb-1', fileType: 'text' }));
    const response = { success: true, deletedCount: 1, embeddingId: 'emb-1', message: 'ok' };
    const state = crudReducer(withOp, CrudActions.deleteFileSuccess({ response }));
    expect(state.loading).toBe(false);
    expect(state.activeDeleteOperations).toBe(0);
    expect(state.deleteOperations[0].status).toBe('success');
    expect(state.deleteOperations[0].deletedCount).toBe(1);
  });

  it('Math.max(0, ...) doit prévenir que activeDeleteOperations devienne négatif', () => {
    const response = { success: true, deletedCount: 0, embeddingId: 'emb-x', message: 'ok' };
    const state = crudReducer(initialCrudState, CrudActions.deleteFileSuccess({ response }));
    expect(state.activeDeleteOperations).toBe(0);
  });

  it('deleteFileError doit passer loading à false et mettre op.status à error', () => {
    const withOp = crudReducer(initialCrudState, CrudActions.deleteFile({ embeddingId: 'emb-1', fileType: 'text' }));
    const state = crudReducer(withOp, CrudActions.deleteFileError({ embeddingId: 'emb-1', error: 'Erreur réseau' }));
    expect(state.loading).toBe(false);
    expect(state.error).toBe('Erreur réseau');
    expect(state.deleteOperations[0].status).toBe('error');
    expect(state.deleteOperations[0].message).toBe('Erreur réseau');
  });

  it('deleteBatch doit incrémenter activeDeleteOperations et ajouter une op de type batch', () => {
    const action = CrudActions.deleteBatch({ batchId: 'batch-1' });
    const state = crudReducer(initialCrudState, action);
    expect(state.loading).toBe(true);
    expect(state.activeDeleteOperations).toBe(1);
    expect(state.deleteOperations[0].type).toBe('batch');
    expect(state.deleteOperations[0].targetId).toBe('batch-1');
  });

  it('deleteBatchSuccess doit supprimer le batchInfo du cache', () => {
    const withBatch = {
      ...initialCrudState,
      activeDeleteOperations: 1,
      batchInfos: { 'batch-1': { batchId: 'batch-1', found: true, textEmbeddings: 2, imageEmbeddings: 0, totalEmbeddings: 2, message: 'ok', timestamp: new Date() } },
      deleteOperations: [{ id: 'batch-batch-1-123', type: 'batch' as const, targetId: 'batch-1', status: 'pending' as const, timestamp: new Date() }]
    };
    const response = { success: true, deletedCount: 2, batchId: 'batch-1', message: 'ok' };
    const state = crudReducer(withBatch, CrudActions.deleteBatchSuccess({ response }));
    expect(state.loading).toBe(false);
    expect(state.batchInfos['batch-1']).toBeUndefined();
  });

  it('deleteAllFilesSuccess doit vider batchInfos et duplicateChecks', () => {
    const withData = {
      ...initialCrudState,
      activeDeleteOperations: 1,
      batchInfos: { 'b1': { batchId: 'b1', found: true, textEmbeddings: 1, imageEmbeddings: 0, totalEmbeddings: 1, message: 'ok', timestamp: new Date() } },
      duplicateChecks: { 'file.pdf': { filename: 'file.pdf', isDuplicate: true, message: 'dup', timestamp: new Date() } },
      deleteOperations: [{ id: 'all-123', type: 'all' as const, targetId: 'all-files', status: 'pending' as const, timestamp: new Date() }]
    };
    const response = { success: true, deletedCount: 10, message: 'ok' };
    const state = crudReducer(withData, CrudActions.deleteAllFilesSuccess({ response }));
    expect(state.batchInfos).toEqual({});
    expect(state.duplicateChecks).toEqual({});
    expect(state.loading).toBe(false);
  });

  it('checkDuplicate doit passer loading à true', () => {
    const file = new File(['content'], 'test.pdf');
    const state = crudReducer(initialCrudState, CrudActions.checkDuplicate({ file }));
    expect(state.loading).toBe(true);
  });

  it('checkDuplicateSuccess doit stocker la vérification par clé filename', () => {
    const response = {
      isDuplicate: true,
      filename: 'test.pdf',
      existingBatchId: 'batch-old',
      message: 'Doublon détecté'
    };
    const state = crudReducer(initialCrudState, CrudActions.checkDuplicateSuccess({ response }));
    expect(state.duplicateChecks['test.pdf']).toBeDefined();
    expect(state.duplicateChecks['test.pdf'].isDuplicate).toBe(true);
    expect(state.duplicateChecks['test.pdf'].existingBatchId).toBe('batch-old');
    expect(state.loading).toBe(false);
  });

  it('getBatchInfoSuccess doit stocker les infos par clé batchId', () => {
    const response = {
      batchId: 'batch-1',
      found: true,
      textEmbeddings: 3,
      imageEmbeddings: 1,
      totalEmbeddings: 4,
      message: 'ok'
    };
    const state = crudReducer(initialCrudState, CrudActions.getBatchInfoSuccess({ response }));
    expect(state.batchInfos['batch-1']).toBeDefined();
    expect(state.batchInfos['batch-1'].totalEmbeddings).toBe(4);
    expect(state.loading).toBe(false);
  });

  it('getSystemStatsSuccess doit mettre à jour systemStats', () => {
    const stats = { totalEmbeddings: 42, redisHealthy: true, systemStatus: 'OK' };
    const state = crudReducer(initialCrudState, CrudActions.getSystemStatsSuccess({ stats }));
    expect(state.systemStats.totalEmbeddings).toBe(42);
    expect(state.systemStats.redisHealthy).toBe(true);
    expect(state.loading).toBe(false);
  });

  it('clearAll doit retourner l\'état initial', () => {
    const withData = {
      ...initialCrudState,
      loading: true,
      error: 'une erreur',
      activeDeleteOperations: 3
    };
    const state = crudReducer(withData, CrudActions.clearAll());
    expect(state).toEqual(initialCrudState);
  });
});
