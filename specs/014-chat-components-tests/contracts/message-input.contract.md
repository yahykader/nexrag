# Component Contract: MessageInputComponent

**Selector**: `app-message-input`  
**File**: `src/app/features/chat/components/message-input/message-input.component.ts`  
**Spec file to create**: `message-input.component.spec.ts` (co-located)

## Public Surface

### Inputs

| Name | Type | Default | Notes |
|------|------|---------|-------|
| `inputText` | `string` | `''` | Bound to textarea value |
| `disabled` | `boolean` | `false` | Disables textarea and send button |
| `isStreaming` | `boolean` | `false` | Shows cancel button when true |

### Outputs

| Name | Type | Emitted When |
|------|------|-------------|
| `send` | `EventEmitter<string>` | Submit click OR Ctrl+Enter, only if `inputText.trim()` non-empty AND `!disabled` |
| `cancel` | `EventEmitter<void>` | Cancel button clicked |
| `inputChange` | `EventEmitter<string>` | Every keystroke in textarea |
| `transcript` | `EventEmitter<string>` | Voice transcript received from VoiceButtonComponent |
| `recordingChange` | `EventEmitter<boolean>` | Mic toggle state from VoiceButtonComponent |
| `voiceError` | `EventEmitter<string>` | Error from VoiceButtonComponent |

### Public Methods

| Method | Signature | Notes |
|--------|-----------|-------|
| `onSend()` | `(): void` | Guards: `trim()` non-empty AND `!disabled` |
| `onCancel()` | `(): void` | Emits `cancel` |
| `onKeyDown(event)` | `(KeyboardEvent): void` | Ctrl+Enter OR Cmd+Enter → `onSend()` |
| `onInputChange(text)` | `(string): void` | Emits `inputChange` |
| `onTranscript(text)` | `(string): void` | Appends voice text to `inputText`, emits `inputChange` |
| `onRecordingChange(b)` | `(boolean): void` | Updates `isVoiceRecording` |
| `onVoiceError(e)` | `(string): void` | `console.error` only |

## Test Strategy

### Setup
```typescript
const createComponent = createComponentFactory({
  component: MessageInputComponent,
  schemas: [NO_ERRORS_SCHEMA],   // stubs VoiceButtonComponent
});
```
No store needed — pure Input/Output component.

### Unit Tests (8)
1. Smoke: component creates successfully
2. Submit disabled when empty: `inputText=''` → `send` button is `[disabled]`
3. Submit emits on click: set `inputText='Bonjour'`, click send → `send` emits `'Bonjour'`
4. Ctrl+Enter submits: set `inputText='Bonjour'`, dispatch `keydown` with `ctrlKey+Enter` → `send` emits
5. Ctrl+Enter without text: `inputText=''`, dispatch `keydown` with `ctrlKey+Enter` → `send` NOT emitted
6. Disabled blocks send: `disabled=true`, `inputText='Hello'`, click send → `send` NOT emitted
7. Whitespace guard: `inputText='   '`, click send → `send` NOT emitted (trim returns empty)
8. isStreaming shows cancel: `isStreaming=true` → cancel button visible

### Integration Tests (1 — prefixed `[INTÉGRATION]`)
9. `[INTÉGRATION]` Voice transcript appended: simulate `(transcriptFinal)` from voice stub → `inputText` updated and `inputChange` emitted

## Naming Convention

```typescript
describe('MessageInputComponent', () => {
  it('doit créer le composant', ...)
  it('doit désactiver le bouton Submit si le champ est vide', ...)
  it('doit émettre send au click submit avec le contenu saisi', ...)
  it('doit émettre send au Ctrl+Enter', ...)
  it('doit ignorer Ctrl+Enter si le champ est vide', ...)
  it('doit être désactivé pendant le streaming', ...)
  it('[INTÉGRATION] doit ajouter le transcript vocal au champ de saisie', ...)
})
```
