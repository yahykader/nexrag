# Implementation Plan: Phase 4 — Streaming

**Branch**: `004-phase-4-streaming` | **Date**: 2026-03-30 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/004-phase-4-streaming/spec.md`

## Summary

Implémentation des tests unitaires pour les 4 classes du module `streaming` :
`ConversationManager` (persistance Redis), `EventEmitter` (SSE + token buffering),
`StreamingOrchestrator` (coordination RAG → historique → génération) et
`OpenAiStreamingClient` (consommation SSE OpenAI via WebClient). Chaque classe obtient
son propre `*Spec.java` avec `@ExtendWith(MockitoExtension.class)`, assertions AssertJ,
`@DisplayName` en français, et WireMock pour les appels HTTP externes. Couverture cible ≥80%
ligne + branche par classe, ≤500ms par test.

## Technical Context

**Language/Version**: Java 21 (Spring Boot 3.4.2)
**Primary Dependencies**:
- LangChain4j 1.0.0-beta1 — `StreamingChatLanguageModel`, `StreamingResponseHandler`
- Spring Data Redis — `RedisTemplate<String, String>`, `ValueOperations`
- Spring WebFlux — `WebClient` (dans `OpenAiStreamingClient` uniquement)
- Jackson — `ObjectMapper` (sérialisation `ConversationState` vers/depuis Redis)
- Lombok — `@Data`, `@Builder`, `@Slf4j`
- WireMock `wiremock-jre8` — stub HTTP OpenAI dans `OpenAiStreamingClientSpec`
- Micrometer — `RAGMetrics` (mocké dans `StreamingOrchestratorSpec`)

**Storage**: Redis (clé `conversation:{id}`, TTL 3600s, `ConversationState` sérialisée en JSON)
**Testing**: JUnit 5 (Jupiter), Mockito, AssertJ, WireMock
**Target Platform**: Spring Boot 3.4.2 / Java 21 — serveur JVM
**Performance Goals**: ≤500ms par test unitaire ; timeout streaming visé 30s (spec Q5 — code actuel 60s, voir R-06#1)
**Constraints**: Aucun appel réseau réel ; `RedisTemplate` mocké via Mockito ; `WebClient` stubbé via WireMock ; pas de Testcontainers dans cette phase (unitaire uniquement)
**Scale/Scope**: 4 nouvelles classes `*Spec.java` ; tous les AC de Phase 4 couverts

## Constitution Check

*GATE: Pré-Phase 0 — re-vérification post-Phase 1 design incluse ci-dessous.*

| Principe | Statut | Justification |
|----------|--------|---------------|
| I. Isolation & Indépendance | ✅ Pass | Mockito pour dépendances internes ; WireMock pour HTTP ; `@BeforeEach`/`@AfterEach` obligatoires |
| II. SOLID dans les tests | ✅ Pass | 1 `*Spec.java` par classe de production ; mocks injectés via constructeur / `@InjectMocks` |
| III. Nommage & Organisation | ✅ Pass | `ConversationManagerSpec.java` etc. ; racine `streaming/` ; `@DisplayName` impératif français |
| IV. Couverture & Quality Gates | ✅ Pass | Cible ≥80% ligne+branche ; 100% ACs de Phase 4 ; happy + failure paths couverts |
| V. Intégration & Contracts | ✅ Pass | Tests unitaires uniquement ; Redis mocké ; WireMock pour HTTP externe |

**Post-Phase 1 re-check** : Aucune nouvelle violation détectée. Le data-model confirme
que `ConversationManager` expose bien ses dépendances via constructeur (DIP ✅).

**Gaps à documenter via tests** (non-bloquants pour le plan) :
- `getConversation()` sans vérification userId (R-06#2) → test qui échoue, signale le gap
- Timeout 60s vs 30s (R-06#1) → test documente la valeur effective

## Project Structure

### Documentation (this feature)

```text
specs/004-phase-4-streaming/
├── plan.md                              ← ce fichier
├── research.md                          ← R-01 à R-07 (Phase 0)
├── data-model.md                        ← entités + transitions + risques
├── quickstart.md                        ← squelettes + commandes + critères SC
├── contracts/
│   └── streaming-service-contracts.md  ← contrats internes des 4 services
└── tasks.md                             ← généré par /speckit.tasks (Phase 2)
```

### Source Code (repository root)

```text
nex-rag/src/main/java/com/exemple/nexrag/service/rag/streaming/
├── ConversationManager.java           [EXISTANT] Redis-backed conversation store
├── EventEmitter.java                  [EXISTANT] SSE emitter avec token buffering
├── StreamingOrchestrator.java         [EXISTANT] Coordinateur RAG → génération
├── model/
│   ├── ConversationState.java         [EXISTANT] État, Message, SourceReference, ContextItem
│   ├── StreamingEvent.java            [EXISTANT] Enum Type (TOKEN, COMPLETE, ERROR, …)
│   ├── StreamingRequest.java          [EXISTANT]
│   └── StreamingResponse.java         [EXISTANT]
└── openai/
    └── OpenAiStreamingClient.java     [EXISTANT] WebClient SSE vers OpenAI

nex-rag/src/test/java/com/exemple/nexrag/service/rag/streaming/
├── ConversationManagerSpec.java       [À CRÉER] — US-1, FR-001 à FR-005, SC-001/002/007
├── EventEmitterSpec.java              [À CRÉER] — US-2 (FR-006 à FR-008), SC-004/005/006
├── StreamingOrchestratorSpec.java     [À CRÉER] — US-3, FR-009 FR-010, SC-003/006
└── openai/
    └── OpenAiStreamingClientSpec.java [À CRÉER] — US-2 AC-12.1/12.2/12.3 (WireMock)
```

**Structure Decision**: Module backend unique. Tests miroirs de la structure de production
dans `src/test/java/`, selon la convention du Principe III de la constitution.

## Complexity Tracking

Aucune violation de constitution — section non applicable.
