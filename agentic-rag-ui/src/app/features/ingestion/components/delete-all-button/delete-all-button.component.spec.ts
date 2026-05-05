import { CommonModule } from '@angular/common';
import { createComponentFactory, Spectator, mockProvider } from '@ngneat/spectator/vitest';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

import { DeleteAllButtonComponent } from './delete-all-button.component';
import { DeleteAllModalComponent } from '../delete-all-modal/delete-all-modal.component';
import { NotificationService } from '../../../../core/services/notification.service';
import * as CrudActions from '../../store/crud/crud.actions';
import { selectCrudLoading } from '../../store/crud/crud.selectors';
import { selectUploads } from '../../store/ingestion/ingestion.selectors';
import {
  mockCrudState,
  mockIngestionState,
  mockUploadFile,
} from '../testing/ingestion-test.helpers';

describe('DeleteAllButtonComponent', () => {
  const createComponent = createComponentFactory({
    component: DeleteAllButtonComponent,
    imports: [CommonModule, DeleteAllModalComponent],
    providers: [
      provideMockStore({
        initialState: { ...mockCrudState(), ...mockIngestionState([]) },
      }),
      mockProvider(NotificationService),
    ],
  });

  let spectator: Spectator<DeleteAllButtonComponent>;
  let mockStore: MockStore;

  beforeEach(() => {
    (window as any).bootstrap = {
      Modal: class {
        constructor(_el: HTMLElement) {}
        show() {}
        hide() {}
        static getInstance(_el: HTMLElement) { return null; }
      },
    };
    spectator = createComponent();
    mockStore = spectator.inject(MockStore);
  });

  afterEach(() => vi.useRealTimers());

  it('doit être disabled quand la liste d\'uploads est vide', () => {
    spectator.detectChanges();
    const btn = spectator.query('button.btn-danger');
    expect(btn?.hasAttribute('disabled')).toBe(true);
  });

  it('doit être disabled quand une suppression est en cours', () => {
    mockStore.overrideSelector(selectCrudLoading, true);
    mockStore.overrideSelector(selectUploads, []);
    mockStore.refreshState();
    spectator.detectChanges();
    const btn = spectator.query('button.btn-danger');
    expect(btn?.hasAttribute('disabled')).toBe(true);
  });

  it('doit être actif quand la liste n\'est pas vide et aucune suppression en cours', () => {
    mockStore.overrideSelector(selectCrudLoading, false);
    mockStore.overrideSelector(selectUploads, [mockUploadFile()]);
    mockStore.refreshState();
    spectator.detectChanges();
    const btn = spectator.query<HTMLButtonElement>('button.btn-danger');
    expect(btn?.disabled).toBeFalsy();
  });

  it('doit ouvrir le modal de confirmation au click quand le bouton est actif', () => {
    vi.useFakeTimers();
    mockStore.overrideSelector(selectCrudLoading, false);
    mockStore.overrideSelector(selectUploads, [mockUploadFile()]);
    mockStore.refreshState();
    spectator.detectChanges();
    const openModalSpy = vi.spyOn(spectator.component.modal, 'openModal').mockImplementation(() => {});
    spectator.click('button.btn-danger');
    vi.runAllTimers();
    expect(openModalSpy).toHaveBeenCalledOnce();
  });

  it('doit dispatcher deleteAllFiles quand onConfirmed est appelé', () => {
    vi.useFakeTimers();
    const dispatchSpy = vi.spyOn(mockStore, 'dispatch');
    spectator.component.onConfirmed();
    expect(dispatchSpy).toHaveBeenCalledWith(
      CrudActions.deleteAllFiles({ confirmation: 'DELETE_ALL_FILES' }),
    );
  });

  it('onCancelled ne doit pas déclencher d\'action dans le store', () => {
    const dispatchSpy = vi.spyOn(mockStore, 'dispatch');
    spectator.component.onCancelled();
    expect(dispatchSpy).not.toHaveBeenCalled();
  });
});
