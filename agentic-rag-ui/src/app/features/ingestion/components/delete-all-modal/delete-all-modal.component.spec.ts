import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import { DeleteAllModalComponent } from './delete-all-modal.component';

describe('DeleteAllModalComponent', () => {
  const createComponent = createComponentFactory({
    component: DeleteAllModalComponent,
    imports: [CommonModule, FormsModule],
  });

  let spectator: Spectator<DeleteAllModalComponent>;

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

  it('doit afficher le message de confirmation à l\'étape 1', () => {
    spectator.component.openModal();
    spectator.detectChanges();
    expect(spectator.element.textContent).toContain('Étape 1');
  });

  it('doit émettre confirmed au click "Confirmer" avec le texte DELETE_ALL_FILES saisi', () => {
    const confirmedSpy = vi.spyOn(spectator.component.confirmed, 'emit');
    spectator.component.step = 3;
    spectator.component.confirmationText = 'DELETE_ALL_FILES';
    spectator.component.validateAndDelete();
    expect(confirmedSpy).toHaveBeenCalledOnce();
    expect(spectator.component.isDeleting).toBe(true);
  });

  it('doit émettre cancelled au click "Annuler"', () => {
    const cancelledSpy = vi.spyOn(spectator.component.cancelled, 'emit');
    spectator.component.cancel();
    expect(cancelledSpy).toHaveBeenCalledOnce();
  });

  it('doit réinitialiser l\'état à la fermeture', () => {
    spectator.component.openModal();
    spectator.component.step = 2;
    spectator.component.cancel();
    expect(spectator.component.step).toBe(1);
    expect(spectator.component.confirmationText).toBe('');
    expect(spectator.component.isDeleting).toBe(false);
  });

  it('doit retourner le titre correct pour chaque étape', () => {
    spectator.component.step = 1;
    expect(spectator.component.getTitle()).toContain('Étape 1');
    spectator.component.step = 2;
    expect(spectator.component.getTitle()).toContain('Étape 2');
    spectator.component.step = 3;
    expect(spectator.component.getTitle()).toContain('Étape 3');
    spectator.component.step = 4;
    expect(spectator.component.getTitle()).toContain('cours');
    spectator.component.step = 99;
    expect(spectator.component.getTitle()).toBeTruthy();
  });

  it('doit avancer d\'étape via nextStep()', () => {
    expect(spectator.component.step).toBe(1);
    spectator.component.nextStep();
    expect(spectator.component.step).toBe(2);
    spectator.component.nextStep();
    expect(spectator.component.step).toBe(3);
    spectator.component.nextStep();
    expect(spectator.component.step).toBe(3);
  });

  it('ne doit pas émettre confirmed si le texte de confirmation est incorrect', () => {
    const confirmedSpy = vi.spyOn(spectator.component.confirmed, 'emit');
    spectator.component.step = 3;
    spectator.component.confirmationText = 'WRONG_TEXT';
    spectator.component.validateAndDelete();
    expect(confirmedSpy).not.toHaveBeenCalled();
    expect(spectator.component.isDeleting).toBe(false);
  });

  it('doit réinitialiser l\'état via onDeleteSuccess()', () => {
    spectator.component.step = 4;
    spectator.component.isDeleting = true;
    spectator.component.onDeleteSuccess();
    expect(spectator.component.step).toBe(1);
    expect(spectator.component.isDeleting).toBe(false);
  });
});
