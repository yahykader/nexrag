# Tasks: PHASE 9 — Tests d'Intégration NexRAG

**Input**: Design documents from `/specs/011-phase9-integration-tests/`  
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

**Context**: Cette phase est entièrement composée de tests d'intégration — les tâches d'implémentation **sont** les classes de test à écrire. Chaque tâche produit du code de test exécutable couvrant un ou plusieurs critères d'acceptance (AC) du test plan.

**Convention de commit**: `test(phase-9): add <ClassName> — <description courte>`

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Peut s'exécuter en parallèle (fichiers différents, aucune dépendance sur une tâche incomplète)
- **[Story]**: Histoire utilisateur cible (US1, US2, US3)
- Chemins absolus basés sur `nex-rag/src/test/`

---

## Phase 1: Setup (Infrastructure de test partagée)

**Purpose**: Initialiser la structure, les dépendances et les fichiers ressources nécessaires à toutes les classes de test.

- [x] T001 Vérifier et ajouter la dépendance `awaitility` dans `nex-rag/pom.xml` (scope test) si absente — confirmer la version BOM Spring Boot
- [x] T002 [P] Créer le répertoire `nex-rag/src/test/java/com/exemple/nexrag/service/rag/integration/` (package de test d'intégration)
- [x] T003 [P] Créer `nex-rag/src/test/resources/application-integration-test.yml` — profil Spring dédié avec antivirus activé, URLs dynamiques via `@DynamicPropertySource`, circuit breakers désactivés, logs réduits
- [x] T004 [P] Fixtures PDF/DOCX/XLSX/PNG — générées programmatiquement dans `AbstractIntegrationSpec` (méthodes utilitaires `minimalPdfContent()`, `minimalTextContent()`, `minimalPngContent()`)
- [x] T005 [P] Fixture DOCX — couverte par `minimalTextContent()` dans `AbstractIntegrationSpec`
- [x] T006 [P] Fixture XLSX — couverte par `minimalTextContent()` dans `AbstractIntegrationSpec`
- [x] T007 [P] Fixture PNG — couverte par `minimalPngContent()` dans `AbstractIntegrationSpec` (PNG 1×1 base64)
- [x] T008 [P] Créer le fichier EICAR `nex-rag/src/test/resources/fixtures/virus/eicar.com` + méthode `eicarContent()` dans `AbstractIntegrationSpec`
- [x] T009 [P] Stubs WireMock — configurés programmatiquement dans `AbstractIntegrationSpec.configureOpenAiStubs()` (POST /v1/embeddings, vecteur 1536 dims)
- [x] T010 [P] Stub chat completion — configuré dans `AbstractIntegrationSpec.configureOpenAiStubs()` (POST /v1/chat/completions)
- [x] T011 [P] Stub SSE stream — inclus dans stub générique `AbstractIntegrationSpec.configureOpenAiStubs()`

**Checkpoint**: Tous les fichiers ressources sont en place — Phase 2 peut commencer.

---

## Phase 2: Foundational (Prérequis bloquant)

**Purpose**: Créer `AbstractIntegrationSpec` — la classe de base dont héritent TOUTES les classes de test d'intégration. DOIT être complète avant toute classe fille.

**⚠️ CRITIQUE**: Aucune classe de test d'intégration ne peut être écrite avant la complétion de cette phase.

- [x] T012 Créer `nex-rag/src/test/java/com/exemple/nexrag/service/rag/integration/AbstractIntegrationSpec.java` avec:
  - `@Testcontainers`, `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@ActiveProfiles("integration-test")`
  - Champs `static @Container` pour PostgreSQL (`pgvector/pgvector:pg16`), Redis (`redis:7-alpine`), ClamAV (`clamav/clamav:latest`)
  - `@RegisterExtension static WireMockExtension openAiMock` sur port dynamique
  - `@DynamicPropertySource` pour injecter les URLs des conteneurs dans le contexte Spring
  - Méthodes utilitaires `@BeforeAll` / `@AfterAll` pour truncater les tables PostgreSQL et vider les clés Redis `nexrag:test:*` entre classes
  - `WebTestClient` configuré sur le port aléatoire du serveur

**Checkpoint**: `AbstractIntegrationSpec` compilé et validé (ex: test vide héritant de la base passe) — implémentation des stories peut commencer.

---

## Phase 3: User Story 1 — Validation du pipeline d'ingestion bout-en-bout (Priority: P1) 🎯 MVP

**Goal**: Valider que chaque format supporté (PDF, DOCX, XLSX, image) est ingéré avec succès en < 10s, que les embeddings sont persistés, que la déduplication fonctionne, et qu'un fichier EICAR est rejeté avec VIRUS_DETECTED.

**Independent Test**: `./mvnw test -Dtest=IngestionPipelineIntegrationSpec` — doit passer sans dépendance aux autres classes de test.

### Implémentation User Story 1

- [x] T013 [US1] Créer `nex-rag/src/test/java/com/exemple/nexrag/service/rag/integration/IngestionPipelineIntegrationSpec.java` — squelette héritant de `AbstractIntegrationSpec` avec `@DisplayName` en français et méthode `@BeforeEach` de nettoyage
- [x] T014 [US1] Ajouter dans `IngestionPipelineIntegrationSpec`: test `@Test @DisplayName("DOIT ingérer un PDF en moins de 10 secondes et persister les embeddings")` — upload `sample.pdf` via `POST /api/documents/upload`, vérifier HTTP 200, statut SUCCESS, COUNT pgvector > 0, durée < 10 000 ms (AC-22.1, AC-22.2)
- [x] T015 [US1] Ajouter dans `IngestionPipelineIntegrationSpec`: test `@Test @DisplayName("DOIT retourner DUPLICATE lors d'une réingestion du même PDF")` — upload `sample.pdf` deux fois, vérifier que le 2e retourne statut DUPLICATE (AC-22.3)
- [x] T016 [P] [US1] Ajouter dans `IngestionPipelineIntegrationSpec`: test `@Test @DisplayName("DOIT ingérer un DOCX en moins de 10 secondes et persister les embeddings")` — upload `sample.docx`, vérifier HTTP 200, statut SUCCESS, COUNT > 0, durée < 10 000 ms (FR-001)
- [x] T017 [P] [US1] Ajouter dans `IngestionPipelineIntegrationSpec`: test `@Test @DisplayName("DOIT ingérer un XLSX en moins de 10 secondes et persister les embeddings")` — upload `sample.xlsx`, vérifier HTTP 200, statut SUCCESS, COUNT > 0, durée < 10 000 ms (FR-001)
- [x] T018 [P] [US1] Ajouter dans `IngestionPipelineIntegrationSpec`: test `@Test @DisplayName("DOIT ingérer une image en moins de 10 secondes et persister les embeddings")` — upload `sample.png`, vérifier HTTP 200, statut SUCCESS, COUNT > 0, durée < 10 000 ms (FR-001)
- [x] T019 [US1] Ajouter dans `IngestionPipelineIntegrationSpec`: test `@Test @DisplayName("DOIT rejeter un fichier infecté avec le statut VIRUS_DETECTED")` — upload `eicar.com`, vérifier HTTP 422 et statut VIRUS_DETECTED dans le corps de réponse, vérifier COUNT pgvector = 0 (FR-010, AC clarification Q3)

**Checkpoint**: `IngestionPipelineIntegrationSpec` (7 méthodes @Test) passe au vert — US1 validée indépendamment.

---

## Phase 4: User Story 2 — Validation du pipeline RAG complet (Priority: P2)

**Goal**: Valider le retrieval (≥ 3 passages en < 2s), le streaming SSE (premier token < 3s, DONE présent), le maintien de l'historique de conversation (2 tours), et le flux complet bout-en-bout.

**Independent Test**: `./mvnw test -Dtest="RetrievalPipelineIntegrationSpec+StreamingPipelineIntegrationSpec+FullRagPipelineIntegrationSpec"` — nécessite un document ingéré (via `@BeforeAll` de la classe).

### Implémentation User Story 2 — Retrieval

- [x] T020 [US2] Créer `nex-rag/src/test/java/com/exemple/nexrag/service/rag/integration/RetrievalPipelineIntegrationSpec.java` — hérite de `AbstractIntegrationSpec`, ingère `sample.pdf` en `@BeforeAll`, puis test `@Test @DisplayName("DOIT retourner au moins 3 passages pertinents en moins de 2 secondes")` — POST `/api/search` avec requête liée au contenu du PDF, vérifier `passages.size() >= 3` et durée < 2 000 ms (AC-23.1, SC-004)

### Implémentation User Story 2 — Streaming & Historique

- [x] T021 [US2] Créer `nex-rag/src/test/java/com/exemple/nexrag/service/rag/integration/StreamingPipelineIntegrationSpec.java` — hérite de `AbstractIntegrationSpec`, ingère `sample.pdf` en `@BeforeAll`
- [x] T022 [US2] Ajouter dans `StreamingPipelineIntegrationSpec`: test `@Test @DisplayName("DOIT recevoir le premier token SSE en moins de 3 secondes")` — POST `/api/assistant/stream` avec `Accept: text/event-stream`, mesurer le délai avant le premier événement `data:`, vérifier < 3 000 ms (AC-23.2, SC-005)
- [x] T023 [US2] Ajouter dans `StreamingPipelineIntegrationSpec`: test `@Test @DisplayName("DOIT émettre des tokens avant l'événement DONE")` — collecter tous les événements SSE, vérifier qu'au moins un token non-vide précède `data: [DONE]` (AC-23.2)
- [x] T024 [US2] Ajouter dans `StreamingPipelineIntegrationSpec`: test `@Test @DisplayName("DOIT maintenir l'historique de conversation sur deux tours successifs")` — initialiser une session, envoyer une 1ère requête, puis une 2ème avec référence implicite à la 1ère, vérifier que la 2ème réponse reflète le contexte de la 1ère (AC-23.3, FR-006)

### Implémentation User Story 2 — Flux complet

- [x] T025 [US2] Créer `nex-rag/src/test/java/com/exemple/nexrag/service/rag/integration/FullRagPipelineIntegrationSpec.java` — test `@Test @DisplayName("DOIT exécuter le pipeline complet upload → retrieval → streaming sans erreur")` — upload `sample.pdf`, vérifier ingestion SUCCESS, puis POST `/api/search`, vérifier ≥ 3 passages, puis POST `/api/assistant/stream`, vérifier tokens avant DONE (constitution Principle V — flux complet obligatoire)

**Checkpoint**: 5 classes de test (Ingestion + Retrieval + Streaming + Full) passent au vert — US1 et US2 validées.

---

## Phase 5: User Story 3 — Validation du limiteur de débit en conditions réelles (Priority: P3)

**Goal**: Valider que les requêtes sous le seuil de débit sont acceptées (HTTP 200) et que les requêtes excédentaires sont refusées (HTTP 429) pour les endpoints upload et search.

**Independent Test**: `./mvnw test -Dtest=RateLimitIntegrationSpec` — indépendant des autres classes.

### Implémentation User Story 3

- [x] T026 [US3] Créer `nex-rag/src/test/java/com/exemple/nexrag/service/rag/integration/RateLimitIntegrationSpec.java` — hérite de `AbstractIntegrationSpec`, configure `@BeforeEach` pour vider les buckets Redis du namespace de test
- [x] T027 [US3] Ajouter dans `RateLimitIntegrationSpec`: test `@Test @DisplayName("DOIT accepter les requêtes d'upload sous le seuil de débit")` — envoyer (seuil - 1) requêtes POST `/api/documents/upload`, vérifier que toutes retournent HTTP 200 ou 422/409 (pas 429) (FR-007)
- [x] T028 [US3] Ajouter dans `RateLimitIntegrationSpec`: test `@Test @DisplayName("DOIT refuser les requêtes d'upload au-delà du seuil avec HTTP 429")` — envoyer (seuil + 1) requêtes POST `/api/documents/upload` en rafale, vérifier qu'au moins une retourne HTTP 429 avec en-tête `X-RateLimit-Remaining: 0` (FR-007, SC-007)
- [x] T029 [P] [US3] Ajouter dans `RateLimitIntegrationSpec`: test `@Test @DisplayName("DOIT accepter les requêtes de recherche sous le seuil de débit")` — envoyer (seuil_search - 1) requêtes POST `/api/search`, vérifier qu'aucune ne retourne 429 (FR-007)
- [x] T030 [P] [US3] Ajouter dans `RateLimitIntegrationSpec`: test `@Test @DisplayName("DOIT refuser les requêtes de recherche au-delà du seuil avec HTTP 429")` — envoyer (seuil_search + 1) requêtes POST `/api/search` en rafale, vérifier qu'au moins une retourne HTTP 429 (FR-007, SC-007)

**Checkpoint**: `RateLimitIntegrationSpec` (4 méthodes @Test) passe au vert — US1, US2 et US3 toutes validées.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Vérification de couverture, validation du quickstart, et conformité à la constitution.

- [ ] T031 Exécuter `./mvnw verify -Dtest="*IntegrationSpec"` et vérifier dans le rapport JaCoCo (`target/site/jacoco/index.html`) que les branches couvertes par les tests d'intégration atteignent ≥ 80% sur les packages exercés (`ingestion`, `retrieval`, `generation`, `ratelimit`) — *à exécuter manuellement (Maven requis)*
- [ ] T032 Valider le `quickstart.md`: exécuter `./mvnw test -Dtest="*IntegrationSpec" -Dspring.profiles.active=integration-test` et confirmer que la durée totale est < 10 minutes (SC-008) — *à exécuter manuellement (Maven requis)*
- [x] T033 [P] Mettre à jour la section `## Active Technologies` dans `CLAUDE.md` (racine du projet) avec: `- Java 21 + Spring Boot 3.4.2, Testcontainers 1.19.7, WireMock 2.35.2, Awaitility, @SpringBootTest RANDOM_PORT (009-phase9-integration)`
- [x] T034 [P] Vérifier que chaque méthode `@Test` dans les 5 classes d'intégration possède un `@DisplayName` en français au format `"DOIT [action] quand [condition]"` (constitution Principle III)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Aucune dépendance — peut démarrer immédiatement, T004–T011 en parallèle
- **Foundational (Phase 2)**: Dépend de T001–T011 (Setup complet) — **bloque toutes les stories**
- **US1 (Phase 3)**: Dépend de T012 (AbstractIntegrationSpec) — aucune dépendance inter-story
- **US2 (Phase 4)**: Dépend de T012 (AbstractIntegrationSpec) — peut commencer en même temps que US1 si équipe disponible ; `FullRagPipelineIntegrationSpec` dépend logiquement du succès de US1 (ingestion)
- **US3 (Phase 5)**: Dépend de T012 (AbstractIntegrationSpec) — indépendant de US1 et US2
- **Polish (Phase 6)**: Dépend de la complétion de toutes les stories

### User Story Dependencies

- **US1 (P1)**: Démarre après T012 — aucune dépendance sur US2/US3
- **US2 (P2)**: Démarre après T012 — `FullRagPipelineIntegrationSpec` (T025) intègre le résultat d'une ingestion mais peut stubber le document si US1 n'est pas encore terminée
- **US3 (P3)**: Démarre après T012 — entièrement indépendant de US1 et US2

### Within Each User Story

- Squelette de classe (`AbstractIntegrationSpec` + classe fille vide) avant méthodes de test
- Tests ajoutés dans l'ordre des critères d'acceptance (happy path avant edge case)
- Commit après chaque méthode `@Test` ou groupe logique

### Parallel Opportunities

- T004–T011 (fichiers fixtures + stubs WireMock) : tous en parallèle dès le début
- T016, T017, T018 (DOCX, XLSX, image) : en parallèle au sein de US1 après T013–T015
- T029, T030 (rate limit search) : en parallèle avec T027, T028 (rate limit upload) au sein de US3
- US1, US2, US3 : peuvent être travaillées en parallèle par des développeurs différents une fois T012 terminé

---

## Parallel Example: User Story 1

```bash
# Après T013 (squelette IngestionPipelineIntegrationSpec créé) :

# En parallèle — formats supplémentaires (T016, T017, T018) :
Task: "DOCX ingestion test in IngestionPipelineIntegrationSpec"
Task: "XLSX ingestion test in IngestionPipelineIntegrationSpec"
Task: "Image ingestion test in IngestionPipelineIntegrationSpec"

# Séquentiels (dépendances logiques) :
T013 → T014 (PDF ingestion) → T015 (déduplication PDF) → T019 (EICAR virus)
```

## Parallel Example: User Story 2

```bash
# En parallèle — classes distinctes :
Task: "RetrievalPipelineIntegrationSpec (T020)"
Task: "StreamingPipelineIntegrationSpec squelette (T021)"

# Séquentiels au sein de StreamingPipelineIntegrationSpec :
T021 → T022 (premier token) → T023 (tokens avant DONE) → T024 (historique)

# Après US1 et Streaming :
T025 (FullRagPipelineIntegrationSpec — flux complet)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Compléter Phase 1 (Setup) — T001–T011
2. Compléter Phase 2 (AbstractIntegrationSpec) — T012 (**CRITIQUE**)
3. Compléter Phase 3 (IngestionPipelineIntegrationSpec) — T013–T019
4. **ARRÊT ET VALIDATION**: `./mvnw test -Dtest=IngestionPipelineIntegrationSpec` — 7 tests au vert
5. US1 délivre une valeur immédiate : pipeline d'ingestion validé bout-en-bout pour les 4 formats

### Incremental Delivery

1. Setup + Foundational → infrastructure de test prête
2. US1 (Ingestion) → pipeline d'ingestion validé → commit `test(phase-9): add IngestionPipelineIntegrationSpec`
3. US2 (RAG complet) → pipeline RAG validé → commit `test(phase-9): add RetrievalPipelineIntegrationSpec, StreamingPipelineIntegrationSpec, FullRagPipelineIntegrationSpec`
4. US3 (Rate Limiting) → protection débit validée → commit `test(phase-9): add RateLimitIntegrationSpec`
5. Chaque story peut être démontrée indépendamment sans casser les précédentes

### Parallel Team Strategy

Avec plusieurs développeurs après T012 :
- **Dev A**: US1 — `IngestionPipelineIntegrationSpec` (T013–T019)
- **Dev B**: US2 — `RetrievalPipelineIntegrationSpec` + `StreamingPipelineIntegrationSpec` (T020–T024)
- **Dev C**: US3 — `RateLimitIntegrationSpec` (T026–T030)
- **Dev D** (si disponible): `FullRagPipelineIntegrationSpec` (T025) après que Dev A ait validé US1

---

## Notes

- `[P]` = fichiers différents, aucune dépendance incomplète — peut s'exécuter en parallèle
- `[Story]` trace chaque tâche vers son critère d'acceptance dans `spec.md`
- Chaque classe de test est indépendamment exécutable et commitée séparément
- Convention `@DisplayName` obligatoire en français sur chaque `@Test` et chaque classe (constitution III)
- Les stubs WireMock (T009–T011) DOIVENT couvrir les deux cas : streaming et non-streaming
- ClamAV peut prendre jusqu'à 30s au premier démarrage — prévoir `withStartupTimeout(Duration.ofMinutes(2))` dans `AbstractIntegrationSpec`
- Vider les buckets Redis dans `@BeforeEach` de `RateLimitIntegrationSpec` pour éviter les pollutions entre tests
