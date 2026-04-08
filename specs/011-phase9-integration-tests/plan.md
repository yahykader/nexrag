# Implementation Plan: PHASE 9 — Tests d'Intégration NexRAG

**Branch**: `011-phase9-integration-tests` | **Date**: 2026-04-06 | **Spec**: [spec.md](spec.md)  
**Input**: Feature specification from `/specs/011-phase9-integration-tests/spec.md`

## Summary

Créer la suite complète de tests d'intégration (PHASE 9) pour le backend NexRAG, couvrant les pipelines d'ingestion (4 formats : PDF, DOCX, XLSX, image), le pipeline RAG complet (retrieval + streaming SSE), la déduplication, la détection antivirus, et le rate limiting. Les tests utilisent Testcontainers (PostgreSQL/pgvector, Redis, ClamAV) avec des conteneurs partagés par classe, WireMock pour les stubs OpenAI, et un profil Spring dédié `integration-test`. La suite doit s'exécuter en moins de 10 minutes en CI.

## Technical Context

**Language/Version**: Java 21  
**Primary Dependencies**: Spring Boot 3.4.2, LangChain4j 1.0.0-beta1, JUnit 5, Mockito, AssertJ, Testcontainers 1.19.7, WireMock 2.35.2, Awaitility (à ajouter si absent)  
**Storage**: PostgreSQL/pgvector (`pgvector/pgvector:pg16`), Redis (`redis:7-alpine`)  
**Testing**: JUnit 5 (Jupiter), `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@Testcontainers`, WireMock Extension  
**Target Platform**: CI/CD Linux (GitHub Actions), Docker disponible  
**Project Type**: Tests d'intégration backend — suite complémentaire aux phases 1–8 unitaires  
**Performance Goals**: Ingestion < 10s/document, retrieval < 2s, premier token SSE < 3s, suite complète < 10 min  
**Constraints**: ClamAV réel (pas de mock), conteneurs Testcontainers partagés par classe, stubs OpenAI via WireMock  
**Scale/Scope**: 5 classes de tests, ~25–30 méthodes `@Test` au total, 4 formats de documents, 2 endpoints de rate limiting

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principe | Statut | Notes |
|----------|--------|-------|
| I. Test Isolation & Independence | ✅ PASS | Testcontainers éphémères, `@BeforeAll`/`@AfterAll` nettoyage, pas de dépendance d'ordre entre classes |
| II. SOLID Reflected in Tests | ✅ PASS | Chaque `*IntegrationSpec` couvre un scénario fonctionnel distinct ; `AbstractIntegrationSpec` extrait l'infrastructure commune (SRP) |
| III. Naming & Organisation | ✅ PASS | Convention `<Feature>IntegrationSpec.java`, package `integration/`, `@DisplayName` French imperative obligatoire |
| IV. Coverage Gates (≥ 80%) | ✅ PASS | Les tests d'intégration exercent les chemins réels ; la couverture de branche est capturée par JaCoCo |
| V. Integration & Contract Testing | ✅ PASS | Testcontainers pour toute infra, WireMock pour OpenAI, `@SpringBootTest`, flux complet requis par AC-22/23 |

**Violation documentée — budget CI**:  
La constitution (Testing Standards) fixe la suite d'intégration à < 3 minutes. Le scope étendu à 4 formats + ClamAV (démarrage ~20s) porte le budget à < 10 minutes (clarification 2026-04-06). Voir Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/011-phase9-integration-tests/
├── plan.md              ← ce fichier
├── research.md          ← décisions techniques (Phase 0)
├── data-model.md        ← entités de test et contraintes (Phase 1)
├── quickstart.md        ← guide d'exécution (Phase 1)
├── contracts/
│   └── integration-test-contracts.md  ← contrats HTTP + stubs WireMock (Phase 1)
└── tasks.md             ← Phase 2 (/speckit.tasks — non encore créé)
```

### Source Code (repository root)

```text
nex-rag/src/test/
├── java/com/exemple/nexrag/service/rag/integration/
│   ├── AbstractIntegrationSpec.java
│   │   └── Conteneurs statiques (PostgreSQL, Redis, ClamAV)
│   │       WireMock Extension partagée
│   │       @DynamicPropertySource pour les URLs dynamiques
│   │       @ActiveProfiles("integration-test")
│   │
│   ├── IngestionPipelineIntegrationSpec.java
│   │   └── FR-001: 4 formats (PDF, DOCX, XLSX, image) — ingestion OK < 10s
│   │       FR-002: Déduplication (DUPLICATE au 2e envoi)
│   │       FR-003: Embeddings COUNT > 0 après ingestion
│   │       FR-010: VIRUS_DETECTED pour eicar.com
│   │
│   ├── RetrievalPipelineIntegrationSpec.java
│   │   └── FR-004: Requête pertinente → ≥ 3 passages, < 2s
│   │       AC-23.1 couvert
│   │
│   ├── StreamingPipelineIntegrationSpec.java
│   │   └── FR-005: Streaming SSE — premier token < 3s
│   │       FR-006: Historique de conversation — 2 tours successifs
│   │       AC-23.2, AC-23.3 couverts
│   │
│   ├── RateLimitIntegrationSpec.java
│   │   └── FR-007: Rate limiting upload (HTTP 429 au-delà du seuil)
│   │       FR-007: Rate limiting search (HTTP 429 au-delà du seuil)
│   │
│   └── FullRagPipelineIntegrationSpec.java
│       └── Flux complet : upload PDF → retrieval → stream
│           Constitution V : "at least one spec exercising the full flow"
│
└── resources/
    ├── application-integration-test.yml    ← profil intégration
    ├── fixtures/
    │   ├── sample.pdf
    │   ├── sample.docx
    │   ├── sample.xlsx
    │   ├── sample.png
    │   └── virus/eicar.com
    └── wiremock/
        ├── embeddings-response.json
        ├── chat-completion-response.json
        └── chat-completion-stream.json
```

**Structure Decision**: Option 1 (single project). Tous les tests vivent sous `nex-rag/src/test/` dans le package `integration/` qui miroir le package de production `service/rag/`. L'infrastructure partagée est centralisée dans `AbstractIntegrationSpec`.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Budget CI 10 min > constitution 3 min | Scope étendu à 4 formats (PDF, DOCX, XLSX, image) + ClamAV démarrage ~20s + WireMock startup | Réduire à PDF uniquement rejeté — décision de couverture maximale actée en clarification Q4 (2026-04-06) |
| `AbstractIntegrationSpec` (classe de base partagée) | Éviter la duplication de 60+ lignes de déclaration Testcontainers dans chaque classe | Pattern sans base class: chaque classe redéclare les conteneurs statiques — duplication excessive, risque de désynchronisation des versions d'image |
