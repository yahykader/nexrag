import { createServiceFactory, SpectatorService, SpyObject } from '@ngneat/spectator/vitest';
import { Action } from '@ngrx/store';
import { provideMockActions } from '@ngrx/effects/testing';
import { provideMockStore } from '@ngrx/store/testing';
import { ReplaySubject, of, throwError } from 'rxjs';

import { CrudEffects } from './crud.effects';
import * as CrudActions from './crud.actions';
import { CrudApiService } from '../../../../core/services/crud-api.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { initialCrudState } from './crud.state';

describe('CrudEffects', () => {
  let spectator: SpectatorService<CrudEffects>;
  let actions$: ReplaySubject<Action>;
  let crudApi: SpyObject<CrudApiService>;
  let notificationService: SpyObject<NotificationService>;

  const createService = createServiceFactory({
    service: CrudEffects,
    providers: [
      provideMockActions(() => actions$),
      provideMockStore({ initialState: { crud: initialCrudState } }),
    ],
    mocks: [CrudApiService, NotificationService],
  });

  beforeEach(() => {
    actions$ = new ReplaySubject<Action>(1);
    spectator = createService();
    crudApi = spectator.inject(CrudApiService);
    notificationService = spectator.inject(NotificationService);
  });

  it('deleteFile$ doit dispatcher deleteFileSuccess quand l\'API réussit', () => {
    const response = { success: true, deletedCount: 1, embeddingId: 'emb-1', message: 'ok' };
    crudApi.deleteFile.mockReturnValue(of(response));

    const results: Action[] = [];
    spectator.service.deleteFile$.subscribe(a => results.push(a));
    actions$.next(CrudActions.deleteFile({ embeddingId: 'emb-1', fileType: 'text' }));

    expect(results[0]).toEqual(CrudActions.deleteFileSuccess({ response }));
  });

  it('deleteFile$ doit dispatcher deleteFileError quand l\'API échoue', () => {
    crudApi.deleteFile.mockReturnValue(throwError(() => new Error('Erreur réseau')));

    const results: Action[] = [];
    spectator.service.deleteFile$.subscribe(a => results.push(a));
    actions$.next(CrudActions.deleteFile({ embeddingId: 'emb-1', fileType: 'text' }));

    expect(results[0].type).toBe('[CRUD] Delete File Error');
    expect((results[0] as any).embeddingId).toBe('emb-1');
  });

  it('deleteBatch$ doit dispatcher deleteBatchSuccess quand l\'API réussit', () => {
    const response = { success: true, deletedCount: 3, batchId: 'batch-1', message: 'ok' };
    crudApi.deleteBatch.mockReturnValue(of(response));

    const results: Action[] = [];
    spectator.service.deleteBatch$.subscribe(a => results.push(a));
    actions$.next(CrudActions.deleteBatch({ batchId: 'batch-1' }));

    expect(results[0]).toEqual(CrudActions.deleteBatchSuccess({ response }));
  });

  it('deleteAllFiles$ doit dispatcher deleteAllFilesSuccess quand l\'API réussit', () => {
    const response = { success: true, deletedCount: 10, message: 'ok' };
    crudApi.deleteAllFiles.mockReturnValue(of(response));

    const results: Action[] = [];
    spectator.service.deleteAllFiles$.subscribe(a => results.push(a));
    actions$.next(CrudActions.deleteAllFiles({ confirmation: 'DELETE_ALL' }));

    expect(results[0]).toEqual(CrudActions.deleteAllFilesSuccess({ response }));
  });

  it('checkDuplicate$ doit dispatcher checkDuplicateSuccess quand l\'API réussit', () => {
    const response = { isDuplicate: false, filename: 'test.pdf', message: 'ok' };
    crudApi.checkDuplicate.mockReturnValue(of(response));

    const results: Action[] = [];
    spectator.service.checkDuplicate$.subscribe(a => results.push(a));
    actions$.next(CrudActions.checkDuplicate({ file: new File(['content'], 'test.pdf') }));

    expect(results[0]).toEqual(CrudActions.checkDuplicateSuccess({ response }));
  });

  it('getBatchInfo$ doit dispatcher getBatchInfoSuccess quand l\'API réussit', () => {
    const response = { batchId: 'batch-1', found: true, textEmbeddings: 2, imageEmbeddings: 0, totalEmbeddings: 2, message: 'ok' };
    crudApi.getBatchInfo.mockReturnValue(of(response));

    const results: Action[] = [];
    spectator.service.getBatchInfo$.subscribe(a => results.push(a));
    actions$.next(CrudActions.getBatchInfo({ batchId: 'batch-1' }));

    expect(results[0]).toEqual(CrudActions.getBatchInfoSuccess({ response }));
  });

  it('getSystemStats$ doit dispatcher getSystemStatsSuccess quand l\'API réussit', () => {
    const stats = { totalEmbeddings: 42, redisHealthy: true };
    crudApi.getSystemStats.mockReturnValue(of(stats));

    const results: Action[] = [];
    spectator.service.getSystemStats$.subscribe(a => results.push(a));
    actions$.next(CrudActions.getSystemStats());

    expect(results[0]).toEqual(CrudActions.getSystemStatsSuccess({ stats }));
  });
});
