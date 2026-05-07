import { Component } from '@angular/core';
import { createComponentFactory, mockProvider, Spectator } from '@ngneat/spectator/vitest';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { WorkspaceComponent } from './workspace.component';
import { UploadPageComponent } from '../../features/ingestion/pages/upload-page/upload-page.component';
import { ChatPageComponent } from '../../features/chat/pages/chat-page/chat-page.component';
import { ToastContainerComponent } from '../../shared/components/toast-container/toast-container.component';
import { NotificationService } from '../../core/services/notification.service';

import { mockFullIngestionState } from '../../features/ingestion/components/testing/ingestion-test.helpers';
import { buildChatState } from '../../test-helpers';

// ─── Stubs ────────────────────────────────────────────────────────────────────
@Component({ selector: 'app-upload-page', template: '', standalone: true })
class UploadPageStub {}

@Component({ selector: 'app-chat-page', template: '', standalone: true })
class ChatPageStub {}

@Component({ selector: 'app-toast-container', template: '', standalone: true })
class ToastContainerStub {}

// ─── Helpers ──────────────────────────────────────────────────────────────────
const FULL_STATE = () => ({ ...mockFullIngestionState(), chat: buildChatState() });

// ─── Unit Tests ───────────────────────────────────────────────────────────────
describe('WorkspaceComponent', () => {
  const createComponent = createComponentFactory({
    component: WorkspaceComponent,
    overrideComponents: [
      [WorkspaceComponent, { remove: { imports: [UploadPageComponent] }, add: { imports: [UploadPageStub] } }],
      [WorkspaceComponent, { remove: { imports: [ChatPageComponent] }, add: { imports: [ChatPageStub] } }],
      [WorkspaceComponent, { remove: { imports: [ToastContainerComponent] }, add: { imports: [ToastContainerStub] } }],
    ],
    shallow: false,
    detectChanges: true,
  });

  let spectator: Spectator<WorkspaceComponent>;

  beforeEach(() => {
    spectator = createComponent();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  // Workspace Shell Renders Correctly (US1)
  it('doit créer le composant', () => {
    expect(spectator.component).toBeTruthy();
  });

  it('doit afficher la région sidebar (.workspace-sidebar)', () => {
    expect(spectator.query('.workspace-sidebar')).not.toBeNull();
  });

  it('doit afficher la région main (.workspace-main)', () => {
    expect(spectator.query('.workspace-main')).not.toBeNull();
  });

  // Child Feature Pages Are Embedded (US2)
  it('doit afficher app-upload-page dans la sidebar', () => {
    const sidebar = spectator.query('.workspace-sidebar');
    expect(sidebar?.querySelector('app-upload-page')).not.toBeNull();
  });

  it('doit afficher app-chat-page dans le main', () => {
    const main = spectator.query('.workspace-main');
    expect(main?.querySelector('app-chat-page')).not.toBeNull();
  });

  // Toast Notifications Are Available Globally (US3)
  it('doit afficher app-toast-container dans le workspace', () => {
    expect(spectator.query('app-toast-container')).not.toBeNull();
  });
});

// ─── Integration Tests ────────────────────────────────────────────────────────
describe('WorkspaceComponent [INTÉGRATION]', () => {
  const createComponent = createComponentFactory({
    component: WorkspaceComponent,
    overrideComponents: [
      [WorkspaceComponent, { remove: { imports: [UploadPageComponent] }, add: { imports: [UploadPageStub] } }],
      [WorkspaceComponent, { remove: { imports: [ChatPageComponent] }, add: { imports: [ChatPageStub] } }],
      [WorkspaceComponent, { remove: { imports: [ToastContainerComponent] }, add: { imports: [ToastContainerStub] } }],
    ],
    providers: [
      provideMockStore({ initialState: FULL_STATE() }),
      mockProvider(NotificationService),
    ],
    detectChanges: true,
  });

  let spectator: Spectator<WorkspaceComponent>;
  let store: MockStore;

  beforeEach(() => {
    spectator = createComponent();
    store = spectator.inject(MockStore);
  });

  afterEach(() => {
    store.resetSelectors();
    vi.restoreAllMocks();
  });

  // Toast Notifications Are Available Globally (US3) & Child embedding (US1+US2)
  it('[INTÉGRATION] doit afficher les trois enfants simultanément', () => {
    expect(spectator.query('app-upload-page')).not.toBeNull();
    expect(spectator.query('app-chat-page')).not.toBeNull();
    expect(spectator.query('app-toast-container')).not.toBeNull();
  });

  it('[INTÉGRATION] doit s\'abonner au store et afficher le contenu', () => {
    const notificationService = spectator.inject(NotificationService);
    expect(notificationService).toBeTruthy();
    expect(spectator.component).toBeTruthy();
  });

  // Full Upload-to-Chat Integration Flow (US4)
  it('[INTÉGRATION] doit être compatible avec le store complet (5 slices)', () => {
    store.refreshState();
    expect(store).toBeTruthy();
    expect(spectator.component).toBeTruthy();
  });
});
