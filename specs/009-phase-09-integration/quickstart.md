# Quickstart: PHASE 9 — Tests d'Intégration

**Date**: 2026-05-07 | **Branch**: `009-phase-09-integration`

## Prérequis

| Outil | Version minimum | Vérification |
|-------|----------------|-------------|
| Docker Desktop | 24+ | `docker --version` |
| Java | 21+ | `java -version` |
| Maven Wrapper | inclus dans le projet | `./mvnw --version` |

> Docker doit être **démarré** avant de lancer les tests — Testcontainers démarre les containers automatiquement.

---

## Lancer tous les tests d'intégration

```bash
# Depuis D:\Formation-DATA-2024\IA-Genrative\TP\NexRAG\nex-rag\

./mvnw test -Dtest="*IntegrationSpec"
```

ou en filtrant uniquement le package `integration` :

```bash
./mvnw test -Dtest="com.exemple.nexrag.service.rag.integration.*"
```

---

## Lancer une seule suite

```bash
# Pipeline d'ingestion uniquement
./mvnw test -Dtest="IngestionPipelineIntegrationSpec"

# Pipeline RAG complet
./mvnw test -Dtest="FullRagPipelineIntegrationSpec"

# Rate limiting
./mvnw test -Dtest="RateLimitIntegrationSpec"
```

---

## Lancer avec rapport de couverture

```bash
./mvnw verify -Dtest="*IntegrationSpec" jacoco:report
# Rapport généré dans : target/site/jacoco/index.html
```

---

## Lancer UNIQUEMENT les tests unitaires (sans intégration)

```bash
./mvnw test -Dexclude="**/*IntegrationSpec.java"
```

---

## Durées attendues

| Suite | Première exécution (cold) | Réexécutions (containers réutilisés) |
|-------|--------------------------|--------------------------------------|
| `IngestionPipelineIntegrationSpec` | ~45 s | ~15 s |
| `RetrievalPipelineIntegrationSpec` | ~20 s | ~8 s |
| `StreamingPipelineIntegrationSpec` | ~15 s | ~5 s |
| `RateLimitIntegrationSpec` | ~25 s | ~10 s |
| `FullRagPipelineIntegrationSpec` | ~30 s | ~12 s |
| **Suite complète** | **~2–3 min** | **~1 min** |

> Le Singleton Container Pattern (`.withReuse(true)`) permet de réutiliser les containers entre runs successifs sur la même machine — réduction significative du temps sur les cycles de développement.

---

## Variables d'environnement (optionnel)

Les tests d'intégration n'ont **pas besoin** de variables d'environnement — toute l'infrastructure est gérée par Testcontainers. Pour forcer la réinitialisation des containers :

```bash
# Stopper et supprimer les containers Testcontainers réutilisables
docker stop $(docker ps -q --filter "label=org.testcontainers.reuse=true") 2>/dev/null
```

---

## Diagnostic en cas d'échec

| Symptôme | Cause probable | Solution |
|----------|---------------|---------|
| `Could not find a valid Docker environment` | Docker non démarré | Démarrer Docker Desktop |
| `Connection refused` sur port pgvector | Container PostgreSQL pas encore prêt | Vérifier `waitingFor` dans `AbstractIntegrationSpec` |
| Tests de retrieval retournent 0 passages | Vecteurs WireMock embedding malformés | Vérifier le stub `/v1/embeddings` retourne un vecteur de dimension 1536 |
| `429` sur premier appel rate limit | Quota résiduel d'un test précédent | Ajouter `@BeforeEach` qui flush Redis (`FLUSHALL`) |
| ClamAV `Connection timed out` | Container ClamAV pas encore initialisé | Augmenter `withStartupTimeout` dans `AbstractIntegrationSpec` |

---

## Structure des logs de test

Les logs de test intégration suivent le format production (emoji + niveau) :

```
[INFO] 📄 Démarrage ingestion : sample.pdf (batchId=test-batch-001)
[INFO] ✅ Ingestion réussie : 3 chunks, 3 embeddings stockés en 2341 ms
[INFO] 🔄 Test isolation : nettoyage avant IngestionPipelineIntegrationSpec#test2
```
