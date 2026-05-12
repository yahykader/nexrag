# CLAUDE.md — nex-rag (Backend)

Spring Boot 3.4.2 / Java 21 backend for the NexRAG platform.

## Commands

```bash
# Run (from this directory)
./mvnw spring-boot:run

# Build (skip tests)
./mvnw clean package -DskipTests

# Run all tests (unit + integration with Testcontainers)
./mvnw test

# Run unit tests only (excludes *IntegrationSpec)
./mvnw test -Dtest=!*IntegrationSpec

# Run single test class / method
./mvnw test -Dtest=ClassName
./mvnw test -Dtest=ClassName#methodName

# Run only integration tests
./mvnw test -Dtest=*IntegrationSpec

# Run integration test with container logs on failure
./mvnw test -Dtest=IngestionPipelineIntegrationSpec -DcontainerLogMode=OnFailure

# Run code quality checks (SonarQube)
./mvnw sonar:sonar -Dsonar.host.url=http://localhost:9000

# Build JAR and verify
./mvnw clean package
```

## Key Files

| File | Purpose |
|------|---------|
| `src/main/resources/application.yml` | All RAG parameters (666 lines) |
| `.env` | Local secrets (OpenAI key, DB/Redis credentials) |
| `logback-spring.xml` | Logging configuration |

## Java Package Structure (`com.exemple.nexrag`)

> Note: the source directory is `nex-rag/` but the Java package uses **no hyphen**: `com.exemple.nexrag`

```
advice/                     — @ControllerAdvice handlers (Crud, Ingestion, Voice)
config/                     — Spring beans (Async, CircuitBreaker, CORS, OpenAI, PgVector, Redis, WebSocket, etc.)
constant/                   — Redis key constants (DeduplicationRedisKeys, EmbeddingCacheRedisKeys, etc.)
dto/                        — Java records (immutable DTOs)
  batch/                    — Batch DTOs (BatchInfo, TrackerStats)
  cache/                    — Cache config DTOs
  deduplication/file|text/  — Dedup stats/info
exception/                  — Custom exceptions (DuplicateFileException, VirusDetectedException, IngestionException)
monitoring/aspect/          — AOP observability aspects
util/                       — Shared utilities
validation/                 — Custom constraint validators
websocket/                  — STOMP message handlers

service/rag/
  controller/               — REST controllers (NOT at root — inside service/rag/)
  facade/                   — IngestionFacadeImpl, CrudFacadeImpl (controllers delegate here, never call services directly)
  ingestion/
    IngestionOrchestrator   — Main entry point: antivirus → dedup → strategy → metrics → rollback
    analyzer/               — Image analysis (VisionAI, ImageConverter, VisionFallbackGenerator)
    cache/                  — Embedding cache (Caffeine L1 + Redis L2)
    compression/            — INT8 embedding quantization
    deduplication/file/     — SHA-256 file dedup via Redis
    deduplication/text/     — Text-level dedup
    progress/               — Real-time progress via WebSocket
    ratelimit/              — Bucket4j rate limiting per endpoint
    repository/             — EmbeddingRepository (pgvector persistence)
    security/               — AntivirusGuard.assertClean(file) — throws on virus
    strategy/               — IngestionStrategy implementations (PDF, DOCX, XLSX, Image, Text, Tika)
      IngestionConfig       — @Bean that sorts strategies by priority (do NOT sort in orchestrator)
      commun/               — EmbeddingIndexer, TextChunker, LibreOfficeConverter, XlsxProperties
    tracker/                — Batch tracking: BatchEmbeddingRegistry, BatchInfoRegistry, RollbackExecutor
    util/                   — Ingestion utilities
  retrieval/
    aggregator/             — RRF aggregator (k=60, text:0.5 / image:0.3 / bm25:0.2)
    injector/               — Context injector for LLM prompts (max 200k tokens)
    model/                  — Retrieval domain models
    query/                  — QueryTransformer (5 variants) + QueryRouter (HYBRID default)
    reranker/               — Cross-encoder ms-marco-MiniLM-L-6-v2, top-k=10
    retriever/              — Text (top-20 ≥0.7), Image (top-5 ≥0.6), BM25 (top-10)
  generation/               — LLM streaming, context management (gpt-4o)
  streaming/openai/         — SSE token streaming
  voice/                    — Whisper transcription
  metrics/embedding/        — RAGMetrics + embedding-specific metrics
  interceptor/              — HTTP interceptors
```

## Key Dependencies

| Library | Purpose |
|---------|---------|
| `langchain4j` 1.10.0 | Core AI framework (OpenAI, pgvector, parsers) |
| `langchain4j-pgvector` | Vector store integration |
| `langchain4j-document-parser-apache-pdfbox` | PDF parsing |
| `langchain4j-document-parser-apache-poi` | DOCX/XLSX parsing |
| `langchain4j-document-parser-apache-tika` | Fallback parser |
| `resilience4j-spring-boot3` | Circuit breakers + retry |
| `bucket4j_jdk17-lettuce` | Redis-backed rate limiting |
| `micrometer-registry-prometheus` | Metrics export |
| `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` | Distributed tracing |
| `springdoc-openapi-starter-webmvc-ui` 2.8.9 | Swagger UI at `/swagger-ui.html` |
| `testcontainers` 1.19.7 | Integration test infrastructure (PostgreSQL, Redis, ClamAV) |
| `wiremock` 2.35.2 | Mock OpenAI API endpoints in integration tests |
| `spring-cloud-gcp-starter-storage` 4.8.0 | GCP Cloud Storage integration (disabled locally) |

## Code Style Conventions

- **Language**: Log messages and Javadoc are written in **French**
- **Log emojis**: `📄` start, `✅` success, `❌` error, `⚠️` warning, `📦` batch, `🎯` strategy, `📊` stats, `🔄` rollback
- **DTOs**: Use Java **records** — always immutable, no setters
- **Facade layer**: Controllers call facades only — facades call services — services call repositories
- **Strategy pattern**: `IngestionConfig` `@Bean` handles priority sorting — never sort in `IngestionOrchestrator`
- **Guard pattern**: `AntivirusGuard.assertClean(file)` throws `VirusDetectedException` — no temp file management needed
- **SOLID comments**: Classes include SRP/OCP/DIP annotations in Javadoc explaining their role

## Architecture Invariants

- New ingestion format → implement `IngestionStrategy`, register as `@Component` — `IngestionConfig` picks it up automatically
- `IngestionOrchestrator` must not be modified when adding strategies (OCP)
- All Redis key strings live in `constant/` package — no raw string keys in services
- Exception handlers are in `advice/` — never catch-and-swallow in services

## Configuration Notes

### Environment Variables (`.env`)
Required for local development:
```
OPENAI_API_KEY=sk-proj-...
PGVECTOR_HOST=localhost
PGVECTOR_PORT=5432
PGVECTOR_DATABASE=vectordb
PGVECTOR_USER=admin
PGVECTOR_PASSWORD=1234
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=dev_password_123
IMAGES_STORAGE_PATH=<path-to-extracted-images>
```

### Key Settings in `application.yml`
- `app.cors.allowed-origins` — Angular dev server (`http://localhost:4200`)
- `embedding.compression.method: INT8` — reduces storage ~75%, quality threshold 0.98
- `spring.jpa.hibernate.ddl-auto: update` — preserves data on restart (dev mode)
- `spring.cloud.gcp.storage.enabled: false` — GCP storage disabled locally; enable for production
- `pgvector.dimension: 1536` — matches OpenAI embedding model (text-embedding-3-small)
- LibreOffice path (for DOCX→PDF conversion) is commented out — enable in `application.yml` if needed

### GCP Cloud Storage (Production)
- Requires `GOOGLE_APPLICATION_CREDENTIALS` pointing to service account JSON
- Enable `spring.cloud.gcp.storage.enabled: true` in production profile
- Images are stored in GCP Cloud Storage instead of local filesystem
- Terraform configs for GCP setup are in `/terraform/`

## Testing

### Unit Tests
- JUnit 5 (Jupiter) with Mockito, AssertJ, Spring Boot Test
- Redis, pgvector, external services mocked — no real infrastructure
- Use `@ExtendWith(MockitoExtension.class)` for pure unit tests
- Use `@WebMvcTest` or `@SpringBootTest` with mocks for controller/service tests
- Run: `./mvnw test -Dtest=!*IntegrationSpec`

### Integration Tests (Phase 09+)
- Location: `src/test/java/com/exemple/nexrag/service/rag/integration/`
- Framework: Testcontainers 1.19.7 + JUnit 5 + Spring Boot Test
- Real infrastructure: PostgreSQL 16 (pgvector), Redis 7-alpine, ClamAV latest
- External APIs: WireMock stubs (no real OpenAI API calls)
- Base class: `AbstractIntegrationSpec` handles container lifecycle and `@DynamicPropertySource` overrides
- Test configuration: `application-integration-test.yml` (Spring profile `integration-test`) — disables scheduled tasks and reduces log noise
- Classes: `IngestionPipelineIntegrationSpec`, `RetrievalPipelineIntegrationSpec`, `StreamingPipelineIntegrationSpec`, `RateLimitIntegrationSpec`, `FullRagPipelineIntegrationSpec`
- Code coverage: Contributes to JaCoCo 80% threshold on critical modules (ingestion, retrieval, streaming)
- Run: `./mvnw test -Dtest=*IntegrationSpec`

### Test Fixtures
Location: `src/test/resources/fixtures/`
- `sample.pdf` — Multi-page text document
- `sample.docx` — Word document
- `sample.xlsx` — Excel spreadsheet
- `sample.jpg` — JPEG image for vision analysis
- `sample.txt` — Plain text file

## Code Quality & Observability

### SonarQube Integration
- **Setup**: Configure `sonar.host.url` in pom.xml (default: `http://localhost:9000`)
- **Run analysis**: `./mvnw sonar:sonar`
- **Exclusions**: Configured in pom.xml — DTOs, configs, exceptions, utilities, aspect classes are excluded
- **Coverage threshold**: 80% on critical modules (ingestion, retrieval, streaming, voice, generation)
- **JaCoCo reports**: Generated in `target/site/jacoco/` after running tests

### Prometheus & Grafana
- Metrics exported via Micrometer to Prometheus (port 9090)
- Grafana dashboards at port 3000 (started via docker-compose)
- Key metrics: embedding dimensions, retrieval latency, ingestion throughput, cache hit rates
