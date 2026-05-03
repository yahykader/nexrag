# Component Contract: MessageItemComponent

**Selector**: `app-message-item`  
**File**: `src/app/features/chat/components/message-item/message-item.component.ts`  
**Spec file to create**: `message-item.component.spec.ts` (co-located)

## Public Surface

### Inputs

| Name | Type | Default | Notes |
|------|------|---------|-------|
| `message` | `Message` | required | Full message object |
| `isStreaming` | `boolean` | `false` | Shows streaming cursor `▊` when `status==='streaming'` |

### Outputs
None.

### Public Methods (testable)

| Method | Signature | Notes |
|--------|-----------|-------|
| `parseSourceContent(content)` | `(string): string` | Extracts filename from citation content string |
| `onSourceClick(citation)` | `(GroupedCitation): void` | Calls `alert()` — mock `window.alert` in tests |

### CSS Classes Applied

| Class | Condition |
|-------|-----------|
| `user-message` | `message.role === 'user'` |
| `assistant-message` | `message.role === 'assistant'` |
| `streaming` | `isStreaming === true && message.status === 'streaming'` |

### Timestamp Rendering
`{{ message.timestamp | date:'HH:mm' }}` — absolute 24-hour time via Angular DatePipe.

### Markdown Rendering
`<div markdown [data]="message.content">` — uses `MarkdownModule` (ngx-markdown). No custom pipe.

### XSS Handling
ngx-markdown uses `marked` + Angular's `DomSanitizer`. Sanitization effectiveness is configuration-dependent (tested at integration level; not reliably assertable in shallow unit tests).

## Test Strategy

### Setup
```typescript
const createComponent = createComponentFactory({
  component: MessageItemComponent,
  schemas: [NO_ERRORS_SCHEMA],   // prevents MarkdownModule bootstrapping overhead
  // OR: imports: [MarkdownModule.forRoot()] for deep tests
});
```

For unit tests, use `NO_ERRORS_SCHEMA` to avoid bootstrapping `MarkdownModule`. This stubs out `[markdown]` rendering but still allows asserting the attribute is present. For the XSS test, escalate to a deep integration test in Phase 8 page specs.

### Unit Tests (8)
1. Smoke: component creates successfully
2. User class: `role='user'` → host `div.message-item` has class `user-message`
3. Assistant class: `role='assistant'` → host `div.message-item` has class `assistant-message`
4. No streaming class when idle: `isStreaming=false` → class `streaming` absent
5. Streaming cursor shown: `isStreaming=true`, `message.status='streaming'` → `.streaming-cursor` visible
6. Timestamp format: `timestamp=new Date('2026-05-02T14:35:00')` → `.message-time` text contains `'14:35'`
7. Markdown attribute present: `[markdown]` attribute exists on content div (asserts ngx-markdown directive is wired)
8. `parseSourceContent` unit: call directly — markdown link `[source](doc.pdf)` → returns `'doc.pdf'`; plain `.pdf` filename → returns as-is; empty string → returns `''`

### Integration Tests (2 — prefixed `[INTÉGRATION]`)
9. `[INTÉGRATION]` Citations rendered: message with 2 citations → 2 `.source-item` elements visible
10. `[INTÉGRATION]` Source click handler: click `.source-item` → `window.alert` called (spy on `window.alert`)

## Naming Convention

```typescript
describe('MessageItemComponent', () => {
  it('doit créer le composant', ...)
  it('doit appliquer la classe user-message si role=user', ...)
  it('doit appliquer la classe assistant-message si role=assistant', ...)
  it('doit afficher le curseur de streaming quand isStreaming=true et status=streaming', ...)
  it('doit afficher le timestamp au format HH:mm', ...)
  it('doit avoir l\'attribut [markdown] sur le contenu', ...)
  it('parseSourceContent doit extraire le nom de fichier d\'un lien markdown', ...)
  it('[INTÉGRATION] doit afficher les sources groupées', ...)
})
```
