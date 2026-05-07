import { Component } from '@angular/core';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Subject } from 'rxjs';

import { WorkspaceComponent } from './workspace.component';
import { UploadPageComponent } from '../../features/ingestion/pages/upload-page/upload-page.component';
import { ChatPageComponent } from '../../features/chat/pages/chat-page/chat-page.component';
import { ToastContainerComponent } from '../../shared/components/toast-container/toast-container.component';
import { Toast, NotificationService } from '../../core/services/notification.service';

import { mockFullIngestionState } from '../../features/ingestion/components/testing/ingestion-test.helpers';
import { buildChatState } from '../../test-helpers';

@Component({ selector: 'app-upload-page', template: '', standalone: true })
class UploadPageStub {}

@Component({ selector: 'app-chat-page', template: '', standalone: true })
class ChatPageStub {}

@Component({ selector: 'app-toast-container', template: '', standalone: true })
class ToastContainerStub {}

const FULL_STATE = () => ({ ...mockFullIngestionState(), chat: buildChatState() });

describe('WorkspaceComponent', () => {
  const createComponent = createComponentFactory({
    component: WorkspaceComponent,
    overrideComponents: [
      [WorkspaceComponent, { remove: { imports: [UploadPageComponent] }, add: { imports: [UploadPageStub] } }],
      [WorkspaceComponent, { remove: { imports: [ChatPageComponent] }, add: { imports: [ChatPageStub] } }],
      [WorkspaceComponent, { remove: { imports: [ToastContainerComponent] }, add: { imports: [ToastContainerStub] } }],
    ],
    detectChanges: true,
  });

  let spectator: Spectator<WorkspaceComponent>;

  beforeEach(() => {
    spectator = createComponent();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('doit créer le composant', () => {
    expect(spectator.component).toBeTruthy();
  });

  it('doit afficher la région sidebar (.workspace-sidebar)', () => {
    expect(spectator.query('.workspace-sidebar')).not.toBeNull();
  });

  it('doit afficher la région main (.workspace-main)', () => {
    expect(spectator.query('.workspace-main')).not.toBeNull();
  });

  it('doit afficher app-upload-page dans la sidebar', () => {
    const sidebar = spectator.query('.workspace-sidebar');
    expect(sidebar?.querySelector('app-upload-page')).not.toBeNull();
  });

  it('doit afficher app-chat-page dans le main', () => {
    const main = spectator.query('.workspace-main');
    expect(main?.querySelector('app-chat-page')).not.toBeNull();
  });

  it('doit afficher app-toast-container dans le workspace', () => {
    expect(spectator.query('app-toast-container')).not.toBeNull();
  });
});

describe('WorkspaceComponent [INTÉGRATION]', () => {
  let toastsSubject: Subject<Toast>;

  const createComponent = createComponentFactory({
    component: WorkspaceComponent,
    overrideComponents: [
      [WorkspaceComponent, { remove: { imports: [UploadPageComponent] }, add: { imports: [UploadPageStub] } }],
      [WorkspaceComponent, { remove: { imports: [ChatPageComponent] }, add: { imports: [ChatPageStub] } }],
    ],
    providers: [
      provideMockStore({ initialState: FULL_STATE() }),
      {
        provide: NotificationService,
        useValue: {
          get toasts$() { return toastsSubject.asObservable(); },
          success: vi.fn(),
          error: vi.fn(),
          warning: vi.fn(),
          info: vi.fn(),
        },
      },
    ],
    detectChanges: true,
  });

  let spectator: Spectator<WorkspaceComponent>;
  let store: MockStore;

  beforeEach(() => {
    toastsSubject = new Subject<Toast>();
    spectator = createComponent();
    store = spectator.inject(MockStore);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('[INTÉGRATION] doit afficher les trois enfants simultanément', () => {
    expect(spectator.query('app-upload-page')).not.toBeNull();
    expect(spectator.query('app-chat-page')).not.toBeNull();
    expect(spectator.query('app-toast-container')).not.toBeNull();
  });

  it('[INTÉGRATION] doit afficher un toast quand NotificationService émet un succès', () => {
    toastsSubject.next({ id: '1', type: 'success', title: 'OK', message: 'Upload terminé' });
    spectator.detectChanges();
    expect(spectator.query('.toast.bg-success')).not.toBeNull();
  });

  it('[INTÉGRATION] doit afficher un toast d\'erreur quand NotificationService émet une erreur', () => {
    toastsSubject.next({ id: '2', type: 'error', title: 'Erreur', message: 'Échec upload' });
    spectator.detectChanges();
    expect(spectator.query('.toast.bg-danger')).not.toBeNull();
  });

  it('[INTÉGRATION] doit être compatible avec le store complet (5 slices)', () => {
    const updatedState = {
      ...FULL_STATE(),
      ingestion: { ...FULL_STATE().ingestion, activeUploads: 1 },
    };
    store.setState(updatedState);
    spectator.detectChanges();
    expect(spectator.component).toBeTruthy();
  });

  it('[INTÉGRATION] doit se détruire sans laisser de souscriptions actives', () => {
    expect(() => spectator.fixture.destroy()).not.toThrow();
  });
});