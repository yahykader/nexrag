# Tasks: Phase 2 — Ingestion : Stratégies, Cache & Orchestration

**Input**: Design documents from `/specs/002-phase2-ingestion-strategy/`
**Prerequisites**: plan.md ✅ · spec.md ✅ · research.md ✅ · data-model.md ✅ · contracts/ ✅

**Context**: Les classes de production existent déjà. Chaque tâche consiste à créer le fichier `*Spec.java` correspondant selon la constitution (Principe III). L'ordre reflète les dépendances entre mocks : les specs des utilitaires purs d'abord, puis les specs dont les collaborateurs sont déjà couverts.

**Convention** : `@ExtendWith(MockitoExtension.class)` · `@DisplayName` en français · chemin dans `nex-rag/src/test/java/com/exemple/nexrag/service/rag/ingestion/`

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Parallélisable (fichiers distincts, aucune dépendance en cours)
- **[Story]**: User story parente (US1, US2, US3)

---

## Phase 1 : Setup — Infrastructure de test

**Purpose**: Vérifier que les dépendances de test sont en place et que l'arborescence de packages existe.

- [x] T001 Vérifier que `pom.xml` contient `junit-jupiter`, `mockito-junit-jupiter`, `assertj-core` et `wiremock-jre8` dans le scope `test` — fichier `nex-rag/pom.xml`
- [x] T002 S'assurer que l'arborescence de packages de test reflète l'arborescence de production : `strategy/commun/`, `cache/`, `compression/`, `tracker/`, `analyzer/` sous `src/test/java/com/exemple/nexrag/service/rag/ingestion/`

**Checkpoint** : `./mvnw test` passe (hors nouvelles specs) avant de commencer les phases suivantes.

---

## Phase 2 : Fondationnel — Classes sans dépendances Spring

**Purpose**: Specs des classes utilitaires pures (pas de mock Spring, pas de `RedisTemplate`). Bloquant pour US-2.

⚠️ **CRITIQUE** : Compléter avant de commencer les phases US.

- [x] T003 Créer `EmbeddingCompressorSpec` — tester `quantizeInt8`, `quantizeInt16`, mode désactivé, `cosineSimilarity`, `calculateStats` — fichier `compression/EmbeddingCompressorSpec.java`
- [x] T004 [P] Créer `EmbeddingTextHasherSpec` — tester le hachage déterministe (même texte → même hash), sensibilité à la casse — fichier `cache/EmbeddingTextHasherSpec.java`

**Checkpoint** : `./mvnw test -Dtest="EmbeddingCompressorSpec,EmbeddingTextHasherSpec"` vert.

---

## Phase 3 : User Story 1 — Ingestion Multi-Format par Stratégie Adaptée (Priorité P1) 🎯 MVP

**Goal**: Valider que chaque format (PDF, DOCX, XLSX, image, texte brut, fallback Tika) est extrait correctement par sa stratégie dédiée, et que `IngestionConfig` sélectionne la bonne stratégie par type MIME.

**Independent Test**: `./mvnw test -Dtest="Pdf*Spec,Docx*Spec,Xlsx*Spec,Text*Spec,Tika*Spec,Image*Spec,VisionAnalyzerSpec,IngestionConfigSpec,LibreOfficeConverterSpec"` doit passer sans aucun appel réseau réel.

### Analyzer (support image — sans dépendance sur les stratégies)

- [x] T005 [P] [US1] Créer `ImageConverterSpec` — tester la conversion de formats d'image (mock du système de fichiers ou bytes en mémoire) — fichier `analyzer/ImageConverterSpec.java`
- [x] T006 [P] [US1] Créer `ImageSaverSpec` — tester la sauvegarde d'image sur le chemin configuré (mock `ImageStorageProperties`) — fichier `analyzer/ImageSaverSpec.java`
- [x] T007 [P] [US1] Créer `VisionFallbackGeneratorSpec` — tester la génération d'un texte de substitution quand Vision AI est indisponible — fichier `analyzer/VisionFallbackGeneratorSpec.java`
- [x] T008 [US1] Créer `VisionAnalyzerSpec` — tester l'analyse d'image réussie (mock WireMock pour Vision AI endpoint) et le basculement vers `VisionFallbackGenerator` quand indisponible — fichier `analyzer/VisionAnalyzerSpec.java`

### Commun (utilitaires partagés par toutes les stratégies)

- [x] T009 [P] [US1] Créer `LibreOfficeConverterSpec` — tester la conversion DOCX→PDF (mock appel process LibreOffice) et la gestion d'erreur si binaire absent — fichier `strategy/commun/LibreOfficeConverterSpec.java`

### Stratégies par format

- [x] T010 [P] [US1] Créer `PdfIngestionStrategySpec` — tester `canHandle` (extension "pdf"), extraction de 3 pages → ≥ 3 chunks (mock `TextChunker`), PDF corrompu → `IngestionException` — fichier `strategy/PdfIngestionStrategySpec.java`
- [x] T011 [P] [US1] Créer `DocxIngestionStrategySpec` — tester `canHandle` (extension "docx"), extraction réussie (mock `LibreOfficeConverter` + `TextChunker`), DOCX corrompu → `IngestionException` avec nom de fichier — fichier `strategy/DocxIngestionStrategySpec.java`
- [x] T012 [P] [US1] Créer `XlsxIngestionStrategySpec` — tester `canHandle` (extension "xlsx"), extraction de toutes les feuilles, feuille vide → `EmptyContentException` — fichier `strategy/XlsxIngestionStrategySpec.java`
- [x] T013 [P] [US1] Créer `TextIngestionStrategySpec` — tester `canHandle` (extension "txt"), lecture encodage UTF-8 détecté automatiquement, texte vide → `EmptyContentException` — fichier `strategy/TextIngestionStrategySpec.java`
- [x] T014 [P] [US1] Créer `TikaIngestionStrategySpec` — tester `canHandle` toujours `true` (fallback universel), extraction via Tika (mock), `getPriority()` == 5 (plus basse priorité) — fichier `strategy/TikaIngestionStrategySpec.java`
- [x] T015 [US1] Créer `ImageIngestionStrategySpec` — tester `canHandle` (extensions image : jpg, png, gif), délégation à `VisionAnalyzer` vérifiée par mock, indisponibilité VisionAI → fallback produit un texte non vide — fichier `strategy/ImageIngestionStrategySpec.java`

### Sélection de stratégie

- [x] T016 [US1] Créer `IngestionConfigSpec` — tester que le bean `ingestionStrategies` trie les stratégies par priorité croissante, que PDF est sélectionné avant Tika pour un PDF, qu'un type inconnu est pris en charge par Tika — fichier `strategy/IngestionConfigSpec.java`

**Checkpoint** : `./mvnw test -Dtest="*IngestionStrategySpec,IngestionConfigSpec,Vision*Spec,LibreOfficeConverterSpec,ImageConverterSpec,ImageSaverSpec"` vert. US-1 intégralement couverte.

---

## Phase 4 : User Story 2 — Découpage et Indexation des Embeddings avec Cache (Priorité P2)

**Goal**: Valider que `TextChunker` découpe correctement avec chevauchement, que `EmbeddingIndexer` interroge le cache avant OpenAI, et que `EmbeddingCacheStore` applique le TTL de 7 jours.

**Independent Test**: `./mvnw test -Dtest="TextChunkerSpec,EmbeddingIndexerSpec,EmbeddingCacheStoreSpec,EmbeddingSerializerSpec"` vert sans appel réseau réel.

### Cache Redis

- [x] T017 [P] [US2] Créer `EmbeddingSerializerSpec` — tester la sérialisation/désérialisation aller-retour d'un vecteur float[] (Base64 JSON), vérifier que le vecteur reconstitué est identique à l'original — fichier `cache/EmbeddingSerializerSpec.java`
- [x] T018 [P] [US2] Créer `EmbeddingCacheStoreSpec` — mock `RedisTemplate`, tester `get` (hit / miss), `save` avec TTL 168 h, `deleteByBatchId` (3 entrées → 3 suppressions), idempotence sur batchId inexistant — fichier `cache/EmbeddingCacheStoreSpec.java`

### Chunking

- [x] T019 [US2] Créer `TextChunkerSpec` — mock `EmbeddingIndexer` + `MetadataSanitizer`, tester : 1000 chars / chunkSize=200 / overlap=50 → ≥ 6 chunks, chevauchement vérifié entre chunks consécutifs, texte < chunkSize → 1 chunk, texte vide → 0 chunk — fichier `strategy/commun/TextChunkerSpec.java`

### Indexation et cache d'embeddings

- [x] T020 [US2] Créer `EmbeddingIndexerSpec` — mock `EmbeddingModel`, `EmbeddingCache`, `TextDeduplicationService`, `IngestionTracker`, `EmbeddingStore` ; tester : texte non-dupliqué → `embeddingModel.embed()` appelé une fois + mis en cache, texte déjà en cache → `embeddingModel.embed()` NON appelé (cache hit), texte dupliqué → `null` retourné + `store.add()` non appelé, indexation image → pas de déduplication texte — fichier `strategy/commun/EmbeddingIndexerSpec.java`

**Checkpoint** : `./mvnw test -Dtest="TextChunkerSpec,EmbeddingIndexerSpec,EmbeddingCacheStoreSpec,EmbeddingSerializerSpec,EmbeddingTextHasherSpec"` vert. US-2 intégralement couverte.

---

## Phase 5 : User Story 3 — Suivi de Batch et Rollback (Priorité P3)

**Goal**: Valider que `IngestionTracker` enregistre correctement les états, que `RollbackExecutor` supprime tous les embeddings (100 % branch coverage), et que l'orchestrateur déclenche le rollback sur toute exception (y compris `EmptyContentException`).

**Independent Test**: `./mvnw test -Dtest="BatchInfoRegistrySpec,RollbackExecutorSpec,IngestionTrackerSpec,IngestionOrchestratorSpec"` vert.

### Registres

- [x] T021 [P] [US3] Créer `BatchInfoRegistrySpec` — tester `register` (batchId unique), `get` (présent / absent), `addTextEmbeddingId` et `addImageEmbeddingId`, `totalEmbeddings`, `remove` — fichier `tracker/BatchInfoRegistrySpec.java`

### Rollback (100 % branch coverage obligatoire)

- [x] T022 [US3] Créer `RollbackExecutorSpec` — mock `textEmbeddingStore` (@Qualifier) + `imageEmbeddingStore` (@Qualifier) ; tester : 5 text + 2 image → 7 suppressions, `store.remove()` lance exception → log warning + continuation (best-effort), batch inexistant → 0 retourné sans exception, rollback appelé deux fois → idempotent — fichier `tracker/RollbackExecutorSpec.java`

### Tracker

- [x] T023 [US3] Créer `IngestionTrackerSpec` — mock `BatchEmbeddingRegistry` + `BatchInfoRegistry` + `RollbackExecutor` ; tester : `addTextEmbeddingId` avec batchId blank → ignoré silencieusement, `rollbackBatch` → `rollbackExecutor.rollback()` appelé + batch retiré du registre, batch inexistant → 0 retourné — fichier `tracker/IngestionTrackerSpec.java`

### Orchestrateur

- [x] T024 [US3] Créer `IngestionOrchestratorSpec` — mock toutes les dépendances (strategies, `IngestionTracker`, `AntivirusGuard`, `DeduplicationService`, `RAGMetrics`, `EmbeddingRepository`) ; utiliser `InOrder` Mockito ; tester :
  - Séquence antivirus → stratégie → dédup → ingestion → métriques (happy path)
  - Exception dans `strategy.ingest()` → `rollbackSafely()` appelé + exception re-propagée
  - `EmptyContentException` → batch FAILED + rollback déclenché
  - `DuplicateFileException` → PAS de rollback
  - OCP : ajouter une mock stratégie supplémentaire → tests existants passent inchangés
  — fichier `IngestionOrchestratorSpec.java`

**Checkpoint** : `./mvnw test -Dtest="BatchInfoRegistrySpec,RollbackExecutorSpec,IngestionTrackerSpec,IngestionOrchestratorSpec"` vert. US-3 intégralement couverte.

---

## Phase 6 : Polish & Couverture

**Purpose**: Vérifier les seuils de couverture, corriger les lacunes, commit de phase.

- [x] T025 Générer le rapport JaCoCo : `./mvnw test jacoco:report` — vérifier que chaque module atteint ≥ 80 % lignes + branches (`target/site/jacoco/index.html`)
- [x] T026 [P] Compléter les chemins d'erreur manquants identifiés par JaCoCo pour atteindre ≥ 80 % sur les modules en dessous du seuil
- [x] T027 Vérifier que `RollbackExecutorSpec` atteint **100 %** branch coverage (constitution Principe IV — seuil bloquant)
- [ ] T028 Committer la Phase 2 : `git commit -m "test(phase-2): add ingestion strategy/cache/tracker/orchestrator specs — Phase 2 complete"`

---

## Dépendances & Ordre d'exécution

### Dépendances entre phases

- **Phase 1 (Setup)** : Aucune dépendance — démarrage immédiat
- **Phase 2 (Fondationnel)** : Dépend de Phase 1 — bloque US-2 (`EmbeddingCompressorSpec` utilisé par `EmbeddingIndexerSpec`)
- **Phase 3 (US-1)** : Dépend de Phase 1 uniquement — parallélisable avec Phase 2
- **Phase 4 (US-2)** : Dépend de Phase 2 (fondationnel) ; T019-T020 dépendent de T004 (`EmbeddingTextHasher`)
- **Phase 5 (US-3)** : Dépend de Phase 3 et Phase 4 pour `IngestionOrchestratorSpec` (T024) qui mock tous les composants
- **Phase 6 (Polish)** : Dépend de toutes les phases précédentes

### Dépendances entre User Stories

- **US-1 (P1)** : Peut démarrer après Phase 1 — indépendante de US-2 et US-3
- **US-2 (P2)** : Peut démarrer après Phase 2 — indépendante de US-1
- **US-3 (P3)** : `IngestionOrchestratorSpec` (T024) mock tous les composants → peut démarrer après Phase 1 ; `IngestionTrackerSpec` (T023) peut démarrer dès Phase 1

### Dépendances internes par User Story

```
US-1:
  T005-T007 (Analyzer support) [P] ──► T008 VisionAnalyzerSpec
  T009 LibreOfficeConverterSpec [P]
  T010-T014 (Strategies) [P] ──────────────────────────────────► T016 IngestionConfigSpec
  T015 ImageIngestionStrategySpec (dépend T008 VisionAnalyzerSpec)

US-2:
  T017-T018 (Cache) [P] ──► T020 EmbeddingIndexerSpec
  T019 TextChunkerSpec (dépend T020 par mock)

US-3:
  T021 BatchInfoRegistrySpec [P]
  T022 RollbackExecutorSpec ──► T023 IngestionTrackerSpec ──► T024 IngestionOrchestratorSpec
```

### Opportunités de parallélisation

- **Phase 3 (US-1)** : T005, T006, T007, T009, T010, T011, T012, T013, T014 tous parallélisables
- **Phase 4 (US-2)** : T017 et T018 parallélisables
- **Phase 5 (US-3)** : T021 parallélisable avec T022
- **Phase 6** : T026 parallélisable avec T027

---

## Exemple d'exécution parallèle : US-1

```bash
# Lancer toutes les specs analyzer en parallèle (fichiers distincts) :
./mvnw test -Dtest="ImageConverterSpec,ImageSaverSpec,VisionFallbackGeneratorSpec,LibreOfficeConverterSpec"

# Puis lancer toutes les specs de stratégies en parallèle :
./mvnw test -Dtest="PdfIngestionStrategySpec,DocxIngestionStrategySpec,XlsxIngestionStrategySpec,TextIngestionStrategySpec,TikaIngestionStrategySpec"

# Puis (séquentiel, dépend des précédents) :
./mvnw test -Dtest="VisionAnalyzerSpec,ImageIngestionStrategySpec,IngestionConfigSpec"
```

---

## Stratégie d'implémentation

### MVP : User Story 1 uniquement

1. Compléter **Phase 1** (Setup)
2. Compléter **Phase 3** (US-1 : 12 specs, toutes indépendantes de US-2 et US-3)
3. **STOP & VALIDER** : `./mvnw test -Dtest="*IngestionStrategySpec,IngestionConfigSpec,Vision*Spec"` → vert
4. MVP livrable : sélection de stratégie multi-format intégralement testée

### Livraison incrémentale

1. Phase 1 → Phase 2 → Phase 3 (US-1) → Valider → MVP
2. Phase 4 (US-2) → Valider cache + chunking → Demo
3. Phase 5 (US-3) → Valider rollback + orchestration → Demo complet Phase 2
4. Phase 6 → Couverture ≥ 80 % → Commit → Prêt pour Phase 3 du plan de tests (Retrieval)

### Stratégie en parallèle (2 développeurs)

- **Dev A** : Phase 1 + Phase 3 (US-1 : strategies + analyzer)
- **Dev B** : Phase 2 + Phase 4 (US-2 : cache + chunking)
- Une fois les deux terminés → **Dev A + Dev B** : Phase 5 (US-3 : tracker + orchestrateur)

---

## Notes

- `[P]` = fichiers distincts, aucune dépendance en cours d'exécution
- `[Story]` = traçabilité directe vers les user stories de `spec.md`
- Constitution Principe II (OCP) : `IngestionConfigSpec` (T016) valide qu'ajouter une stratégie ne modifie PAS les tests existants
- Constitution Principe IV : `RollbackExecutorSpec` (T022) = **100 % branch coverage** — bloquant pour merge
- Convention de commit : `test(phase-2): add <ClassName>Spec — <description courte>`
