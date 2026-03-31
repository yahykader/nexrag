# Quickstart — Phase 4 : Streaming Tests

**Branch**: `004-phase-4-streaming` | **Date**: 2026-03-30

Guide de démarrage rapide pour implémenter et exécuter les tests unitaires de la Phase 4.

---

## Prérequis

```bash
# Vérifier Java 21
java --version  # openjdk 21.x

# Depuis nex-rag/
cd /d/Formation-DATA-2024/IA-Genrative/TP/NexRAG/nex-rag
```

## Dépendances de test requises

Vérifier que `pom.xml` contient (scope `test`) :

```xml
<!-- JUnit 5 + Mockito + AssertJ — fournis par Spring Boot BOM -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- WireMock pour OpenAiStreamingClientSpec -->
<dependency>
    <groupId>com.github.tomakehurst</groupId>
    <artifactId>wiremock-jre8</artifactId>
    <scope>test</scope>
</dependency>
```

---

## Structure cible des tests

```
src/test/java/com/exemple/nexrag/service/rag/streaming/
├── ConversationManagerSpec.java
├── EventEmitterSpec.java
├── StreamingOrchestratorSpec.java
└── openai/
    └── OpenAiStreamingClientSpec.java
```

---

## Squelette minimal par classe

### `ConversationManagerSpec.java`

```java
@DisplayName("Spec : ConversationManager — Persistance Redis des conversations")
@ExtendWith(MockitoExtension.class)
class ConversationManagerSpec {

    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock ObjectMapper objectMapper;
    @InjectMocks ConversationManager manager;

    @BeforeEach
    void setup() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("DOIT créer une conversation avec userId et ID unique")
    void shouldCreateConversationWithUserIdAndUniqueId() { ... }

    @Test
    @DisplayName("DOIT tronquer l'historique quand messages dépasse MAX_MESSAGES")
    void shouldTruncateHistoryWhenMaxMessagesExceeded() { ... }

    @Test
    @DisplayName("DOIT retourner Optional.empty() pour une conversation inexistante")
    void shouldReturnEmptyForUnknownConversation() { ... }

    @Test
    @DisplayName("DOIT lever IllegalArgumentException quand conversation introuvable à addUserMessage")
    void shouldThrowWhenAddingMessageToUnknownConversation() { ... }
}
```

### `EventEmitterSpec.java`

```java
@DisplayName("Spec : EventEmitter — Emission SSE et gestion des sessions")
@ExtendWith(MockitoExtension.class)
class EventEmitterSpec {

    private EventEmitter emitter;
    private SseEmitter sseEmitter;

    @BeforeEach
    void setup() {
        emitter = new EventEmitter();
        sseEmitter = spy(new SseEmitter(Long.MAX_VALUE));
        emitter.registerSSE("session-test", sseEmitter);
    }

    @AfterEach
    void teardown() {
        emitter.complete("session-test");
    }

    @Test
    @DisplayName("DOIT émettre un événement TOKEN pour chaque token reçu")
    void shouldEmitTokenEvent() throws Exception { ... }

    @Test
    @DisplayName("DOIT supprimer l'emitter de la session après complete()")
    void shouldRemoveEmitterAfterComplete() { ... }

    @Test
    @DisplayName("DOIT ignorer l'émission si la session est inconnue")
    void shouldIgnoreEmitForUnknownSession() { ... }
}
```

### `StreamingOrchestratorSpec.java`

```java
@DisplayName("Spec : StreamingOrchestrator — Pipeline RAG → historique → génération")
@ExtendWith(MockitoExtension.class)
class StreamingOrchestratorSpec {

    @Mock RetrievalAugmentorOrchestrator retrievalAugmentor;
    @Mock ConversationManager conversationManager;
    @Mock EventEmitter eventEmitter;
    @Mock StreamingChatLanguageModel streamingModel;
    @Mock RAGMetrics ragMetrics;
    @InjectMocks StreamingOrchestrator orchestrator;

    @Test
    @DisplayName("DOIT injecter le contexte RAG avant de démarrer la génération")
    void shouldInjectRagContextBeforeGeneration() throws Exception {
        // Capturer le StreamingResponseHandler via ArgumentCaptor
        // Déclencher onNext(token) puis onComplete()
        // Vérifier l'ordre d'appels via inOrder()
    }

    @Test
    @DisplayName("DOIT sauvegarder la réponse complète dans l'historique à réception de DONE")
    void shouldSaveCompleteResponseOnDone() throws Exception { ... }

    @Test
    @DisplayName("DOIT émettre ERROR sans exception propagée en cas d'erreur du modèle")
    void shouldEmitErrorWithoutPropagatingException() throws Exception { ... }
}
```

### `OpenAiStreamingClientSpec.java`

```java
@DisplayName("Spec : OpenAiStreamingClient — Streaming SSE depuis l'API OpenAI")
class OpenAiStreamingClientSpec {

    private WireMockServer wireMock;
    private OpenAiStreamingClient client;

    @BeforeEach
    void setup() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();

        StreamingConfig config = mock(StreamingConfig.class);
        StreamingConfig.OpenAiConfig openAiConfig = mock(StreamingConfig.OpenAiConfig.class);
        when(config.getOpenAi()).thenReturn(openAiConfig);
        when(openAiConfig.getApiUrl()).thenReturn("http://localhost:" + wireMock.port());
        when(openAiConfig.getApiKey()).thenReturn("fake-key");

        client = new OpenAiStreamingClient(config, new ObjectMapper());
    }

    @AfterEach
    void teardown() { wireMock.stop(); }

    @Test
    @DisplayName("DOIT émettre des tokens puis GENERATION_COMPLETE depuis le flux OpenAI")
    void shouldEmitTokensThenGenerationComplete() { ... }

    @Test
    @DisplayName("DOIT émettre ERROR quand l'API retourne une erreur HTTP")
    void shouldEmitErrorOnHttpFailure() { ... }
}
```

---

## Commandes d'exécution

```bash
# Exécuter tous les tests de la phase 4
./mvnw test -Dtest="ConversationManagerSpec,EventEmitterSpec,StreamingOrchestratorSpec,OpenAiStreamingClientSpec"

# Exécuter une classe spécifique
./mvnw test -Dtest=ConversationManagerSpec

# Exécuter une méthode spécifique
./mvnw test -Dtest=ConversationManagerSpec#shouldCreateConversationWithUserIdAndUniqueId

# Rapport de couverture JaCoCo (après test)
./mvnw test jacoco:report
# Rapport HTML : target/site/jacoco/index.html
```

---

## Critères de succès (SC-001 à SC-008)

| SC | Critère | Comment vérifier |
|----|---------|-----------------|
| SC-001 | Couverture ≥80% ligne + branche par classe | Rapport JaCoCo |
| SC-002 | Chaque test ≤500ms | `@Timeout(value=500, unit=MILLISECONDS)` sur chaque `@Test` |
| SC-003 | 100% des AC des US 1, 2, 3 couverts | Tracing AC→méthode dans les `@DisplayName` |
| SC-004 | Tokens émis dans l'ordre de réception | `assertThat(events).extracting(type).containsSubsequence(TOKEN, TOKEN, ...)` |
| SC-005 | DONE (GENERATION_COMPLETE) toujours en dernier dans un flux normal | `assertThat(events).last().extracting(type).isEqualTo(GENERATION_COMPLETE)` |
| SC-006 | Aucune exception propagée sur erreur | `assertThatNoException()` + vérification `emitError()` appelé |
| SC-007 | Troncature déterministe à MAX_MESSAGES | `assertThat(state.getMessages()).hasSize(100)` après ajout du 101ème |
| SC-008 | ERROR émis dans ≤30s sur timeout (simulé) | Mock synchrone avec `CountDownLatch` simulant un timeout |

---

## Convention de commit

```
test(phase-4): add ConversationManagerSpec — création, historique, TTL Redis
test(phase-4): add EventEmitterSpec — émission SSE, buffering, nettoyage session
test(phase-4): add StreamingOrchestratorSpec — pipeline RAG→historique→génération
test(phase-4): add OpenAiStreamingClientSpec — streaming SSE WireMock, erreurs HTTP
```
