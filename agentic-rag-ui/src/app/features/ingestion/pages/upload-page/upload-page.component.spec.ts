import { CommonModule } from '@angular/common';
import { createComponentFactory, mockProvider, Spectator } from '@ngneat/spectator/vitest';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { UploadPageComponent } from './upload-page.component';
import { DeleteAllButtonComponent } from '../../components/delete-all-button/delete-all-button.component';
import { ProgressPanelComponent } from '../../components/progress-panel/progress-panel.component';
import { UploadItemComponent } from '../../components/upload-item/upload-item.component';
import { UploadZoneComponent } from '../../components/upload-zone/upload-zone.component';
import { NotificationService } from '../../../../core/services/notification.service';

import * as CrudActions from '../../store/crud/crud.actions';
import * as IngestionActions from '../../store/ingestion/ingestion.actions';
import * as ProgressActions from '../../store/progress/progress.actions';

import { selectProgressState } from '../../store/progress/progress.selectors';
import { selectRateLimitState } from '../../store/rate-limit/rate-limit.selectors';
import { selectIngestionState } from '../../store/ingestion/ingestion.selectors';
import { selectCrudState } from '../../store/crud/crud.selectors';

import {
  mockFullIngestionState,
  mockUploadFile,
  mockUploadProgress,
} from '../../components/testing/ingestion-test.helpers';
import { releaseSelectors } from '../../../../test-helpers';

describe('UploadPageComponent', () => {
  const createComponent = createComponentFactory({
    component: UploadPageComponent,
    imports: [
      CommonModule,
      UploadZoneComponent,
      UploadItemComponent,
      ProgressPanelComponent,
      DeleteAllButtonComponent,
    ],
    providers: [
      provideMockStore({ initialState: mockFullIngestionState() }),
      mockProvider(NotificationService),
    ],
  });

  let spectator: Spectator<UploadPageComponent>;
  let mockStore: MockStore;

  beforeEach(() => {
    (window as any).bootstrap = {
      Modal: class {
        constructor(_el: HTMLElement) {}
        show() {}
        hide() {}
        static getInstance(_el: HTMLElement) {
          return null;
        }
      },
    };
    spectator = createComponent();
    mockStore = spectator.inject(MockStore);
  });

  afterEach(() => {
    mockStore.resetSelectors();
    releaseSelectors([selectProgressState, selectRateLimitState, selectIngestionState, selectCrudState]);
    vi.restoreAllMocks();
  });

  // ── Phase 3 : US1 — Lifecycle & Initial Render ──────────────────────────

  it('doit créer le composant', () => {
    expect(spectator.component).toBeTruthy();
  });

  it('doit dispatcher connectWebSocket au ngOnInit', () => {
    const dispatchSpy = vi.spyOn(mockStore, 'dispatch');
    spectator.component.ngOnInit();
    expect(dispatchSpy).toHaveBeenCalledWith(ProgressActions.connectWebSocket());
  });

  it('doit dispatcher loadStrategies au ngOnInit', () => {
    const dispatchSpy = vi.spyOn(mockStore, 'dispatch');
    spectator.component.ngOnInit();
    expect(dispatchSpy).toHaveBeenCalledWith(IngestionActions.loadStrategies());
  });

  it('doit dispatcher disconnectWebSocket au ngOnDestroy', () => {
    const dispatchSpy = vi.spyOn(mockStore, 'dispatch');
    spectator.component.ngOnDestroy();
    expect(dispatchSpy).toHaveBeenCalledWith(ProgressActions.disconnectWebSocket());
  });

  it('doit afficher UploadZoneComponent', () => {
    expect(spectator.query(UploadZoneComponent)).toBeTruthy();
  });

  it('doit afficher DeleteAllButtonComponent', () => {
    expect(spectator.query(DeleteAllButtonComponent)).toBeTruthy();
  });

  it("doit afficher l'état vide quand la liste est vide", () => {
    mockStore.setState(mockFullIngestionState({ uploads: [] }));
    spectator.detectChanges();
    expect(spectator.query('.empty-state')).toBeTruthy();
  });

  // ── Phase 4 : US2 — Upload Zone & File Dispatch ─────────────────────────

  it('doit dispatcher addFilesToUpload quand filesSelected est émis', () => {
    const files = [new File(['content'], 'doc.pdf', { type: 'application/pdf' })];
    const dispatchSpy = vi.spyOn(mockStore, 'dispatch');
    spectator.component.onFilesSelected(files);
    expect(dispatchSpy).toHaveBeenCalledWith(IngestionActions.addFilesToUpload({ files }));
  });

  it('doit passer disabled=true à UploadZoneComponent si isRateLimited', () => {
    mockStore.setState(mockFullIngestionState({ isRateLimited: true }));
    spectator.detectChanges();
    const uploadZone = spectator.query(UploadZoneComponent);
    expect(uploadZone?.disabled).toBe(true);
  });

  // ── Phase 5 : US3 — Upload List by Status Group ─────────────────────────

  it('doit afficher la liste des UploadItemComponent', () => {
    mockStore.setState(mockFullIngestionState({
      uploads: [
        mockUploadFile({ id: 'p1' }),
        mockUploadFile({ id: 'p2' }),
        mockUploadFile({ id: 'a1', status: 'uploading' }),
        mockUploadFile({ id: 'c1', status: 'success' }),
      ],
    }));
    spectator.detectChanges();
    expect(spectator.queryAll(UploadItemComponent)).toHaveLength(4);
  });

  // ── Phase 6 : US5+US6 — Rate Limit Banner, Progress Panel & DeleteAll ───

  it('doit afficher RateLimitIndicatorComponent', () => {
    mockStore.setState(mockFullIngestionState({ isRateLimited: true, retryAfterSeconds: 30 }));
    spectator.detectChanges();
    const banner = spectator.query('.alert-warning');
    expect(banner).toBeTruthy();
    expect(banner?.textContent).toContain('30');
  });

  it('doit afficher ProgressPanelComponent', () => {
    const activeProgress = mockUploadProgress({ batchId: 'b1', stage: 'PROCESSING' });
    mockStore.setState({
      ...mockFullIngestionState(),
      progress: {
        connected: false,
        connecting: false,
        error: null,
        progressByBatch: { b1: activeProgress, b2: { ...activeProgress, batchId: 'b2' } },
        subscribedBatches: [],
      },
    });
    spectator.detectChanges();
    expect(spectator.query(ProgressPanelComponent)).toBeTruthy();
  });

  it('doit dispatcher deleteAllFiles quand deleteAll est confirmé', () => {
    mockStore.setState(mockFullIngestionState({ uploads: [mockUploadFile()] }));
    spectator.detectChanges();
    const dispatchSpy = vi.spyOn(mockStore, 'dispatch');
    const deleteAllBtn = spectator.query(DeleteAllButtonComponent);
    deleteAllBtn?.onConfirmed();
    expect(dispatchSpy).toHaveBeenCalledWith(
      CrudActions.deleteAllFiles({ confirmation: 'DELETE_ALL_FILES' }),
    );
  });

  // ── Phase 6b : Coverage — Upload Controls ───────────────────────────────

  it('doit dispatcher removeUpload quand removeUpload est appelé', () => {
    const dispatchSpy = vi.spyOn(mockStore, 'dispatch');
    spectator.component.removeUpload('file-1');
    expect(dispatchSpy).toHaveBeenCalledWith(IngestionActions.removeUpload({ fileId: 'file-1' }));
  });

  it('doit dispatcher clearCompletedUploads quand clearCompleted est appelé', () => {
    const dispatchSpy = vi.spyOn(mockStore, 'dispatch');
    spectator.component.clearCompleted();
    expect(dispatchSpy).toHaveBeenCalledWith(IngestionActions.clearCompletedUploads());
  });

  it('doit dispatcher toggleUploadMode quand toggleUploadMode est appelé', () => {
    const dispatchSpy = vi.spyOn(mockStore, 'dispatch');
    spectator.component.toggleUploadMode();
    expect(dispatchSpy).toHaveBeenCalledWith(IngestionActions.toggleUploadMode());
  });

  it('doit dispatcher uploadFileAsync quand startAllUploads est appelé sans rate limit', () => {
    const file = mockUploadFile({ id: 'f1' });
    mockStore.setState(mockFullIngestionState({ uploads: [file], isRateLimited: false }));
    spectator.detectChanges();
    const dispatchSpy = vi.spyOn(mockStore, 'dispatch');
    spectator.component.startAllUploads();
    expect(dispatchSpy).toHaveBeenCalledWith(
      IngestionActions.uploadFileAsync({ fileId: 'f1', file: file.file }),
    );
  });

  it('ne doit pas dispatcher upload quand startAllUploads est appelé avec rate limit actif', () => {
    mockStore.setState(mockFullIngestionState({ isRateLimited: true }));
    spectator.detectChanges();
    const dispatchSpy = vi.spyOn(mockStore, 'dispatch');
    spectator.component.startAllUploads();
    expect(dispatchSpy).not.toHaveBeenCalledWith(
      expect.objectContaining({ type: '[Ingestion] Upload File Async' }),
    );
  });

  // ── Phase 7 : US7 — Integration Tests ──────────────────────────────────

  it('[INTÉGRATION] doit dispatcher addFilesToUpload quand filesSelected est émis', () => {
    const files = [new File([''], 'rapport.pdf', { type: 'application/pdf' })];
    const dispatchSpy = vi.spyOn(mockStore, 'dispatch');
    spectator.triggerEventHandler(UploadZoneComponent, 'filesSelected', files);
    expect(dispatchSpy).toHaveBeenCalledWith(IngestionActions.addFilesToUpload({ files }));
  });

  it('[INTÉGRATION] doit afficher les items par groupe de statut', () => {
    const file = mockUploadFile({ id: 'f1' });

    // Étape 1 : fichier en attente
    mockStore.setState(mockFullIngestionState({ uploads: [file] }));
    spectator.detectChanges();
    expect(spectator.queryAll(UploadItemComponent)).toHaveLength(1);

    // Étape 2 : passage en cours
    mockStore.setState(mockFullIngestionState({ uploads: [mockUploadFile({ id: 'f1', status: 'uploading' })] }));
    spectator.detectChanges();
    expect(spectator.queryAll(UploadItemComponent)).toHaveLength(1);

    // Étape 3 : terminé
    mockStore.setState(mockFullIngestionState({ uploads: [mockUploadFile({ id: 'f1', status: 'success' })] }));
    spectator.detectChanges();
    expect(spectator.queryAll(UploadItemComponent)).toHaveLength(1);
  });

  it('[INTÉGRATION] le flux complet : sélection → uploading → done', () => {
    const file = mockUploadFile({ id: 'f1' });

    // Étape 1 : sélection → pending → état vide masqué
    mockStore.setState(mockFullIngestionState({ uploads: [file] }));
    spectator.detectChanges();
    expect(spectator.queryAll(UploadItemComponent)).toHaveLength(1);
    expect(spectator.query('.empty-state')).toBeNull();

    // Étape 2 : uploading

    mockStore.setState(mockFullIngestionState({ uploads: [mockUploadFile({ id: 'f1', status: 'uploading' })] }));
    spectator.detectChanges();
    expect(spectator.queryAll(UploadItemComponent)).toHaveLength(1);
    expect(spectator.query('.empty-state')).toBeNull();

    // Étape 3 : done
    mockStore.setState(mockFullIngestionState({ uploads: [mockUploadFile({ id: 'f1', status: 'success' })] }));
    spectator.detectChanges();
    expect(spectator.queryAll(UploadItemComponent)).toHaveLength(1);
    expect(spectator.query('.empty-state')).toBeNull();
  });
});
