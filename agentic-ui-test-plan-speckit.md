# 🧪 Plan de Tests — agentic-rag-ui
## Framework : **Spectator** (`@ngneat/spectator`) + Jasmine/Karma

---

## 📦 Installation & Configuration

```bash
npm install --save-dev @ngneat/spectator
npm install --save-dev @ngneat/spectator/jest   # si vous utilisez Jest
```

### `karma.conf.js` — aucun changement requis
### `tsconfig.spec.json` — vérifier que `spec.ts` est inclus

```ts
// Exemple d'import dans chaque spec
import { createComponentFactory, Spectator } from '@ngneat/spectator';
import { createServiceFactory, SpectatorService } from '@ngneat/spectator';
import { createHttpFactory, SpectatorHttp, HttpMethod } from '@ngneat/spectator';
```

---

## 🗂️ Structure des phases

| Phase | Périmètre | Type |
|---|---|---|
| **Phase 1** | `core/models` | Tests unitaires purs |
| **Phase 2** | `core/interceptors` | Tests unitaires HTTP |
| **Phase 3** | `core/services` | Tests unitaires + HTTP |
| **Phase 4** | `shared/pipes` + `shared/directives` | Tests unitaires |
| **Phase 5** | `shared/components/toast-container` | Tests composants |
| **Phase 6** | `features/chat/store` | Tests NgRx (actions/reducer/selectors/effects) |
| **Phase 7** | `features/chat/components` | Tests composants + intégration |
| **Phase 8** | `features/chat/pages` + `resolvers` | Tests pages + routing |
| **Phase 9** | `features/ingestion/store` | Tests NgRx |
| **Phase 10** | `features/ingestion/components` | Tests composants + intégration |
| **Phase 11** | `features/ingestion/pages` | Tests pages |
| **Phase 12** | `features/management` | Tests composants + store + pages |
| **Phase 13** | `pages/workspace` | Tests intégration page principale |
| **Phase 14** | `app.component` + `app.routes` | Tests intégration application |

---

## ✅ PHASE 1 — `src/app/core/models`

> **Type** : Tests unitaires purs (pas de DOM, pas d'Angular)  
> **Fichiers** : `crud.model.ts`, `ingestion.model.ts`, `streaming.model.ts`

### Specs à créer

#### `crud.model.spec.ts`
```ts
describe('CrudModel', () => {
  it('doit avoir les propriétés requises (id, createdAt, updatedAt)', () => { ... });
  it('doit typer correctement les champs optionnels', () => { ... });
  it('doit valider la structure d\'une réponse paginée', () => { ... });
});
```

#### `ingestion.model.spec.ts`
```ts
describe('IngestionModel', () => {
  it('doit exposer les statuts d\'ingestion (PENDING, IN_PROGRESS, DONE, ERROR)', () => { ... });
  it('doit typer UploadItem avec filename, size, status', () => { ... });
  it('doit typer BatchIngestion avec items[]', () => { ... });
});
```

#### `streaming.model.spec.ts`
```ts
describe('StreamingModel', () => {
  it('doit typer StreamChunk avec content et isDone', () => { ... });
  it('doit valider le type StreamEvent (delta | done | error)', () => { ... });
});
```

---

## ✅ PHASE 2 — `src/app/core/interceptors`

> **Type** : Tests unitaires HTTP  
> **Fichiers** : `duplicate-interceptor.ts`, `rate-limit.interceptor.ts`  
> **Outil** : `HttpClientTestingModule` + `createHttpFactory`

### Specs à créer

#### `duplicate-interceptor.spec.ts`
```ts
import { createServiceFactory, SpectatorService } from '@ngneat/spectator';
import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

describe('DuplicateInterceptor', () => {
  it('doit annuler une requête identique déjà en cours', () => { ... });
  it('doit laisser passer deux requêtes différentes simultanément', () => { ... });
  it('doit nettoyer le registre après la fin d\'une requête', () => { ... });
});
```

#### `rate-limit.interceptor.spec.ts`
```ts
describe('RateLimitInterceptor', () => {
  it('doit ajouter les headers de rate-limit à chaque requête', () => { ... });
  it('doit intercepter une réponse 429 et dispatcher une action RateLimitExceeded', () => { ... });
  it('doit retenter la requête après le délai de backoff', () => { ... });
  it('doit laisser passer les réponses 200 sans modification', () => { ... });
});
```

---

## ✅ PHASE 3 — `src/app/core/services`

> **Type** : Tests unitaires + HTTP mocking  
> **Outil** : `createServiceFactory` + `createHttpFactory`

### Specs à créer

#### `http-client.service.spec.ts`
```ts
import { createHttpFactory, SpectatorHttp, HttpMethod } from '@ngneat/spectator';

describe('HttpClientService', () => {
  let spectator: SpectatorHttp<HttpClientService>;
  const createHttp = createHttpFactory(HttpClientService);
  beforeEach(() => spectator = createHttp());

  it('doit effectuer un GET et retourner les données', () => {
    spectator.service.get('/api/test').subscribe(data => expect(data).toBeTruthy());
    spectator.expectOne('/api/test', HttpMethod.GET).flush({ ok: true });
  });

  it('doit effectuer un POST avec le body correct', () => { ... });
  it('doit gérer une erreur 500 avec catchError', () => { ... });
  it('doit ajouter le Content-Type application/json', () => { ... });
});
```

#### `crud-api.service.spec.ts`
```ts
describe('CrudApiService', () => {
  it('getAll() doit appeler GET /api/documents', () => { ... });
  it('getById(id) doit appeler GET /api/documents/:id', () => { ... });
  it('create(item) doit appeler POST /api/documents', () => { ... });
  it('update(id, item) doit appeler PUT /api/documents/:id', () => { ... });
  it('delete(id) doit appeler DELETE /api/documents/:id', () => { ... });
  it('deleteAll() doit appeler DELETE /api/documents', () => { ... });
});
```

#### `ingestion-api.service.spec.ts`
```ts
describe('IngestionApiService', () => {
  it('upload(files) doit envoyer un FormData en POST', () => { ... });
  it('getStatus(batchId) doit appeler GET /api/ingestion/:batchId', () => { ... });
  it('doit retourner une erreur observable si le serveur répond 422', () => { ... });
});
```

#### `streaming-api.service.spec.ts`
```ts
describe('StreamingApiService', () => {
  it('sendMessage(query) doit ouvrir un flux SSE', () => { ... });
  it('doit émettre les chunks reçus via un Observable', () => { ... });
  it('doit compléter le stream quand isDone=true est reçu', () => { ... });
  it('doit gérer les erreurs de connexion', () => { ... });
});
```

#### `websocket-api.service.spec.ts`
```ts
describe('WebsocketApiService', () => {
  it('connect() doit créer une connexion WebSocket', () => { ... });
  it('doit émettre les messages reçus via un Subject', () => { ... });
  it('disconnect() doit fermer proprement la connexion', () => { ... });
  it('doit tenter une reconnexion automatique en cas de fermeture inattendue', () => { ... });
});
```

#### `websocket-progress.service.spec.ts`
```ts
describe('WebsocketProgressService', () => {
  it('doit s\'abonner aux events de type "progress"', () => { ... });
  it('doit mettre à jour le pourcentage d\'avancement', () => { ... });
  it('doit dispatcher l\'action ProgressUpdated au store', () => { ... });
});
```

#### `notification.service.spec.ts`
```ts
describe('NotificationService', () => {
  it('success(msg) doit ajouter un toast de type "success"', () => { ... });
  it('error(msg) doit ajouter un toast de type "error"', () => { ... });
  it('doit retirer le toast après le délai configuré', () => { ... });
});
```

#### `voice.service.spec.ts`
```ts
describe('VoiceService', () => {
  it('startListening() doit activer la SpeechRecognition', () => { ... });
  it('stopListening() doit arrêter et retourner le transcript', () => { ... });
  it('doit émettre une erreur si le navigateur ne supporte pas l\'API', () => { ... });
  it('isListening$ doit émettre true pendant l\'écoute', () => { ... });
});
```

---

## ✅ PHASE 4 — `src/app/shared/pipes` + `src/app/shared/directives`

> **Type** : Tests unitaires (pipes isolés)  
> **Outil** : `createPipeFactory`, `SpectatorPipe`

### Specs à créer

#### `highlight.pipe.spec.ts`
```ts
import { createPipeFactory, SpectatorPipe } from '@ngneat/spectator';

describe('HighlightPipe', () => {
  let spectator: SpectatorPipe<HighlightPipe>;
  const createPipe = createPipeFactory(HighlightPipe);

  it('doit retourner le texte inchangé si le terme est vide', () => {
    spectator = createPipe(`{{ 'Hello World' | highlight:'' }}`);
    expect(spectator.element).toHaveText('Hello World');
  });

  it('doit entourer le terme trouvé d\'un <mark>', () => {
    spectator = createPipe(`{{ 'Hello World' | highlight:'World' }}`);
    expect(spectator.element.querySelector('mark')).toBeTruthy();
  });

  it('doit être insensible à la casse', () => { ... });
  it('doit gérer les caractères spéciaux dans le terme', () => { ... });
  it('doit retourner chaîne vide si input est null', () => { ... });
});
```

#### `markdown.pipe.spec.ts`
```ts
describe('MarkdownPipe', () => {
  it('doit convertir **texte** en <strong>texte</strong>', () => { ... });
  it('doit convertir *texte* en <em>texte</em>', () => { ... });
  it('doit convertir les blocs de code ```...``` en <pre><code>', () => { ... });
  it('doit convertir les liens [text](url) en <a href="url">', () => { ... });
  it('doit sanitiser le HTML pour éviter les injections XSS', () => { ... });
  it('doit retourner une chaîne vide si input est null', () => { ... });
});
```

#### `shared/directives/*.spec.ts` (si directives présentes)
```ts
describe('AutofocusDirective', () => {
  it('doit appeler focus() sur l\'élément hôte au OnInit', () => { ... });
});

describe('ClickOutsideDirective', () => {
  it('doit émettre l\'événement quand on clique hors du composant', () => { ... });
  it('ne doit pas émettre si le clic est à l\'intérieur', () => { ... });
});
```

---

## ✅ PHASE 5 — `src/app/shared/components/toast-container`

> **Type** : Tests composant  
> **Outil** : `createComponentFactory`, `Spectator`

#### `toast-container.component.spec.ts`
```ts
import { createComponentFactory, Spectator } from '@ngneat/spectator';

describe('ToastContainerComponent', () => {
  let spectator: Spectator<ToastContainerComponent>;
  const createComponent = createComponentFactory({
    component: ToastContainerComponent,
    providers: [mockProvider(NotificationService)],
  });
  beforeEach(() => spectator = createComponent());

  it('doit créer le composant', () => {
    expect(spectator.component).toBeTruthy();
  });

  it('doit afficher un toast quand NotificationService émet', () => { ... });
  it('doit afficher la classe "success" pour un toast de succès', () => { ... });
  it('doit afficher la classe "error" pour un toast d\'erreur', () => { ... });
  it('doit supprimer le toast après son dismiss', () => { ... });
  it('doit afficher plusieurs toasts en même temps', () => { ... });
});
```

---

## ✅ PHASE 6 — `src/app/features/chat/store`

> **Type** : Tests NgRx  
> **Fichiers** : `chat.actions.ts`, `chat.reducer.ts`, `chat.selectors.ts`, `chat.effects.ts`, `chat.state.ts`

### Specs à créer

#### `chat.reducer.spec.ts`
```ts
describe('ChatReducer', () => {
  it('doit retourner l\'état initial', () => {
    const state = chatReducer(undefined, { type: '@@INIT' });
    expect(state).toEqual(initialChatState);
  });

  it('SendMessage doit ajouter le message utilisateur à la liste', () => { ... });
  it('ReceiveChunk doit mettre à jour le dernier message assistant', () => { ... });
  it('StreamComplete doit passer isStreaming à false', () => { ... });
  it('ClearChat doit réinitialiser messages à []', () => { ... });
  it('LoadHistorySuccess doit charger les messages depuis le serveur', () => { ... });
  it('SetError doit stocker le message d\'erreur', () => { ... });
});
```

#### `chat.selectors.spec.ts`
```ts
describe('ChatSelectors', () => {
  const mockState: AppState = { chat: { messages: [...], isStreaming: false, error: null } };

  it('selectAllMessages doit retourner tous les messages', () => {
    expect(selectAllMessages(mockState)).toHaveLength(2);
  });
  it('selectIsStreaming doit retourner false', () => { ... });
  it('selectLastMessage doit retourner le dernier message', () => { ... });
  it('selectError doit retourner null si pas d\'erreur', () => { ... });
});
```

#### `chat.effects.spec.ts`
```ts
import { createServiceFactory } from '@ngneat/spectator';
import { provideMockActions } from '@ngrx/effects/testing';
import { provideMockStore } from '@ngrx/store/testing';

describe('ChatEffects', () => {
  it('sendMessage$ doit appeler StreamingApiService.sendMessage()', () => { ... });
  it('doit dispatcher ReceiveChunk pour chaque chunk reçu', () => { ... });
  it('doit dispatcher StreamComplete quand isDone=true', () => { ... });
  it('doit dispatcher StreamError si le service échoue', () => { ... });
  it('loadHistory$ doit appeler CrudApiService.getAll()', () => { ... });
});
```

#### `chat.actions.spec.ts`
```ts
describe('ChatActions', () => {
  it('SendMessage doit avoir le bon type et payload', () => {
    const action = ChatActions.sendMessage({ content: 'Bonjour' });
    expect(action.type).toBe('[Chat] Send Message');
    expect(action.content).toBe('Bonjour');
  });
  // ... idem pour chaque action
});
```

---

## ✅ PHASE 7 — `src/app/features/chat/components`

> **Type** : Tests composants + intégration DOM  
> **Outil** : `createComponentFactory`, `mockProvider`, `SpyObject`

### Specs à créer

#### `chat-interface.component.spec.ts`
```ts
describe('ChatInterfaceComponent', () => {
  it('doit créer le composant', () => { ... });
  it('doit afficher la liste des messages depuis le store', () => { ... });
  it('doit afficher un spinner quand isStreaming=true', () => { ... });
  it('doit scroller en bas quand un nouveau message est reçu', () => { ... });
  it('doit afficher le composant MessageInput', () => { ... });
  it('doit afficher le composant VoiceButton', () => { ... });
  // Test d'intégration
  it('[INTÉGRATION] doit dispatcher SendMessage quand l\'utilisateur soumet', () => { ... });
});
```

#### `message-input.component.spec.ts`
```ts
describe('MessageInputComponent', () => {
  it('doit créer le composant', () => { ... });
  it('doit désactiver le bouton Submit si le champ est vide', () => { ... });
  it('doit émettre l\'événement messageSent au click submit', () => { ... });
  it('doit émettre au Enter (et pas au Shift+Enter)', () => { ... });
  it('doit vider le champ après soumission', () => { ... });
  it('doit être disabled pendant le streaming', () => { ... });
});
```

#### `message-item.component.spec.ts`
```ts
describe('MessageItemComponent', () => {
  it('doit créer le composant', () => { ... });
  it('doit afficher le contenu du message', () => { ... });
  it('doit appliquer la classe "user" si role=user', () => { ... });
  it('doit appliquer la classe "assistant" si role=assistant', () => { ... });
  it('doit appliquer le pipe markdown au contenu assistant', () => { ... });
  it('doit appliquer le pipe highlight si un terme est recherché', () => { ... });
  it('doit afficher un timestamp formaté', () => { ... });
});
```

#### `voice-button.component.spec.ts`
```ts
describe('VoiceButtonComponent', () => {
  it('doit créer le composant', () => { ... });
  it('doit appeler VoiceService.startListening() au click', () => { ... });
  it('doit afficher une animation "pulsing" pendant l\'écoute', () => { ... });
  it('doit émettre voiceTranscript quand l\'écoute se termine', () => { ... });
  it('doit afficher un message d\'erreur si navigateur non supporté', () => { ... });
});
```

---

## ✅ PHASE 8 — `src/app/features/chat/pages` + `resolvers`

#### `chat-page.component.spec.ts`
```ts
describe('ChatPageComponent', () => {
  it('doit créer le composant', () => { ... });
  it('doit inclure ChatInterfaceComponent dans son template', () => { ... });
  it('doit s\'abonner aux messages du store au OnInit', () => { ... });
  it('doit dispatcher LoadHistory au OnInit', () => { ... });
  it('doit se désabonner au OnDestroy', () => { ... });
});
```

#### `chat.resolver.spec.ts`
```ts
import { createServiceFactory } from '@ngneat/spectator';

describe('ChatResolver', () => {
  it('doit dispatcher LoadHistory avant l\'activation de la route', () => { ... });
  it('doit retourner true quand l\'historique est chargé', () => { ... });
  it('doit rediriger vers /error si le chargement échoue', () => { ... });
});
```

---

## ✅ PHASE 9 — `src/app/features/ingestion/store`

> **Sous-stores** : `crud`, `ingestion`, `progress`, `rate-limit`

### `crud.reducer.spec.ts`
```ts
describe('CrudReducer', () => {
  it('LoadDocumentsSuccess doit peupler la liste', () => { ... });
  it('DeleteDocumentSuccess doit retirer l\'item par id', () => { ... });
  it('DeleteAllSuccess doit vider la liste', () => { ... });
  it('SetLoading doit passer loading à true', () => { ... });
});
```

### `ingestion.reducer.spec.ts`
```ts
describe('IngestionReducer', () => {
  it('AddUploadItems doit ajouter les fichiers en PENDING', () => { ... });
  it('UpdateItemStatus doit changer le statut d\'un item', () => { ... });
  it('RemoveItem doit retirer un item de la liste', () => { ... });
  it('ResetIngestion doit vider complètement la liste', () => { ... });
});
```

### `progress.reducer.spec.ts`
```ts
describe('ProgressReducer', () => {
  it('UpdateProgress doit mettre à jour le % d\'avancement par batchId', () => { ... });
  it('CompleteProgress doit passer isComplete à true', () => { ... });
  it('ResetProgress doit réinitialiser l\'état', () => { ... });
});
```

### `rate-limit.reducer.spec.ts`
```ts
describe('RateLimitReducer', () => {
  it('RateLimitExceeded doit stocker le retryAfter', () => { ... });
  it('RateLimitReset doit remettre isLimited à false', () => { ... });
  it('doit calculer correctement le temps restant', () => { ... });
});
```

### `crud.effects.spec.ts` / `ingestion.effects.spec.ts` / `progress.effects.spec.ts`
```ts
// Pattern commun pour les effects NgRx
describe('XxxEffects', () => {
  it('doit appeler l\'API et dispatcher le succès', () => { ... });
  it('doit dispatcher l\'action d\'erreur si l\'API échoue', () => { ... });
});
```

---

## ✅ PHASE 10 — `src/app/features/ingestion/components`

### `upload-zone.component.spec.ts`
```ts
describe('UploadZoneComponent', () => {
  it('doit créer le composant', () => { ... });
  it('doit accepter le drop de fichiers (dragover + drop)', () => { ... });
  it('doit rejeter les fichiers avec extension non autorisée', () => { ... });
  it('doit afficher la taille max autorisée', () => { ... });
  it('doit émettre filesSelected avec les fichiers valides', () => { ... });
  it('doit appliquer la classe "drag-over" pendant le dragover', () => { ... });
});
```

### `upload-item.component.spec.ts`
```ts
describe('UploadItemComponent', () => {
  it('doit afficher le nom du fichier', () => { ... });
  it('doit afficher une barre de progression pour IN_PROGRESS', () => { ... });
  it('doit afficher une icône de succès pour DONE', () => { ... });
  it('doit afficher une icône d\'erreur pour ERROR', () => { ... });
  it('doit émettre removeItem au click sur le bouton supprimer', () => { ... });
});
```

### `progress-panel.component.spec.ts`
```ts
describe('ProgressPanelComponent', () => {
  it('doit créer le composant', () => { ... });
  it('doit afficher le % global d\'avancement', () => { ... });
  it('doit se connecter au store progress via selector', () => { ... });
  it('doit se masquer si aucun batch n\'est en cours', () => { ... });
});
```

### `delete-all-button.component.spec.ts`
```ts
describe('DeleteAllButtonComponent', () => {
  it('doit créer le composant', () => { ... });
  it('doit être disabled si la liste est vide', () => { ... });
  it('doit ouvrir le modal de confirmation au click', () => { ... });
});
```

### `delete-all-modal.component.spec.ts`
```ts
describe('DeleteAllModalComponent', () => {
  it('doit afficher le message de confirmation', () => { ... });
  it('doit émettre confirmed au click "Confirmer"', () => { ... });
  it('doit émettre cancelled au click "Annuler"', () => { ... });
  it('doit fermer le modal à la confirmation', () => { ... });
});
```

### `delete-batch-modal.component.spec.ts`
```ts
describe('DeleteBatchModalComponent', () => {
  it('doit afficher le nom du batch à supprimer', () => { ... });
  it('doit émettre confirmed avec le batchId', () => { ... });
  it('doit émettre cancelled', () => { ... });
});
```

### `rate-limit-indicator.component.spec.ts`
```ts
describe('RateLimitIndicatorComponent', () => {
  it('doit se masquer quand isLimited=false', () => { ... });
  it('doit afficher le compte à rebours quand isLimited=true', () => { ... });
  it('doit mettre à jour le compteur chaque seconde', fakeAsync(() => { ... }));
});
```

### `rate-limit-toast.component.spec.ts`
```ts
describe('RateLimitToastComponent', () => {
  it('doit afficher le message de rate limit', () => { ... });
  it('doit disparaître après le délai retryAfter', fakeAsync(() => { ... }));
});
```

---

## ✅ PHASE 11 — `src/app/features/ingestion/pages`

### `upload-page.component.spec.ts`
```ts
describe('UploadPageComponent', () => {
  it('doit créer le composant', () => { ... });
  it('doit afficher UploadZoneComponent', () => { ... });
  it('doit afficher la liste des UploadItemComponent', () => { ... });
  it('doit afficher ProgressPanelComponent', () => { ... });
  it('doit afficher RateLimitIndicatorComponent', () => { ... });
  it('doit dispatcher AddUploadItems quand filesSelected est émis', () => { ... });
  it('doit dispatcher DeleteAll quand deleteAll est confirmé', () => { ... });
  // Tests intégration
  it('[INTÉGRATION] le flux complet : upload → progress → done', () => { ... });
});
```

---

## ✅ PHASE 12 — `src/app/features/management`

> **Dossiers** : `components/`, `pages/management-page/`, `store/`

### `management.reducer.spec.ts`
```ts
describe('ManagementReducer', () => {
  it('doit retourner l\'état initial', () => { ... });
  it('LoadDocuments doit déclencher le chargement', () => { ... });
  it('LoadDocumentsSuccess doit remplir la liste', () => { ... });
  it('LoadDocumentsFailure doit stocker l\'erreur', () => { ... });
});
```

### `management.selectors.spec.ts`
```ts
describe('ManagementSelectors', () => {
  it('selectDocuments doit retourner la liste', () => { ... });
  it('selectIsLoading doit retourner true pendant le chargement', () => { ... });
  it('selectTotalCount doit retourner le nombre total', () => { ... });
});
```

### Composants Management
```ts
// Pour chaque composant dans management/components/
describe('ManagementXxxComponent', () => {
  it('doit créer le composant', () => { ... });
  it('doit afficher les données du store', () => { ... });
  it('doit dispatcher les actions correctes', () => { ... });
});
```

### `management-page.component.spec.ts`
```ts
describe('ManagementPageComponent', () => {
  it('doit créer le composant', () => { ... });
  it('doit dispatcher LoadDocuments au OnInit', () => { ... });
  it('doit afficher le loader pendant isLoading=true', () => { ... });
  it('doit afficher la liste quand les documents sont chargés', () => { ... });
  it('doit afficher un message vide si aucun document', () => { ... });
});
```

---

## ✅ PHASE 13 — `src/app/pages/workspace`

### `workspace.component.spec.ts`
```ts
describe('WorkspaceComponent', () => {
  it('doit créer le composant', () => { ... });
  it('doit afficher le layout principal (sidebar + main)', () => { ... });
  it('doit afficher le ToastContainerComponent', () => { ... });
  it('doit router vers /chat par défaut', () => { ... });
  it('doit router vers /upload quand la navigation upload est activée', () => { ... });
  it('doit router vers /management quand la navigation management est activée', () => { ... });
});
```

---

## ✅ PHASE 14 — `src/app` (Application Root)

### `app.component.spec.ts`
```ts
describe('AppComponent', () => {
  it('doit créer l\'application', () => { ... });
  it('doit contenir un <router-outlet>', () => { ... });
  it('doit initialiser le store au démarrage', () => { ... });
});
```

### `app.routes.spec.ts`
```ts
describe('AppRoutes', () => {
  it('/ doit rediriger vers /workspace', () => { ... });
  it('/workspace/chat doit charger ChatPageComponent', () => { ... });
  it('/workspace/upload doit charger UploadPageComponent', () => { ... });
  it('/workspace/management doit charger ManagementPageComponent', () => { ... });
  it('route inconnue doit rediriger vers 404 ou /', () => { ... });
});
```

### `app.config.spec.ts`
```ts
describe('AppConfig', () => {
  it('doit inclure provideRouter()', () => { ... });
  it('doit inclure provideStore()', () => { ... });
  it('doit inclure provideEffects()', () => { ... });
  it('doit inclure provideHttpClient()', () => { ... });
});
```

---

## 🛠️ Helpers & Utilitaires recommandés

### `test-helpers.ts` (à créer dans `src/app`)
```ts
import { mockProvider } from '@ngneat/spectator';
import { provideMockStore } from '@ngrx/store/testing';

// Helper store mock
export function mockStore(initialState: Partial<AppState>) {
  return provideMockStore({ initialState });
}

// Factory messages de test
export const mockMessage = (overrides = {}) => ({
  id: 'msg-1',
  role: 'user',
  content: 'Bonjour',
  timestamp: new Date(),
  ...overrides,
});

export const mockUploadItem = (overrides = {}) => ({
  id: 'item-1',
  filename: 'doc.pdf',
  size: 1024,
  status: 'PENDING',
  ...overrides,
});
```

---

## 📊 Récapitulatif

| Phase | Fichiers | Tests unitaires | Tests intégration |
|---|---|---|---|
| 1 – Models | 3 | 9 | 0 |
| 2 – Interceptors | 2 | 7 | 0 |
| 3 – Services | 7 | 28 | 0 |
| 4 – Pipes/Directives | 3+ | 12 | 0 |
| 5 – Toast Container | 1 | 5 | 1 |
| 6 – Chat Store | 5 | 18 | 0 |
| 7 – Chat Components | 4 | 22 | 4 |
| 8 – Chat Pages/Resolvers | 2 | 8 | 2 |
| 9 – Ingestion Store | 8 | 24 | 0 |
| 10 – Ingestion Components | 8 | 32 | 0 |
| 11 – Ingestion Pages | 1 | 7 | 3 |
| 12 – Management | 4+ | 14 | 2 |
| 13 – Workspace | 1 | 6 | 3 |
| 14 – App Root | 3 | 12 | 2 |
| **TOTAL** | **~52** | **~204** | **~17** |

---

## 🚀 Commandes de lancement

```bash
# Lancer tous les tests
ng test

# Lancer les tests en mode watch
ng test --watch

# Lancer les tests avec couverture
ng test --code-coverage

# Lancer une seule phase (par pattern)
ng test --include="**/core/services/**"
ng test --include="**/features/chat/**"
ng test --include="**/features/ingestion/**"
```

---

## ✅ Objectifs de couverture cibles

| Métrique | Cible |
|---|---|
| Statements | ≥ 80% |
| Branches | ≥ 75% |
| Functions | ≥ 85% |
| Lines | ≥ 80% |
