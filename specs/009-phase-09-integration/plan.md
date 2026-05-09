# Implementation Plan: PHASE 9 — Tests d'Intégration

**Branch**: `009-phase-09-integration` | **Date**: 2026-05-07 | **Spec**: [spec.md](spec.md)

## Summary

Créer 5 classes de tests d'intégration Java couvrant le pipeline complet NexRAG — ingestion
multi-format (PDF, DOCX, XLSX, image, texte), recherche RAG, streaming SSE, rate limiting
distribué et régression bout-en-bout. L'infrastructure de test s'appuie sur Testcontainers
(PostgreSQL/pgvector, Redis, ClamAV) et WireMock (API OpenAI) pour une exécution
entièrement reproductible sans réseau réel ni credentials de production.

## Technical Context

**Language/Version**: Java 21 / Spring Boot 3.4.2  
**Primary Dependencies**: JUnit 5 Jupiter, Mockito, AssertJ, Testcontainers 1.19.7, WireMock 2.35.2, Awaitility, Spring Boot Test  
**Storage**: PostgreSQL/pgvector (`pgvector/pgvector:pg16`), Redis (`redis:7-alpine`), ClamAV (`clamav/clamav:latest`)  
**Testing**: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@Testcontainers` + `TestRestTemplate` / `MockMvc`  
**Target Platform**: JVM Linux (CI/CD)  
**Performance Goals**: ingestion < 10 s/doc, retrieval < 3 s, premier token streaming < 5 s, suite complète < 3 min  
**Constraints**: Aucun accès réseau réel, aucun credential en clair, isolation totale entre tests  
**Scale/Scope**: 5 classes IntegrationSpec, ~25 méthodes `@Test`, ~5 fixtures de documents

## Constitution Check

### ✅ Principe I — Test Isolation & Independence

- Testcontainers fournit une infrastructure éphémère recréée pour chaque run ✅
- `@DynamicPropertySource` surcharge `spring.datasource.*`, `spring.redis.*`, `antivirus.*`, `openai.*` au démarrage des containers ✅
- `@BeforeEach` supprime les données de test via les API de l'application (pas via JDBC direct) ✅

### ✅ Principe III — Naming & Organisation

- Classes : `IngestionPipelineIntegrationSpec.java`, `RetrievalPipelineIntegrationSpec.java`, `StreamingPipelineIntegrationSpec.java`, `RateLimitIntegrationSpec.java`, `FullRagPipelineIntegrationSpec.java` ✅
- Package : `com.exemple.nexrag.service.rag.integration` ✅
- `@DisplayName` sur chaque classe et méthode en français : `"DOIT [action] quand [condition]"` ✅

### ✅ Principe IV — Coverage & Quality Gates

- Les tests d'intégration contribuent aux seuils JaCoCo 80% (modules `ingestion`, `retrieval`, `streaming`) ✅
- Chemins critiques antivirus et déduplication couverts par scénarios E2E (100% branch sur `AntivirusGuard`, `DeduplicationService`) ✅

### ✅ Principe V — Integration & Contract Testing

- PostgreSQL/pgvector : `pgvector/pgvector:pg16` via `PostgreSQLContainer` ✅
- Redis : `redis:7-alpine` via `GenericContainer` (pas de fake in-memory) ✅
- ClamAV : `clamav/clamav:latest` via `GenericContainer` exposé port 3310 ✅
- OpenAI API : stubbing WireMock via `WireMockExtension` (aucune clé réelle) ✅

**Verdict** : Aucune violation — plan conforme à la constitution v1.1.0.

## Project Structure

### Documentation (this feature)

```text
specs/009-phase-09-integration/
├── plan.md              ← ce fichier
├── research.md          ← Phase 0 output
├── data-model.md        ← Phase 1 output
├── quickstart.md        ← Phase 1 output
├── contracts/
│   └── api-endpoints.md ← Phase 1 output
└── tasks.md             ← Phase 2 output (/speckit-tasks)
```

### Source Code

```text
nex-rag/
├── pom.xml                                    ← +3 dépendances test (voir research.md)
└── src/test/
    ├── java/com/exemple/nexrag/
    │   └── service/rag/
    │       └── integration/                   ← nouveau package
    │           ├── AbstractIntegrationSpec.java       ← containers partagés, @DynamicPropertySource
    │           ├── IngestionPipelineIntegrationSpec.java   ← US-1 (FR-001, FR-002, FR-007, FR-008)
    │           ├── RetrievalPipelineIntegrationSpec.java   ← US-2 (FR-003, FR-005)
    │           ├── StreamingPipelineIntegrationSpec.java   ← US-3 (FR-004)
    │           ├── RateLimitIntegrationSpec.java           ← US-4 (FR-006)
    │           └── FullRagPipelineIntegrationSpec.java     ← US-5 (FR-010)
    └── resources/
        ├── application-integration-test.yml   ← profil Spring (surcharge infra vers containers)
        └── fixtures/
            ├── sample.pdf                     ← 2 pages, contenu textuel indexable
            ├── sample.docx                    ← document Word léger
            ├── sample.xlsx                    ← 1 feuille, données tabulaires
            ├── sample.jpg                     ← image JPEG pour ImageIngestionStrategy
            └── sample.txt                     ← texte brut UTF-8
```

## Implementation Sequence

| Étape | Fichier créé / modifié | Dépendances | Constitue |
|-------|------------------------|-------------|-----------|
| 1 | `pom.xml` — +3 dépendances | — | Prérequis pom |
| 2 | `application-integration-test.yml` | — | Profil Spring |
| 3 | Fixtures `src/test/resources/fixtures/` | — | Données de test |
| 4 | `AbstractIntegrationSpec.java` | pom + yml | Base class containers |
| 5 | `IngestionPipelineIntegrationSpec.java` | AbstractIntegrationSpec | FR-001, FR-002, FR-007, FR-008 |
| 6 | `RetrievalPipelineIntegrationSpec.java` | IngestionPipelineIntegrationSpec | FR-003, FR-005 |
| 7 | `StreamingPipelineIntegrationSpec.java` | AbstractIntegrationSpec | FR-004 |
| 8 | `RateLimitIntegrationSpec.java` | AbstractIntegrationSpec | FR-006 |
| 9 | `FullRagPipelineIntegrationSpec.java` | All above | FR-010 |
