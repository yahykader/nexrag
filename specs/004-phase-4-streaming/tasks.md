# Tasks: Phase 4 — Streaming (Tests Unitaires)

**Input**: Design documents from `/specs/004-phase-4-streaming/`
**Prerequisites**: plan.md ✅ spec.md ✅ research.md ✅ data-model.md ✅ contracts/ ✅ quickstart.md ✅

**Tests**: Cette phase IS la phase de tests — toutes les tâches produisent des `*Spec.java`.

**Organization**: Tâches groupées par User Story pour permettre une implémentation et
validation indépendante de chaque story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Exécutable en parallèle avec d'autres tâches `[P]` de la même phase (fichiers distincts)
- **[Story]**: User Story associée (US1, US2, US3)
- Chaque tâche inclut le chemin de fichier exact

---

## Phase 1: Setup (Infrastructure de test)

**Purpose**: Créer la structure de répertoires et vérifier les dépendances de test requises.

- [X] T001 Créer le répertoire de tests `nex-rag/src/test/java/com/exemple/nexrag/service/rag/streaming/openai/` (package manquant pour `OpenAiStreamingClientSpec`)
- [X] T002 Vérifier que `nex-rag/pom.xml` contient la dépendance WireMock `wiremock-jre8` en scope `test` — l'ajouter si absente

---

## Phase 2: Foundational (Prérequis bloquants)

**Purpose**: Comprendre les contrats des classes existantes avant d'écrire les specs.
Chaque Spec dépend d'une lecture précise des sources de production.

**⚠️ CRITIQUE**: Les phases US ne peuvent démarrer qu'après compréhension des contrats.

- [X] T003 Lire `nex-rag/src/main/java/com/exemple/nexrag/service/rag/streaming/ConversationManager.java` et noter : signature de constructeur, clé Redis `conversation:`, `MAX_MESSAGES=100`, absence de userId dans `getConversation()` (R-06#2)
- [X] T004 [P] Lire `nex-rag/src/main/java/com/exemple/nexrag/service/rag/streaming/EventEmitter.java` et noter : `TOKEN_BUFFER_SIZE=5`, `ConcurrentHashMap<String,SseEmitter>`, méthodes publiques, comportement après `complete()`
- [X] T005 [P] Lire `nex-rag/src/main/java/com/exemple/nexrag/service/rag/streaming/StreamingOrchestrator.java` et noter : ordre d'appels pipeline, `CountDownLatch(1).await(60, SECONDS)` (R-06#1), signature du constructeur avec 5 dépendances
- [X] T006 [P] Lire `nex-rag/src/main/java/com/exemple/nexrag/service/rag/streaming/openai/OpenAiStreamingClient.java` et noter : constructeur `(StreamingConfig, ObjectMapper)`, format SSE consommé, usage de `WebClient`

**Checkpoint**: Contrats des 4 classes compris — implémentation des Spec peut démarrer

---

## Phase 3: User Story 1 - Gestion de l'historique de conversation (Priority: P1) 🎯 MVP

**Goal**: `ConversationManagerSpec.java` couvre intégralement la persistance Redis, le
fenêtrage de l'historique, et les cas limites de la gestion de conversations.

**Independent Test**: `./mvnw test -Dtest=ConversationManagerSpec` — aucune dépendance sur
les autres Spec classes.

### Tests pour User Story 1

- [X] T007 [US1] Créer le squelette de `nex-rag/src/test/java/com/exemple/nexrag/service/rag/streaming/ConversationManagerSpec.java` : `@DisplayName` français, `@ExtendWith(MockitoExtension.class)`, `@Mock RedisTemplate`, `@Mock ValueOperations`, `@Mock ObjectMapper`, `@InjectMocks ConversationManager`, `@BeforeEach` stub `redisTemplate.opsForValue() → valueOps`
- [X] T008 [US1] Écrire `DOIT créer une conversation avec userId et ID unique (format conv_XXXXXXXXXXXXXXXX)` dans `ConversationManagerSpec.java` — vérifie FR-001 / AC-11.1 : `assertThat(state.getConversationId()).startsWith("conv_")`, `assertThat(state.getUserId()).isEqualTo(userId)`, `assertThat(state.getMessages()).isEmpty()`
- [X] T009 [US1] Écrire `DOIT retourner Optional.empty() pour une conversation inexistante` dans `ConversationManagerSpec.java` — vérifie FR-004 / edge case : `when(valueOps.get(key)).thenReturn(null)` → `assertThat(result).isEmpty()`
- [X] T010 [US1] Écrire `DOIT retourner Optional.empty() quand la désérialisation JSON échoue` dans `ConversationManagerSpec.java` — vérifie résilience : `when(objectMapper.readValue(...)).thenThrow(JsonProcessingException.class)` → `assertThat(result).isEmpty()`
- [X] T011 [US1] Écrire `DOIT ajouter un message utilisateur et persister dans Redis` dans `ConversationManagerSpec.java` — vérifie FR-002 : message avec `role="user"`, contenu correct, `verify(valueOps).set(key, json, ttl, SECONDS)` appelé
- [X] T012 [US1] Écrire `DOIT ajouter un message assistant avec sources et mettre à jour le contexte` dans `ConversationManagerSpec.java` — vérifie FR-002 : message avec `role="assistant"`, sources non-null, contexte mis à jour dans `ConversationState`
- [X] T013 [US1] Écrire `DOIT tronquer le message le plus ancien quand messages dépasse MAX_MESSAGES (100)` dans `ConversationManagerSpec.java` — vérifie FR-003 / SC-007 : remplir avec 100 messages, ajouter le 101ème → `assertThat(state.getMessages()).hasSize(100)`, premier message de la liste = le 2ème inséré
- [X] T014 [US1] Écrire `DOIT lever IllegalArgumentException quand conversationId introuvable à addUserMessage` dans `ConversationManagerSpec.java` — vérifie edge case : `when(valueOps.get(key)).thenReturn(null)` → `assertThatThrownBy(...).isInstanceOf(IllegalArgumentException.class)`
- [X] T015 [US1] Écrire `DOIT supprimer la conversation de Redis via deleteConversation` dans `ConversationManagerSpec.java` — vérifie FR-004 : `verify(redisTemplate).delete("conversation:" + id)` appelé exactement une fois
- [X] T016 [US1] Écrire `DOIT renouveler le TTL sans modifier le contenu via refreshTTL` dans `ConversationManagerSpec.java` — vérifie FR-005 : `verify(redisTemplate).expire("conversation:" + id, 3600, SECONDS)`
- [X] T017 [US1] Écrire `DOIT retourner les N derniers messages avec getMessageHistory` dans `ConversationManagerSpec.java` — vérifie FR-002 : conversation avec 10 messages, `getMessageHistory(id, 3)` → liste de 3 messages (les plus récents)
- [X] T018 [US1] Écrire `DOIT enrichir la query avec les 3 derniers messages de l'historique` dans `ConversationManagerSpec.java` — vérifie FR-002 : résultat contient "Contexte de la conversation" + contenu des 3 derniers messages
- [X] T019 [US1] Écrire `DOIT retourner la query inchangée quand la conversation est inexistante (enrichQueryWithContext)` dans `ConversationManagerSpec.java` — edge case : `when(valueOps.get(key)).thenReturn(null)` → résultat == query originale
- [X] T020 [US1] Écrire `[GAP R-06#2] DOIT_ECHOUER — getConversation() ne vérifie pas le userId propriétaire` dans `ConversationManagerSpec.java` — test documentant le gap de sécurité : userId "userB" peut accéder à conversation de "userA" ; annoter `@Disabled("Gap identifié : FR-004 requiert userId scoping — voir R-06#2")`

**Checkpoint**: `./mvnw test -Dtest=ConversationManagerSpec` — tous les tests passent (T020 @Disabled) ; couverture `ConversationManager` ≥80%

---

## Phase 4: User Story 2 - Streaming SSE token par token (Priority: P2)

**Goal**: `EventEmitterSpec.java` et `OpenAiStreamingClientSpec.java` couvrent l'émission
d'événements SSE, le buffering de tokens, et la consommation du flux OpenAI via WireMock.

**Independent Test**: `./mvnw test -Dtest="EventEmitterSpec,OpenAiStreamingClientSpec"` —
les deux classes sont indépendantes entre elles.

### Tests pour User Story 2 — EventEmitter (fichier A)

- [X] T021 [P] [US2] Créer le squelette de `nex-rag/src/test/java/com/exemple/nexrag/service/rag/streaming/EventEmitterSpec.java` : `@DisplayName` français, `@ExtendWith(MockitoExtension.class)`, `EventEmitter emitter`, `@BeforeEach` instanciation + `registerSSE("session-test", spy(new SseEmitter(Long.MAX_VALUE)))`, `@AfterEach` appel `emitter.complete("session-test")`
- [X] T022 [P] [US2] Écrire `DOIT enregistrer un SseEmitter et configurer les callbacks de nettoyage` dans `EventEmitterSpec.java` — vérifie `registerSSE()` : emitter enregistré, callbacks `onCompletion`/`onTimeout`/`onError` configurés
- [X] T023 [P] [US2] Écrire `DOIT émettre un événement TOKEN via le SseEmitter enregistré` dans `EventEmitterSpec.java` — vérifie AC-12.1 / FR-007 : `emitToken(session, "Bonjour", 0)` + flush forcé → `verify(sseEmitter).send(argThat(event → event contient type=TOKEN))`
- [X] T024 [P] [US2] Écrire `DOIT émettre les tokens dans l'ordre de réception` dans `EventEmitterSpec.java` — vérifie SC-004 : capture de tous les événements TOKEN émis via spy, `assertThat(capturedTexts).containsExactly("A","B","C")` dans l'ordre d'appel
- [X] T025 [P] [US2] Écrire `DOIT flusher le buffer dès que TOKEN_BUFFER_SIZE (5) est atteint` dans `EventEmitterSpec.java` — vérifie buffering : 5 appels `emitToken()` → `verify(sseEmitter, atLeastOnce()).send(...)` appelé sans attendre le scheduler
- [X] T026 [P] [US2] Écrire `DOIT émettre un événement ERROR avec message et code` dans `EventEmitterSpec.java` — vérifie AC-12.3 / FR-008 : `emitError(session, "API error", "GENERATION_ERROR")` → événement de type ERROR avec `data.message` et `data.code` corrects
- [X] T027 [P] [US2] Écrire `DOIT émettre un événement COMPLETE avec les données de réponse` dans `EventEmitterSpec.java` — vérifie FR-008 : `emitComplete(session, responseData)` → événement de type COMPLETE émis
- [X] T028 [P] [US2] Écrire `DOIT supprimer l'emitter de la map et fermer le flux après complete()` dans `EventEmitterSpec.java` — vérifie invariant DONE terminal (Q2) : après `complete(session)`, `verify(sseEmitter).complete()` appelé ; tentative d'émission suivante = no-op (warn log)
- [X] T029 [P] [US2] Écrire `DOIT être impossible d'émettre après complete() — DONE est terminal absolu` dans `EventEmitterSpec.java` — vérifie SC-005 / Q2 : après `complete()`, appel `emit()` → `verify(sseEmitter, never()).send(...)` (session retirée de la map)
- [X] T030 [P] [US2] Écrire `DOIT ignorer silencieusement l'émission pour une session inconnue` dans `EventEmitterSpec.java` — edge case : `emit("session-inconnue", event)` → aucune exception, `verify(sseEmitter, never()).send(...)`
- [X] T031 [P] [US2] Écrire `DOIT nettoyer les ressources quand le SseEmitter signale une erreur IOException` dans `EventEmitterSpec.java` — edge case : `when(sseEmitter.send(...)).thenThrow(IOException.class)` → session retirée de la map après l'émission suivante

### Tests pour User Story 2 — OpenAiStreamingClient (fichier B, parallélisable avec A)

- [X] T032 [P] [US2] Créer le squelette de `nex-rag/src/test/java/com/exemple/nexrag/service/rag/streaming/openai/OpenAiStreamingClientSpec.java` : `WireMockServer wireMock` (port dynamique), `@BeforeEach` démarrage WireMock + création `OpenAiStreamingClient(mockConfig, objectMapper)` où `mockConfig.getOpenAi().getApiUrl()` = URL WireMock, `@AfterEach` `wireMock.stop()`
- [X] T033 [P] [US2] Écrire `DOIT émettre des tokens puis GENERATION_COMPLETE depuis le flux OpenAI SSE` dans `OpenAiStreamingClientSpec.java` — vérifie AC-12.1/12.2/SC-004/SC-005 : WireMock répond `text/event-stream` avec 2 tokens + `[DONE]` → vérifier liste d'événements dans l'ordre TOKEN, TOKEN, GENERATION_COMPLETE (jamais l'inverse)
- [X] T034 [P] [US2] Écrire `DOIT émettre ERROR et ne pas propager l'exception sur réponse HTTP 429` dans `OpenAiStreamingClientSpec.java` — vérifie AC-12.3 / SC-006 : WireMock répond 429 → événement ERROR émis, aucune exception non gérée propagée
- [X] T035 [P] [US2] Écrire `DOIT émettre ERROR sur réponse HTTP 500` dans `OpenAiStreamingClientSpec.java` — vérifie AC-12.3 : WireMock répond 500 → événement ERROR émis
- [X] T036 [P] [US2] Écrire `DOIT ignorer les lignes SSE avec delta.content vide ou null` dans `OpenAiStreamingClientSpec.java` — edge case : `data: {"choices":[{"delta":{}}]}\n\n` → aucun TOKEN émis pour ce chunk
- [X] T037 [P] [US2] Écrire `DOIT envoyer le header Authorization Bearer dans la requête OpenAI` dans `OpenAiStreamingClientSpec.java` — contrat : `wireMock.verify(postRequestedFor(urlEqualTo("/v1/chat/completions")).withHeader("Authorization", equalTo("Bearer fake-key")))`

**Checkpoint**: `./mvnw test -Dtest="EventEmitterSpec,OpenAiStreamingClientSpec"` — tous les tests passent ; couverture `EventEmitter` ≥80%, `OpenAiStreamingClient` ≥80%

---

## Phase 5: User Story 3 - Réponse enrichie par le contexte RAG (Priority: P3)

**Goal**: `StreamingOrchestratorSpec.java` couvre la coordination complète du pipeline
RAG → historique → génération → sauvegarde de la réponse.

**Independent Test**: `./mvnw test -Dtest=StreamingOrchestratorSpec` — dépendances mockées.

### Tests pour User Story 3

- [X] T038 [US3] Créer le squelette de `nex-rag/src/test/java/com/exemple/nexrag/service/rag/streaming/StreamingOrchestratorSpec.java` : `@DisplayName` français, `@ExtendWith(MockitoExtension.class)`, `@Mock` des 5 dépendances (`RetrievalAugmentorOrchestrator`, `ConversationManager`, `EventEmitter`, `StreamingChatLanguageModel`, `RAGMetrics`), `@InjectMocks StreamingOrchestrator`, `ArgumentCaptor<StreamingResponseHandler<AiMessage>>` déclaré en champ de classe
- [X] T039 [US3] Écrire méthode utilitaire privée `triggerStreamingWith(List<String> tokens)` dans `StreamingOrchestratorSpec.java` — capture le `StreamingResponseHandler` via `ArgumentCaptor`, appelle `onNext(token)` pour chaque token puis `onComplete(mockResponse)` — réutilisée dans les tests suivants
- [X] T040 [US3] Écrire `DOIT ajouter le message utilisateur à l'historique AVANT de démarrer la génération` dans `StreamingOrchestratorSpec.java` — vérifie FR-010 / US-3 AC-1 : utiliser `InOrder inOrder = inOrder(conversationManager, streamingModel)` → `inOrder.verify(conversationManager).addUserMessage(...)` avant `inOrder.verify(streamingModel).generate(...)`
- [X] T041 [US3] Écrire `DOIT injecter le contexte RAG dans le prompt avant d'appeler le service de génération` dans `StreamingOrchestratorSpec.java` — vérifie FR-009 / AC-12.4 : `verify(retrievalAugmentor).execute(enrichedQuery)` appelé avant `verify(streamingModel).generate(...)` ; capturer `UserMessage` passé au modèle et vérifier qu'il contient le contenu du contexte RAG mocké
- [X] T042 [US3] Écrire `DOIT coordonner le pipeline dans l'ordre : RAG → historique → génération → sauvegarde` dans `StreamingOrchestratorSpec.java` — vérifie FR-010 / US-3 AC-2 : `InOrder` sur les 5 étapes, déclencher streaming via `triggerStreamingWith(List.of("Bonjour"," monde"))`, vérifier `conversationManager.addAssistantMessage(...)` appelé APRÈS `onComplete()`
- [X] T043 [US3] Écrire `DOIT sauvegarder la réponse complète dans l'historique à réception de DONE` dans `StreamingOrchestratorSpec.java` — vérifie US-3 AC-2 / SC-005 : déclencher streaming avec tokens `["Bon","jour"," monde"]`, capturer le contenu passé à `addAssistantMessage()` → `assertThat(content).isEqualTo("Bonjour monde")`
- [X] T044 [US3] Écrire `DOIT continuer normalement quand le contexte RAG est vide (aucun document pertinent)` dans `StreamingOrchestratorSpec.java` — vérifie US-3 AC-3 : `retrievalAugmentor.execute()` retourne résultat avec contexte vide → streaming se déroule normalement, aucune exception
- [X] T045 [US3] Écrire `DOIT émettre ERROR sans propager l'exception quand le modèle de génération échoue` dans `StreamingOrchestratorSpec.java` — vérifie SC-006 : déclencher `onError(new RuntimeException("API down"))` via handler capturé → `verify(eventEmitter).emitError(sessionId, contains("API down"), "GENERATION_ERROR")` ; `assertThatNoException().isThrownBy(...)` sur le CompletableFuture
- [X] T046 [US3] Écrire `DOIT créer une nouvelle conversation quand conversationId est null dans la requête` dans `StreamingOrchestratorSpec.java` — vérifie US-3 intégration : `request.getConversationId() == null` → `verify(conversationManager).createConversation(userId)` appelé
- [X] T047 [US3] Écrire `[GAP R-06#1] DOIT_DOCUMENTER — timeout effectif est 60s, spec cible 30s` dans `StreamingOrchestratorSpec.java` — test documentant la divergence : annoter `@Disabled("Divergence documentée R-06#1 : timeout code=60s, spec=30s — mettre à jour StreamingOrchestrator.streamAiResponse()")` avec assertion commentée sur la valeur cible

**Checkpoint**: `./mvnw test -Dtest=StreamingOrchestratorSpec` — tous les tests passent (T047 @Disabled) ; couverture `StreamingOrchestrator` ≥80%

---

## Phase 6: Polish & Quality Gates

**Purpose**: Validation finale de la couverture, cohérence des conventions, et commits.

- [X] T048 Exécuter la suite complète Phase 4 : `./mvnw test -Dtest="ConversationManagerSpec,EventEmitterSpec,StreamingOrchestratorSpec,OpenAiStreamingClientSpec"` — vérifier 0 échec (hors tests `@Disabled`)
- [X] T049 [P] Générer le rapport JaCoCo : `./mvnw test jacoco:report` et ouvrir `nex-rag/target/site/jacoco/index.html` — vérifier couverture ligne+branche ≥80% pour chacune des 4 classes ; documenter les résultats
- [X] T050 [P] Vérifier la couverture des AC du test plan : tracer chaque `AC-11.x` et `AC-12.x` de `nexrag-test-plan-speckit.md` (Phase 4) vers la méthode de test correspondante — confirmer 100% des AC couverts (SC-003)
- [X] T051 Corriger les éventuels gaps de couverture identifiés en T049 dans les fichiers `*Spec.java` concernés
- [X] T052 Vérifier que chaque `@Test` a un `@DisplayName` français au format `"DOIT [action] quand [condition]"` dans tous les 4 fichiers `*Spec.java`
- [ ] T053 Committer `ConversationManagerSpec.java` : `test(phase-4): add ConversationManagerSpec — création, historique fenêtré, TTL Redis`
- [ ] T054 [P] Committer `EventEmitterSpec.java` : `test(phase-4): add EventEmitterSpec — émission SSE, buffering tokens, terminality DONE`
- [ ] T055 [P] Committer `OpenAiStreamingClientSpec.java` : `test(phase-4): add OpenAiStreamingClientSpec — streaming SSE WireMock, erreurs HTTP`
- [ ] T056 Committer `StreamingOrchestratorSpec.java` : `test(phase-4): add StreamingOrchestratorSpec — pipeline RAG→historique→génération`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Aucune dépendance — peut démarrer immédiatement
- **Foundational (Phase 2)**: Dépend de Phase 1 — bloque toutes les US
- **US-1 (Phase 3)**: Dépend de Phase 2 (T003 lu) — indépendant de US-2 et US-3
- **US-2 (Phase 4)**: Dépend de Phase 2 (T004, T006 lus) — indépendant de US-1 et US-3
- **US-3 (Phase 5)**: Dépend de Phase 2 (T005 lu) — peut démarrer en parallèle avec US-1 et US-2
- **Polish (Phase 6)**: Dépend des phases 3, 4, 5 complètes

### User Story Dependencies

- **US-1 (ConversationManager)**: Peut démarrer après Phase 2 — aucune dépendance inter-story
- **US-2 (EventEmitter + OpenAiStreamingClient)**: Peut démarrer après Phase 2 — aucune dépendance inter-story
- **US-3 (StreamingOrchestrator)**: Peut démarrer après Phase 2 — mockE les composants US-1 et US-2

### Within Each User Story

- Squelette de classe → méthodes de test dans l'ordre des AC
- Tests `@Disabled` (gap tests) écrits en dernier dans leur phase
- Chaque `@Test` doit être RED avant implémentation (constitution : test-first pour les nouvelles classes)

### Parallel Opportunities

- **Phase 2** : T004, T005, T006 peuvent être lus simultanément (3 fichiers distincts)
- **Phase 4** : Toutes les tâches T021–T031 (EventEmitter) peuvent être réalisées simultanément avec T032–T037 (OpenAiStreamingClient) — fichiers différents
- **Phase 6** : T049, T050 en parallèle après T048 ; T053 avant T054/T055 (ordre de commit recommandé)

---

## Parallel Example — Phase 4

```bash
# Deux développeurs ou deux agents en parallèle :

# Agent A : EventEmitter
Task T021: Créer EventEmitterSpec.java skeleton
Task T022: Test registerSSE
Task T023: Test emitToken
...jusqu'à T031

# Agent B : OpenAiStreamingClient (en même temps)
Task T032: Créer OpenAiStreamingClientSpec.java skeleton
Task T033: Test tokens + DONE
Task T034: Test erreur 429
...jusqu'à T037
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Compléter Phase 1 + Phase 2
2. Compléter Phase 3 (US-1 — `ConversationManagerSpec`)
3. **STOP & VALIDATE** : `./mvnw test -Dtest=ConversationManagerSpec` + couverture JaCoCo
4. Si couverture ≥80% → MVP de Phase 4 atteint, passer à US-2

### Incremental Delivery

1. Phase 1+2 → Infrastructure prête
2. Phase 3 → `ConversationManagerSpec` validé indépendamment
3. Phase 4 (en parallèle si possible) → `EventEmitterSpec` + `OpenAiStreamingClientSpec` validés
4. Phase 5 → `StreamingOrchestratorSpec` validé
5. Phase 6 → Couverture ≥80% confirmée sur les 4 classes, commits formatés

### Parallel Team Strategy

Avec plusieurs développeurs :

1. Tous ensemble : Phase 1 + Phase 2
2. En parallèle après Phase 2 :
   - Développeur A : Phase 3 (ConversationManagerSpec)
   - Développeur B : Phase 4 — EventEmitterSpec
   - Développeur C : Phase 4 — OpenAiStreamingClientSpec
   - Développeur D : Phase 5 (StreamingOrchestratorSpec)
3. Tous ensemble : Phase 6 (polish + commits)

---

## Notes

- `[P]` = fichiers différents, pas de dépendances incomplétes entre eux
- `[Story]` = traçabilité directe vers user story et AC de la spec
- Les tests `@Disabled` (T020, T047) documentent des gaps — NE PAS les supprimer
- Convention de commit obligatoire : `test(phase-4): add <ClassName>Spec — <description>`
- Arrêter à chaque checkpoint pour valider l'histoire indépendamment avant de continuer
- Rapport JaCoCo disponible dans `nex-rag/target/site/jacoco/` après `./mvnw test jacoco:report`
