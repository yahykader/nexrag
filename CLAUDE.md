# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**NexRAG** is a production-grade Retrieval-Augmented Generation (RAG) platform for multimodal document ingestion and conversational AI querying. It consists of:
- **Backend**: Spring Boot 3.4.2 (Java 21) — `/nex-rag/`
- **Frontend**: Angular 21 — `/agentic-rag-ui/`
- **Infrastructure**: Docker Compose (PostgreSQL/pgvector, Redis, ClamAV, Prometheus, Grafana, Zipkin)

## Common Commands

### Backend (Spring Boot)
```bash
# Run backend (from /nex-rag/)
./mvnw spring-boot:run

# Build (skip tests)
./mvnw clean package -DskipTests

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=ClassName

# Run a single test method
./mvnw test -Dtest=ClassName#methodName
```

### Frontend (Angular)
```bash
# Install dependencies (from /agentic-rag-ui/)
npm install

# Start dev server (port 4200)
npm start

# Build for production
npm run build

# Run tests (Vitest)
npm test
```

### Infrastructure
```bash
# Start all infrastructure services
docker-compose up -d

# Start with dev overrides
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up -d

# Start production
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

## Key Ports
| Service | Port |
|---------|------|
| Backend API | 8090 |
| Frontend | 4200 |
| PostgreSQL/pgvector | 5432 |
| Redis | 6379 |
| Redis Commander | 8081 |
| Zipkin | 9411 |
| ClamAV | 3310 |
| Prometheus | 9090 |
| Grafana | 3000 |

## Environment Variables

The backend reads from `/nex-rag/.env`:
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

## Architecture

### Three-Phase RAG Pipeline

**Phase 1 — Retrieval Augmentor** (Query-time):
1. Query Transformer: Generates 5 query variants with synonyms + temporal context
2. Query Router: Selects retrieval strategy (HYBRID by default, confidence ≥0.7)
3. Parallel Retrievers: Text (top-20, score ≥0.7), Image (top-5, score ≥0.6), BM25 (top-10)
4. RRF Aggregator: Reciprocal Rank Fusion (k=60), weights text:0.5/image:0.3/bm25:0.2
5. Reranker: Cross-encoder (ms-marco-MiniLM-L-6-v2), top-k=10
6. Content Injector: Optimized prompts with citations (max 200k tokens)

**Phase 2 — Streaming API**:
- WebSocket STOMP for real-time ingestion progress and chat
- SSE for LLM token streaming
- Redis-based deduplication and embedding cache

**Phase 3 — Ingestion Pipeline**:
- Strategy pattern per document type: PDF (PDFBox), DOCX/XLSX (Apache POI), images, text
- ClamAV antivirus scanning before processing
- Embedding compression: INT8 quantization
- Embedding model: `text-embedding-3-small` (dim=1536)
- LLM: `gpt-4o` (temp=0.7, max_tokens=2000)

### Backend Package Structure (`com.exemple.nex-rag`)
```
config/          — Spring configs (async, circuit breaker, CORS, pgvector, rate limiting, Redis, WebSocket)
service/rag/
  ingestion/     — Document parsing strategies, ClamAV scanning, embedding pipeline
  retrieval/     — Query transformation, routing, multi-source retrieval, reranking
  generation/    — LLM streaming, context management
controller/      — REST endpoints (upload, chat, streaming)
dto/             — Request/response models
metrics/         — Micrometer-based observability
```

### Frontend Package Structure (`/agentic-rag-ui/src/app/`)
```
core/
  services/      — WebSocket/STOMP, voice recognition (Whisper), HTTP interceptors
  models/        — TypeScript data models
features/
  chat/          — Conversational UI (NgRx state, STOMP messages)
  ingestion/     — File upload with real-time progress tracking
  management/    — Document list, query history
shared/          — Reusable components, directives
```

### State Management (Frontend)
The frontend uses **NgRx** (store, effects, entity) for state management across chat, ingestion, and document management features.

### Resilience Patterns
- **Circuit Breakers** (Resilience4j): Configured for visionAI, Redis, ClamAV — 50% failure threshold, 30s wait
- **Rate Limiting** (Bucket4j + Redis): upload=10/min, search=50/min, default=30/min
- **Retry**: 3 attempts, exponential backoff (1s→10s, multiplier=2.0)
- **Caching**: Caffeine (in-memory) + Redis (embeddings cached 7 days)

### Observability
- **Metrics**: Micrometer → Prometheus → Grafana (dashboards in `/grafana/`)
- **Tracing**: Zipkin via Brave bridge (100% sampling in dev)
- **Alerts**: Alertmanager config in `/alertmanager/`

## Key Configuration File
All RAG pipeline parameters are in [nex-rag/src/main/resources/application.yml](nex-rag/src/main/resources/application.yml) — 666 lines covering embedding dimensions, retrieval thresholds, circuit breaker settings, rate limits, and async thread pools.

## Deployment
- **GCP**: Terraform configs in `/terraform/`
- **CI/CD**: GitHub Actions in `.github/workflows/` — orchestrates GCP bootstrap, Terraform provisioning, Docker build/push, and VM deployment
- **Local**: `docker-compose.yml` starts all infrastructure; run backend and frontend separately
