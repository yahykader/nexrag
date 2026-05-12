# Tasks: PHASE 9 — Tests d'Intégration

**Input**: `specs/009-phase-09-integration/` — plan.md, spec.md, research.md, data-model.md, contracts/api-endpoints.md
**Prerequisites**: plan.md ✅ spec.md ✅ research.md ✅ data-model.md ✅ contracts/ ✅

> **Note**: Cette feature consiste à **implémenter les tests d'intégration eux-mêmes**.
> Les "implémentations" dans chaque phase sont les méthodes `@Test` Java.
> Aucun code de production n'est créé (sauf éventuellement `DELETE /api/files` — voir CHK033, Phase 8).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Parallélisable (fichiers différents, pas de dépendance incomplète)
- **[Story]**: User story cible ([US1]…[US5])
- Chaque chemin est relatif à la racine du dépôt

---

## Phase 1: Setup (Infrastructure Maven + Profil Spring + Fixtures)

**Purpose**: Mettre en place les prérequis sans lesquels aucune classe de test ne peut compiler ni démarrer.

> **⚠️ PRÉREQUIS BLOQUANT (CHK033)** : Avant T008, vérifier que `DELETE /api/files` existe dans le controller de production (`nex-rag/src/main/java/com/exemple/nexrag/service/rag/controller/`). Si absent, le créer (retourne `204 No Content`, supprime toutes les entrées pgvector + invalide le cache Redis). L'`@BeforeEach` de la Phase 2 (T011) appelle cet endpoint — s'il n'existe pas, Phase 2 est bloquée. **Traiter cette vérification en premier**, avant T001.

- [x] T001 Ajouter les 3 dépendances manquantes dans `nex-rag/pom.xml` : `testcontainers:testcontainers:1.19.7`, `testcontainers:junit-jupiter:1.19.7`, `awaitility:awaitility:4.2.1` (toutes `<scope>test</scope>`) — voir research.md §Décision 1
- [x] T002 Créer `nex-rag/src/test/resources/application-integration-test.yml` avec `antivirus.enabled: true` et les placeholders `@DynamicPropertySource` pour datasource, redis, antivirus et openai — voir research.md §Décision 2
- [x] T003 [P] Créer `nex-rag/src/test/resources/fixtures/sample.pdf` — 2 pages, contenu : "NexRAG est un système RAG multimodal. Il supporte l'ingestion de documents PDF, DOCX, XLSX, images et texte." (< 50 KB) — voir data-model.md §Fixtures
- [x] T004 [P] Créer `nex-rag/src/test/resources/fixtures/sample.docx` — 1 page, même contenu adapté Word (< 30 KB) — voir data-model.md §Fixtures
- [x] T005 [P] Créer `nex-rag/src/test/resources/fixtures/sample.xlsx` — 1 feuille "Données", colonnes Système/Type/Technologie, 5 lignes (< 20 KB) — voir data-model.md §Fixtures
- [x] T006 [P] Créer `nex-rag/src/test/resources/fixtures/sample.jpg` — image JPEG 100×100 px placeholder (< 200 KB) — voir data-model.md §Fixtures
- [x] T007 [P] Créer `nex-rag/src/test/resources/fixtures/sample.txt` — texte brut UTF-8, même contenu que sample.pdf (< 5 KB) — voir data-model.md §Fixtures

**Checkpoint**: Maven compile, profil `integration-test` reconnu par Spring, fixtures présentes en classpath.

---

## Phase 2: Foundational (AbstractIntegrationSpec — bloquant pour toutes les user stories)

**Purpose**: Classe de base partagée déclarant les containers statiques, `@DynamicPropertySource`, et le nettoyage `@BeforeEach`. Doit être finalisée avant toute `IntegrationSpec`.

**⚠️ CRITIQUE**: Aucune user story ne peut démarrer avant la fin de cette phase.

- [x] T008 Créer `nex-rag/src/test/java/com/exemple/nexrag/service/rag/integration/AbstractIntegrationSpec.java` — classe abstraite avec `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@Testcontainers`, `@ActiveProfiles("integration-test")` — voir research.md §Décision 3
- [x] T009 Déclarer les containers statiques dans `AbstractIntegrationSpec.java` : `PostgreSQLContainer<>` (`pgvector/pgvector:pg16`, `.withReuse(true)`, `withStartupTimeout(Duration.ofMinutes(2))`), `GenericContainer<>` Redis (`redis:7-alpine`, port 6379), `GenericContainer<>` ClamAV (`clamav/clamav:latest`, port 3310, `withStartupTimeout(Duration.ofMinutes(3))`), `WireMockExtension` sur port dynamique — voir research.md §Décision 3 + CHK016
- [x] T010 Implémenter `@DynamicPropertySource` dans `AbstractIntegrationSpec.java` surchargeant `spring.datasource.*`, `spring.redis.host/port`, `antivirus.host/port`, `openai.base-url` — voir research.md §Décision 3
- [x] T011 Implémenter `@BeforeEach` dans `AbstractIntegrationSpec.java` : (1) `DELETE /api/files` via `TestRestTemplate`, (2) `FLUSHALL` via `RedisTemplate`, (3) `OPEN_AI_MOCK.resetAll()`, (4) enregistrement stubs WireMock embeddings (1536 × 0.1) et chat SSE — voir research.md §Décisions 4 et 6 *(Prérequis : endpoint `DELETE /api/files` validé dans le prérequis bloquant Phase 1)*

**Checkpoint**: `AbstractIntegrationSpec` compile, containers démarrent, `@BeforeEach` s'exécute sans erreur. Valider avec un test vide héritant de la classe.

---

## Phase 3: User Story 1 - Validation Ingestion Bout-en-Bout (Priority: P1) 🎯 MVP

**Goal**: Valider le pipeline complet d'ingestion pour les 5 formats (PDF, DOCX, XLSX, image, texte), la détection de doublons, le rejet antivirus EICAR, et la concurrence atomique.

**Independent Test**: `./mvnw test -Dtest="IngestionPipelineIntegrationSpec"`

### Implémentation — User Story 1

- [x] T012 [US1] Créer `nex-rag/src/test/java/com/exemple/nexrag/service/rag/integration/IngestionPipelineIntegrationSpec.java` — squelette : `extends AbstractIntegrationSpec`, `@DisplayName("DOIT valider le pipeline d'ingestion bout-en-bout")`, injection `TestRestTemplate` + `@Autowired EmbeddingRepository` pour assertions pgvector — voir plan.md §Source Code
- [x] T013 [P] [US1] Implémenter `devraitIngererpdfEnMoinsDe10Secondes()` dans `IngestionPipelineIntegrationSpec.java` — POST multipart `sample.pdf` → assert 202, `batchId` non nul, vecteurs pgvector présents, durée < 10 s — voir spec.md §SC-001, contracts/api-endpoints.md §POST /api/ingest
- [x] T014 [P] [US1] Implémenter `devraitIngererDocxEnMoinsDe10Secondes()` dans `IngestionPipelineIntegrationSpec.java` — POST multipart `sample.docx` → assert 202, durée < 10 s — voir spec.md §SC-001
- [x] T015 [P] [US1] Implémenter `devraitIngererXlsxEnMoinsDe10Secondes()` dans `IngestionPipelineIntegrationSpec.java` — POST multipart `sample.xlsx` → assert 202, durée < 10 s — voir spec.md §SC-001
- [x] T016 [P] [US1] Implémenter `devraitIngererImageEnMoinsDe10Secondes()` dans `IngestionPipelineIntegrationSpec.java` — POST multipart `sample.jpg` → assert 202, durée < 10 s — voir spec.md §SC-001
- [x] T017 [P] [US1] Implémenter `devraitIngererTexteEnMoinsDe10Secondes()` dans `IngestionPipelineIntegrationSpec.java` — POST multipart `sample.txt` → assert 202, durée < 10 s — voir spec.md §SC-001
- [x] T018 [US1] Implémenter `devraitRetournerDuplicatePourMemeDocument()` dans `IngestionPipelineIntegrationSpec.java` — POST `sample.pdf` deux fois → 1er = 202 SUCCESS, 2nd = 409 DUPLICATE, comptage vecteurs identique ; capturer `Instant.now()` avant le 2ème POST et assert `Duration.between(start, Instant.now()).toSeconds() < 2` (SC-002 : détection doublon < 2 s) — voir spec.md §SC-002, data-model.md §IngestionResponse
- [x] T019 [US1] Implémenter `devraitRejeterFichierEicarAvecErreurVirus()` dans `IngestionPipelineIntegrationSpec.java` — POST fichier EICAR inline → assert 400 `{"status":"REJECTED"}`, zéro vecteur créé — voir spec.md §US-1 scénario 3, contracts/api-endpoints.md §POST /api/ingest
- [x] T020 [US1] Implémenter `devraitAccepterFichierSain()` dans `IngestionPipelineIntegrationSpec.java` — POST `sample.txt` → assert 202 SUCCESS, confirme que ClamAV est actif (sinon FR-008 non couvert) — voir spec.md §FR-008
- [x] T021 [US1] Implémenter `devraitGererIngestionConcurrenteAtomiquement()` dans `IngestionPipelineIntegrationSpec.java` — 2 threads `ExecutorService` soumettant `sample.pdf` simultanément → assert exactement 1 SUCCESS + 1 DUPLICATE, comptage vecteurs = ingestion simple — voir spec.md §US-1 scénario 4, spec.md §FR-002

**Checkpoint**: `IngestionPipelineIntegrationSpec` — tous les tests passent en < 45 s à froid. US-1 validée indépendamment.

---

## Phase 4: User Story 2 - Validation Pipeline RAG (Priority: P2)

**Goal**: Valider la chaîne requête → passages classés, la latence < 3 s, et la persistance de l'historique de conversation.

**Independent Test**: `./mvnw test -Dtest="RetrievalPipelineIntegrationSpec"` (nécessite un document ingéré — se fait dans `@BeforeEach`)

### Implémentation — User Story 2

- [x] T022 [US2] Créer `nex-rag/src/test/java/com/exemple/nexrag/service/rag/integration/RetrievalPipelineIntegrationSpec.java` — squelette : `extends AbstractIntegrationSpec`, `@DisplayName("DOIT valider le pipeline de récupération RAG")`, `@BeforeEach` ingérant `sample.pdf` pour pré-charger la base vectorielle — voir plan.md §Source Code, data-model.md §RetrievalResponse
- [x] T023 [US2] Implémenter `devraitRetournerAuMoins3PassagesClassesEnMoinsDe3Secondes()` dans `RetrievalPipelineIntegrationSpec.java` — GET/POST requête `"Qu'est-ce que NexRAG ?"` → assert `passages.size() >= 3`, scores décroissants, `durationMs <= 3000` — voir spec.md §SC-003, contracts/api-endpoints.md §GET /api/search
- [x] T024 [US2] Implémenter `devraitPreserverHistoriqueConversation()` dans `RetrievalPipelineIntegrationSpec.java` — 2 requêtes successives avec même `conversationId` → assert que la 2ème requête retourne bien des passages (historique disponible en Redis) — voir spec.md §US-2 scénario 2, spec.md §FR-005

**Checkpoint**: `RetrievalPipelineIntegrationSpec` — tous les tests passent en < 20 s à froid. US-2 validée indépendamment.

---

## Phase 5: User Story 3 - Validation Streaming SSE (Priority: P3)

**Goal**: Valider que les tokens de contenu arrivent avant `DONE` et que les erreurs mid-stream sont gérées proprement.

**Independent Test**: `./mvnw test -Dtest="StreamingPipelineIntegrationSpec"` (Awaitility pour assertions asynchrones)

### Implémentation — User Story 3

- [x] T025 [US3] Créer `nex-rag/src/test/java/com/exemple/nexrag/service/rag/integration/StreamingPipelineIntegrationSpec.java` — squelette : `extends AbstractIntegrationSpec`, `@DisplayName("DOIT valider le streaming SSE de réponse")`, injection `WebClient` ou `TestRestTemplate` pour consommation SSE, import Awaitility — voir plan.md §Source Code, data-model.md §StreamingEvent
- [x] T026 [US3] Implémenter `devraitEmettreTokensAvantSignalDeFin()` dans `StreamingPipelineIntegrationSpec.java` — POST `/api/stream` avec `sample.pdf` pré-ingéré → Awaitility collecte les `StreamingEvent`, assert ≥ 1 `TOKEN` avant `DONE`, premier token < 5 s — voir spec.md §SC-004, contracts/api-endpoints.md §POST /api/stream
- [x] T027 [US3] Implémenter `devraitEmettreEvenementErreurSansPlantage()` dans `StreamingPipelineIntegrationSpec.java` — configurer WireMock pour retourner 500 sur `/v1/chat/completions` → assert événement `ERROR` émis, flux fermé proprement, requête suivante fonctionne — voir spec.md §US-3 scénario 2, data-model.md §StreamingEvent §ERROR

**Checkpoint**: `StreamingPipelineIntegrationSpec` — tous les tests passent en < 15 s à froid. US-3 validée indépendamment.

---

## Phase 6: User Story 4 - Validation Rate Limiting Distribué (Priority: P4)

**Goal**: Valider les quotas par endpoint avec un vrai Redis et le comportement fail-open en cas d'indisponibilité Redis.

**Independent Test**: `./mvnw test -Dtest="RateLimitIntegrationSpec"` (`@BeforeEach` inclut `FLUSHALL` Redis)

### Implémentation — User Story 4

- [x] T028 [US4] Créer `nex-rag/src/test/java/com/exemple/nexrag/service/rag/integration/RateLimitIntegrationSpec.java` — squelette : `extends AbstractIntegrationSpec`, `@DisplayName("DOIT valider le rate limiting distribué en conditions réelles")` — voir plan.md §Source Code, data-model.md §RateLimitResponse
- [x] T029 [US4] Implémenter `devraitRetourner429AuDela10RequetesMinute()` dans `RateLimitIntegrationSpec.java` — boucle 11 POST `/api/upload` (fichier léger) → assert requêtes 1–10 = 202, requête 11 = 429 avec headers `Retry-After`, `X-RateLimit-Remaining: 0`, `X-RateLimit-Reset` — voir spec.md §US-4 scénario 1, contracts/api-endpoints.md §POST /api/upload
- [x] T030 [US4] Implémenter `devraitEtreFailOpenSiRedisIndisponible()` dans `RateLimitIntegrationSpec.java` — utiliser `@MockBean ProxyManager<String>` pour simuler une exception sur `tryConsume()` (ne pas appeler `REDIS.stop()` — stopper le container singleton corrompt les tests suivants avec `.withReuse(true)`) → POST `/api/upload` → assert 202 (fail-open, pas de 500) — voir spec.md §US-4 scénario 2, spec.md §FR-006, research.md §Décision 4

**Checkpoint**: `RateLimitIntegrationSpec` — tous les tests passent en < 25 s à froid. US-4 validée indépendamment.

---

## Phase 7: User Story 5 - Validation Pipeline Complet Bout-en-Bout (Priority: P5)

**Goal**: Test de régression couvrant upload → vecteurs → requête → streaming, et garantie d'isolation entre suites successives.

**Independent Test**: `./mvnw test -Dtest="FullRagPipelineIntegrationSpec"`

### Implémentation — User Story 5

- [x] T031 [US5] Créer `nex-rag/src/test/java/com/exemple/nexrag/service/rag/integration/FullRagPipelineIntegrationSpec.java` — squelette : `extends AbstractIntegrationSpec`, `@DisplayName("DOIT valider le pipeline complet ingestion→requête→streaming")` — voir plan.md §Source Code
- [x] T032 [US5] Implémenter `devraitCompleterFluxCompletIngestionVersStreaming()` dans `FullRagPipelineIntegrationSpec.java` — séquence : (1) POST `sample.pdf` → assert 202, (2) GET `/api/search` → assert ≥ 3 passages, (3) POST `/api/stream` → Awaitility assert token avant DONE, total < 30 s — voir spec.md §SC-007, spec.md §FR-010, contracts/api-endpoints.md tous endpoints
- [x] T033 [US5] Implémenter `devraitGarantirIsolationEntreSuitesConsecutives()` dans `FullRagPipelineIntegrationSpec.java` — vérifier qu'après `@BeforeEach` pgvector retourne 0 documents, Redis est vide (DBSIZE = 0), WireMock a 0 requêtes reçues — voir spec.md §US-5 scénario 2, spec.md §SC-008

**Checkpoint**: `FullRagPipelineIntegrationSpec` — tous les tests passent en < 30 s à froid. US-5 validée. Suite complète < 3 min.

---

## Phase 8: Polish & Vérifications Transversales

**Purpose**: Résoudre les items bloquants du checklist integration-readiness.md avant la mise en CI.

- [x] T034 [P] *(Normalement résolu via le prérequis bloquant Phase 1)* Confirmer que `DELETE /api/files` retourne bien `204 No Content` avec suppression pgvector + invalidation cache — exécuter `curl -X DELETE http://localhost:8090/api/files` et valider la réponse et les logs (CHK033)
- [x] T035 [P] Vérifier que la propriété OpenAI utilisée par `OpenAiEmbeddingService` et `OpenAiStreamingClient` correspond à `openai.base-url` dans `application-integration-test.yml` — corriger si nécessaire (CHK004)
- [x] T036 [P] Vérifier que la configuration JaCoCo dans `nex-rag/pom.xml` n'exclut pas le package `integration/` — confirmer que les tests d'intégration contribuent aux 80% de couverture (CHK037)
- [x] T037 [P] Documenter la création de `~/.testcontainers.properties` (contenu : `testcontainers.reuse.enable=true`) dans `specs/009-phase-09-integration/quickstart.md` §Prérequis (CHK018)
- [x] T038 Exécuter la suite complète en local : `./mvnw test -Dtest="*IntegrationSpec"` — valider que les 5 specs passent, durée < 3 min à chaud, couverture JaCoCo générée — voir quickstart.md
- [x] T039 [P] Ajouter dans la section `<rules>` de la configuration JaCoCo dans `nex-rag/pom.xml` deux règles de classe (`<element>CLASS</element>` + `<includes>`) avec `BRANCHCOVERAGE` = `1.0` ciblant `com.exemple.nexrag.service.rag.ingestion.security.AntivirusGuard` et `com.exemple.nexrag.service.rag.ingestion.deduplication.*DeduplicationService` — Constitution Principe IV : "safety-critical paths MUST achieve 100% branch coverage" ; exécuter `./mvnw verify jacoco:check` pour confirmer que la gate passe
- [x] T040 [P] Vérifier que le workflow GitHub Actions `.github/workflows/ci.yml` (ou équivalent) inclut un step `Integration Tests` exécutant `./mvnw test -Dtest="*IntegrationSpec"` avec Docker disponible (runner `ubuntu-latest` + services Docker) — si absent, ajouter le step après le step de tests unitaires existant (SC-006 : 5 suites passent en env CI/CD propre sans accès réseau externe)
- [x] T041 [P] Implémenter `devraitBloquerIngestionSiAntivirusIndisponible()` dans `IngestionPipelineIntegrationSpec.java` — utiliser `@MockBean AntivirusGuard` configuré pour lancer `VirusDetectedException` (simule service indisponible) → POST `sample.pdf` → déterminer et asserter le comportement attendu : soit 503 (bloqué) soit passage (fail-open) selon la décision d'équipe ; documenter la décision dans `application-integration-test.yml` (CHK034 — spec.md §Edge Cases)
- [x] T042 [P] Implémenter `devraitRetournerListeVideSiAucunDocumentIngere()` dans `RetrievalPipelineIntegrationSpec.java` — sans `@BeforeEach` ayant ingéré de document (état propre post-FLUSHALL), POST requête `"Qu'est-ce que NexRAG ?"` → assert `passages.isEmpty()` ou `passages.size() == 0`, status 200 (pas de 500) — spec.md §Edge Cases : "que se passe-t-il quand la base vectorielle ne contient aucun document ?"
- [x] T043 [P] Vérifier que le cold cache Redis est couvert implicitement : `@BeforeEach` appelle `FLUSHALL` avant chaque test → tous les `@Test` s'exécutent déjà avec un cache vide. Ajouter un commentaire explicite dans `AbstractIntegrationSpec.java` `@BeforeEach` : `// Cache Redis vidé → chaque test démarre en cold cache (spec.md §Edge Cases)` — spec.md §Edge Cases : "cold start lors d'une requête d'embedding"

---

## Dépendances & Ordre d'Exécution

### Dépendances entre phases

- **Phase 1 (Setup)** : Aucune dépendance — démarrer immédiatement
- **Phase 2 (Foundational)** : Dépend de la Phase 1 (T001, T002) — **bloque toutes les user stories**
- **Phases 3–7 (User Stories)** : Dépendent toutes de la Phase 2 (T008–T011)
  - US1 (Phase 3) : indépendante des autres stories
  - US2 (Phase 4) : indépendante — mais partage l'infra commune
  - US3 (Phase 5) : indépendante — peut paralléliser avec US2 (fichier différent)
  - US4 (Phase 6) : indépendante — dépend uniquement de l'AbstractIntegrationSpec
  - US5 (Phase 7) : dépend conceptuellement de US1–US4 (test de régression global), mais peut être écrite et compilée indépendamment
- **Phase 8 (Polish)** : Dépend de toutes les phases précédentes

### Dépendances entre user stories

| Story | Peut démarrer après | Bloque |
|-------|---------------------|--------|
| US1 — Ingestion | Phase 2 terminée | Rien (P1 — MVP) |
| US2 — Retrieval | Phase 2 terminée | Rien (P2) |
| US3 — Streaming | Phase 2 terminée | Rien (P3) |
| US4 — Rate Limit | Phase 2 terminée | Rien (P4) |
| US5 — Full Pipeline | Idéalement US1–US4 | — (P5) |

### Au sein de chaque user story

- Squelette de classe (`T012`, `T022`, `T025`, `T028`, `T031`) avant toute méthode `@Test`
- Méthodes [P] dans une même classe : parallélisables car elles n'ont pas de dépendances entre elles
- `@BeforeEach` (défini dans `AbstractIntegrationSpec`) garantit l'isolation — pas d'ordre imposé entre méthodes

### Opportunités de parallélisation

- T003–T007 : toutes les fixtures en parallèle (fichiers indépendants)
- T009–T011 : déclarations containers et `@DynamicPropertySource` peuvent être écrites en parallèle dans `AbstractIntegrationSpec`
- T013–T017 : les 5 méthodes d'ingestion par format sont indépendantes [P]
- T022–T024 (US2) et T025–T027 (US3) : peuvent démarrer simultanément (classes différentes)
- T034–T043 : toutes les vérifications de polish et tâches d'edge cases sont indépendantes [P] (sauf T038 run final)

---

## Exemple d'exécution parallèle — User Story 1

```bash
# Lancer les 5 tests d'ingestion par format en parallèle (classes différentes = non applicable ici,
# mais dans le même fichier les méthodes @Test sont isolées par @BeforeEach)
./mvnw test -Dtest="IngestionPipelineIntegrationSpec#devraitIngererpdfEnMoinsDe10Secondes"
./mvnw test -Dtest="IngestionPipelineIntegrationSpec#devraitIngererDocxEnMoinsDe10Secondes"
./mvnw test -Dtest="IngestionPipelineIntegrationSpec#devraitIngererXlsxEnMoinsDe10Secondes"

# Lancer US2 et US3 en parallèle (fichiers différents)
./mvnw test -Dtest="RetrievalPipelineIntegrationSpec" &
./mvnw test -Dtest="StreamingPipelineIntegrationSpec" &
wait
```

---

## Stratégie d'Implémentation

### MVP (User Story 1 uniquement)

1. Compléter **Phase 1** : Setup (T001–T007)
2. Compléter **Phase 2** : AbstractIntegrationSpec (T008–T011)
3. Compléter **Phase 3** : IngestionPipelineIntegrationSpec (T012–T021)
4. **STOP et VALIDATION** : `./mvnw test -Dtest="IngestionPipelineIntegrationSpec"` → 10 tests passent
5. Démo / revue de code avant de passer aux stories suivantes

### Livraison Incrémentale

1. Phase 1–2 → Infrastructure prête
2. Phase 3 (US1) → Pipeline d'ingestion validé → démo MVP
3. Phase 4 (US2) → Pipeline RAG complet validé
4. Phase 5 (US3) → Streaming validé
5. Phase 6 (US4) → Rate limiting validé
6. Phase 7 (US5) → Régression bout-en-bout validée
7. Phase 8 → Prêt pour CI/CD

### Stratégie parallèle (équipe)

Après complétion des Phases 1–2 :
- Dev A : User Story 1 (IngestionPipelineIntegrationSpec)
- Dev B : User Stories 2 + 3 (RetrievalPipelineIntegrationSpec + StreamingPipelineIntegrationSpec)
- Dev C : User Story 4 (RateLimitIntegrationSpec) — peut démarrer immédiatement après Phase 2
  - US5 (FullRagPipelineIntegrationSpec) : démarrer après stabilisation de US1–US4 (test de régression global — les 4 specs précédentes doivent passer avant d'écrire T032)

---

## Notes

- **[P]** = fichiers/méthodes différents, pas de dépendances non satisfaites — peuvent s'écrire simultanément
- **[Story]** = traçabilité vers la user story cible (US1–US5)
- Chaque user story est indépendamment exécutable via `./mvnw test -Dtest="<ClasseIntegrationSpec>"`
- **CHK033 bloquant** : vérifier l'existence de `DELETE /api/files` **avant T001** (prérequis bloquant Phase 1) — T011 (`@BeforeEach`) en dépend
- **CHK016 bloquant** : `withStartupTimeout(Duration.ofMinutes(3))` sur ClamAV est requis — T009 en dépend
- **Format de commit obligatoire** (Constitution §Backend Workflow) : `test(phase-9): add <ClassName> — <description>` — ex. `test(phase-9): add IngestionPipelineIntegrationSpec — 5 formats + EICAR + concurrence`
- Valider le checklist `checklists/integration-readiness.md` après chaque phase pour identifier les items restants
