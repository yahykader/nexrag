# Quickstart — PHASE 9 : Tests d'Intégration NexRAG

**Feature**: 011-phase9-integration-tests  
**Date**: 2026-04-06

---

## Prérequis

- Docker Desktop en cours d'exécution (pour Testcontainers)
- Java 21 installé
- Maven Wrapper (`./mvnw`) disponible dans `nex-rag/`
- Connexion internet au premier lancement (pull des images Docker)

---

## Lancer la suite d'intégration complète

```bash
cd nex-rag/
./mvnw test -Dtest="*IntegrationSpec" -Dspring.profiles.active=integration-test
```

Durée attendue : < 10 minutes (premier run inclut le pull des images Docker).

---

## Lancer une classe individuelle

```bash
# Pipeline d'ingestion uniquement
./mvnw test -Dtest=IngestionPipelineIntegrationSpec

# Pipeline RAG complet
./mvnw test -Dtest=FullRagPipelineIntegrationSpec

# Rate limiting
./mvnw test -Dtest=RateLimitIntegrationSpec
```

---

## Exécuter avec rapport de couverture

```bash
./mvnw verify -Dtest="*IntegrationSpec" -Dspring.profiles.active=integration-test
# Rapport disponible : target/site/jacoco/index.html
```

---

## Structure des fichiers créés par cette phase

```
nex-rag/src/test/
├── java/com/exemple/nexrag/service/rag/integration/
│   ├── AbstractIntegrationSpec.java
│   ├── IngestionPipelineIntegrationSpec.java
│   ├── RetrievalPipelineIntegrationSpec.java
│   ├── StreamingPipelineIntegrationSpec.java
│   ├── RateLimitIntegrationSpec.java
│   └── FullRagPipelineIntegrationSpec.java
│
└── resources/
    ├── application-integration-test.yml
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

---

## Vérification rapide de l'environnement

```bash
# Vérifier que Docker répond
docker info

# Vérifier que les images sont accessibles
docker pull pgvector/pgvector:pg16
docker pull redis:7-alpine
docker pull clamav/clamav:latest
```

---

## Résolution des problèmes courants

| Problème | Cause probable | Solution |
|----------|---------------|---------- |
| `Could not find a valid Docker environment` | Docker non démarré | Démarrer Docker Desktop |
| `ClamAV container health check failed` | Image lente à démarrer | Augmenter le `withStartupTimeout` dans `AbstractIntegrationSpec` |
| `Connection refused` sur PostgreSQL | Port 5432 déjà utilisé | Testcontainers utilise un port aléatoire — vérifier que `@DynamicPropertySource` est bien actif |
| `429 Too Many Requests` inattendu dans les tests | Rate limit Redis persisté d'un run précédent | Vider les clés Redis de test (`KEYS "nexrag:test:*"`) ou relancer les conteneurs |
| Tests WireMock échouent avec `no stub found` | Stub non enregistré | Vérifier que `@RegisterExtension` est dans `AbstractIntegrationSpec` et que les fichiers JSON sont dans `wiremock/` |
