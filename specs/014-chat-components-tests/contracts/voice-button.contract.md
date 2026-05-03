# Component Contract: VoiceButtonComponent

**Selector**: `app-voice-button`  
**File**: `src/app/features/chat/components/voice-control/voice-button.component.ts`  
**Spec file to create**: `voice-button.component.spec.ts` (co-located)

## Public Surface

### Inputs

| Name | Type | Default | Notes |
|------|------|---------|-------|
| `language` | `string` | `'fr'` | Passed to `transcribeWithWhisper()` as language |
| `disabled` | `boolean` | `false` | Disables the button |

### Outputs

| Name | Type | Emitted When |
|------|------|-------------|
| `transcriptFinal` | `EventEmitter<string>` | After successful Whisper transcription |
| `recordingChange` | `EventEmitter<boolean>` | `true` when recording starts, `false` when it stops |
| `error` | `EventEmitter<string>` | On any VoiceService error |

### Component State (DOM-visible)

| Property | Type | Notes |
|----------|------|-------|
| `isSupported` | `boolean` | Set by `voiceService.isRecordingSupported()` in `ngOnInit` |
| `isRecording` | `boolean` | `true` while MediaRecorder active |
| `isProcessing` | `boolean` | `true` while Whisper HTTP call is in flight |
| `errorMessage` | `string` | Shown for 5 s, then cleared |
| `recordingDuration` | `string` | `'MM:SS'` — updated by `setInterval` outside Angular zone |

### Public Methods

| Method | Signature | Notes |
|--------|-----------|-------|
| `toggleRecording()` | `async (): Promise<void>` | Calls `startRecording()` or `stopRecording()` |
| `startRecording()` | `async (): Promise<void>` | Calls `voiceService.startRecording()` |
| `stopRecording()` | `async (): Promise<void>` | Calls `voiceService.stopRecording()`, then `transcribeWithWhisper()` |
| `stopRecognition()` | `(): void` | Public alias for stopping from parent |
| `getTooltip()` | `(): string` | Returns localised tooltip string |
| `getButtonClass()` | `(): string` | Returns Bootstrap class based on state |

### VoiceService Mock Requirements

```typescript
const mockVoiceService = {
  isRecordingSupported: vi.fn().mockReturnValue(true),
  startRecording:       vi.fn().mockResolvedValue(undefined),
  stopRecording:        vi.fn().mockResolvedValue(new Blob(['audio'], { type: 'audio/webm' })),
  transcribeWithWhisper: vi.fn().mockReturnValue(of({
    success: true,
    transcript: 'Bonjour',
    language: 'fr',
    audioSize: 100,
    filename: 'audio.webm',
  })),
  getErrors:            vi.fn().mockReturnValue(EMPTY),
};
```

## Test Strategy

### Setup
```typescript
const createComponent = createComponentFactory({
  component: VoiceButtonComponent,
  providers: [mockProvider(VoiceService)],
  // NgZone and ChangeDetectorRef provided automatically by Spectator
});
```

### Unit Tests (7)
1. Smoke: component creates successfully
2. Not supported: `isRecordingSupported()` returns `false` → unsupported state indicator visible; button disabled or hidden
3. Start recording: click voice button → `voiceService.startRecording()` called; `recordingChange` emits `true`
4. Pulsing state: after `startRecording()`, `isRecording===true` → component displays recording class (e.g., `btn-danger` via `getButtonClass()`)
5. Stop and transcribe: click again (stop) → `voiceService.stopRecording()` called, `transcribeWithWhisper()` called
6. Transcript emitted: `transcribeWithWhisper()` returns success response → `transcriptFinal` emits `'Bonjour'`
7. Error handling: `getErrors()` emits an error string → `error` output emits and `errorMessage` is set

### Integration Tests (1 — prefixed `[INTÉGRATION]`)
8. `[INTÉGRATION]` Full recording flow: click start → click stop → mock Whisper returns transcript → `transcriptFinal` emits correct value; `recordingChange` emits `true` then `false`

## Timing Considerations

`VoiceButtonComponent` uses `NgZone.runOutsideAngular()` with `setInterval` for the duration timer. When testing `recordingDuration`, use `vi.useFakeTimers()` and `vi.advanceTimersByTime(1000)` to advance the timer. Call `spectator.detectChanges()` after advancing. Clean up with `vi.useRealTimers()` in `afterEach`.

## Naming Convention

```typescript
describe('VoiceButtonComponent', () => {
  it('doit créer le composant', ...)
  it('doit désactiver le bouton si le microphone n\'est pas supporté', ...)
  it('doit appeler startRecording() au click', ...)
  it('doit appliquer la classe btn-danger pendant l\'enregistrement', ...)
  it('doit émettre transcriptFinal quand la transcription réussit', ...)
  it('doit émettre error si VoiceService signale une erreur', ...)
  it('[INTÉGRATION] le flux complet : démarrage → arrêt → transcript', ...)
})
```
