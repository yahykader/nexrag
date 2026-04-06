# NexRAG — Plan de Tests Unitaires & Intégration (Spec Kit)

> **Framework** : JUnit 5 · Mockito · Spring Boot Test · Testcontainers · WireMock · AssertJ  
> **Méthodologie** : Spec-Driven Development (github/spec-kit)  
> **Convention** : chaque Phase = un Spec autonome → spécification → critères → classes de tests

---

## Constitution du Projet de Tests

```
RÈGLES GÉNÉRALES (constitution.md)
──────────────────────────────────
- Couverture minimale : 80 % par module (lignes + branches)
- Chaque test doit être isolé, répétable et rapide (< 500 ms pour les unitaires)
- Les tests d'intégration utilisent @SpringBootTest + Testcontainers
- Mocks : Mockito pour les dépendances unitaires, WireMock pour les APIs HTTP externes
- Aucun appel réseau réel dans les tests unitaires
- Nommage : [ClasseTestée]Spec.java pour les unitaires, [Feature]IntegrationSpec.java pour les intégrations
- Organisation : src/test/java/com/exemple/nexrag/service/rag/[dossier]/
- Les specs Spec Kit sont sauvegardées dans .spec/[phase]/[nom].md
```

---

## Vue d'ensemble des Phases

| Phase | Dossier | Type | Priorité |
|-------|---------|------|----------|
| 1 | `ingestion` (util + security + deduplication + cache) | Unitaire | 🔴 Critique |
| 2 | `ingestion` (strategy + analyzer + compression + tracker) | Unitaire | 🔴 Critique |
| 3 | `retrieval` | Unitaire | 🔴 Critique |
| 4 | `streaming` | Unitaire | 🟠 Haute |
| 5 | `voice` + `metrics` | Unitaire | 🟠 Haute |
| 6 | `facade` | Unitaire | 🟠 Haute |
| 7 | `controller` | Unitaire (MockMvc) | 🟡 Moyenne |
| 8 | `interceptor` + `validation` | Unitaire | 🟡 Moyenne |
| 9 | Intégration bout-en-bout | Intégration | 🔴 Critique |

---

---

# PHASE 1 — Ingestion : Utilitaires, Sécurité & Déduplication

## Spec : `phase-01-ingestion-foundation.md`

### User Stories

#### US-1 : Validation et détection de fichiers
> En tant que système d'ingestion, je veux valider et détecter le type de fichier uploadé, afin de rejeter les fichiers invalides avant tout traitement.

**Functional Requirements**
- FR-1.1 : `FileTypeDetector` doit identifier le type MIME réel par magic bytes (pas l'extension)
- FR-1.2 : `FileValidator` doit rejeter les fichiers dépassant la taille limite configurée
- FR-1.3 : `FileValidator` doit rejeter les extensions non autorisées
- FR-1.4 : `MetadataSanitizer` doit supprimer les métadonnées sensibles des fichiers

**Acceptance Criteria**
- AC-1.1 : Un fichier `.txt` renommé en `.pdf` est détecté comme `text/plain`
- AC-1.2 : Un fichier > `maxSize` lève une `FileSizeExceededException`
- AC-1.3 : Une extension `.exe` lève une `InvalidFileTypeException`
- AC-1.4 : Les métadonnées EXIF GPS d'une image sont supprimées après sanitisation

#### US-2 : Déduplication de fichiers
> En tant que système d'ingestion, je veux détecter les fichiers en double, afin d'éviter d'indexer le même contenu plusieurs fois.

**Functional Requirements**
- FR-2.1 : `HashComputer` calcule un hash SHA-256 déterministe du contenu binaire
- FR-2.2 : `DeduplicationService` retourne `true` si le hash existe déjà en store
- FR-2.3 : `DeduplicationStore` persiste et interroge les hashes via Redis

**Acceptance Criteria**
- AC-2.1 : Deux fichiers identiques produisent le même hash
- AC-2.2 : Un fichier déjà ingéré est signalé comme doublon
- AC-2.3 : La vérification est idempotente (appels multiples sans effet de bord)

#### US-3 : Déduplication de texte
> En tant que système d'ingestion, je veux détecter les chunks de texte dupliqués, afin d'éviter les embeddings redondants.

**Functional Requirements**
- FR-3.1 : `TextNormalizer` normalise le texte (lowercase, trim, suppression accents)
- FR-3.2 : `TextDeduplicationService` rejette les chunks déjà vus dans la session
- FR-3.3 : `TextLocalCache` maintient un cache local rapide pour la session courante

**Acceptance Criteria**
- AC-3.1 : "Hello World" et "  hello world  " sont normalisés identiquement
- AC-3.2 : Un chunk déjà vu dans le batch est ignoré sans erreur
- AC-3.3 : Le cache local est purgeable sans affecter le store Redis

#### US-4 : Sécurité antivirus
> En tant que système d'ingestion, je veux scanner chaque fichier avec ClamAV, afin de bloquer les fichiers malveillants.

**Functional Requirements**
- FR-4.1 : `ClamAvSocketClient` envoie le fichier via socket INSTREAM
- FR-4.2 : `ClamAvResponseParser` interprète `OK`, `FOUND` et `ERROR`
- FR-4.3 : `AntivirusGuard` bloque l'ingestion si le scan retourne FOUND
- FR-4.4 : `ClamAvHealthScheduler` vérifie périodiquement la disponibilité ClamAV

**Acceptance Criteria**
- AC-4.1 : Un fichier EICAR (test virus) retourne `VirusFoundException`
- AC-4.2 : Un fichier sain passe sans exception
- AC-4.3 : Si ClamAV est indisponible, `AntivirusGuard` lève `AntivirusUnavailableException`

---

### Classes de Tests à créer — Phase 1

```
src/test/java/com/exemple/nexrag/service/rag/
├── ingestion/
│   ├── util/
│   │   ├── FileTypeDetectorSpec.java
│   │   ├── FileValidatorSpec.java
│   │   ├── MetadataSanitizerSpec.java
│   │   ├── FileUtilsSpec.java
│   │   └── InMemoryMultipartFileSpec.java
│   ├── deduplication/
│   │   ├── file/
│   │   │   ├── HashComputerSpec.java
│   │   │   ├── DeduplicationServiceSpec.java
│   │   │   └── DeduplicationStoreSpec.java
│   │   └── text/
│   │       ├── TextNormalizerSpec.java
│   │       ├── TextDeduplicationServiceSpec.java
│   │       └── TextLocalCacheSpec.java
│   └── security/
│       ├── ClamAvSocketClientSpec.java
│       ├── ClamAvResponseParserSpec.java
│       ├── AntivirusGuardSpec.java
│       └── ClamAvHealthSchedulerSpec.java
```

### Dépendances de Test — Phase 1

```xml
<!-- pom.xml - scope test -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.github.tomakehurst</groupId>
    <artifactId>wiremock-jre8</artifactId>
    <scope>test</scope>
</dependency>
```

### Exemple de Spec JUnit 5 — `FileValidatorSpec.java`

```java
@DisplayName("Spec : FileValidator — Validation des fichiers uploadés")
@ExtendWith(MockitoExtension.class)
class FileValidatorSpec {

    @InjectMocks
    private FileValidator fileValidator;

    @Mock
    private FileValidationProperties properties;

    // US-1 / AC-1.2 — Rejet taille excessive
    @Test
    @DisplayName("DOIT lever FileSizeExceededException quand fichier > maxSize")
    void shouldRejectFileExceedingMaxSize() {
        when(properties.getMaxSizeBytes()).thenReturn(1024L);
        MockMultipartFile bigFile = new MockMultipartFile("file", new byte[2048]);

        assertThatThrownBy(() -> fileValidator.validate(bigFile))
            .isInstanceOf(FileSizeExceededException.class)
            .hasMessageContaining("2048");
    }

    // US-1 / AC-1.3 — Rejet extension interdite
    @Test
    @DisplayName("DOIT lever InvalidFileTypeException pour extension .exe")
    void shouldRejectForbiddenExtension() {
        when(properties.getAllowedExtensions()).thenReturn(List.of("pdf", "docx", "txt"));
        MockMultipartFile exeFile = new MockMultipartFile("file", "malware.exe", "application/octet-stream", new byte[10]);

        assertThatThrownBy(() -> fileValidator.validate(exeFile))
            .isInstanceOf(InvalidFileTypeException.class);
    }
}
```

---

---

# PHASE 2 — Ingestion : Stratégies, Cache & Orchestration

## Spec : `phase-02-ingestion-strategy.md`

### User Stories

#### US-5 : Stratégies d'ingestion par type de fichier
> En tant que système d'ingestion, je veux appliquer une stratégie adaptée à chaque type de fichier, afin d'extraire le texte de manière optimale.

**Functional Requirements**
- FR-5.1 : `PdfIngestionStrategy` extrait le texte de chaque page d'un PDF
- FR-5.2 : `DocxIngestionStrategy` extrait le texte d'un document Word via conversion LibreOffice
- FR-5.3 : `XlsxIngestionStrategy` extrait les données de chaque feuille Excel
- FR-5.4 : `ImageIngestionStrategy` délègue l'extraction à `VisionAnalyzer`
- FR-5.5 : `TextIngestionStrategy` lit le fichier texte brut avec l'encodage détecté
- FR-5.6 : `TikaIngestionStrategy` sert de fallback via Apache Tika
- FR-5.7 : `IngestionConfig` sélectionne la bonne stratégie selon le type MIME

**Acceptance Criteria**
- AC-5.1 : Un PDF de 3 pages produit 3 chunks minimum
- AC-5.2 : Un .docx corrompu lève `IngestionException` avec le nom du fichier
- AC-5.3 : Une image JPEG délègue bien à `VisionAnalyzer` (vérifié par mock)
- AC-5.4 : Un type MIME inconnu utilise `TikaIngestionStrategy` comme fallback

#### US-6 : Chunking et indexation des embeddings
> En tant que système d'ingestion, je veux découper le texte en chunks et indexer leurs embeddings, afin de permettre une recherche vectorielle efficace.

**Functional Requirements**
- FR-6.1 : `TextChunker` découpe en chunks avec chevauchement configurable
- FR-6.2 : `EmbeddingIndexer` appelle le service d'embedding et stocke les vecteurs
- FR-6.3 : `EmbeddingCacheStore` met en cache les embeddings pour éviter les appels API redondants
- FR-6.4 : `EmbeddingCompressor` réduit la dimension des vecteurs si `QuantizationConfig` l'exige

**Acceptance Criteria**
- AC-6.1 : Un texte de 1000 tokens avec chunkSize=200 et overlap=50 produit 6 chunks
- AC-6.2 : Le même texte soumis deux fois n'appelle l'API embedding qu'une seule fois
- AC-6.3 : Le cache Redis est interrogé avant tout appel à l'API OpenAI
- AC-6.4 : La compression vectorielle réduit les vecteurs de 1536 à 256 dimensions

#### US-7 : Tracking et rollback de batch
> En tant que système d'ingestion, je veux tracer les batches d'ingestion et pouvoir les annuler, afin de garantir la cohérence en cas d'erreur.

**Functional Requirements**
- FR-7.1 : `IngestionTracker` enregistre l'état (STARTED, COMPLETED, FAILED) de chaque batch
- FR-7.2 : `RollbackExecutor` supprime les embeddings d'un batch échoué
- FR-7.3 : `BatchInfoRegistry` conserve les métadonnées (fichier, taille, timestamp)
- FR-7.4 : `IngestionOrchestrator` coordonne validation → scan → stratégie → indexation → tracking

**Acceptance Criteria**
- AC-7.1 : Un batch FAILED déclenche `RollbackExecutor.rollback(batchId)`
- AC-7.2 : `IngestionOrchestrator` notifie `ProgressNotifier` à chaque étape
- AC-7.3 : Une exception dans la stratégie arrête l'orchestration et lance le rollback

---

### Classes de Tests à créer — Phase 2

```
src/test/java/com/exemple/nexrag/service/rag/
├── ingestion/
│   ├── strategy/
│   │   ├── PdfIngestionStrategySpec.java
│   │   ├── DocxIngestionStrategySpec.java
│   │   ├── XlsxIngestionStrategySpec.java
│   │   ├── ImageIngestionStrategySpec.java
│   │   ├── TextIngestionStrategySpec.java
│   │   ├── TikaIngestionStrategySpec.java
│   │   └── IngestionConfigSpec.java
│   │   └── commun/
│   │       ├── TextChunkerSpec.java
│   │       ├── EmbeddingIndexerSpec.java
│   │       └── LibreOfficeConverterSpec.java
│   ├── cache/
│   │   ├── EmbeddingCacheStoreSpec.java
│   │   ├── EmbeddingTextHasherSpec.java
│   │   └── EmbeddingSerializerSpec.java
│   ├── compression/
│   │   └── EmbeddingCompressorSpec.java
│   ├── tracker/
│   │   ├── IngestionTrackerSpec.java
│   │   ├── RollbackExecutorSpec.java
│   │   └── BatchInfoRegistrySpec.java
│   ├── analyzer/
│   │   ├── VisionAnalyzerSpec.java
│   │   ├── VisionFallbackGeneratorSpec.java
│   │   ├── ImageConverterSpec.java
│   │   └── ImageSaverSpec.java
│   └── IngestionOrchestratorSpec.java
```

### Exemple de Spec — `TextChunkerSpec.java`

```java
@DisplayName("Spec : TextChunker — Découpage de texte en chunks")
class TextChunkerSpec {

    private final TextChunker chunker = new TextChunker();

    // US-6 / AC-6.1
    @Test
    @DisplayName("DOIT produire le bon nombre de chunks avec overlap")
    void shouldProduceCorrectChunksWithOverlap() {
        String text = "A".repeat(1000);
        List<String> chunks = chunker.chunk(text, 200, 50);

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(6);
        chunks.forEach(c -> assertThat(c.length()).isLessThanOrEqualTo(200));
    }

    @Test
    @DisplayName("DOIT inclure le chevauchement entre chunks consécutifs")
    void shouldIncludeOverlapBetweenConsecutiveChunks() {
        String text = "Hello World ".repeat(100);
        List<String> chunks = chunker.chunk(text, 100, 20);

        // Le début du chunk n+1 doit se trouver dans la fin du chunk n
        String endOfFirst = chunks.get(0).substring(chunks.get(0).length() - 20);
        assertThat(chunks.get(1)).startsWith(endOfFirst);
    }

    @Test
    @DisplayName("DOIT retourner un seul chunk si texte < chunkSize")
    void shouldReturnSingleChunkForShortText() {
        assertThat(chunker.chunk("Bonjour", 500, 50)).hasSize(1);
    }
}
```

---

---

# PHASE 3 — Retrieval

## Spec : `phase-03-retrieval.md`

### User Stories

#### US-8 : Transformation et routing de requêtes
> En tant que système RAG, je veux transformer et router les requêtes utilisateur, afin d'optimiser la pertinence des résultats récupérés.

**Functional Requirements**
- FR-8.1 : `QueryTransformerService` reformule la requête pour améliorer le recall
- FR-8.2 : `QueryRouterService` détermine si la requête doit cibler le texte, les images ou les deux
- FR-8.3 : `RoutingDecision` contient le type de retrieval et la confiance du routing

**Acceptance Criteria**
- AC-8.1 : Une requête courte ("météo") est expansée avec des synonymes
- AC-8.2 : Une requête contenant "image" ou "photo" route vers `ImageVectorRetriever`
- AC-8.3 : Une requête générale route vers les deux retrievers en parallèle

#### US-9 : Récupération parallèle multi-sources
> En tant que système RAG, je veux récupérer des résultats depuis plusieurs sources en parallèle, afin de réduire la latence et maximiser la couverture.

**Functional Requirements**
- FR-9.1 : `TextVectorRetriever` effectue une recherche cosinus dans le store vectoriel
- FR-9.2 : `BM25Retriever` effectue une recherche BM25 (texte sparse)
- FR-9.3 : `ImageVectorRetriever` effectue une recherche dans l'espace d'embedding image
- FR-9.4 : `ParallelRetrieverService` exécute les retrievers configurés en parallèle via CompletableFuture
- FR-9.5 : Le timeout par retriever est configurable (défaut : 3 secondes)

**Acceptance Criteria**
- AC-9.1 : `ParallelRetrieverService` retourne les résultats des deux retrievers fusionnés
- AC-9.2 : Si un retriever timeout, ses résultats sont ignorés sans bloquer les autres
- AC-9.3 : Les résultats sont triés par score décroissant

#### US-10 : Reranking et agrégation
> En tant que système RAG, je veux réordonner et agréger les résultats récupérés, afin de ne garder que les passages les plus pertinents.

**Functional Requirements**
- FR-10.1 : `CrossEncoderReranker` recalcule un score de pertinence query-passage
- FR-10.2 : `ContentAggregatorService` fusionne et déduplique les résultats
- FR-10.3 : `ContentInjectorService` injecte le contexte dans le prompt final
- FR-10.4 : `InjectedPrompt` contient le prompt augmenté avec le contexte RAG

**Acceptance Criteria**
- AC-10.1 : Le reranker réordonne correctement 5 passages (meilleur score en premier)
- AC-10.2 : L'agrégateur supprime les passages avec similarité cosinus > 0.95
- AC-10.3 : Le prompt injecté respecte la limite de tokens configurée

---

### Classes de Tests à créer — Phase 3

```
src/test/java/com/exemple/nexrag/service/rag/
├── retrieval/
│   ├── query/
│   │   ├── QueryTransformerServiceSpec.java
│   │   └── QueryRouterServiceSpec.java
│   ├── retriever/
│   │   ├── TextVectorRetrieverSpec.java
│   │   ├── BM25RetrieverSpec.java
│   │   ├── ImageVectorRetrieverSpec.java
│   │   └── ParallelRetrieverServiceSpec.java
│   ├── reranker/
│   │   └── CrossEncoderRerankerSpec.java
│   ├── aggregator/
│   │   └── ContentAggregatorServiceSpec.java
│   ├── injector/
│   │   └── ContentInjectorServiceSpec.java
│   └── RetrievalAugmentorOrchestratorSpec.java
```

### Exemple de Spec — `ParallelRetrieverServiceSpec.java`

```java
@DisplayName("Spec : ParallelRetrieverService — Récupération parallèle")
@ExtendWith(MockitoExtension.class)
class ParallelRetrieverServiceSpec {

    @Mock private TextVectorRetriever textRetriever;
    @Mock private BM25Retriever bm25Retriever;
    @InjectMocks private ParallelRetrieverService service;

    // US-9 / AC-9.1
    @Test
    @DisplayName("DOIT fusionner les résultats des deux retrievers")
    void shouldMergeResultsFromBothRetrievers() {
        var r1 = new RetrievalResult("doc1", 0.9);
        var r2 = new RetrievalResult("doc2", 0.7);
        when(textRetriever.retrieve(any())).thenReturn(List.of(r1));
        when(bm25Retriever.retrieve(any())).thenReturn(List.of(r2));

        var results = service.retrieveAll("test query");

        assertThat(results).containsExactlyInAnyOrder(r1, r2);
    }

    // US-9 / AC-9.2
    @Test
    @DisplayName("DOIT retourner des résultats partiels si un retriever timeout")
    void shouldReturnPartialResultsOnTimeout() {
        var r1 = new RetrievalResult("doc1", 0.9);
        when(textRetriever.retrieve(any())).thenReturn(List.of(r1));
        when(bm25Retriever.retrieve(any())).thenAnswer(inv -> {
            Thread.sleep(5000); // simule timeout
            return List.of();
        });

        var results = service.retrieveAll("test query");

        assertThat(results).contains(r1);
    }
}
```

---

---

# PHASE 4 — Streaming

## Spec : `phase-04-streaming.md`

### User Stories

#### US-11 : Gestion des conversations
> En tant qu'utilisateur, je veux que mes conversations soient maintenues entre les requêtes, afin d'avoir un contexte cohérent dans le chat.

**Functional Requirements**
- FR-11.1 : `ConversationManager` crée, récupère et supprime les états de conversation
- FR-11.2 : `ConversationState` maintient l'historique des messages (user + assistant)
- FR-11.3 : La taille de l'historique est bornée (window de N messages configurables)

**Acceptance Criteria**
- AC-11.1 : Une nouvelle conversation est créée avec un ID unique
- AC-11.2 : L'historique est tronqué quand il dépasse `maxHistory` messages
- AC-11.3 : Une conversation expirée est nettoyée par le scheduler

#### US-12 : Streaming SSE vers le client
> En tant qu'utilisateur, je veux recevoir la réponse en streaming token par token, afin d'avoir une expérience réactive.

**Functional Requirements**
- FR-12.1 : `OpenAiStreamingClient` consomme l'API OpenAI en mode stream (SSE)
- FR-12.2 : `EventEmitter` publie chaque `StreamingEvent` (token, done, error) via SSE
- FR-12.3 : `StreamingOrchestrator` coordonne : RAG → historique → OpenAI stream → emit

**Acceptance Criteria**
- AC-12.1 : Chaque token OpenAI produit un `StreamingEvent.TOKEN`
- AC-12.2 : La fin de stream produit un `StreamingEvent.DONE`
- AC-12.3 : Une erreur OpenAI produit un `StreamingEvent.ERROR` sans exception non catchée
- AC-12.4 : `StreamingOrchestrator` injecte le contexte RAG avant d'appeler OpenAI

---

### Classes de Tests à créer — Phase 4

```
src/test/java/com/exemple/nexrag/service/rag/
├── streaming/
│   ├── ConversationManagerSpec.java
│   ├── EventEmitterSpec.java
│   ├── StreamingOrchestratorSpec.java
│   └── openai/
│       └── OpenAiStreamingClientSpec.java
```

### Exemple de Spec — `OpenAiStreamingClientSpec.java`

```java
@DisplayName("Spec : OpenAiStreamingClient — Client streaming OpenAI")
class OpenAiStreamingClientSpec {

    private WireMockServer wireMock;
    private OpenAiStreamingClient client;

    @BeforeEach
    void setup() {
        wireMock = new WireMockServer(8089);
        wireMock.start();
        client = new OpenAiStreamingClient("http://localhost:8089", "fake-key");
    }

    @AfterEach
    void teardown() { wireMock.stop(); }

    // US-12 / AC-12.1 + AC-12.2
    @Test
    @DisplayName("DOIT émettre des tokens puis DONE depuis le stream OpenAI")
    void shouldEmitTokensThenDone() {
        wireMock.stubFor(post(urlEqualTo("/v1/chat/completions"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "text/event-stream")
                .withBody("data: {\"choices\":[{\"delta\":{\"content\":\"Bonjour\"}}]}\n\n" +
                          "data: {\"choices\":[{\"delta\":{\"content\":\" monde\"}}]}\n\n" +
                          "data: [DONE]\n\n")));

        List<StreamingEvent> events = new ArrayList<>();
        client.stream(new StreamingRequest("Salut"), events::add);

        assertThat(events)
            .extracting(StreamingEvent::getType)
            .containsSubsequence(EventType.TOKEN, EventType.TOKEN, EventType.DONE);
    }
}
```

---

---

# PHASE 5 — Voice & Metrics



## Spec : `phase-05-voice-metrics.md`

### User Stories

#### US-13 : Transcription vocale
> En tant qu'utilisateur, je veux envoyer un fichier audio et recevoir sa transcription, afin d'utiliser le RAG en mode vocal.

**Functional Requirements**
- FR-13.1 : `WhisperService.transcribeAudio()` orchestre validation → fichier temp → appel API → validation du résultat
- FR-13.2 : La transcription retournée est une `String` non vide et trimmée pour un audio valide
- FR-13.3 : Un audio nul ou de taille 0 lève `IllegalArgumentException("Données audio vides ou absentes")`
- FR-13.4 : `AudioTempFile.create()` utilise l'extension du fichier original ; `.webm` par défaut si absente

**Acceptance Criteria**
- AC-13.1 : Un tableau de bytes valide retourne une transcription non vide et trimmée
- AC-13.2 : Un tableau de bytes nul ou vide lève `IllegalArgumentException`
- AC-13.3 : `WhisperService.isAvailable()` retourne `true` si `apiKey` est non blank, `false` sinon
- AC-13.4 : `AudioTempFile.create("audio.mp3")` produit un fichier temporaire avec l'extension `.mp3`
- AC-13.5 : `AudioTempFile.deleteSilently(null)` ne lève aucune exception

#### US-14 : Métriques et observabilité
> En tant qu'ops, je veux mesurer les performances du RAG, afin de détecter les dégradations.

**Functional Requirements**
- FR-14.1 : `RAGMetrics` enregistre `Timer`, `Counter` et `Gauge` Micrometer pour chaque couche du pipeline (ingestion, retrieval, generation, cache, API)
- FR-14.2 : `RAGMetrics` expose le hit/miss de cache via `recordCacheHit(cacheType)` / `recordCacheMiss(cacheType)`
- FR-14.3 : `OpenAiEmbeddingService` appelle `ragMetrics.recordApiCall(operation, durationMs)` en succès et `ragMetrics.recordApiError(operation)` en échec
- FR-14.4 : `RAGMetrics` accumule `totalTokensGenerated` atomiquement via `recordGeneration(durationMs, tokens)`
- FR-14.5 : `RAGMetrics` reflète les opérations actives via `getActiveIngestions()` / `getActiveQueries()`

**Acceptance Criteria**
- AC-14.1 : Après `recordIngestionSuccess("pdf", 100, 5)`, `rag_ingestion_files_total{strategy=pdf,status=success}` vaut 1 et `getTotalFilesProcessed()` vaut 1
- AC-14.2 : `startIngestion()` incrémente `getActiveIngestions()` ; `endIngestion()` le ramène à 0
- AC-14.3 : `recordCacheHit("embedding")` incrémente `rag_cache_hits_total{cache=embedding}` sans toucher au counter miss
- AC-14.4 : `OpenAiEmbeddingService.embedText()` appelle `recordApiCall("embed_text", _)` ; en cas d'exception du modèle, appelle `recordApiError("embed_text")` et relève une `RuntimeException`
- AC-14.5 : Après `recordGeneration(100, 80)` puis `recordGeneration(200, 70)`, `getTotalTokensGenerated()` vaut 150

---

### Classes de Tests à créer — Phase 5

```
src/test/java/com/exemple/nexrag/service/rag/
├── voice/
│   ├── WhisperServiceSpec.java
│   └── AudioTempFileSpec.java
└── metrics/
    ├── RAGMetricsSpec.java
    └── embedding/
        └── OpenAiEmbeddingServiceSpec.java
```

---

### Exemple de Spec — `WhisperServiceSpec.java`

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("Spec : WhisperService — Validation et disponibilité du service Whisper")
class WhisperServiceSpec {

    @Mock private WhisperProperties props;
    @Mock private AudioTempFile     audioTempFile;
    @InjectMocks private WhisperService service;

    @BeforeEach
    void setUp() {
        // @Value("${openai.api.key}") non injecté par Mockito — ReflectionTestUtils requis
        ReflectionTestUtils.setField(service, "apiKey", "test-key-fake");
        lenient().when(props.getMinAudioBytes()).thenReturn(1_000);
    }

    // US-1 / AC-13.2
    @Test
    @DisplayName("DOIT lever IllegalArgumentException quand l'audio est null")
    void devraitLeverIllegalArgumentExceptionPourAudioNull() {
        assertThatThrownBy(() -> service.transcribeAudio(null, "test.wav", "fr"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Données audio vides ou absentes");
    }

    // US-1 / FR-001
    @Test
    @DisplayName("DOIT lever IllegalArgumentException quand la taille dépasse 25 MB")
    void devraitLeverIllegalArgumentExceptionSiTailleDepasse25Mo() {
        byte[] tooBig = new byte[26_214_401]; // 25 MB + 1 byte

        assertThatThrownBy(() -> service.transcribeAudio(tooBig, "gros.wav", "fr"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("25 MB");
    }
}
```

---

### Exemple de Spec — `RAGMetricsSpec.java`

```java
@DisplayName("Spec : RAGMetrics — Métriques centralisées du pipeline RAG")
class RAGMetricsSpec {

    private MeterRegistry registry;
    private RAGMetrics    metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics  = new RAGMetrics(registry);
    }

    // US-2 / AC-14.1
    @Test
    @DisplayName("DOIT incrémenter le counter de succès après une ingestion réussie")
    void devraitIncrementerCompteurSuccesApresIngestionReussie() {
        metrics.recordIngestionSuccess("pdf", 100, 5);

        assertThat(registry.counter("rag_ingestion_files_total",
                "strategy", "pdf", "status", "success").count())
            .isEqualTo(1.0);
        assertThat(metrics.getTotalFilesProcessed()).isEqualTo(1L);
    }

    // US-2 / AC-14.2
    @Test
    @DisplayName("DOIT suivre les ingestions actives via startIngestion et endIngestion")
    void devraitSuivreIngestionsActivesViaStartEtEnd() {
        metrics.startIngestion();
        assertThat(metrics.getActiveIngestions()).isEqualTo(1);

        metrics.endIngestion();
        assertThat(metrics.getActiveIngestions()).isEqualTo(0);
    }

    // US-2 / AC-14.3
    @Test
    @DisplayName("DOIT incrémenter uniquement le counter hit sans affecter le counter miss")
    void devraitIncrementerSeulementLeCompteurHitSansAffecterMiss() {
        metrics.recordCacheHit("embedding");

        assertThat(registry.counter("rag_cache_hits_total", "cache", "embedding").count())
            .isEqualTo(1.0);
        assertThat(registry.find("rag_cache_misses_total").counter()).isNull();
    }

    // US-2 / AC-14.5
    @Test
    @DisplayName("DOIT accumuler correctement les tokens générés sur plusieurs appels")
    void devraitAccumulerTokensGeneresCorrectementSurPlusieursAppels() {
        metrics.recordGeneration(100, 80);
        metrics.recordGeneration(200, 70);

        assertThat(metrics.getTotalTokensGenerated()).isEqualTo(150L);
    }
}
```

---

---

# PHASE 6 — Facade

## Spec : `phase-06-facade.md`

### User Stories

#### US-15 : Façade d'ingestion
> En tant que controller, je veux déléguer l'ingestion à une façade, afin d'avoir une interface simple sans connaître les détails d'implémentation.

**Functional Requirements**
- FR-15.1 : `IngestionFacadeImpl` orchestre validation → antivirus → déduplication → stratégie
- FR-15.2 : `IngestionFacadeImpl` retourne un `IngestionResult` avec statut et métadonnées
- FR-15.3 : Un doublon retourne `IngestionResult.DUPLICATE` sans relancer l'ingestion

**Acceptance Criteria**
- AC-15.1 : Un fichier valide retourne `IngestionResult.SUCCESS` avec l'ID du batch
- AC-15.2 : Un fichier dupliqué retourne `IngestionResult.DUPLICATE` sans appeler l'orchestrateur
- AC-15.3 : Un virus retourne `IngestionResult.REJECTED` avec le motif

#### US-16 : Façade CRUD
> En tant que controller, je veux gérer les documents ingérés (lecture, suppression), afin de maintenir la base de connaissances.

**Functional Requirements**
- FR-16.1 : `CrudFacadeImpl` liste les documents avec pagination
- FR-16.2 : `CrudFacadeImpl` supprime un document et ses embeddings associés
- FR-16.3 : `DuplicateChecker` est utilisé avant toute nouvelle ingestion CRUD

**Acceptance Criteria**
- AC-16.1 : La liste paginée retourne le bon nombre d'éléments par page
- AC-16.2 : La suppression appelle `EmbeddingStoreDeleter` avec le bon documentId
- AC-16.3 : Un document inexistant lève `DocumentNotFoundException`

#### US-17 : Façade Streaming
> En tant que controller, je veux déléguer le streaming à une façade, afin de garder les controllers légers.

**Functional Requirements**
- FR-17.1 : `StreamingFacadeImpl` prépare la requête RAG et délègue à `StreamingOrchestrator`
- FR-17.2 : La façade gère les erreurs et les transforme en `StreamingError` exploitable

**Acceptance Criteria**
- AC-17.1 : Une requête valide retourne un `Flux<StreamingEvent>` non vide
- AC-17.2 : Une erreur d'orchestration émet un event `ERROR` sans propager l'exception

---

### Classes de Tests à créer — Phase 6

```
src/test/java/com/exemple/nexrag/service/rag/
└── facade/
    ├── IngestionFacadeImplSpec.java
    ├── CrudFacadeImplSpec.java
    ├── StreamingFacadeImplSpec.java
    ├── VoiceFacadeImplSpec.java
    └── DuplicateCheckerSpec.java
```

### Exemple de Spec — `IngestionFacadeImplSpec.java`

```java
@DisplayName("Spec : IngestionFacadeImpl — Façade d'ingestion multimodale")
@ExtendWith(MockitoExtension.class)
class IngestionFacadeImplSpec {

    @Mock private FileValidator fileValidator;
    @Mock private AntivirusGuard antivirusGuard;
    @Mock private DeduplicationService deduplicationService;
    @Mock private IngestionOrchestrator ingestionOrchestrator;
    @InjectMocks private IngestionFacadeImpl facade;

    // US-15 / AC-15.1
    @Test
    @DisplayName("DOIT retourner SUCCESS pour un fichier PDF valide")
    void shouldReturnSuccessForValidPdf() throws Exception {
        var file = new MockMultipartFile("doc", "test.pdf", "application/pdf", "content".getBytes());
        when(deduplicationService.isDuplicate(any())).thenReturn(false);
        when(ingestionOrchestrator.ingest(any())).thenReturn(new IngestionResult("batch-123"));

        var result = facade.ingest(file, Map.of("source", "test"));

        assertThat(result.getStatus()).isEqualTo(IngestionStatus.SUCCESS);
        assertThat(result.getBatchId()).isEqualTo("batch-123");
    }

    // US-15 / AC-15.2
    @Test
    @DisplayName("DOIT court-circuiter l'orchestration pour un doublon")
    void shouldShortCircuitOnDuplicate() throws Exception {
        var file = new MockMultipartFile("doc", "dup.pdf", "application/pdf", "same".getBytes());
        when(deduplicationService.isDuplicate(any())).thenReturn(true);

        var result = facade.ingest(file, Map.of());

        assertThat(result.getStatus()).isEqualTo(IngestionStatus.DUPLICATE);
        verifyNoInteractions(ingestionOrchestrator);
    }
}
```

---

---

# PHASE 7 — Controllers

## Spec : `phase-07-controller.md`

### User Stories

#### US-18 : Controller d'ingestion multimodale
> En tant que client API, je veux uploader des fichiers via REST, afin de les ingérer dans la base de connaissances RAG.

**Functional Requirements**
- FR-18.1 : `POST /api/ingest` accepte multipart/form-data avec fichier + métadonnées
- FR-18.2 : Retourne HTTP 202 ACCEPTED avec batchId si l'ingestion démarre
- FR-18.3 : Retourne HTTP 409 CONFLICT si doublon détecté
- FR-18.4 : Retourne HTTP 400 BAD REQUEST si fichier invalide

**Acceptance Criteria**
- AC-18.1 : Un PDF valide retourne 202 avec body `{"batchId": "..."}`
- AC-18.2 : Un fichier dupliqué retourne 409
- AC-18.3 : Absence du champ `file` retourne 400

#### US-19 : Controller de streaming assistant
> En tant que client, je veux interroger l'assistant en mode streaming SSE, afin de recevoir la réponse progressivement.

**Functional Requirements**
- FR-19.1 : `POST /api/stream` retourne `text/event-stream` (SSE)
- FR-19.2 : Chaque ligne SSE contient un token ou le signal DONE
- FR-19.3 : Retourne HTTP 400 si la requête est vide ou manquante

**Acceptance Criteria**
- AC-19.1 : La réponse a le Content-Type `text/event-stream`
- AC-19.2 : Au moins un event `data:` est émis avant `data: [DONE]`
- AC-19.3 : Une requête vide retourne 400

#### US-20 : Controller de métriques
> En tant qu'ops, je veux accéder aux métriques RAG via un endpoint dédié, afin de les intégrer dans Prometheus/Grafana.

**Functional Requirements**
- FR-20.1 : `GET /api/metrics` retourne les métriques en format JSON
- FR-20.2 : Les métriques incluent : latence moyenne, tokens/s, cache hit rate

**Acceptance Criteria**
- AC-20.1 : L'endpoint retourne HTTP 200 avec les champs attendus
- AC-20.2 : Sans données, les valeurs numériques sont 0 (pas null)

---

### Classes de Tests à créer — Phase 7

```
src/test/java/com/exemple/nexrag/service/rag/
└── controller/
    ├── MultimodalIngestionControllerSpec.java
    ├── StreamingAssistantControllerSpec.java
    ├── MultimodalCrudControllerSpec.java
    ├── MetricsControllerSpec.java
    ├── VoiceControllerSpec.java
    └── WebSocketStatsControllerSpec.java
```

### Exemple de Spec — `MultimodalIngestionControllerSpec.java`

```java
@DisplayName("Spec : MultimodalIngestionController — API d'ingestion REST")
@WebMvcTest(MultimodalIngestionController.class)
class MultimodalIngestionControllerSpec {

    @Autowired private MockMvc mockMvc;
    @MockBean private IngestionFacade ingestionFacade;

    // US-18 / AC-18.1
    @Test
    @DisplayName("DOIT retourner 202 avec batchId pour un PDF valide")
    void shouldReturn202ForValidPdf() throws Exception {
        when(ingestionFacade.ingest(any(), any()))
            .thenReturn(IngestionResult.success("batch-abc"));

        mockMvc.perform(multipart("/api/ingest")
                .file(new MockMultipartFile("file", "doc.pdf", "application/pdf", "data".getBytes())))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.batchId").value("batch-abc"));
    }

    // US-18 / AC-18.2
    @Test
    @DisplayName("DOIT retourner 409 pour un fichier dupliqué")
    void shouldReturn409ForDuplicate() throws Exception {
        when(ingestionFacade.ingest(any(), any()))
            .thenReturn(IngestionResult.duplicate());

        mockMvc.perform(multipart("/api/ingest")
                .file(new MockMultipartFile("file", "dup.pdf", "application/pdf", "data".getBytes())))
            .andExpect(status().isConflict());
    }

    // US-18 / AC-18.3
    @Test
    @DisplayName("DOIT retourner 400 si le champ file est absent")
    void shouldReturn400WhenFileIsMissing() throws Exception {
        mockMvc.perform(multipart("/api/ingest"))
            .andExpect(status().isBadRequest());
    }
}
```

---

---

# PHASE 8 — Interceptor & Validation

## Spec : `phase-08-interceptor-validation.md`

### User Stories

#### US-21 : Rate limiting multi-endpoint par identifiant client
> En tant qu'ops, je veux limiter le nombre de requêtes par client et par type d'endpoint, afin de protéger le système contre les abus.

**Functional Requirements**
- FR-21.1 : `RateLimitInterceptor` résout l'identifiant client par priorité : header `X-User-Id` → attribut de session → IP client (avec support `X-Forwarded-For` et `X-Real-IP`)
- FR-21.2 : `RateLimitInterceptor.selectLimit()` route vers la bonne méthode de `RateLimitService` selon l'URI et la méthode HTTP : `/upload/batch` → `checkBatchLimit`, `/upload` → `checkUploadLimit`, `/search` → `checkSearchLimit`, `DELETE /file/|/batch/|/files/` → `checkDeleteLimit`, sinon → `checkDefaultLimit`
- FR-21.3 : `RateLimitService` délègue à Bucket4j via `ProxyManager<String>` avec une bucket par paire `(userId, endpoint)` — clé Redis : `rate-limit:{userId}:{endpoint}`
- FR-21.4 : Le dépassement de limite retourne HTTP 429 avec les headers `X-RateLimit-Remaining: 0`, `Retry-After: {secondes}`, `X-RateLimit-Reset: {epoch}`
- FR-21.5 : Les requêtes OPTIONS (CORS preflight) sont court-circuitées immédiatement sans appel à `RateLimitService`
- FR-21.6 : En cas d'exception Redis dans `RateLimitService.check()`, la requête est autorisée (fail-open) et `RateLimitResult.allowed(0)` est retourné
- FR-21.7 : Limites configurées via `RateLimitProperties` — upload: 10/min, batch: 5/min, delete: 20/min, search: 50/min, default: 30/min

**Acceptance Criteria**
- AC-21.1 : Un appel upload dont la limite est atteinte retourne `RateLimitResult` avec `isAllowed()=false` et `retryAfterSeconds > 0`
- AC-21.2 : Un appel autorisé retourne `RateLimitResult` avec `isAllowed()=true` et `remainingTokens ≥ 0`
- AC-21.3 : Une requête OPTIONS retourne `preHandle()=true` sans aucune interaction avec `RateLimitService`
- AC-21.4 : `resolveUserId` utilise `X-User-Id` en priorité, puis attribut de session, puis IP via `X-Forwarded-For`
- AC-21.5 : Un URI `/upload` est routé vers `checkUploadLimit`, `/search` vers `checkSearchLimit`, `DELETE /file/123` vers `checkDeleteLimit`
- AC-21.6 : En cas d'exception dans `check()`, `RateLimitResult.allowed(0)` est retourné (fail-open)
- AC-21.7 : La réponse 429 contient un corps JSON avec les champs `error`, `message`, `retryAfterSeconds`, `timestamp`

---

#### US-22 : Validation de fichiers — taille et extension
> En tant que système, je veux valider les fichiers entrants avant tout traitement, afin de rejeter les fichiers dangereux ou invalides dès l'entrée.

**Functional Requirements**
- FR-22.1 : `FileValidator.validate()` rejette les fichiers null ou vides (0 octet)
- FR-22.2 : `FileValidator.validate()` rejette les fichiers dont le nom est absent ou vide
- FR-22.3 : `FileValidator.validate()` rejette les fichiers dont la taille dépasse `FileSizeConstants.MAX_FILE_SIZE_BYTES`, avec un message incluant la taille réelle en MB et la limite
- FR-22.4 : `FileValidator.validate()` rejette les extensions dangereuses : `exe`, `bat`, `cmd`, `msi`, `com`, `scr`, `vbs`, `ps1`, `sh`
- FR-22.5 : `FileValidator.validateBatch()` rejette les listes null ou vides, puis délègue à `validate()` pour chaque fichier
- FR-22.6 : `AudioFileValidator.validate()` rejette les fichiers audio null/vides et ceux dont la taille dépasse `VoiceConstants.MAX_AUDIO_SIZE_BYTES` (25 MB)

**Acceptance Criteria**
- AC-22.1 : Un PDF valide (nom présent, taille ≤ max, extension autorisée) passe sans exception *(déjà couvert par `FileValidatorSpec`)*
- AC-22.2 : Un fichier null lève `IllegalArgumentException("Fichier vide ou absent")`
- AC-22.3 : Un fichier de 0 octet lève `IllegalArgumentException("Fichier vide ou absent")`
- AC-22.4 : Un fichier sans nom lève `IllegalArgumentException("Nom de fichier absent")`
- AC-22.5 : Un fichier à `MAX_FILE_SIZE_BYTES + 1` lève `IllegalArgumentException` contenant la taille en MB et la limite
- AC-22.6 : Un fichier à exactement `MAX_FILE_SIZE_BYTES` passe sans exception
- AC-22.7 : Un fichier `.exe` lève `IllegalArgumentException` mentionnant `.exe`
- AC-22.8 : Un batch null ou vide lève `IllegalArgumentException("Aucun fichier fourni")`
- AC-22.9 : Un batch contenant un fichier invalide propage l'exception de `validate()`
- AC-22.10 : Un fichier audio > 25 MB lève `IllegalArgumentException` mentionnant la taille reçue

---

#### US-23 : Validation de signature de fichiers (magic bytes)
> En tant que système de sécurité, je veux vérifier que le contenu réel d'un fichier correspond à son extension déclarée, afin de bloquer les fichiers déguisés.

**Functional Requirements**
- FR-23.1 : `FileSignatureValidator.validate()` bloque systématiquement les extensions dangereuses (exe, dll, bat, sh, cmd, vbs, ps1, jar) — indépendamment du contenu
- FR-23.2 : `FileSignatureValidator.validate()` lit les premiers octets du fichier et les compare aux magic bytes de l'extension déclarée
- FR-23.3 : Si la signature est incorrecte, `SecurityException` est levée avec un message explicite
- FR-23.4 : Si aucune signature n'est connue pour l'extension (ex : .txt, .csv), la validation est ignorée (skip silencieux)
- FR-23.5 : `detectRealType()` parcourt toutes les signatures connues et retourne l'extension correspondante, ou `null` si inconnue
- FR-23.6 : `isExtensionMatching()` traite DOCX/XLSX/PPTX comme équivalents (même signature ZIP `50 4B 03 04`)
- FR-23.7 : `validateComplete()` retourne un record `ValidationResult(isValid, errorMessage, detectedType, extensionMatches)` et ne lève pas d'exception

**Acceptance Criteria**
- AC-23.1 : Un fichier déclaré `.exe` lève `SecurityException` contenant "EXE" et "dangereuse"
- AC-23.2 : Un fichier dont les magic bytes ne correspondent pas à l'extension déclarée (ex : contenu PNG nommé `.pdf`) lève `SecurityException` contenant "Signature invalide"
- AC-23.3 : Un fichier texte (`.txt`) sans signature connue passe `validate()` sans exception
- AC-23.4 : `detectRealType()` retourne `"pdf"` pour un fichier dont les premiers octets sont `25 50 44 46` (`%PDF`)
- AC-23.5 : `validateComplete()` retourne `ValidationResult(isValid=false, ...)` pour un fichier `.exe`
- AC-23.6 : `isExtensionMatching()` retourne `true` pour un fichier DOCX dont le type détecté est `zip`
- AC-23.7 : Un fichier trop court pour contenir la signature attendue lève `SecurityException` mentionnant la taille

---

### Classes de Tests — Phase 8

```
src/test/java/com/exemple/nexrag/
├── validation/
│   ├── FileValidatorSpec.java            ✅ implémenté
│   ├── FileSignatureValidatorSpec.java   ⬜ à créer
│   └── AudioFileValidatorSpec.java       ⬜ à créer
└── service/rag/interceptor/
    ├── RateLimitInterceptorSpec.java     ⬜ à créer
    └── RateLimitServiceSpec.java         ⬜ à créer
```

---

### Spec — `RateLimitInterceptorSpec.java`

```java
@DisplayName("Spec : RateLimitInterceptor — Limitation de débit par endpoint")
@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorSpec {

    @Mock private RateLimitService rateLimitService;
    @Mock private ObjectMapper     objectMapper;
    @InjectMocks private RateLimitInterceptor interceptor;

    // US-21 / AC-21.1 + AC-21.7
    @Test
    @DisplayName("DOIT retourner 429 et écrire le corps JSON quand la limite upload est atteinte")
    void shouldReturn429WithJsonBodyWhenUploadLimitExceeded() throws Exception {
        when(rateLimitService.checkUploadLimit("user-42"))
            .thenReturn(RateLimitResult.blocked(30L));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"error\":\"Too Many Requests\"}");

        MockHttpServletRequest  req = new MockHttpServletRequest("POST", "/api/upload");
        req.addHeader("X-User-Id", "user-42");
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(req, res, new Object());

        assertThat(proceed).isFalse();
        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isEqualTo("30");
        assertThat(res.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
    }

    // US-21 / AC-21.2
    @Test
    @DisplayName("DOIT ajouter X-RateLimit-Remaining et laisser passer quand la limite n'est pas atteinte")
    void shouldPassWithRemainingHeaderWhenAllowed() throws Exception {
        when(rateLimitService.checkDefaultLimit("user-1"))
            .thenReturn(RateLimitResult.allowed(29L));

        MockHttpServletRequest  req = new MockHttpServletRequest("GET", "/api/documents");
        req.addHeader("X-User-Id", "user-1");
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(req, res, new Object());

        assertThat(proceed).isTrue();
        assertThat(res.getHeader("X-RateLimit-Remaining")).isEqualTo("29");
    }

    // US-21 / AC-21.3
    @Test
    @DisplayName("DOIT court-circuiter les requêtes OPTIONS sans appeler RateLimitService")
    void shouldBypassOptionsCorsPreflightWithoutCallingService() throws Exception {
        MockHttpServletRequest  req = new MockHttpServletRequest("OPTIONS", "/api/upload");
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(req, res, new Object());

        assertThat(proceed).isTrue();
        verifyNoInteractions(rateLimitService);
    }

    // US-21 / AC-21.4
    @Test
    @DisplayName("DOIT utiliser l'IP X-Forwarded-For quand X-User-Id est absent")
    void shouldFallbackToForwardedIpWhenNoUserId() throws Exception {
        when(rateLimitService.checkDefaultLimit("203.0.113.5"))
            .thenReturn(RateLimitResult.allowed(30L));

        MockHttpServletRequest  req = new MockHttpServletRequest("GET", "/api/documents");
        req.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
        MockHttpServletResponse res = new MockHttpServletResponse();

        interceptor.preHandle(req, res, new Object());

        verify(rateLimitService).checkDefaultLimit("203.0.113.5");
    }

    // US-21 / AC-21.5
    @Test
    @DisplayName("DOIT router /search vers checkSearchLimit")
    void shouldRouteSearchEndpointToSearchLimit() throws Exception {
        when(rateLimitService.checkSearchLimit("user-7"))
            .thenReturn(RateLimitResult.allowed(50L));

        MockHttpServletRequest  req = new MockHttpServletRequest("POST", "/api/search");
        req.addHeader("X-User-Id", "user-7");
        MockHttpServletResponse res = new MockHttpServletResponse();

        interceptor.preHandle(req, res, new Object());

        verify(rateLimitService).checkSearchLimit("user-7");
        verify(rateLimitService, never()).checkDefaultLimit(any());
    }

    // US-21 / AC-21.5
    @Test
    @DisplayName("DOIT router DELETE /file/{id} vers checkDeleteLimit")
    void shouldRouteDeleteFileToDeleteLimit() throws Exception {
        when(rateLimitService.checkDeleteLimit("user-3"))
            .thenReturn(RateLimitResult.allowed(20L));

        MockHttpServletRequest  req = new MockHttpServletRequest("DELETE", "/api/file/abc-123");
        req.addHeader("X-User-Id", "user-3");
        MockHttpServletResponse res = new MockHttpServletResponse();

        interceptor.preHandle(req, res, new Object());

        verify(rateLimitService).checkDeleteLimit("user-3");
    }
}
```

---

### Spec — `RateLimitServiceSpec.java`

```java
@DisplayName("Spec : RateLimitService — Gestion des quotas Bucket4j/Redis")
@ExtendWith(MockitoExtension.class)
class RateLimitServiceSpec {

    @Mock private ProxyManager<String>            proxyManager;
    @Mock private ProxyManager.Builder<String>    bucketBuilder;
    @Mock private Bucket                          bucket;
    @Mock private ConsumptionProbe                probe;
    @Mock private Supplier<BucketConfiguration>   uploadConfig;
    @Mock private Supplier<BucketConfiguration>   batchConfig;
    @Mock private Supplier<BucketConfiguration>   deleteConfig;
    @Mock private Supplier<BucketConfiguration>   searchConfig;
    @Mock private Supplier<BucketConfiguration>   defaultConfig;

    private RateLimitService service;

    @BeforeEach
    void setUp() {
        service = new RateLimitService(proxyManager, uploadConfig, batchConfig,
                                        deleteConfig, searchConfig, defaultConfig);
        when(proxyManager.builder()).thenReturn(bucketBuilder);
        when(bucketBuilder.build(anyString(), any())).thenReturn(bucket);
    }

    // US-21 / AC-21.2
    @Test
    @DisplayName("DOIT retourner allowed avec remainingTokens quand le token est consommé")
    void shouldReturnAllowedWithRemainingTokensWhenConsumed() {
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        when(probe.isConsumed()).thenReturn(true);
        when(probe.getRemainingTokens()).thenReturn(9L);

        RateLimitResult result = service.checkUploadLimit("user-1");

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getRemainingTokens()).isEqualTo(9L);
    }

    // US-21 / AC-21.1
    @Test
    @DisplayName("DOIT retourner blocked avec retryAfterSeconds quand la limite est atteinte")
    void shouldReturnBlockedWithRetryAfterWhenLimitReached() {
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        when(probe.isConsumed()).thenReturn(false);
        when(probe.getNanosToWaitForRefill()).thenReturn(30_000_000_000L); // 30s

        RateLimitResult result = service.checkUploadLimit("user-1");

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getRetryAfterSeconds()).isEqualTo(30L);
    }

    // US-21 / AC-21.6
    @Test
    @DisplayName("DOIT retourner allowed(0) en cas d'exception Redis (fail-open)")
    void shouldReturnAllowedOnRedisException() {
        when(bucket.tryConsumeAndReturnRemaining(1)).thenThrow(new RuntimeException("Redis down"));

        RateLimitResult result = service.checkUploadLimit("user-1");

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getRemainingTokens()).isZero();
    }

    // US-21 / AC-21.5 — clé Redis
    @Test
    @DisplayName("DOIT construire la clé Redis rate-limit:{userId}:{endpoint}")
    void shouldBuildCorrectRedisKey() {
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        when(probe.isConsumed()).thenReturn(true);
        when(probe.getRemainingTokens()).thenReturn(5L);

        service.checkSearchLimit("user-99");

        verify(bucketBuilder).build(eq("rate-limit:user-99:search"), any());
    }
}
```

---

### Spec — `FileSignatureValidatorSpec.java`

```java
@DisplayName("Spec : FileSignatureValidator — Validation des magic bytes")
@ExtendWith(MockitoExtension.class)
class FileSignatureValidatorSpec {

    private FileSignatureValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FileSignatureValidator();
    }

    // US-23 / AC-23.1
    @Test
    @DisplayName("DOIT lever SecurityException pour une extension dangereuse (.exe)")
    void shouldThrowSecurityExceptionForDangerousExtension() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "virus.exe", "application/octet-stream",
            new byte[]{0x4D, 0x5A, 0x00, 0x01} // MZ header
        );

        assertThatThrownBy(() -> validator.validate(file, "exe"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContainingIgnoringCase("EXE")
            .hasMessageContaining("dangereuse");
    }

    // US-23 / AC-23.2
    @Test
    @DisplayName("DOIT lever SecurityException si les magic bytes ne correspondent pas à l'extension")
    void shouldThrowSecurityExceptionForSignatureMismatch() {
        // Fichier PNG renommé .pdf
        byte[] pngBytes = new byte[]{(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile file = new MockMultipartFile(
            "file", "fake.pdf", "application/pdf", pngBytes
        );

        assertThatThrownBy(() -> validator.validate(file, "pdf"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("Signature invalide");
    }

    // US-23 / AC-23.3
    @Test
    @DisplayName("DOIT ignorer la validation de signature pour les extensions sans magic bytes (.txt)")
    void shouldSkipSignatureValidationForUnknownExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "notes.txt", "text/plain",
            "Hello world".getBytes()
        );

        assertThatNoException().isThrownBy(() -> validator.validate(file, "txt"));
    }

    // US-23 / AC-23.4
    @Test
    @DisplayName("DOIT détecter 'pdf' pour un fichier commençant par %PDF")
    void shouldDetectPdfType() throws Exception {
        byte[] pdfBytes = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31}; // %PDF-1
        MockMultipartFile file = new MockMultipartFile(
            "file", "doc.pdf", "application/pdf", pdfBytes
        );

        String detectedType = validator.detectRealType(file);

        assertThat(detectedType).isEqualTo("pdf");
    }

    // US-23 / AC-23.5
    @Test
    @DisplayName("DOIT retourner ValidationResult invalide pour un .exe via validateComplete()")
    void shouldReturnInvalidResultForExeViaValidateComplete() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "bad.exe", "application/octet-stream",
            new byte[]{0x4D, 0x5A}
        );

        FileSignatureValidator.ValidationResult result = validator.validateComplete(file, "exe");

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).isNotBlank();
    }

    // US-23 / AC-23.6
    @Test
    @DisplayName("DOIT accepter un DOCX dont le type détecté est zip (même signature)")
    void shouldAcceptDocxWhenDetectedTypeIsZip() throws Exception {
        byte[] zipBytes = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x14, 0x00}; // PK\x03\x04
        MockMultipartFile file = new MockMultipartFile(
            "file", "report.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            zipBytes
        );

        assertThat(validator.isExtensionMatching(file, "docx")).isTrue();
    }

    // US-23 / AC-23.7
    @Test
    @DisplayName("DOIT lever SecurityException si le fichier est trop court pour sa signature")
    void shouldThrowWhenFileTooShortForSignature() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "short.pdf", "application/pdf",
            new byte[]{0x25} // 1 seul byte, PDF signature = 4 bytes
        );

        assertThatThrownBy(() -> validator.validate(file, "pdf"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("trop court");
    }
}
```

---

### Spec — `AudioFileValidatorSpec.java`

```java
@DisplayName("Spec : AudioFileValidator — Validation des fichiers audio")
@ExtendWith(MockitoExtension.class)
class AudioFileValidatorSpec {

    private AudioFileValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AudioFileValidator();
    }

    // US-22 / AC-22.10
    @Test
    @DisplayName("DOIT lever IllegalArgumentException pour un fichier audio null")
    void shouldRejectNullAudioFile() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> validator.validate(null))
            .withMessageContaining("vide");
    }

    // US-22 / AC-22.10
    @Test
    @DisplayName("DOIT lever IllegalArgumentException pour un fichier audio vide")
    void shouldRejectEmptyAudioFile() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "audio.mp3", "audio/mpeg", new byte[0]
        );

        assertThatIllegalArgumentException()
            .isThrownBy(() -> validator.validate(file))
            .withMessageContaining("vide");
    }

    // US-22 / AC-22.10
    @Test
    @DisplayName("DOIT lever IllegalArgumentException si le fichier audio dépasse 25 MB")
    void shouldRejectAudioFileExceedingMaxSize() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "audio.mp3", "audio/mpeg", new byte[0]
        ) {
            @Override public long    getSize()  { return VoiceConstants.MAX_AUDIO_SIZE_BYTES + 1; }
            @Override public boolean isEmpty()  { return false; }
        };

        assertThatIllegalArgumentException()
            .isThrownBy(() -> validator.validate(file))
            .withMessageContaining("25 MB");
    }

    // US-22 / AC-22.10
    @Test
    @DisplayName("DOIT accepter un fichier audio valide en dessous de la limite")
    void shouldAcceptValidAudioFile() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "speech.wav", "audio/wav", new byte[]{1, 2, 3}
        );

        assertThatNoException().isThrownBy(() -> validator.validate(file));
    }
}
```

---

---

# PHASE 9 — Tests d'Intégration

## Spec : `phase-09-integration.md`

### User Stories

#### US-22 : Ingestion bout-en-bout
> En tant que testeur, je veux valider le pipeline complet d'ingestion depuis l'API jusqu'au stockage vectoriel, afin de garantir la cohérence de l'ensemble.

**Functional Requirements**
- FR-22.1 : Upload d'un PDF → antivirus → chunking → embedding → stockage dans pgvector
- FR-22.2 : Redis est disponible pour le cache et la déduplication
- FR-22.3 : L'API OpenAI est mockée avec WireMock (pas d'appels réels)

**Stack Testcontainers**
```java
@Testcontainers
@SpringBootTest(webEnvironment = RANDOM_PORT)
class IngestionIntegrationSpec {
    
    @Container
    static PostgreSQLContainer<?> postgres = 
        new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("nexrag_test");
    
    @Container
    static GenericContainer<?> redis = 
        new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    
    @Container
    static GenericContainer<?> clamav = 
        new GenericContainer<>("clamav/clamav:latest").withExposedPorts(3310);
    
    @RegisterExtension
    static WireMockExtension openAiMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort()).build();
}
```

**Acceptance Criteria**
- AC-22.1 : Un PDF de 2 pages est ingéré en moins de 10 secondes
- AC-22.2 : Les embeddings sont bien stockés dans pgvector (SELECT COUNT > 0)
- AC-22.3 : Une deuxième ingestion du même PDF retourne DUPLICATE (Redis)

#### US-23 : Pipeline RAG complet
> En tant que testeur, je veux valider la chaîne complète requête → retrieval → stream, afin de garantir la qualité des réponses.

**Acceptance Criteria**
- AC-23.1 : Une requête pertinente retourne au moins 3 passages du document ingéré
- AC-23.2 : La réponse streaming contient des tokens avant DONE
- AC-23.3 : L'historique de conversation est maintenu entre deux requêtes

---

### Classes de Tests d'Intégration

```
src/test/java/com/exemple/nexrag/service/rag/
└── integration/
    ├── IngestionPipelineIntegrationSpec.java
    ├── RetrievalPipelineIntegrationSpec.java
    ├── StreamingPipelineIntegrationSpec.java
    ├── RateLimitIntegrationSpec.java
    └── FullRagPipelineIntegrationSpec.java
```

---

---

## Récapitulatif des Dépendances Maven (scope test)

```xml
<!-- JUnit 5 -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>

<!-- Mockito -->
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>

<!-- AssertJ -->
<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <scope>test</scope>
</dependency>

<!-- Spring Boot Test (MockMvc, @WebMvcTest, @SpringBootTest) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- Testcontainers -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>redis</artifactId>
    <scope>test</scope>
</dependency>

<!-- WireMock (mock OpenAI, Whisper) -->
<dependency>
    <groupId>com.github.tomakehurst</groupId>
    <artifactId>wiremock-jre8-standalone</artifactId>
    <scope>test</scope>
</dependency>

<!-- Awaitility (tests asynchrones/streaming) -->
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <scope>test</scope>
</dependency>
```

---

## Roadmap d'Exécution

```
SEMAINE 1 — Phases 1 & 2 (Ingestion fondation + stratégies)
  → Objectif : couvrir toute la couche d'ingestion (util, security, dedup, cache, strategy)
  → KPI : 80% coverage sur le package ingestion

SEMAINE 2 — Phases 3 & 4 (Retrieval + Streaming)
  → Objectif : valider le pipeline RAG et le streaming SSE
  → KPI : tous les AC retrieval et streaming au vert

SEMAINE 3 — Phases 5, 6 & 7 (Voice, Metrics, Facade, Controller)
  → Objectif : couvrir les couches de surface (API, facade)
  → KPI : 80% coverage sur facade et controller

SEMAINE 4 — Phases 8 & 9 (Interceptor, Validation, Intégration)
  → Objectif : tests bout-en-bout avec vraie infra (Testcontainers)
  → KPI : pipeline complet vert en CI/CD
```

---

*Document généré avec Spec Kit — Spec-Driven Development (github/spec-kit)*  
*Projet : NexRAG Backend — `src/main/java/com/exemple/nexrag/service/rag`*
