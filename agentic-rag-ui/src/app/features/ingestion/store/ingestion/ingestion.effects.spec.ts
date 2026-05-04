import { createServiceFactory, SpectatorService, SpyObject } from '@ngneat/spectator/vitest';
import { Action } from '@ngrx/store';
import { provideMockActions } from '@ngrx/effects/testing';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { ReplaySubject, of, throwError } from 'rxjs';

import { IngestionEffects } from './ingestion.effects';
import * as IngestionActions from './ingestion.actions';
import * as ProgressActions from '../progress/progress.actions';
import { IngestionApiService } from '../../../../core/services/ingestion-api.service';
import { initialState } from './ingestion.state';

describe('IngestionEffects', () => {
  let spectator: SpectatorService<IngestionEffects>;
  let actions$: ReplaySubject<Action>;
  let ingestionApi: SpyObject<IngestionApiService>;
  let store: MockStore;

  const createService = createServiceFactory({
    service: IngestionEffects,
    providers: [
      provideMockActions(() => actions$),
      provideMockStore({ initialState: { ingestion: initialState } }),
    ],
    mocks: [IngestionApiService],
  });

  beforeEach(() => {
    actions$ = new ReplaySubject<Action>(1);
    spectator = createService();
    ingestionApi = spectator.inject(IngestionApiService);
    store = spectator.inject(MockStore);
  });

  afterEach(() => {
    store.resetSelectors();
  });

  it('uploadFileAsync$ doit dispatcher uploadFileAsyncAccepted quand l\'API retourne 202', () => {
    const response = { accepted: true, batchId: 'batch-1', filename: 'test.pdf', message: 'ok', statusUrl: '/status', duplicate: false };
    ingestionApi.uploadFileAsync.mockReturnValue(of(response));

    const results: Action[] = [];
    spectator.service.uploadFileAsync$.subscribe(a => results.push(a));
    actions$.next(IngestionActions.uploadFileAsync({ fileId: 'f1', file: new File(['content'], 'test.pdf') }));

    expect(results[0].type).toBe('[Ingestion] Upload File Async Accepted');
    expect((results[0] as any).response.batchId).toBe('batch-1');
  });

  it('uploadFileAsync$ doit dispatcher uploadFileRateLimited quand l\'API retourne 429', () => {
    ingestionApi.uploadFileAsync.mockReturnValue(
      throwError(() => ({ status: 429, error: { retryAfterSeconds: 60, message: 'Rate limit' } }))
    );

    const results: Action[] = [];
    spectator.service.uploadFileAsync$.subscribe(a => results.push(a));
    actions$.next(IngestionActions.uploadFileAsync({ fileId: 'f1', file: new File(['content'], 'test.pdf') }));

    expect(results[0].type).toBe('[Ingestion] Upload File Rate Limited');
    expect((results[0] as any).retryAfterSeconds).toBe(60);
  });

  it('uploadFileAsync$ doit dispatcher uploadFileDuplicate quand l\'API retourne 409', () => {
    ingestionApi.uploadFileAsync.mockReturnValue(
      throwError(() => ({ status: 409, error: { batchId: 'b-new', existingBatchId: 'b-old', message: 'Doublon' } }))
    );

    const results: Action[] = [];
    spectator.service.uploadFileAsync$.subscribe(a => results.push(a));
    actions$.next(IngestionActions.uploadFileAsync({ fileId: 'f1', file: new File(['content'], 'test.pdf') }));

    expect(results[0].type).toBe('[Ingestion] Upload File Duplicate');
  });

  it('subscribeAfterAsyncUpload$ doit dispatcher subscribeToProgress avec le batchId', () => {
    const response = { accepted: true, batchId: 'batch-1', filename: 'test.pdf', message: 'ok', statusUrl: '/status', duplicate: false };

    const results: Action[] = [];
    spectator.service.subscribeAfterAsyncUpload$.subscribe(a => results.push(a));
    actions$.next(IngestionActions.uploadFileAsyncAccepted({ fileId: 'f1', response }));

    expect(results[0].type).toBe('[Progress] Subscribe To Progress');
    expect((results[0] as any).batchId).toBe('batch-1');
  });

  it('uploadFile$ (sync) doit dispatcher uploadFileSuccess quand l\'API réussit', () => {
    const response = {
      success: true, batchId: 'b1', filename: 'test.pdf', fileSize: 100,
      textEmbeddings: 1, imageEmbeddings: 0, durationMs: 500, streamingUsed: false,
      message: 'ok', duplicate: false
    };
    ingestionApi.uploadFile.mockReturnValue(of(response));

    const results: Action[] = [];
    spectator.service.uploadFile$.subscribe(a => results.push(a));
    actions$.next(IngestionActions.uploadFile({ fileId: 'f1', file: new File(['content'], 'test.pdf') }));

    expect(results[0].type).toBe('[Ingestion] Upload File Success');
  });
});
