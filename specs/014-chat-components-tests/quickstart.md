# Quickstart: Phase 7 — Chat Components Test Suite

**Feature**: `014-chat-components-tests`  
**Date**: 2026-05-02

## Prerequisites

Verified in place — no installation needed:

```bash
# From agentic-rag-ui/
npm test          # Vitest test runner
# Dependencies confirmed in CLAUDE.md:
# @ngneat/spectator ^22.1.0
# @ngrx/store/testing ^21.0.1
# @ngrx/effects/testing ^21.0.1
```

---

## Standard Spec File Structure

Every spec in Phase 7 follows this skeleton:

```typescript
// src/app/features/chat/components/<component>/<component>.component.spec.ts

import { createComponentFactory, mockProvider, Spectator } from '@ngneat/spectator';
import { provideMockStore, MockStore } from '@ngrx/store/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { describe, it, expect, beforeEach } from 'vitest';

import { <ComponentName> } from './<component>.component';

describe('<ComponentName>', () => {
  let spectator: Spectator<<ComponentName>>;

  const createComponent = createComponentFactory({
    component: <ComponentName>,
    schemas: [NO_ERRORS_SCHEMA],   // stubs child components
    providers: [
      // add mockProvider(SomeService) as needed
    ],
  });

  beforeEach(() => {
    spectator = createComponent();
  });

  it('doit créer le composant', () => {
    expect(spectator.component).toBeTruthy();
  });
});
```

---

## Template: ChatInterfaceComponent Spec

```typescript
// chat-interface.component.spec.ts
import { createComponentFactory, Spectator } from '@ngneat/spectator';
import { provideMockStore, MockStore } from '@ngrx/store/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { describe, it, expect, beforeEach } from 'vitest';
import { ChatInterfaceComponent } from './chat-interface.component';
import * as ChatActions from '../../store/chat.actions';

const initialState = {
  chat: {
    activeConversationId: 'conv-1',   // prevents createConversation dispatch
    isStreaming: false,
    inputText: '',
    error: null,
    conversations: {
      ids: ['conv-1'],
      entities: {
        'conv-1': {
          id: 'conv-1',
          messages: [
            { id: 'msg-1', role: 'user', content: 'Bonjour', timestamp: new Date(), status: 'complete' },
            { id: 'msg-2', role: 'assistant', content: 'Bonjour!', timestamp: new Date(), status: 'complete' },
          ]
        }
      }
    }
  }
};

describe('ChatInterfaceComponent', () => {
  let spectator: Spectator<ChatInterfaceComponent>;
  let store: MockStore;

  const createComponent = createComponentFactory({
    component: ChatInterfaceComponent,
    schemas: [NO_ERRORS_SCHEMA],
    providers: [provideMockStore({ initialState })],
  });

  beforeEach(() => {
    spectator = createComponent();
    store = spectator.inject(MockStore);
  });

  it('doit créer le composant', () => {
    expect(spectator.component).toBeTruthy();
  });

  it('[INTÉGRATION] doit dispatcher SendMessage quand l\'utilisateur soumet', () => {
    const dispatchSpy = vi.spyOn(store, 'dispatch');
    // Simulate (send) output from app-message-input child
    const messageInput = spectator.debugElement.query(/* app-message-input selector */);
    // Trigger the (send) event binding
    spectator.triggerEventHandler('app-message-input', 'send', 'Bonjour');
    expect(dispatchSpy).toHaveBeenCalledWith(ChatActions.sendMessage({ content: 'Bonjour' }));
  });
});
```

---

## Template: MessageInputComponent Spec

```typescript
// message-input.component.spec.ts
import { createComponentFactory, Spectator } from '@ngneat/spectator';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MessageInputComponent } from './message-input.component';

describe('MessageInputComponent', () => {
  let spectator: Spectator<MessageInputComponent>;

  const createComponent = createComponentFactory({
    component: MessageInputComponent,
    schemas: [NO_ERRORS_SCHEMA],  // stubs VoiceButtonComponent
  });

  beforeEach(() => {
    spectator = createComponent({ props: { inputText: '' } });
  });

  it('doit créer le composant', () => {
    expect(spectator.component).toBeTruthy();
  });

  it('doit désactiver le bouton Submit si le champ est vide', () => {
    spectator.setInput('inputText', '');
    spectator.detectChanges();
    const btn = spectator.query('[data-testid="send-btn"]') as HTMLButtonElement;
    expect(btn?.disabled).toBe(true);
  });

  it('doit émettre send au Ctrl+Enter quand le champ est rempli', () => {
    const sendSpy = vi.fn();
    spectator.output('send').subscribe(sendSpy);
    spectator.setInput('inputText', 'Bonjour');
    spectator.detectChanges();
    spectator.dispatchKeyboardEvent('textarea', 'keydown', { key: 'Enter', ctrlKey: true });
    expect(sendSpy).toHaveBeenCalledWith('Bonjour');
  });
});
```

---

## Template: VoiceButtonComponent Spec

```typescript
// voice-button.component.spec.ts
import { createComponentFactory, mockProvider, Spectator, SpyObject } from '@ngneat/spectator';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { of, EMPTY } from 'rxjs';
import { VoiceButtonComponent } from './voice-button.component';
import { VoiceService } from '../../../../core/services/voice.service';

describe('VoiceButtonComponent', () => {
  let spectator: Spectator<VoiceButtonComponent>;
  let voiceService: SpyObject<VoiceService>;

  const createComponent = createComponentFactory({
    component: VoiceButtonComponent,
    providers: [mockProvider(VoiceService)],
  });

  beforeEach(() => {
    spectator = createComponent();
    voiceService = spectator.inject(VoiceService);

    // Default: microphone supported, no errors
    voiceService.isRecordingSupported.mockReturnValue(true);
    voiceService.startRecording.mockResolvedValue(undefined);
    voiceService.stopRecording.mockResolvedValue(new Blob([''], { type: 'audio/webm' }));
    voiceService.transcribeWithWhisper.mockReturnValue(
      of({ success: true, transcript: 'Bonjour', language: 'fr', audioSize: 50, filename: 'rec.webm' })
    );
    voiceService.getErrors.mockReturnValue(EMPTY);
  });

  it('doit créer le composant', () => {
    expect(spectator.component).toBeTruthy();
  });

  it('doit désactiver le bouton si le microphone n\'est pas supporté', () => {
    voiceService.isRecordingSupported.mockReturnValue(false);
    spectator = createComponent();   // re-create to trigger ngOnInit with new mock
    spectator.detectChanges();
    expect(spectator.component.isSupported).toBe(false);
  });

  it('doit appeler startRecording() au click et émettre recordingChange=true', async () => {
    const recordingChangeSpy = vi.fn();
    spectator.output('recordingChange').subscribe(recordingChangeSpy);
    await spectator.component.startRecording();
    expect(voiceService.startRecording).toHaveBeenCalled();
    expect(recordingChangeSpy).toHaveBeenCalledWith(true);
  });

  it('doit émettre transcriptFinal après une transcription réussie', async () => {
    const transcriptSpy = vi.fn();
    spectator.output('transcriptFinal').subscribe(transcriptSpy);
    await spectator.component.startRecording();
    await spectator.component.stopRecording();
    expect(transcriptSpy).toHaveBeenCalledWith('Bonjour');
  });
});
```

---

## Running Phase 7 Tests

```bash
# From agentic-rag-ui/

# Run all Phase 7 component tests
npm test -- --include="**/features/chat/components/**"

# Run a single spec
npm test -- --include="**/chat-interface.component.spec.ts"

# Run with coverage report
npm test -- --coverage --include="**/features/chat/components/**"
```

---

## Coverage Target

| Metric | Target | Scope |
|--------|--------|-------|
| Statements | ≥ 80 % | `src/app/features/chat/components/**` |
| Branches | ≥ 75 % | `src/app/features/chat/components/**` |
| Functions | ≥ 85 % | `src/app/features/chat/components/**` |
| Lines | ≥ 80 % | `src/app/features/chat/components/**` |

---

## Known Constraints

- **XSS (FR-017)**: Unit tests cannot reliably assert ngx-markdown sanitization without deep rendering. This is escalated to Phase 8 (page-level integration tests) where `MarkdownModule.forRoot()` can be bootstrapped fully.
- **Highlight pipe (FR-009)**: `highlight.pipe.ts` is empty and not used in templates. FR-009 is suspended until the pipe is implemented. Replace with a note in the spec.
- **setInterval in VoiceButton**: Use `vi.useFakeTimers()` when testing `recordingDuration` updates. Always call `vi.useRealTimers()` in `afterEach`.
- **NgZone**: Provided automatically by Spectator — no manual mock needed.
- **ChatInterface scroll**: `AfterViewChecked` scroll is driven by `shouldScrollToBottom`. Tests asserting scroll use `spectator.query('#messagesContainer')` and read `scrollTop` after dispatching to the mock store.
