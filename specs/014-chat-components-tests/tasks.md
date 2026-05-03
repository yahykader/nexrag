# Tasks: Phase 7 — Chat Components Test Suite

**Input**: Design documents from `/specs/014-chat-components-tests/`  
**Prerequisites**: plan.md ✅ spec.md ✅ research.md ✅ data-model.md ✅ contracts/ ✅ quickstart.md ✅

**Organization**: Tasks grouped by user story — each story maps to one Angular component spec file and is independently executable and verifiable.

**Test runner**: `npm test` (Vitest) from `agentic-rag-ui/`  
**Run Phase 7 only**: `npm test -- --include="**/features/chat/components/**"`

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on sibling tasks)
- **[Story]**: User story label (US1–US4) — maps to spec.md priorities

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create shared test fixtures and verify the test environment before writing any spec.

- [X] T001 Create shared test fixture factory file at `agentic-rag-ui/src/app/features/chat/components/test-helpers.ts` exporting `mockMessage()`, `mockAssistantMessage()`, and `mockChatState()` factories (see data-model.md for shapes)
- [X] T002 Verify Vitest config (`agentic-rag-ui/vitest.config.ts` or `vite.config.ts`) includes `**/*.spec.ts` glob and Angular plugin — add coverage thresholds if absent (statements ≥ 80, branches ≥ 75, functions ≥ 85)
- [X] T003 [P] Confirm `@ngneat/spectator`, `@ngrx/store/testing`, and `NO_ERRORS_SCHEMA` imports resolve by running `npm test -- --passWithNoTests` with a minimal smoke import in any existing spec file

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Resolve the six spec-vs-implementation discrepancies documented in `research.md` so every downstream spec uses the correct selectors, class names, method signatures, and API names.

**⚠️ CRITICAL**: All four component specs depend on these decisions — confirm each correction before writing assertions.

- [X] T004 Read `agentic-rag-ui/src/app/features/chat/store/chat.selectors.ts` and confirm selector name `selectActiveMessages` (not `selectAllMessages`) — document the correct initial state shape required by `provideMockStore` for ChatInterfaceComponent tests
- [X] T005 Read `agentic-rag-ui/src/app/features/chat/components/message-item/message-item.component.html` and confirm: timestamp pipe is `date:'HH:mm'`, CSS classes are `user-message`/`assistant-message`, markdown uses `[markdown]` attribute (not a custom pipe), highlight pipe is NOT present
- [X] T006 Read `agentic-rag-ui/src/app/core/services/voice.service.ts` and confirm public API: `isRecordingSupported()`, `startRecording()`, `stopRecording()`, `transcribeWithWhisper()`, `getErrors()` — these are the only methods to mock in VoiceButtonComponent tests

**Checkpoint**: Research discrepancies confirmed → spec file authoring can begin in parallel across all stories.

---

## Phase 3: User Story 1 — ChatInterfaceComponent (Priority: P1) 🎯 MVP

**Goal**: Verify that `ChatInterfaceComponent` reads messages and streaming state from the NgRx store, renders the correct child elements, auto-scrolls on new messages, and dispatches `sendMessage` when the input emits.

**Independent Test**: Run `npm test -- --include="**/chat-interface.component.spec.ts"` → 7 tests pass.

### Implementation — US1

- [X] T007 [US1] Create spec file skeleton `agentic-rag-ui/src/app/features/chat/components/chat-interface/chat-interface.component.spec.ts` with `createComponentFactory`, `provideMockStore({ initialState })` seeding `activeConversationId: 'conv-1'` (prevents `createConversation` dispatch), and `NO_ERRORS_SCHEMA`
- [X] T008 [US1] Write smoke test: `'doit créer le composant'` — asserts `spectator.component` is truthy
- [X] T009 [US1] Write message rendering test: `'doit afficher 3 messages quand le store en contient 3'` — seed mock store with 3 messages in `conv-1`; assert 3 `app-message-item` elements in the DOM via `spectator.queryAll('app-message-item')`
- [X] T010 [US1] Write empty state test: `'doit afficher un état vide sans erreur quand la liste de messages est vide'` — seed 0 messages; assert `queryAll('app-message-item').length === 0` and component does not throw
- [X] T011 [US1] Write streaming state test: `'doit afficher un indicateur de streaming quand isStreaming=true'` — update mock store state `isStreaming: true`; assert streaming indicator element is present (query by CSS class or test-id used in the template)
- [X] T012 [US1] Write child component test: `'doit contenir app-message-input dans le template'` — assert `spectator.query('app-message-input')` is not null
- [X] T013 [US1] Write init dispatch guard test: `'ne doit pas dispatcher createConversation si activeConversationId est défini'` — spy `store.dispatch`; verify `ChatActions.createConversation()` was NOT called when `activeConversationId: 'conv-1'`
- [X] T014 [US1] Write integration test: `'[INTÉGRATION] doit dispatcher SendMessage quand l\'utilisateur soumet un message'` — spy `store.dispatch`; call `spectator.triggerEventHandler('app-message-input', 'send', 'Bonjour')`; assert `ChatActions.sendMessage({ content: 'Bonjour' })` was dispatched

**Checkpoint**: Run `npm test -- --include="**/chat-interface.component.spec.ts"` — all 7 tests green.

---

## Phase 4: User Story 2 — MessageInputComponent (Priority: P1)

**Goal**: Verify all submit paths (click, Ctrl+Enter), the empty/whitespace/disabled guards, the streaming cancel button, and voice transcript integration.

**Independent Test**: Run `npm test -- --include="**/message-input.component.spec.ts"` → 9 tests pass.

### Implementation — US2

- [X] T015 [P] [US2] Create spec file skeleton `agentic-rag-ui/src/app/features/chat/components/message-input/message-input.component.spec.ts` with `createComponentFactory`, `NO_ERRORS_SCHEMA` (stubs `VoiceButtonComponent`), no store needed
- [X] T016 [US2] Write smoke test: `'doit créer le composant'`
- [X] T017 [US2] Write empty guard test: `'doit désactiver le bouton Submit si le champ est vide'` — set `inputText=''`; assert send button is `[disabled]`
- [X] T018 [US2] Write click submit test: `'doit émettre send au click submit avec le contenu saisi'` — set `inputText='Bonjour'`; click submit button; assert `send` output emitted `'Bonjour'`
- [X] T019 [US2] Write Ctrl+Enter submit test: `'doit émettre send au Ctrl+Enter quand le champ est rempli'` — set `inputText='Bonjour'`; dispatch `keydown` event with `{ key: 'Enter', ctrlKey: true }` on textarea; assert `send` emitted
- [X] T020 [US2] Write Ctrl+Enter empty guard test: `'doit ne pas émettre send au Ctrl+Enter si le champ est vide'` — `inputText=''`; dispatch `ctrlKey+Enter`; assert `send` NOT emitted
- [X] T021 [US2] Write whitespace guard test: `'doit ne pas émettre send si le champ contient uniquement des espaces'` — `inputText='   '`; click submit; assert `send` NOT emitted
- [X] T022 [US2] Write disabled-blocks-send test: `'doit ne pas émettre send quand disabled=true'` — `disabled=true`, `inputText='Hello'`; click submit; assert `send` NOT emitted
- [X] T023 [US2] Write streaming cancel test: `'doit afficher le bouton Annuler quand isStreaming=true'` — `isStreaming=true`; assert cancel button visible; click it; assert `cancel` output emitted
- [X] T024 [US2] Write integration test: `'[INTÉGRATION] doit ajouter le transcript vocal au champ de saisie'` — trigger `(transcriptFinal)` event from stubbed `app-voice-button`; assert `spectator.component.inputText` is updated and `inputChange` output emitted with new value

**Checkpoint**: Run `npm test -- --include="**/message-input.component.spec.ts"` — all 9 tests green.

---

## Phase 5: User Story 3 — MessageItemComponent (Priority: P2)

**Goal**: Verify CSS role classes (`user-message`/`assistant-message`), streaming cursor, `HH:mm` timestamp, `[markdown]` directive presence, `parseSourceContent()` logic, and citation rendering.

**Independent Test**: Run `npm test -- --include="**/message-item.component.spec.ts"` → 10 tests pass.

### Implementation — US3

- [X] T025 [P] [US3] Create spec file skeleton `agentic-rag-ui/src/app/features/chat/components/message-item/message-item.component.spec.ts` with `createComponentFactory`, `NO_ERRORS_SCHEMA` (prevents MarkdownModule bootstrap), import `mockMessage` and `mockAssistantMessage` from test-helpers
- [X] T026 [US3] Write smoke test: `'doit créer le composant'`
- [X] T027 [US3] Write user class test: `'doit appliquer la classe user-message si role=user'` — set `message=mockMessage({ role: 'user' })`; assert `spectator.query('.message-item')` has class `user-message`
- [X] T028 [US3] Write assistant class test: `'doit appliquer la classe assistant-message si role=assistant'` — set `message=mockAssistantMessage()`; assert class `assistant-message` present
- [X] T029 [US3] Write streaming cursor test: `'doit afficher le curseur de streaming quand isStreaming=true et status=streaming'` — set `isStreaming=true`, `message.status='streaming'`; assert `.streaming-cursor` element is present
- [X] T030 [US3] Write no-cursor test: `'doit masquer le curseur de streaming quand isStreaming=false'` — `isStreaming=false`; assert `.streaming-cursor` absent
- [X] T031 [US3] Write timestamp format test: `'doit afficher le timestamp au format HH:mm'` — set `message.timestamp=new Date('2026-05-02T14:35:00')`; assert `.message-time` text content contains `'14:35'`
- [X] T032 [US3] Write markdown directive test: `'doit avoir l\'attribut markdown sur l\'élément de contenu'` — assert `spectator.query('[markdown]')` is not null (confirms ngx-markdown directive is wired)
- [X] T033 [US3] Write `parseSourceContent` unit tests (call method directly, no DOM): `'parseSourceContent doit extraire le nom de fichier d\'un lien markdown'` — test 4 cases: markdown link `[source](doc.pdf)` → `'doc.pdf'`; generic link `[label](file.docx)` → `'file.docx'`; plain `.pdf` filename → as-is; unrecognized string → `''`
- [X] T034 [US3] Write citation integration tests: `'[INTÉGRATION] doit afficher les sources groupées quand le message a des citations'` — set message with 2 citations; assert 2 `.source-item` elements visible; click one and assert `window.alert` was called (spy on `vi.spyOn(window, 'alert')`)

**Checkpoint**: Run `npm test -- --include="**/message-item.component.spec.ts"` — all 10 tests green.

---

## Phase 6: User Story 4 — VoiceButtonComponent (Priority: P3)

**Goal**: Verify microphone support detection, `startRecording()`/`stopRecording()` lifecycle, `transcriptFinal` emission, error handling from `getErrors()`, and the full recording flow integration.

**Independent Test**: Run `npm test -- --include="**/voice-button.component.spec.ts"` → 8 tests pass.

### Implementation — US4

- [X] T035 [P] [US4] Create spec file skeleton `agentic-rag-ui/src/app/features/chat/components/voice-control/voice-button.component.spec.ts` with `createComponentFactory({ providers: [mockProvider(VoiceService)] })`; set up default mock returns in `beforeEach`: `isRecordingSupported → true`, `startRecording → resolves`, `stopRecording → resolves Blob`, `transcribeWithWhisper → of(successResponse)`, `getErrors → EMPTY`
- [X] T036 [US4] Write smoke test: `'doit créer le composant'`
- [X] T037 [US4] Write unsupported browser test: `'doit désactiver le bouton si le microphone n\'est pas supporté'` — `isRecordingSupported.mockReturnValue(false)`; re-create component (re-runs `ngOnInit`); assert `spectator.component.isSupported === false`; assert button is disabled or unsupported indicator is visible
- [X] T038 [US4] Write start recording test: `'doit appeler startRecording() au click et émettre recordingChange=true'` — spy `recordingChange` output; call `spectator.component.startRecording()` (async); await; assert `voiceService.startRecording` called; assert `recordingChange` emitted `true`
- [X] T039 [US4] Write recording state class test: `'doit appliquer la classe btn-danger pendant l\'enregistrement'` — after `startRecording()`, assert `spectator.component.getButtonClass() === 'btn-danger'`
- [X] T040 [US4] Write stop + transcribe test: `'doit appeler stopRecording() puis transcribeWithWhisper() à l\'arrêt'` — start then stop recording; assert `voiceService.stopRecording` called; assert `voiceService.transcribeWithWhisper` called with the returned Blob and `'fr'` language
- [X] T041 [US4] Write transcript emit test: `'doit émettre transcriptFinal après une transcription réussie'` — spy `transcriptFinal` output; start then stop; await observable; assert `transcriptFinal` emitted `'Bonjour'`
- [X] T042 [US4] Write error handling test: `'doit émettre error si VoiceService.getErrors() émet une erreur'` — `getErrors.mockReturnValue(of('Microphone non disponible'))`; re-create component; `spectator.detectChanges()`; spy `error` output; assert `error` output emitted `'Microphone non disponible'`; assert `spectator.component.errorMessage` is set
- [X] T043 [US4] Write integration test: `'[INTÉGRATION] le flux complet : démarrage → arrêt → transcript'` — spy both `recordingChange` and `transcriptFinal` outputs; call `toggleRecording()` (start); assert `recordingChange(true)`; call `toggleRecording()` again (stop); await; assert `recordingChange(false)`; assert `transcriptFinal('Bonjour')`

**Checkpoint**: Run `npm test -- --include="**/voice-button.component.spec.ts"` — all 8 tests green.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Coverage verification, suspended requirements documentation, and commit hygiene.

- [X] T044 [P] Run full Phase 7 coverage report: `npm test -- --coverage --include="**/features/chat/components/**"` and verify statements ≥ 80 %, branches ≥ 75 %, functions ≥ 85 % for each of the 4 component files
- [X] T045 Add a `// TODO(phase-8)` comment in `message-item.component.spec.ts` above the XSS placeholder to document that FR-017 XSS sanitization testing is deferred to Phase 8 page-level integration tests (requires real `MarkdownModule.forRoot()`)
- [X] T046 Add a `// TODO(pipe-impl)` comment in `message-item.component.spec.ts` to document that FR-009 (highlight pipe test) is suspended pending implementation of `shared/pipes/highlight.pipe.ts`
- [X] T047 Commit all 4 spec files and test-helpers in a single commit following the convention: `test(phase-7): add chat-interface, message-input, message-item, voice-button specs`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1, T001–T003)**: No dependencies — start immediately
- **Foundational (Phase 2, T004–T006)**: Depends on Setup — BLOCKS all spec authoring
- **US1 (Phase 3, T007–T014)**: Depends on Foundational — can start in parallel with US2 after T006
- **US2 (Phase 4, T015–T024)**: Depends on Foundational — can start in parallel with US1 after T006
- **US3 (Phase 5, T025–T034)**: Depends on Foundational — can start after T006 (no US1/US2 dependency)
- **US4 (Phase 6, T035–T043)**: Depends on Foundational and T001 (test-helpers) — can start after T006
- **Polish (Phase 7, T044–T047)**: Depends on all four component specs being green

### User Story Dependencies

- **US1 (ChatInterface)**: Independent after Foundational — no dependency on US2, US3, US4
- **US2 (MessageInput)**: Independent after Foundational — no dependency on other stories
- **US3 (MessageItem)**: Independent after Foundational — imports `mockMessage` from T001
- **US4 (VoiceButton)**: Independent after Foundational — imports `mockProvider(VoiceService)`

### Within Each User Story

1. Create spec skeleton (createComponentFactory + beforeEach setup)
2. Write smoke test first — confirms imports and DI resolve
3. Write unit tests in any order within the story
4. Write integration tests last (requires unit tests to be green first)
5. Run story-level subset and confirm all pass before moving to next story

### Parallel Opportunities

- T003 can run alongside T001 and T002 (different operations)
- T007 (US1 skeleton) and T015 (US2 skeleton) and T025 (US3 skeleton) and T035 (US4 skeleton) can all be created in parallel after T006
- All `[P]`-marked tasks within a story can be authored simultaneously

---

## Parallel Example: Phase 3 (US1) + Phase 4 (US2)

```bash
# After T006 completes, these two spec files can be authored simultaneously:

# Terminal A — US1
npm test -- --watch --include="**/chat-interface.component.spec.ts"

# Terminal B — US2
npm test -- --watch --include="**/message-input.component.spec.ts"
```

---

## Implementation Strategy

### MVP First (US1 Only — ChatInterfaceComponent)

1. Complete Phase 1 (T001–T003): Setup
2. Complete Phase 2 (T004–T006): Foundational confirmations
3. Complete Phase 3 (T007–T014): ChatInterfaceComponent spec — 7 tests
4. **STOP AND VALIDATE**: `npm test -- --include="**/chat-interface.component.spec.ts"` → all green
5. This validates the full Spectator + provideMockStore setup pattern used by all remaining stories

### Incremental Delivery

1. Phase 1 + 2 → environment confirmed
2. Phase 3 (US1) → ChatInterface spec green (7 tests) ← **MVP**
3. Phase 4 (US2) → MessageInput spec green (+9 tests = 16 total)
4. Phase 5 (US3) → MessageItem spec green (+10 tests = 26 total)
5. Phase 6 (US4) → VoiceButton spec green (+8 tests = 34 total)
6. Phase 7 → coverage gates verified, deferred items documented

### Parallel Team Strategy

With two developers after Foundational is complete:
- **Developer A**: US1 (ChatInterface) → US3 (MessageItem)
- **Developer B**: US2 (MessageInput) → US4 (VoiceButton)

---

## Notes

- `[P]` tasks = different files, no hard dependency on each other
- `[USn]` label maps each task to its user story for traceability
- **Vitest**: use `vi.fn()`, `vi.spyOn()`, `vi.useFakeTimers()` — not Jasmine `jasmine.createSpy()` or `fakeAsync`
- **Constitution VIII**: all `it()` labels in French imperative: `'doit [action] quand [condition]'`
- **Constitution VIII**: integration tests prefixed `'[INTÉGRATION]'`
- **XSS (FR-017)**: deferred to Phase 8 — document with `// TODO(phase-8)` comment (T045)
- **Highlight pipe (FR-009)**: suspended — pipe is empty and not referenced in templates (T046)
- **Scroll assertion**: `AfterViewChecked` scroll is synchronous — call `spectator.detectChanges()` then read `nativeElement.scrollTop`
- **VoiceButton timers**: use `vi.useFakeTimers()` + `vi.advanceTimersByTime(1000)` for `recordingDuration` tests; restore with `vi.useRealTimers()` in `afterEach`
