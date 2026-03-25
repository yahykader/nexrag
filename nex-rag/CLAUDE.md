# CLAUDE.md — nex-rag (Backend)

Spring Boot 3.4.2 / Java 21 backend for the NexRAG platform.

## Commands

```bash
# Run (from this directory)
./mvnw spring-boot:run

# Build (skip tests)
./mvnw clean package -DskipTests

# Run all tests
./mvnw test

# Run single test class / method
./mvnw test -Dtest=ClassName
./mvnw test -Dtest=ClassName#methodName
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
| `langchain4j` 1.0.0-beta1 | Core AI framework (OpenAI, pgvector, parsers) |
| `langchain4j-pgvector` | Vector store integration |
| `langchain4j-document-parser-apache-pdfbox` | PDF parsing |
| `langchain4j-document-parser-apache-poi` | DOCX/XLSX parsing |
| `langchain4j-document-parser-apache-tika` | Fallback parser |
| `resilience4j-spring-boot3` | Circuit breakers + retry |
| `bucket4j_jdk17-lettuce` | Redis-backed rate limiting |
| `micrometer-registry-prometheus` | Metrics export |
| `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` | Distributed tracing |
| `springdoc-openapi-starter-webmvc-ui` 2.8.9 | Swagger UI at `/swagger-ui.html` |

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

- `app.cors.allowed-origins` — set to Angular dev server (`http://localhost:4200`)
- `embedding.compression.method: INT8` — reduces storage ~75%, quality threshold 0.98
- `spring.jpa.hibernate.ddl-auto: update` — preserves data on restart (dev mode)
- `spring.cloud.gcp.storage.enabled: false` — GCP storage off by default locally
- LibreOffice path (for DOCX→PDF conversion) is commented out — enable in `application.yml` if needed

## Testing Status

- **Only stub exists**: `TransactionServiceApplicationTests.java` (legacy placeholder, no real assertions)
- No unit or integration tests — do not assume test coverage when modifying services
