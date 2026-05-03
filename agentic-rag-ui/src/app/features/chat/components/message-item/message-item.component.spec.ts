import { NO_ERRORS_SCHEMA } from '@angular/core';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { MarkdownModule } from 'ngx-markdown';
import { MessageItemComponent } from './message-item.component';
import { mockMessage } from '../../../../test-helpers';

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('MessageItemComponent', () => {
  let spectator: Spectator<MessageItemComponent>;

  const createComponent = createComponentFactory({
    component: MessageItemComponent,
    schemas: [NO_ERRORS_SCHEMA],
    overrideComponents: [
      [MessageItemComponent, {
        remove: { imports: [MarkdownModule] },
        add: {},
      }]
    ],
  });

  beforeEach(() => {
    spectator = createComponent({ props: { message: mockMessage(), isStreaming: false } });
  });

  afterEach(() => vi.restoreAllMocks());

  // ── Smoke ─────────────────────────────────────────────────────────────────

  it('doit créer le composant', () => {
    expect(spectator.component).toBeTruthy();
  });

  // ── CSS classes ────────────────────────────────────────────────────────────

  it('doit appliquer la classe user-message si role=user', () => {
    spectator.setInput('message', mockMessage({ role: 'user' }));
    expect(spectator.query('.message-item')).toHaveClass('user-message');
  });

  it('doit appliquer la classe assistant-message si role=assistant', () => {
    spectator.setInput('message', mockMessage({ role: 'assistant' }));
    expect(spectator.query('.message-item')).toHaveClass('assistant-message');
  });

  // ── Streaming cursor ──────────────────────────────────────────────────────

  it('doit afficher le curseur de streaming quand isStreaming=true et status=streaming', () => {
    spectator.setInput('message', mockMessage({ status: 'streaming' }));
    spectator.setInput('isStreaming', true);
    expect(spectator.query('.streaming-cursor')).toBeTruthy();
  });

  it('doit masquer le curseur de streaming quand isStreaming=false', () => {
    spectator.setInput('isStreaming', false);
    expect(spectator.query('.streaming-cursor')).toBeFalsy();
  });

  // ── Timestamp ─────────────────────────────────────────────────────────────

  it('doit afficher le timestamp au format HH:mm', () => {
    const timestamp = new Date(2026, 4, 2, 14, 35, 0);
    spectator.setInput('message', mockMessage({ timestamp }));
    const timeEl = spectator.query('.message-time');
    expect(timeEl?.textContent?.trim()).toContain('14:35');
  });

  // ── Markdown directive ────────────────────────────────────────────────────

  it('doit avoir l\'attribut markdown sur l\'élément de contenu', () => {
    expect(spectator.query('[markdown]')).toBeTruthy();
  });

  // ── parseSourceContent ────────────────────────────────────────────────────

  it('parseSourceContent doit extraire le nom de fichier d\'un lien markdown', () => {
    const { component } = spectator;
    expect(component.parseSourceContent('[source](doc.pdf)')).toBe('doc.pdf');
    expect(component.parseSourceContent('[label](file.docx)')).toBe('file.docx');
    expect(component.parseSourceContent('rapport.pdf')).toBe('rapport.pdf');
    expect(component.parseSourceContent('texte sans extension')).toBe('');
  });

  // ── Integration ───────────────────────────────────────────────────────────

  // TODO(phase-8): FR-017 XSS sanitization testing deferred to Phase 8 page-level integration tests
  //               (requires real MarkdownModule.forRoot() bootstrapping)

  // TODO(pipe-impl): FR-009 highlight pipe test suspended — highlight.pipe.ts is empty and not used in templates

  it('[INTÉGRATION] doit afficher les sources groupées quand le message a des citations', () => {
    const messageWithCitations = mockMessage({
      role: 'assistant',
      citations: [
        { index: 1, content: 'doc1.pdf' },
        { index: 2, content: 'doc2.pdf' },
      ],
    });
    spectator.setInput('message', messageWithCitations);
    expect(spectator.queryAll('.source-item').length).toBe(2);
  });

  it('[INTÉGRATION] doit déclencher window.alert au clic sur une source', () => {
    vi.spyOn(window, 'alert').mockImplementation(() => {});
    const messageWithCitations = mockMessage({
      role: 'assistant',
      citations: [{ index: 1, content: 'doc1.pdf' }],
    });
    spectator.setInput('message', messageWithCitations);
    spectator.queryAll('.source-item')[0].dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(window.alert).toHaveBeenCalled();
  });
});
