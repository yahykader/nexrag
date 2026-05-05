import { CommonModule } from '@angular/common';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { describe, it, expect, beforeEach } from 'vitest';

import { ProgressPanelComponent } from './progress-panel.component';
import { selectActiveProgress } from '../../store/progress/progress.selectors';
import { mockProgressState, mockUploadProgress } from '../testing/ingestion-test.helpers';

describe('ProgressPanelComponent', () => {
  const createComponent = createComponentFactory({
    component: ProgressPanelComponent,
    imports: [CommonModule],
    providers: [provideMockStore({ initialState: mockProgressState() })],
  });

  let spectator: Spectator<ProgressPanelComponent>;
  let mockStore: MockStore;

  beforeEach(() => {
    spectator = createComponent();
    mockStore = spectator.inject(MockStore);
  });

  it('doit créer le composant', () => {
    expect(spectator.component).toBeTruthy();
  });

  it('doit afficher le pourcentage d\'avancement quand des batches sont actifs', () => {
    mockStore.overrideSelector(selectActiveProgress, [
      mockUploadProgress({ progressPercentage: 75 }),
    ]);
    mockStore.refreshState();
    spectator.detectChanges();
    expect(spectator.element.textContent).toContain('75');
  });

  it('doit se connecter au sélecteur de progression du store', () => {
    expect(spectator.component.activeProgress$).toBeTruthy();
  });

  it('doit se masquer si aucun batch n\'est en cours', () => {
    mockStore.overrideSelector(selectActiveProgress, []);
    mockStore.refreshState();
    spectator.detectChanges();
    expect(spectator.query('.progress-item')).toBeNull();
    expect(spectator.element.textContent).toContain('Aucun upload en cours');
  });
});
