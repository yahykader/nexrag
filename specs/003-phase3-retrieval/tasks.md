# Tasks: Phase 3 — Retrieval Pipeline

**Input**: Design documents from `/specs/003-phase3-retrieval/`
**Prerequisites**: plan.md ✅ · spec.md ✅ · research.md ✅ · data-model.md ✅ · quickstart.md ✅

**Context**: Production code for all 10 retrieval classes already exists. Every task in this list is the writing of a `*Spec.java` test class. The constitution mandates test-first; since production code exists, write each spec, run it, and confirm it passes before moving on.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm test directory exists and create the shared test helper used by all spec classes.

- [x] T001 Créer `RetrievalTestHelper.java` avec `buildTestConfig()`, `buildScoredChunk()`, `buildRetrievalResult()` et `buildEmptyResult()` dans `nex-rag/src/test/java/com/exemple/nexrag/service/rag/retrieval/RetrievalTestHelper.java`

**Checkpoint**: Helper compilable et importable par tous les specs de la Phase 3.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Valider que la configuration de test reproduit fidèlement les valeurs par défaut de `application.yml` (text top-20 / image top-5 / BM25 top-10 / RRF k=60 / timeout 200 ms en test / max-tokens 200 000).

**⚠️ CRITIQUE**: Toutes les specs de la Phase 3 dépendent de `RetrievalTestHelper` et de la configuration validée ici.

- [x] T002 Vérifier dans `RetrievalTestHelper.java` que `buildTestConfig()` produit une `RetrievalConfig` avec `text.topK=20`, `image.topK=5`, `bm25.topK=10`, `rrfK=60`, `parallelTimeout=200` (ms, court pour les tests), `maxTokens=200000` dans `nex-rag/src/test/java/com/exemple/nexrag/service/rag/retrieval/RetrievalTestHelper.java`

**Checkpoint**: Foundation prête — l'implémentation des user stories peut commencer en parallèle.

---

## Phase 3: User Story 1 — Query Transformation and Intelligent Routing (Priority: P1) 🎯 MVP

**Goal**: Garantir que le transformateur génère exactement 5 variantes (FR-001) et que le routeur sélectionne la bonne stratégie selon les mots-clés de la query (FR-002, FR-003).

**Independent Test**: `./mvnw test -Dtest="QueryTransformerServiceSpec,QueryRouterServiceSpec"` passe entièrement sans dépendance externe.

- [x] T003 [P] [US1] Écrire `QueryTransformerServiceSpec.java` avec 6 `@Test` couvrant : expansion de synonymes (AC-8.1 / FR-001), expansion d'acronymes (CA → chiffre d'affaires), limite à `maxVariants=5`, query originale toujours présente dans les variantes, désactivation (`enabled=false`) retourne la query seule, fallback `rule-based` quand le LLM mock lance une exception dans `nex-rag/src/test/java/com/exemple/nexrag/service/rag/retrieval/query/QueryTransformerServiceSpec.java`
- [x] T004 [P] [US1] Écrire `QueryRouterServiceSpec.java` avec 7 `@Test` couvrant : stratégie `IMAGE_ONLY` pour query contenant "graphique"/"image" (AC-8.2), stratégie `HYBRID` pour query générale (AC-8.3), stratégie `TEXT_ONLY` pour query d'explication, stratégie `STRUCTURED` pour query avec chiffres/données, score de confiance ≥ 0 toujours présent (FR-003), stratégie par défaut `HYBRID` quand router `enabled=false`, config des retrievers selon la stratégie (topK, enabled) dans `nex-rag/src/test/java/com/exemple/nexrag/service/rag/retrieval/query/QueryRouterServiceSpec.java`

**Checkpoint**: User Story 1 entièrement fonctionnelle et testable indépendamment.

---

## Phase 4: User Story 2 — Parallel Multi-Source Document Retrieval (Priority: P1)

**Goal**: Garantir que les trois retrievers individuels respectent leurs top-K et seuils de score, et que `ParallelRetrieverService` les exécute en parallèle avec dégradation gracieuse en cas de timeout (FR-004, FR-005).

**Independent Test**: `./mvnw test -Dtest="TextVectorRetrieverSpec,BM25RetrieverSpec,ImageVectorRetrieverSpec,ParallelRetrieverServiceSpec"` passe entièrement, chaque test < 500 ms.

- [x] T005 [P] [US2] Écrire `TextVectorRetrieverSpec.java` avec 4 `@Test` couvrant : retourne au maximum top-20 résultats (FR-006), filtre les chunks en dessous du seuil de similarité 0.7, résultats triés par score décroissant (AC-9.3), retourne un résultat vide sans erreur si le store ne retourne rien dans `nex-rag/src/test/java/com/exemple/nexrag/service/rag/retrieval/retriever/TextVectorRetrieverSpec.java`
- [x] T006 [P] [US2] Écrire `BM25RetrieverSpec.java` avec 3 `@Test` couvrant : retourne au maximum top-10 résultats (FR-007), opère indépendamment du TextVectorRetriever (aucun mock de TextVectorRetriever requis), retourne un résultat vide sans erreur si aucun document ne correspond dans `nex-rag/src/test/java/com/exemple/nexrag/service/rag/retrieval/retriever/BM25RetrieverSpec.java`
- [x] T007 [P] [US2] Écrire `ImageVectorRetrieverSpec.java` avec 3 `@Test` couvrant : retourne au maximum top-5 résultats (FR-008), filtre les chunks en dessous du seuil image 0.6, retourne un résultat vide sans erreur si le store image est vide dans `nex-rag/src/test/java/com/exemple/nexrag/service/rag/retrieval/retriever/ImageVectorRetrieverSpec.java`
- [x] T008 [US2] Écrire `ParallelRetrieverServiceSpec.java` avec 5 `@Test` couvrant : fusionne les résultats de tous les retrievers activés (AC-9.1 / FR-004), retourne résultats partiels si un retriever dépasse le timeout de 200 ms sans bloquer les autres (AC-9.2 / FR-005), résultats fusionnés triés par score décroissant (AC-9.3), désactive les retrievers dont `enabled=false` dans la `RoutingDecision`, retourne contexte vide sans erreur quand tous les retrievers retournent 0 résultats (FR-015) dans `nex-rag/src/test/java/com/exemple/nexrag/service/rag/retrieval/retriever/ParallelRetrieverServiceSpec.java`

**Checkpoint**: User Stories 1 ET 2 fonctionnent indépendamment.

---

## Phase 5: User Story 3 — Reranking, Deduplication, and Context Injection (Priority: P2)

**Goal**: Garantir que le reranker réordonne les passages (AC-10.1), que l'agrégateur déduplique par ID et applique RRF k=60 (AC-10.2), et que l'injecteur respecte le budget de 200 000 tokens (AC-10.3).

**Independent Test**: `./mvnw test -Dtest="CrossEncoderRerankerSpec,ContentAggregatorServiceSpec,ContentInjectorServiceSpec"` passe entièrement sans dépendance sur US-1 ou US-2.

- [x] T009 [P] [US3] Écrire `CrossEncoderRerankerSpec.java` avec 3 `@Test` couvrant : réordonne 5 passages de sorte que le plus pertinent apparaisse en premier (AC-10.1 / FR-010), enregistre les métriques `recordReranking()` via mock `RAGMetrics`, retourne la liste inchangée sans erreur si un seul passage est fourni dans `nex-rag/src/test/java/com/exemple/nexrag/service/rag/retrieval/reranker/CrossEncoderRerankerSpec.java`
- [x] T010 [P] [US3] Écrire `ContentAggregatorServiceSpec.java` avec 5 `@Test` couvrant : déduplique deux chunks avec le même ID en retenant le score le plus élevé (AC-10.2 / FR-011 — dédup par ID, note du research.md), applique la fusion RRF avec k=60 et produit un score `1/(60+rank+1)` calculable (FR-009), limite le résultat final à `finalTopK=10`, retourne un `AggregatedContext` vide sans erreur quand tous les retrievers retournent 0 chunks (FR-015), délègue au reranker quand `reranker.enabled=true` (appel vérifié via mock) dans `nex-rag/src/test/java/com/exemple/nexrag/service/rag/retrieval/aggregator/ContentAggregatorServiceSpec.java`
- [x] T011 [US3] Écrire `ContentInjectorServiceSpec.java` avec 4 `@Test` couvrant : le prompt final ne dépasse jamais `maxTokens=200000` (AC-10.3 / FR-013), le prompt contient la query utilisateur et le contexte des passages avec citations (FR-012 / FR-014), `contextUsagePercent` est calculé comme `(totalTokens/maxTokens)*100`, le service retourne un prompt valide sans erreur quand `AggregatedContext` est vide (zero chunks) dans `nex-rag/src/test/java/com/exemple/nexrag/service/rag/retrieval/injector/ContentInjectorServiceSpec.java`

**Checkpoint**: Toutes les user stories fonctionnent indépendamment.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Pipeline complet, vérification de couverture et conformité avec la constitution.

- [x] T012 Écrire `RetrievalAugmentorOrchestratorSpec.java` avec 4 `@Test` couvrant : les 5 étapes sont appelées en séquence et leurs résultats enchaînés (queryTransformer → queryRouter → parallelRetriever → contentAggregator → contentInjector), les métriques `RAGMetrics` sont enregistrées pour chaque étape, `result.isSuccess()=false` est retourné sans exception quand une étape lève une RuntimeException, `result.getFinalPrompt()` retourne le prompt de l'étape 5 dans `nex-rag/src/test/java/com/exemple/nexrag/service/rag/retrieval/RetrievalAugmentorOrchestratorSpec.java`
- [x] T013 [P] Exécuter `./mvnw test -Dtest="QueryTransformerServiceSpec,QueryRouterServiceSpec,TextVectorRetrieverSpec,BM25RetrieverSpec,ImageVectorRetrieverSpec,ParallelRetrieverServiceSpec,CrossEncoderRerankerSpec,ContentAggregatorServiceSpec,ContentInjectorServiceSpec,RetrievalAugmentorOrchestratorSpec"` depuis `nex-rag/` et confirmer que tous les tests passent (0 failures, 0 errors)
- [x] T014 [P] Exécuter `./mvnw jacoco:report` depuis `nex-rag/` et vérifier ≥ 80 % de couverture lignes + branches pour le package `service/rag/retrieval/` dans `nex-rag/target/site/jacoco/index.html`
- [x] T015 Vérifier la traçabilité : chaque AC de la Phase 3 dans `nexrag-test-plan-speckit.md` (AC-8.1, AC-8.2, AC-8.3, AC-9.1, AC-9.2, AC-9.3, AC-10.1, AC-10.2, AC-10.3) est couvert par au moins un `@Test` identifié dans la table de traçabilité de `research.md`
- [x] T016 Committer la Phase 3 selon la convention de la constitution : `test(phase-3): add retrieval specs — QueryTransformer, QueryRouter, retrievers, reranker, aggregator, injector, orchestrator`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Aucune dépendance — peut démarrer immédiatement
- **Foundational (Phase 2)**: Dépend de Phase 1 (T001 → T002)
- **User Stories (Phase 3, 4, 5)**: Dépendent toutes de Phase 2 (T002) — les trois US peuvent démarrer en parallèle
- **Polish (Phase 6)**: Dépend de la complétion de toutes les user stories (T003–T011)

### User Story Dependencies

- **US-1 (P1)**: Peut démarrer après Phase 2 — T003 et T004 indépendants
- **US-2 (P1)**: Peut démarrer après Phase 2 — T005, T006, T007 indépendants entre eux; T008 dépend de T005/T006/T007
- **US-3 (P2)**: Peut démarrer après Phase 2 — T009 et T010 indépendants entre eux; T011 dépend de T010

### Within Each User Story

- Les tâches `[P]` d'une même story peuvent être réalisées en parallèle (fichiers différents)
- T008 dépend de T005, T006, T007 (les mocks doivent être compris avant d'écrire le service parallèle)
- T011 dépend de T010 (ContentInjectorService reçoit un `AggregatedContext` produit par l'agrégateur)

---

## Parallel Opportunities

### User Story 1 (T003 + T004 en parallèle)
```bash
# Deux développeurs, deux fichiers différents
Task: "T003 QueryTransformerServiceSpec.java"
Task: "T004 QueryRouterServiceSpec.java"
```

### User Story 2 (T005 + T006 + T007 en parallèle, puis T008)
```bash
# Trois développeurs simultanément
Task: "T005 TextVectorRetrieverSpec.java"
Task: "T006 BM25RetrieverSpec.java"
Task: "T007 ImageVectorRetrieverSpec.java"
# Puis séquentiellement
Task: "T008 ParallelRetrieverServiceSpec.java"
```

### User Story 3 (T009 + T010 en parallèle, puis T011)
```bash
Task: "T009 CrossEncoderRerankerSpec.java"
Task: "T010 ContentAggregatorServiceSpec.java"
# Puis séquentiellement
Task: "T011 ContentInjectorServiceSpec.java"
```

### Polish (T013 + T014 en parallèle)
```bash
Task: "T013 mvnw test — tous les specs"
Task: "T014 mvnw jacoco:report — vérification couverture"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Compléter Phase 1 + Phase 2 (T001–T002)
2. Compléter Phase 3 (T003–T004)
3. **STOP & VALIDER**: `./mvnw test -Dtest="QueryTransformerServiceSpec,QueryRouterServiceSpec"` vert
4. Démontrer que la transformation et le routing sont testés et conformes à la constitution

### Incremental Delivery

1. Setup + Foundational → `RetrievalTestHelper` disponible
2. US-1 → query transformation + routing testés (MVP)
3. US-2 → retrieval parallèle testé (pipeline complet jusqu'à l'agrégation brute)
4. US-3 → reranking + injection testés (pipeline complet jusqu'au prompt final)
5. Polish → orchestrateur + couverture ≥ 80%

### Parallel Team Strategy

Après complétion de T001–T002:
- **Développeur A**: T003 + T004 (US-1)
- **Développeur B**: T005 + T006 + T007, puis T008 (US-2)
- **Développeur C**: T009 + T010, puis T011 (US-3)
- Convergence: T012–T016 en commun

---

## Acceptance Criteria Coverage Map

| AC | FR | Spec class | Task |
|----|-----|-----------|------|
| AC-8.1 (expansion synonymes) | FR-001 | QueryTransformerServiceSpec | T003 |
| AC-8.2 (routing IMAGE_ONLY) | FR-002 | QueryRouterServiceSpec | T004 |
| AC-8.3 (routing HYBRID général) | FR-002/FR-003 | QueryRouterServiceSpec | T004 |
| AC-9.1 (fusion résultats) | FR-004 | ParallelRetrieverServiceSpec | T008 |
| AC-9.2 (timeout graceful) | FR-005 | ParallelRetrieverServiceSpec | T008 |
| AC-9.3 (tri décroissant) | FR-006 | TextVectorRetrieverSpec + ParallelRetrieverServiceSpec | T005 / T008 |
| AC-10.1 (reranking meilleur en tête) | FR-010 | CrossEncoderRerankerSpec | T009 |
| AC-10.2 (dédup — par ID) | FR-011 | ContentAggregatorServiceSpec | T010 |
| AC-10.3 (budget tokens) | FR-013 | ContentInjectorServiceSpec | T011 |
| FR-015 (zéro résultats → contexte vide) | FR-015 | ParallelRetrieverServiceSpec + ContentAggregatorServiceSpec | T008 / T010 |

---

## Notes

- `[P]` = fichiers différents, pas de dépendance — peuvent être écrits simultanément
- `[Story]` = traçabilité vers la user story de `spec.md`
- Chaque spec doit avoir `@ExtendWith(MockitoExtension.class)` et des `@DisplayName` en français (Constitution §III)
- Valider après chaque tâche que le test compile et passe avant de passer à la suivante
- **Gap connu (research.md)**: déduplication par contenu (cosinus > 0.95) non implémentée en production — T010 teste la dédup par ID uniquement
- **Gap connu (research.md)**: poids RRF (0.5/0.3/0.2) configurés mais non appliqués — T010 teste le comportement actuel (poids égaux)
