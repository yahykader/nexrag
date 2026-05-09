# Integration Readiness Checklist: PHASE 9 — Tests d'Intégration

**Purpose**: Valider la qualité et la complétude du plan, des spécifications de test, et des prérequis d'implémentation avant de générer les tâches avec `/speckit-tasks`
**Created**: 2026-05-07
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [research.md](../research.md)
**Audience**: Auteur — auto-review avant génération des tâches

---

## Qualité du Plan (research.md, plan.md, data-model.md)

- [ ] CHK001 — Les 3 dépendances Maven manquantes (`testcontainers:testcontainers`, `testcontainers:junit-jupiter`, `awaitility`) sont-elles documentées avec leurs versions exactes cohérentes avec `testcontainers:postgresql:1.19.7` déjà présent ? [Completeness, research.md §Décision 1]
- [ ] CHK002 — Le nom exact du profil Spring (`integration-test`) est-il utilisé de façon cohérente dans `spec.md`, `plan.md`, `research.md` et `quickstart.md` ? [Consistency, research.md §Décision 2]
- [ ] CHK003 — Les versions des images Testcontainers (`pgvector:pg16`, `redis:7-alpine`, `clamav/clamav:latest`) sont-elles intentionnellement non-pinnées ou doit-on figer une version précise pour la reproductibilité CI ? [Clarity, research.md §Décision 3]
- [ ] CHK004 — La clé de propriété Spring pour surcharger l'URL OpenAI (`openai.base-url` ou autre) est-elle vérifiée contre la propriété réellement utilisée dans `application.yml` (ligne ~249: `openai.chat.api-url` ?) ? [Ambiguity, research.md §Décision 3 vs application.yml]
- [ ] CHK005 — Le pattern `AbstractIntegrationSpec` est-il spécifié comme classe abstraite POJO (containers `static`) ou comme extension d'une classe Spring Boot Test ? [Clarity, plan.md §Source Code]
- [ ] CHK006 — Le mapping FR → classe `IntegrationSpec` en `data-model.md` couvre-t-il tous les 10 FRs de `spec.md` sans omission ? [Completeness, data-model.md §Mapping]
- [ ] CHK007 — La séquence d'implémentation en 9 étapes (`plan.md §Implementation Sequence`) est-elle ordonnée de façon à ce qu'aucune classe `IntegrationSpec` ne soit créée avant `AbstractIntegrationSpec` ? [Consistency, plan.md]

---

## Qualité des Spécifications de Test (spec.md — clarté et mesurabilité)

- [ ] CHK008 — La borne "ingéré en moins de 10 secondes" (SC-001) est-elle définie de quel moment à quel moment — réception HTTP request → réponse 202, ou → commit pgvector vérifié ? [Clarity, Spec §SC-001]
- [ ] CHK009 — La borne "au moins 3 passages" (SC-003) est-elle suffisamment contrainte par les fixtures de test pour être déterministe — le contenu de `sample.pdf` garantit-il ≥3 chunks après découpage avec les paramètres de production ? [Measurability, Spec §SC-003 + data-model.md §Fixtures]
- [ ] CHK010 — La borne "premier token en moins de 5 secondes" (SC-004) est-elle mesurée depuis le début de la requête HTTP ou depuis l'établissement de la connexion SSE ? [Clarity, Spec §SC-004]
- [ ] CHK011 — La borne de 30s pour le pipeline complet (SC-007) est-elle réaliste sachant que SC-001 (10s) + SC-003 (3s) + SC-004 (5s) = 18s de pipeline pur, laissant seulement 12s d'overhead pour démarrage containers et nettoyage ? [Measurability, Spec §SC-007]
- [ ] CHK012 — Les SLAs de performance (10s, 3s, 5s, 30s) sont-ils définis comme seuils absolus (tout dépassement = échec) ou comme P95 (5% de tolérance) ? [Clarity, Spec §SC-001 à SC-007]
- [ ] CHK013 — L'AC de concurrence (US-1 scénario 4 : "exactement l'une SUCCESS et l'autre DUPLICATE") spécifie-t-il si les deux requêtes doivent être des threads simultanés réels ou simplement soumises séquentiellement à < 100ms d'intervalle ? [Clarity, Spec §US-1 scénario 4]
- [ ] CHK014 — Le comportement fail-open en cas d'indisponibilité Redis (US-4 scénario 2) est-il spécifié pour tous les endpoints rate-limités ou seulement pour l'endpoint d'upload utilisé dans le test ? [Completeness, Spec §US-4]
- [ ] CHK015 — Les 5 suites de tests peuvent-elles s'exécuter dans n'importe quel ordre — la spec garantit-elle l'indépendance complète sans pré-requis d'ordre ? [Completeness, Spec §FR-009]

---

## Setup Testcontainers (risque infrastructure)

- [ ] CHK016 — Le temps de démarrage de ClamAV (typiquement 1–3 min pour charger les signatures de virus) est-il pris en compte dans les `withStartupTimeout` documentés dans `AbstractIntegrationSpec` ? [Gap, quickstart.md §Durées attendues]
- [ ] CHK017 — Les valeurs `withStartupTimeout` pour chaque container (PostgreSQL, Redis, ClamAV) sont-elles spécifiées dans le plan ou la recherche ? [Gap, plan.md]
- [ ] CHK018 — La fonctionnalité `.withReuse(true)` de Testcontainers nécessite-t-elle un fichier `~/.testcontainers.properties` avec `testcontainers.reuse.enable=true` — est-ce documenté dans `quickstart.md` ? [Gap, research.md §Décision 3]
- [ ] CHK019 — L'extension `pgvector` est-elle pré-installée dans l'image `pgvector/pgvector:pg16` ou doit-elle être activée via un script SQL au démarrage du container ? [Ambiguity, research.md §Décision 3]
- [ ] CHK020 — Le schéma de base de données (tables, index pgvector) est-il créé via Flyway/Liquibase automatiquement au démarrage de `@SpringBootTest`, ou nécessite-t-il un script d'initialisation dédié ? [Gap, plan.md]
- [ ] CHK021 — La configuration ClamAV en mode INSTREAM (protocole socket sur port 3310) est-elle compatible avec l'image `clamav/clamav:latest` — ou nécessite-t-elle une image spécifique avec le daemon configuré ? [Ambiguity, research.md §Décision 3]

---

## Isolation et Nettoyage des Données (risque déterminisme)

- [ ] CHK022 — La séquence de `@BeforeEach` (suppression documents → flush Redis → reset WireMock) est-elle ordonnée pour éviter les dépendances entre étapes — e.g., flush Redis avant de supprimer les embeddings cacheés ? [Clarity, research.md §Décision 4]
- [ ] CHK023 — L'endpoint `DELETE /api/files` supprime-t-il TOUS les documents de tous les tenants, ou seulement ceux créés dans la session courante — ce comportement est-il spécifié dans `contracts/api-endpoints.md` ? [Ambiguity, contracts/api-endpoints.md]
- [ ] CHK024 — `RateLimitIntegrationSpec` utilise des buckets Redis avec TTL de 60s — le nettoyage `@BeforeEach` (FLUSHALL) efface-t-il les buckets des tests précédents sans interférer avec les autres specs en cours ? [Coverage, Spec §Assumptions]
- [ ] CHK025 — L'isolation entre `IngestionPipelineIntegrationSpec` et `RetrievalPipelineIntegrationSpec` est-elle garantie — `RetrievalPipelineIntegrationSpec` peut-il dépendre d'un document ingéré par la suite précédente ? [Coverage, Spec §FR-009]
- [ ] CHK026 — Le nettoyage des données de test antivirus (fichier EICAR, fichiers temporaires) est-il inclus dans la stratégie `@BeforeEach`/`@AfterEach` ? [Gap, Spec §US-1 scénario 3]

---

## Cohérence des SLAs (risque tests flaky)

- [ ] CHK027 — La borne de 10s pour l'ingestion (SC-001) est-elle cohérente avec le budget total de 3 min pour la suite complète — 5 formats × 10s = 50s d'ingestion seule, laissant ~130s pour les 4 autres specs ? [Consistency, Spec §SC-001 vs constitution.md §Performance budget]
- [ ] CHK028 — La borne de 3 min pour la suite complète (constitution.md) est-elle cohérente avec les durées estimées dans `quickstart.md` (45+20+15+25+30 = 135s ≈ 2m15s en réexécution, mais 5 min à froid) ? [Consistency, constitution.md vs quickstart.md]
- [ ] CHK029 — Les SLAs de latence définis dans `spec.md` sont-ils alignés avec les timeouts configurés dans `application.yml` (timeout par retriever = 3s en Phase 3) ? [Consistency, Spec §SC-003 vs nexrag-test-plan-speckit.md §Phase 3 FR-9.5]

---

## Qualité des Contrats WireMock (risque assertions non-déterministes)

- [ ] CHK030 — Le vecteur d'embedding stub (1536 × 0.1) produit-il une similarité cosinus > 0.7 avec lui-même — soit le seuil minimum du `TextVectorRetriever` — garantissant que les assertions de retrieval passent ? [Completeness, contracts/api-endpoints.md §Stub 1]
- [ ] CHK031 — Le format SSE du stub chat (structure `data: {"choices":[...]}`) est-il conforme au format exact attendu par `OpenAiStreamingClient` tel qu'implémenté et testé en Phase 4 ? [Consistency, contracts/api-endpoints.md §Stub 2 vs Phase 4 specs]
- [ ] CHK032 — Les codes HTTP de retour DUPLICATE (`409`) et REJECTED (`400`) dans `contracts/api-endpoints.md` sont-ils cohérents avec les codes définis dans les specs Phase 7 (`MultimodalIngestionControllerSpec`) ? [Consistency, contracts/api-endpoints.md vs Phase 7]
- [ ] CHK033 — L'endpoint `DELETE /api/files` utilisé pour le nettoyage inter-tests existe-t-il dans le controller de production, ou doit-il être créé spécifiquement pour les tests d'intégration ? [Gap, contracts/api-endpoints.md]

---

## Couverture des Cas Limites (risque omissions spec)

- [ ] CHK034 — L'edge case "ClamAV indisponible pendant l'ingestion" (Spec §Edge Cases) est-il spécifié avec un AC explicite — bloqué ou fail-open — ou reste-t-il non résolu ? [Gap, Spec §Edge Cases]
- [ ] CHK035 — L'edge case "service IA retourne une erreur en milieu de flux" (Spec §Edge Cases) a-t-il un AC dans US-3 scénario 2 — est-il suffisamment précis pour être implémenté comme `@Test` ? [Clarity, Spec §US-3 + Edge Cases]
- [ ] CHK036 — L'ingestion d'un DOCX nécessitant LibreOffice (`LibreOfficeConverter`) est-elle couverte par les requirements — LibreOffice est-il disponible dans l'environnement CI ou l'image de test ? [Gap, Spec §FR-001 + plan.md]
- [ ] CHK037 — La configuration JaCoCo dans `pom.xml` exclut-elle potentiellement le package `integration/` — les tests d'intégration contribuent-ils effectivement aux 80% de couverture requis ? [Gap, plan.md §Constitution Check IV]

## Notes

- Cocher chaque item avec `[x]` une fois validé
- Annoter les `[Gap]` découverts avec une référence au fichier à mettre à jour
- Les items CHK016 (ClamAV startup) et CHK018 (.testcontainers.properties) sont des bloqueurs probables en CI — à traiter en priorité
- Si CHK033 révèle que `DELETE /api/files` n'existe pas → ajouter une tâche dans `/speckit-tasks` pour le créer
