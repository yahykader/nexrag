# Research: Phase 2 — Ingestion : Stratégies, Cache & Orchestration

**Date**: 2026-03-27
**Branch**: `002-phase2-ingestion-strategy`

---

## 1. Patterns de test pour les stratégies d'ingestion (Strategy Pattern)

**Decision**: Chaque `IngestionStrategy` est testée en isolation avec ses dépendances mockées. `IngestionConfig` est testée séparément pour valider la sélection et le tri par priorité.

**Rationale**: La constitution (Principe II / OCP) exige qu'un nouveau `*Spec.java` n'implique aucune modification de spec existante. Les tests de `IngestionConfig` valident uniquement la sélection automatique par priorité — indépendamment des implémentations.

**Alternatives considered**:
- Tests d'intégration pour chaque stratégie (rejeté : réservé Phase 9 via Testcontainers)
- Test unique "omnibus" sur plusieurs stratégies (rejeté : viole SRP constitution Principe II)

---

## 2. Mockage de `EmbeddingModel` et `EmbeddingStore`

**Decision**: `EmbeddingModel` est mocqué via `@Mock` avec `when(model.embed(any())).thenReturn(Response.from(Embedding.from(new float[]{0.1f, 0.2f})))`. `EmbeddingStore<TextSegment>` est mocqué avec un `ArgumentCaptor` pour vérifier les segments stockés.

**Rationale**: La constitution (Principe V) interdit tout appel réseau en tests unitaires. L'API OpenAI réelle n'est jamais appelée. Le mock retourne un vecteur minimal mais valide pour langchain4j.

**Alternatives considered**:
- `EmbeddingModel` local `AllMiniLmL6V2EmbeddingModel` (rejeté : trop lent pour < 500 ms / test)
- WireMock pour OpenAI endpoint (gardé pour Phase 9 intégration uniquement)

---

## 3. Test du cache deux niveaux (Caffeine L1 + Redis L2)

**Decision**: `EmbeddingCache` est testé via ses collaborateurs mockés : `EmbeddingCacheStore` (Redis L2) et la Caffeine map interne exposée. Le test vérifie l'ordre de consultation : L1 d'abord, puis L2, puis appel API.

**Rationale**: Le cache étant un composant de performance critique (SC-002 : 0 double appel API pour contenu identique), les tests doivent couvrir les trois chemins : hit L1, hit L2, miss total.

**Alternatives considered**:
- Embedded Redis pour tests unitaires du cache (rejeté : `EmbeddingRedis` non accepté par constitution Principe V)
- Mockito `@Spy` sur la map Caffeine (retenu uniquement si l'implémentation l'expose via getter)

---

## 4. Test du rollback transactionnel (`RollbackExecutor`)

**Decision**: `RollbackExecutor` est testé avec deux `EmbeddingStore` mockés (qualifiés `textEmbeddingStore` et `imageEmbeddingStore`). Le test vérifie que `store.remove(id)` est appelé une fois par embedding ID — idempotence testée en appelant `rollback` deux fois sur le même batch.

**Rationale**: Constitution Principe IV impose 100 % branch coverage sur `RollbackExecutor` (chemin heureux + exception sur `store.remove`). L'idempotence est couverte pour le cas où certains IDs ont déjà été supprimés.

**Alternatives considered**:
- Test avec pgvector réel via Testcontainers (rejeté : Phase 9 uniquement)

---

## 5. Test de `IngestionOrchestrator` (orchestration et progression)

**Decision**: `IngestionOrchestratorSpec` utilise `@Mock` pour toutes les dépendances (stratégies, `IngestionTracker`, `AntivirusGuard`, `DeduplicationService`, `RAGMetrics`). L'ordre des étapes est vérifié via `InOrder` Mockito. Le comportement sur `EmptyContentException` est testé comme cas d'erreur déclenchant rollback.

**Rationale**: L'orchestrateur coordonne 5 étapes (FR-013, FR-017) ; Mockito `InOrder` garantit que la séquence antivirus → stratégie → indexation → tracking est respectée. Le test de régression OCP valide qu'ajouter une nouvelle stratégie ne casse pas les tests existants.

**Alternatives considered**:
- `@SpringBootTest` pour l'orchestrateur (rejeté : trop lourd, viole Principe I < 500 ms)

---

## 6. Test de la compression `EmbeddingCompressor`

**Decision**: `EmbeddingCompressorSpec` teste `quantizeInt8` et `quantizeInt16` avec des vecteurs connus, vérifie la perte de qualité (MSE < 2 % pour INT8), et teste le mode désactivé (vecteur retourné identique). Aucun mock requis — classe stateless.

**Rationale**: `EmbeddingCompressor` est une classe de calcul pur (pas de dépendances Spring). Les tests sont déterministes sur des vecteurs fixes.

**Alternatives considered**:
- Test basé sur des fichiers de fixtures (rejeté : inutile pour une classe de calcul pur)

---

## 7. Nommage des DisplayName (convention française)

**Decision**: Tous les `@DisplayName` suivent le pattern `"DOIT [action au présent] quand [condition]"` en français, conformément à la constitution Principe III et au code de production.

**Exemples retenus**:
- `"DOIT produire au moins 3 chunks pour un PDF de 3 pages"`
- `"DOIT lever EmptyContentException quand le texte extrait est vide"`
- `"DOIT ne pas appeler le service embedding quand le cache contient déjà la clé"`
- `"DOIT supprimer tous les embeddings du batch quand rollback est déclenché"`

**Alternatives considered**:
- Convention anglaise (rejeté : incompatible avec constitution et code de production)
