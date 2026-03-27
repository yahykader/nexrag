# Implementation Plan: Phase 2 — Ingestion : Stratégies, Cache & Orchestration

**Branch**: `002-phase2-ingestion-strategy` | **Date**: 2026-03-27 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/002-phase2-ingestion-strategy/spec.md`

## Summary

Implémenter les tests unitaires de Phase 2 du plan de tests NexRAG, couvrant les modules `strategy/`, `analyzer/`, `compression/`, `tracker/` et l'orchestrateur principal. Les classes de production existent déjà — l'objectif est de créer les `*Spec.java` manquants pour chacune, atteindre ≥ 80 % de couverture par module, et valider les trois user stories : sélection de stratégie par type MIME, chunking/cache d'embeddings, et rollback transactionnel de batch.

## Technical Context

**Language/Version**: Java 21 (records, sealed classes, pattern matching disponibles)
**Primary Dependencies**:
- `langchain4j` 1.10.0 — `EmbeddingModel`, `EmbeddingStore<TextSegment>`, `Embedding`, `TextSegment`, `Metadata`
- `spring-boot-test` (Spring BOM) — `MockMultipartFile`, `@ExtendWith(MockitoExtension.class)`
- `junit-jupiter` (Spring BOM) — runner, `@Test`, `@DisplayName`, `@BeforeEach`
- `mockito-junit-jupiter` (Spring BOM) — `@Mock`, `@InjectMocks`, `@Spy`
- `assertj-core` (Spring BOM) — fluent assertions
- `wiremock-jre8` (test scope, version explicite) — HTTP stubbing pour VisionAI

**Storage**: Redis (Mockito-stubbé en unitaire) · pgvector (EmbeddingStore mocké)
**Testing**: JUnit 5 (Jupiter) · Mockito · AssertJ
**Target Platform**: Serveur Linux (CI), exécution locale Windows 11
**Project Type**: Backend Spring Boot 3.4.2 — service d'ingestion RAG
**Performance Goals**: Chaque test unitaire < 500 ms (constitution Principe I)
**Constraints**: Aucun appel réseau réel ; Redis et pgvector mocqués via Mockito en unitaire
**Scale/Scope**: 26 classes de tests à créer (Phase 2 complète)

## Constitution Check

*GATE: Évalué avant Phase 0. Re-vérifié après Phase 1 design.*

| Principe | Statut | Justification |
|----------|--------|---------------|
| **I — Isolation & Indépendance** | ✅ PASS | Tous les tests seront `@ExtendWith(MockitoExtension.class)` ; Redis/pgvector mockés ; pas d'I/O réel |
| **II — SOLID dans les tests** | ✅ PASS | Un `*Spec.java` par classe de production ; `IngestionConfig` auto-discovery vérifié sans modifier l'orchestrateur |
| **III — Nommage & organisation** | ✅ PASS | Convention `<Classe>Spec.java` respectée ; `@DisplayName` obligatoire ; messages en français |
| **IV — Coverage ≥ 80 %** | ✅ PASS | Chemin heureux + chemin d'erreur pour chaque méthode publique ; `RollbackExecutor` → 100 % branch coverage obligatoire |
| **V — Pas d'infra réelle** | ✅ PASS | `EmbeddingStore`, `RedisTemplate`, `EmbeddingModel` mocqués partout ; intégration Testcontainers réservée à Phase 9 |

**Aucune violation détectée — passage autorisé vers Phase 0.**

## Project Structure

### Documentation (this feature)

```text
specs/002-phase2-ingestion-strategy/
├── plan.md              ✅ Ce fichier
├── research.md          ✅ Phase 0 output
├── data-model.md        ✅ Phase 1 output
├── quickstart.md        ✅ Phase 1 output
├── contracts/           ✅ Phase 1 output
└── tasks.md             ⏳ Phase 2 output (/speckit.tasks)
```

### Source Code (tests à créer)

```text
nex-rag/src/test/java/com/exemple/nexrag/service/rag/
└── ingestion/
    ├── strategy/
    │   ├── PdfIngestionStrategySpec.java          (US-5 / FR-001)
    │   ├── DocxIngestionStrategySpec.java         (US-5 / FR-002)
    │   ├── XlsxIngestionStrategySpec.java         (US-5 / FR-003)
    │   ├── ImageIngestionStrategySpec.java        (US-5 / FR-004)
    │   ├── TextIngestionStrategySpec.java         (US-5 / FR-005)
    │   ├── TikaIngestionStrategySpec.java         (US-5 / FR-006)
    │   ├── IngestionConfigSpec.java               (US-5 / FR-007)
    │   └── commun/
    │       ├── TextChunkerSpec.java               (US-6 / FR-008)
    │       ├── EmbeddingIndexerSpec.java          (US-6 / FR-009–011)
    │       └── LibreOfficeConverterSpec.java      (US-5 / FR-002)
    ├── cache/
    │   ├── EmbeddingCacheStoreSpec.java           (US-6 / FR-010–011)
    │   ├── EmbeddingTextHasherSpec.java           (US-6 / FR-010)
    │   └── EmbeddingSerializerSpec.java           (US-6 / FR-011)
    ├── compression/
    │   └── EmbeddingCompressorSpec.java           (US-6 / FR-012)
    ├── tracker/
    │   ├── IngestionTrackerSpec.java              (US-7 / FR-013–016)
    │   ├── RollbackExecutorSpec.java              (US-7 / FR-015) ← 100 % branch
    │   └── BatchInfoRegistrySpec.java             (US-7 / FR-016)
    ├── analyzer/
    │   ├── VisionAnalyzerSpec.java                (US-5 / FR-004)
    │   ├── VisionFallbackGeneratorSpec.java       (US-5 / FR-004 edge case)
    │   ├── ImageConverterSpec.java                (US-5 / FR-004)
    │   └── ImageSaverSpec.java                    (US-5 / FR-004)
    └── IngestionOrchestratorSpec.java             (US-7 / FR-013–017)
```

**Structure Decision**: Single project (backend uniquement). Les tests suivent exactement le package tree de production sous `service/rag/ingestion/`.

## Complexity Tracking

> Aucune violation de constitution détectée — section non applicable.
