# Research: PHASE 9 — Tests d'Intégration NexRAG

**Feature**: 011-phase9-integration-tests  
**Date**: 2026-04-06  
**Status**: Complete — all NEEDS CLARIFICATION resolved

---

## Decision 1: Stratégie de partage des conteneurs Testcontainers

**Decision**: Conteneurs partagés au niveau de la classe de test via des champs `static @Container`, démarrés une seule fois par classe et arrêtés après tous les tests de la classe. Une classe utilitaire `AbstractIntegrationSpec` (base class) centralise la déclaration des conteneurs communs (PostgreSQL, Redis, ClamAV) avec `@Testcontainers` et les expose aux sous-classes via des méthodes `@DynamicPropertySource`.

**Rationale**: Démarrer ClamAV prend 15–30 secondes selon l'image. Un démarrage par méthode ou par classe non partagée ferait exploser la durée de la suite. Le pattern `static @Container` garantit un démarrage unique par JVM de test (si `@TestcontainersLifecycle` ou `Testcontainers.SINGLETON_CONTAINERS` est activé). En cas de JUnit Parallel Execution, les conteneurs statiques sont partagés entre threads automatiquement.

**Alternatives considérées**:
- Testcontainers Singleton Pattern via `static { ... }` dans une classe `ContainerConfig`: plus de contrôle mais plus fragile, nécessite des workarounds pour le `@DynamicPropertySource`.
- Conteneur par méthode de test: rejeté — trop lent (×N démarrages ClamAV).
- Docker Compose dans les tests: rejeté — couplage environnemental, non reproductible en CI sans Docker Compose disponible.

---

## Decision 2: Versions des images Testcontainers

**Decision**:
- PostgreSQL/pgvector: `pgvector/pgvector:pg16` (constitution V, conforme)
- Redis: `redis:7-alpine` (constitution V, version allégée)
- ClamAV: `clamav/clamav:latest` (tel que défini dans le test plan US-22)

**Rationale**: Les versions sont prescrites par la constitution (Principle V) et le test plan. `redis:7-alpine` est préféré à `redis:7` pour réduire le temps de téléchargement en CI. ClamAV `latest` est acceptable ici car on ne valide pas la détection de signatures spécifiques mais le comportement du pipeline.

**Alternatives considérées**:
- `redis:7` (non-alpine): rejeté — image plus grande sans bénéfice fonctionnel.
- Embedded Redis (Lettuce fakes): rejeté explicitement par la constitution (Principle V).

---

## Decision 3: Stub de l'API OpenAI (embeddings + complétion)

**Decision**: WireMock Extension (`WireMockExtension`) partagée en champ `static` dans `AbstractIntegrationSpec`. Elle intercepte les appels POST vers `/v1/embeddings` et `/v1/chat/completions`. Les réponses stub sont définies en JSON dans `src/test/resources/wiremock/` (un fichier par scénario).

**Rationale**: WireMock est déjà dans le pom.xml (`wiremock-jre8-standalone` 2.35.2). Le stub centralisé évite la duplication dans chaque classe. Les appels réels à l'API OpenAI sont interdits par la constitution (Principle V) et coûteux.

**Stub responses**:
- `/v1/embeddings` → vecteur de dimension 1536, valeurs constantes (ex: `[0.1, 0.2, ...]`), 200 OK
- `/v1/chat/completions` (streaming SSE) → séquence de `data:` chunks simulant un token stream avant `data: [DONE]`
- `/v1/chat/completions` (non-streaming) → réponse JSON complète avec un message `choices[0].message.content`

**Alternatives considérées**:
- `MockServer` (netty-based): rejeté — WireMock déjà présent, même API, pas de raison d'ajouter une dépendance.
- Mockito sur `OpenAiChatModel`: rejeté — bypasserait la couche HTTP et ne validerait pas la sérialisation/désérialisation réelle.

---

## Decision 4: Fichier de test antivirus (EICAR)

**Decision**: Utiliser le fichier EICAR standard (`eicar.com`) comme fixture de test virus. Ce fichier est une chaîne ASCII universellement reconnue par ClamAV comme virus de test (`Eicar-Signature`), sans être un vrai malware.

**Content**: `X5O!P%@AP[4\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*`

**Rationale**: C'est la méthode standard pour tester les scanners antivirus sans risque. ClamAV avec les signatures à jour le détecte systématiquement. Le fichier fait 68 bytes — aucun impact sur la durée du test.

**Emplacement**: `src/test/resources/fixtures/virus/eicar.com`

**Alternatives considérées**:
- Fichier PDF modifié avec un payload: rejeté — les signatures ClamAV évoluent, la détection n'est pas garantie.
- Mock de ClamAvSocketClient: rejeté — on est en tests d'intégration, le vrai daemon doit être exercé.

---

## Decision 5: Assertion du streaming SSE

**Decision**: Utiliser `Awaitility` + `WebTestClient` (Spring WebFlux test client) ou `MockMvc` avec `ResultActions` pour capturer le flux SSE. Pour les scénarios de streaming, on valide que le premier chunk arrive dans un délai configurable (< 3s) en attendant les événements `data:` sur le flux réponse.

**Rationale**: Le streaming SSE est un flux asynchrone. `Awaitility` permet d'écrire des assertions temporelles lisibles sans `Thread.sleep`. `WebTestClient` supporte nativement les flux SSE via `returnResult(String.class)`. Si le serveur est configuré en mode `RANDOM_PORT` avec `@SpringBootTest`, `WebTestClient` se lie au port réel.

**Dépendance requise**: `awaitility` — à ajouter dans `pom.xml` si absent (vérification nécessaire).

**Alternatives considérées**:
- `reactor-test` + `StepVerifier`: adapté aux `Flux<>` internes mais pas aux endpoints HTTP SSE réels.
- `MockMvc` seul: limité pour les flux asynchrones longue durée.

---

## Decision 6: Gestion du nettoyage des données entre classes

**Decision**: Chaque classe de test hérite de `AbstractIntegrationSpec` et implémente un `@BeforeAll` / `@AfterAll` pour tronquer les tables PostgreSQL pertinentes (`document_embeddings`, `documents`) et vider les clés Redis du namespace de test (`KEYS "nexrag:test:*"`). Les namespaces Redis de test sont préfixés avec `nexrag:test:` pour éviter les collisions.

**Rationale**: Les conteneurs étant partagés (Decision 1), chaque classe doit repartir d'un état propre sans arrêter/redémarrer les conteneurs. La truncation SQL est plus rapide qu'un redémarrage de conteneur.

**Alternatives considérées**:
- `@DirtiesContext` entre classes: rejeté — recharge le contexte Spring, très lent.
- Schema par classe (chaque classe dans son propre schema PostgreSQL): rejeté — trop complexe, nécessite une reconfiguration de LangChain4j pour chaque schema.

---

## Decision 7: Budget temps et parallélisation

**Decision**: La suite doit s'exécuter en moins de 10 minutes. Pour atteindre cet objectif avec 4 formats × pipeline complet, les classes `IngestionPipelineIntegrationSpec`, `RetrievalPipelineIntegrationSpec`, `StreamingPipelineIntegrationSpec`, `RateLimitIntegrationSpec`, et `FullRagPipelineIntegrationSpec` sont exécutées séquentiellement (pas de parallélisation JUnit) pour éviter les conflits sur les conteneurs partagés. Le démarrage ClamAV (~20s) + PostgreSQL (~5s) + Redis (~2s) ne compte qu'une fois.

**Justification du dépassement de la constitution (3 min → 10 min)**: Le budget de 3 minutes de la constitution a été défini pour un scope PDF-uniquement. L'expansion à 4 formats (PDF, DOCX, XLSX, image) multiplie le nombre de scénarios d'ingestion. ClamAV est intrinsèquement lent au démarrage. 10 minutes est le budget négocié (clarification session 2026-04-06).

**Alternatives considérées**:
- Parallélisation JUnit 5: rejetée — les conteneurs partagés ne sont pas thread-safe sans configuration avancée.
- Limiter à 3 formats: rejeté — la décision de couverture maximale est actée (clarification Q4).

---

## Decision 8: Profil de configuration dédié aux tests d'intégration

**Decision**: Créer `src/test/resources/application-integration-test.yml` activé par `@ActiveProfiles("integration-test")`. Ce profil override les URLs des services (PostgreSQL, Redis, OpenAI base URL) avec les valeurs fournies par les `@DynamicPropertySource` de Testcontainers. Il désactive les circuit breakers (ou réduit leurs seuils) pour éviter les faux positifs dans les tests.

**Rationale**: Le profil `test` existant (`application-test.properties`) est configuré pour `@WebMvcTest` avec `antivirus.enabled=false`. Les tests d'intégration nécessitent antivirus activé (ClamAV réel) et des URLs dynamiques. Un profil séparé évite les conflits.

**Alternatives considérées**:
- Réutiliser `application-test.properties`: rejeté — `antivirus.enabled=false` est incompatible avec les tests d'intégration antivirus.
- Tout dans `@DynamicPropertySource` sans profil: rejeté — les propriétés non-URL (circuit breakers, thresholds) ne peuvent pas être gérées ainsi.
