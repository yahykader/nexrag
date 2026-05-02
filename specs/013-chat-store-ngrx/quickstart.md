# Quickstart: Running Phase 6 Tests

**Feature**: 013-chat-store-ngrx  
**Date**: 2026-04-30

---

## Prerequisites

```bash
cd agentic-rag-ui
npm install
```

---

## Run Phase 6 only

```bash
# From agentic-rag-ui/
npm test -- --include="**/features/chat/store/**"
```

## Run with coverage

```bash
npm test -- --coverage --include="**/features/chat/store/**"
```

## Run a single spec file

```bash
npm test -- --include="**/chat.reducer.spec.ts"
npm test -- --include="**/chat.selectors.spec.ts"
npm test -- --include="**/chat.effects.spec.ts"
npm test -- --include="**/chat.actions.spec.ts"
```

## Watch mode (during development)

```bash
npm test -- --watch --include="**/features/chat/store/**"
```

---

## Spec file locations (co-located, per constitution Principle VIII)

```
agentic-rag-ui/src/app/features/chat/store/
├── chat.actions.ts
├── chat.actions.spec.ts        ← create
├── chat.effects.ts
├── chat.effects.spec.ts        ← create
├── chat.reducer.ts
├── chat.reducer.spec.ts        ← create
├── chat.selectors.ts
├── chat.selectors.spec.ts      ← create
└── chat.state.ts               ← no spec needed (pure type definitions)
```

---

## Required imports per spec type

### Reducer specs
```typescript
import { chatReducer } from './chat.reducer';
import { initialChatState, conversationsAdapter } from './chat.state';
import * as ChatActions from './chat.actions';
```

### Selector specs
```typescript
import * as selectors from './chat.selectors';
import { conversationsAdapter, ChatState } from './chat.state';
```

### Effects specs
```typescript
import { createServiceFactory, SpectatorService } from '@ngneat/spectator';
import { provideMockActions } from '@ngrx/effects/testing';
import { provideMockStore, MockStore } from '@ngrx/store/testing';
import { ReplaySubject } from 'rxjs';
import { ChatEffects } from './chat.effects';
import * as ChatActions from './chat.actions';
```

### Action specs
```typescript
import * as ChatActions from './chat.actions';
```

---

## Coverage gate (per constitution Principle IX)

| Metric | Minimum |
|--------|---------|
| Statements | ≥ 80% |
| Branches | ≥ 75% |
| Functions | ≥ 85% |
| Lines | ≥ 80% |

CI will fail the build if any threshold is not met.

---

## Commit message format (per constitution)

```
test(phase-6): add chat.reducer.spec — SendMessage / StreamComplete scenarios
test(phase-6): add chat.selectors.spec — selectActiveMessages / selectIsStreaming
test(phase-6): add chat.effects.spec — startStreaming$ happy path and error
test(phase-6): add chat.actions.spec — action type strings and payload shapes
```
