# Research: PHASE 9 — Tests d'Intégration

**Date**: 2026-05-07 | **Branch**: `009-phase-09-integration`

## Décision 1 — Dépendances Maven manquantes

**Problème** : `pom.xml` a `testcontainers:postgresql:1.19.7` et `wiremock-jre8-standalone:2.35.2` mais manque le core Testcontainers, l'extension JUnit 5, et Awaitility.

**Décision** : Ajouter 3 dépendances `<scope>test</scope>` dans `pom.xml` :

```xml
<!-- Testcontainers core + JUnit 5 extension (@Testcontainers, @Container) -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.7</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.19.7</version>
    <scope>test</scope>
</dependency>

<!-- Awaitility — assertions asynchrones (streaming SSE, progress WebSocket) -->
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <version>4.2.1</version>
    <scope>test</scope>
</dependency>
```

**Rationale** : Sans `testcontainers:testcontainers`, `GenericContainer` (Redis, ClamAV) n'est pas disponible. Sans `junit-jupiter`, `@Testcontainers` et `@Container` ne sont pas détectés par JUnit 5. Awaitility est indispensable pour les assertions sur les flux SSE asynchrones.

**Alternatives rejetées** : Embedded Redis (`it.ozimov:embedded-redis`) — rejeté par la constitution Principe V (un Redis in-memory n'est pas un substitut acceptable à un vrai `redis:7`).

---

## Décision 2 — Profil Spring pour les tests d'intégration

**Problème** : `application-test.properties` désactive l'antivirus (`antivirus.enabled=false`) pour les tests unitaires. Les tests d'intégration Phase 9 ont besoin d'un vrai ClamAV Testcontainers.

**Décision** : Créer un profil dédié `application-integration-test.yml` activé via `@ActiveProfiles("integration-test")` dans `AbstractIntegrationSpec`. Ce profil :
- Active l'antivirus (`antivirus.enabled=true`)
- Pointe `antivirus.host/port` vers le container ClamAV via `@DynamicPropertySource`
- Pointe `spring.datasource.url` vers le container PostgreSQL
- Pointe `spring.redis.host/port` vers le container Redis
- Pointe `openai.chat.api.url` (ou équivalent) vers WireMock

**Rationale** : Évite de modifier `application.yml` de production. Le profil `integration-test` est activé uniquement sur les classes héritant d'`AbstractIntegrationSpec`.

**Alternatives rejetées** : `@TestPropertySource` inline par classe — trop verbeux, pas centralisé; `application-test.yml` partagé — casserait les tests unitaires qui désactivent l'antivirus.

---

## Décision 3 — Pattern AbstractIntegrationSpec (containers partagés)

**Problème** : Démarrer 4 containers (PostgreSQL, Redis, ClamAV, WireMock) par classe = ~4 × 30 s = 2+ minutes overhead. La constitution impose < 3 min pour la suite complète.

**Décision** : `AbstractIntegrationSpec` déclare les containers comme `static` avec scope `@BeforeAll` (via `@Testcontainers` + `@Container`). Testcontainers partage les containers entre classes via le **Singleton Container Pattern** (containers réutilisables).

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
@ActiveProfiles("integration-test")
public abstract class AbstractIntegrationSpec {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("nexrag_test")
            .withReuse(true);

    @Container
    static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withReuse(true);

    @Container
    static final GenericContainer<?> CLAMAV =
        new GenericContainer<>("clamav/clamav:latest")
            .withExposedPorts(3310)
            .withReuse(true);

    @RegisterExtension
    static WireMockExtension OPEN_AI_MOCK = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.redis.host", REDIS::getHost);
        registry.add("spring.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("antivirus.host", CLAMAV::getHost);
        registry.add("antivirus.port", () -> CLAMAV.getMappedPort(3310));
        // Surcharger l'URL OpenAI pour pointer vers WireMock
        registry.add("openai.base-url", () -> OPEN_AI_MOCK.baseUrl());
    }
}
```

**Rationale** : Le Singleton Pattern Testcontainers avec `.withReuse(true)` réduit le démarrage de 4 × 30 s à 1 × 30 s (containers partagés entre les 5 classes). Respecte la contrainte de performance < 3 min.

**Alternatives rejetées** : Containers par classe — trop lent. Embedded infra (H2, fake Redis) — interdit par constitution Principe V.

---

## Décision 4 — Isolation des données entre tests

**Problème** : Les 5 classes de tests partagent la même infrastructure. Sans nettoyage, les données d'un test contaminent les suivants (violation FR-009 et SC-008).

**Décision** : Chaque `@BeforeEach` appelle les endpoints REST de suppression de l'application pour vider les documents ingérés (`DELETE /api/files`), purger le cache Redis (via `RedisTemplate.getConnectionFactory().getConnection().flushAll()`), et réinitialiser les stubs WireMock (`OPEN_AI_MOCK.resetAll()`).

**Rationale** : Utiliser l'API de l'application pour le nettoyage (pas de JDBC direct) respecte le Principe II (SOLID — DIP : les tests passent par les interfaces déclarées, pas par les dépôts directs). `flushAll()` Redis est acceptable car les containers sont dédiés aux tests.

---

## Décision 5 — Fixtures de documents de test

**Problème** : Les fixtures doivent être petites (pour la performance) et contenir du texte indexable (pour que les tests de retrieval fonctionnent).

**Décision** :
- `sample.pdf` (2 pages) : contenu : "NexRAG est un système RAG multimodal. Il supporte l'ingestion de documents PDF, DOCX, XLSX, images et texte." — contenu délibérément simple pour des assertions de retrieval déterministes.
- `sample.docx` : 1 page, même contenu adapté Word.
- `sample.xlsx` : 1 feuille "Données", colonnes "Système", "Type", "Technologie" avec 5 lignes.
- `sample.jpg` : image JPEG 100×100 px, placeholder pour tester `ImageIngestionStrategy`.
- `sample.txt` : même contenu texte brut que `sample.pdf`.

**Rationale** : Un contenu répété et prévisible ("NexRAG", "RAG", "multimodal") garantit que les assertions de retrieval (`assertThat(passages).hasSizeGreaterThanOrEqualTo(3)`) passent de façon déterministe, car le texte est directement retrouvable par recherche vectorielle.

---

## Décision 6 — Stubbing WireMock pour OpenAI

**Problème** : `OpenAiStreamingClient` et `OpenAiEmbeddingService` appellent l'API OpenAI. En intégration, ces appels doivent être simulés.

**Décision** : Enregistrer deux stubs WireMock dans `AbstractIntegrationSpec.@BeforeEach` :

1. **Stub embeddings** (`POST /v1/embeddings`) : retourne un vecteur de 1536 float à 0.1 (valide pour pgvector).
2. **Stub chat/stream** (`POST /v1/chat/completions`) : retourne un flux SSE avec 3 tokens (`"NexRAG"`, `" est"`, `" disponible"`) puis `[DONE]`.

**Rationale** : Des réponses simplifiées mais structurellement valides permettent aux assertions de retrieval et streaming d'être déterministes. Le stub embeddings avec des vecteurs uniformes garantit que la recherche cosinus retourne des résultats (similitude non nulle).
