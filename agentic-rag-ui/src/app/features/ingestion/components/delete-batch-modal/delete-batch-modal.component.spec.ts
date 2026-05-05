import { CommonModule } from '@angular/common';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

import { DeleteBatchModalComponent } from './delete-batch-modal.component';

describe('DeleteBatchModalComponent', () => {
  const createComponent = createComponentFactory({
    component: DeleteBatchModalComponent,
    imports: [CommonModule],
  });

  let spectator: Spectator<DeleteBatchModalComponent>;

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
  });

  afterEach(() => vi.useRealTimers());

  it('doit afficher le nom du fichier passé en entrée', () => {
    spectator.setInput('filename', 'rapport.pdf');
    spectator.setInput('batchId', 'batch-xyz');
    spectator.detectChanges();
    expect(spectator.element.textContent).toContain('rapport.pdf');
  });

  it('doit émettre confirmed (sans payload) et passer isDeleting à true au click confirmer', () => {
    const confirmedSpy = vi.spyOn(spectator.component.confirmed, 'emit');
    spectator.component.confirm();
    expect(confirmedSpy).toHaveBeenCalledOnce();
    expect(spectator.component.isDeleting).toBe(true);
  });

  it('doit émettre cancelled et ne pas modifier isDeleting au click annuler', () => {
    const cancelledSpy = vi.spyOn(spectator.component.cancelled, 'emit');
    spectator.component.cancel();
    expect(cancelledSpy).toHaveBeenCalledOnce();
    expect(spectator.component.isDeleting).toBe(false);
  });

  it('doit ouvrir le modal Bootstrap quand l\'élément existe dans le DOM', () => {
    const modalEl = document.createElement('div');
    modalEl.id = 'deleteBatchModal';
    document.body.appendChild(modalEl);
    const showSpy = vi.fn();
    (window as any).bootstrap.Modal = class {
      constructor(_el: HTMLElement) {}
      show = showSpy;
      hide() {}
      static getInstance(_el: HTMLElement) { return null; }
    };
    spectator.component.openModal();
    expect(showSpy).toHaveBeenCalled();
    document.body.removeChild(modalEl);
  });

  it('doit réinitialiser l\'état et fermer le modal via onDeleteSuccess()', () => {
    vi.useFakeTimers();
    spectator.component.isDeleting = true;
    spectator.component.onDeleteSuccess();
    expect(spectator.component.isDeleting).toBe(false);
  });
});
