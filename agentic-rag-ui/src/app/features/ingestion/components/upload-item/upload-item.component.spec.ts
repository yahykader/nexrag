import { CommonModule } from '@angular/common';
import { createComponentFactory, mockProvider } from '@ngneat/spectator/vitest';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

import { UploadItemComponent } from './upload-item.component';
import { DeleteBatchModalComponent } from '../delete-batch-modal/delete-batch-modal.component';
import { NotificationService } from '../../../../core/services/notification.service';
import * as IngestionActions from '../../store/ingestion/ingestion.actions';
import {
  mockUploadFile,
  mockCrudState,
  mockProgressState,
} from '../testing/ingestion-test.helpers';

describe('UploadItemComponent', () => {
  const createComponent = createComponentFactory({
    component: UploadItemComponent,
    imports: [CommonModule, DeleteBatchModalComponent],
    providers: [
      provideMockStore({ initialState: { ...mockCrudState(), ...mockProgressState() } }),
      mockProvider(NotificationService),
    ],
    detectChanges: false,
  });

  beforeEach(() => {
    (window as any).bootstrap = {
      Modal: class {
        constructor(_el: HTMLElement) {}
        show() {}
        hide() {}
        static getInstance(_el: HTMLElement) { return null; }
      },
    };
  });

  afterEach(() => vi.useRealTimers());

  it('doit afficher le nom du fichier', () => {
    const spectator = createComponent({ props: { upload: mockUploadFile() } });
    spectator.detectChanges();
    expect(spectator.element.textContent).toContain('doc.pdf');
  });

  it('doit afficher une barre de progression pour le statut "uploading"', () => {
    const spectator = createComponent({
      props: { upload: mockUploadFile({ status: 'uploading', batchId: 'batch-1' }) },
    });
    spectator.detectChanges();
    const badge = spectator.query('.badge.bg-primary');
    expect(badge).toBeTruthy();
    expect(badge?.textContent?.trim()).toContain('En cours');
  });

  it('doit afficher l\'icône de succès pour le statut "success"', () => {
    const spectator = createComponent({
      props: { upload: mockUploadFile({ status: 'success' }) },
    });
    spectator.detectChanges();
    const card = spectator.query('.card.upload-item')!;
    expect(card.querySelector('.bi-check-circle-fill')).toBeTruthy();
  });

  it('doit afficher l\'icône d\'erreur pour le statut "error"', () => {
    const spectator = createComponent({
      props: { upload: mockUploadFile({ status: 'error' }) },
    });
    spectator.detectChanges();
    const card = spectator.query('.card.upload-item')!;
    expect(card.querySelector('.bi-x-circle-fill')).toBeTruthy();
  });

  it('doit afficher l\'icône d\'avertissement pour le statut "duplicate"', () => {
    const spectator = createComponent({
      props: { upload: mockUploadFile({ status: 'duplicate', existingBatchId: 'dup-batch' }) },
    });
    spectator.detectChanges();
    const card = spectator.query('.card.upload-item')!;
    // The status icon is in the card body icon cell
    const iconEl = card.querySelector('i.bi.fs-2') as HTMLElement | null;
    expect(iconEl?.classList.contains('bi-exclamation-triangle-fill')).toBe(true);
  });

  it('doit afficher l\'icône par défaut pour le statut "rate-limited"', () => {
    const spectator = createComponent({
      props: { upload: mockUploadFile({ status: 'rate-limited' }) },
    });
    spectator.detectChanges();
    const card = spectator.query('.card.upload-item')!;
    const iconEl = card.querySelector('i.bi.fs-2') as HTMLElement | null;
    expect(iconEl?.classList.contains('bi-file-earmark')).toBe(true);
    expect(iconEl?.classList.contains('bi-check-circle-fill')).toBe(false);
    expect(iconEl?.classList.contains('bi-x-circle-fill')).toBe(false);
    expect(iconEl?.classList.contains('bi-exclamation-triangle-fill')).toBe(false);
  });

  it('doit dispatcher removeUpload au click supprimer quand statut est "pending"', () => {
    const spectator = createComponent({
      props: { upload: mockUploadFile({ status: 'pending' }) },
    });
    spectator.detectChanges();
    const mockStore = spectator.inject(MockStore);
    const dispatchSpy = vi.spyOn(mockStore, 'dispatch');
    spectator.click('.btn.btn-sm.btn-outline-danger');
    expect(dispatchSpy).toHaveBeenCalledWith(
      IngestionActions.removeUpload({ fileId: 'file-1' }),
    );
  });

  it('doit ouvrir le modal de suppression quand statut est "success" et batchId est défini', () => {
    vi.useFakeTimers();
    const spectator = createComponent({
      props: { upload: mockUploadFile({ status: 'success', batchId: 'batch-abc' }) },
    });
    spectator.detectChanges();
    const openModalSpy = vi.spyOn(spectator.component.deleteModal, 'openModal').mockImplementation(() => {});
    spectator.click('.btn.btn-sm.btn-outline-danger');
    vi.runAllTimers();
    expect(openModalSpy).toHaveBeenCalledOnce();
    expect(spectator.component.deleteModal.batchId).toBe('batch-abc');
    expect(spectator.component.deleteModal.filename).toBe('doc.pdf');
  });

  it('doit afficher une notification d\'erreur si batchId est undefined lors d\'une suppression "success"', () => {
    const spectator = createComponent({
      props: { upload: mockUploadFile({ status: 'success', batchId: undefined }) },
    });
    spectator.detectChanges();
    const notificationService = spectator.inject(NotificationService);
    const errorSpy = vi.spyOn(notificationService, 'error');
    spectator.click('.btn.btn-sm.btn-outline-danger');
    expect(errorSpy).toHaveBeenCalled();
  });
});
