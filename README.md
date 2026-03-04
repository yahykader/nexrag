# 🧠 NexRAG

> **Next-Generation Retrieval-Augmented Generation Platform**

*"Ask anything. From any document."*

---

## 📖 Description

NexRAG est une plateforme intelligente d'ingestion et d'interrogation documentaire multimodale.  
Uploadez vos documents, posez vos questions — NexRAG retrouve, raisonne et répond en temps réel.

NexRAG est une plateforme RAG full-stack conçue pour ingérer, vectoriser et interroger des documents multimodaux (PDF, DOCX, XLSX, images, texte) via une interface conversationnelle assistée par IA.

---

## ✨ Fonctionnalités

- 📂 **Ingestion multimodale** — PDF, DOCX, XLSX, images, texte et 1000+ formats
- ⚡ **Pipeline asynchrone/synchrone** — traitement en arrière-plan avec suivi temps réel
- 🔍 **Retrieval sémantique** — recherche vectorielle dans vos documents
- 💬 **Interface conversationnelle** — posez des questions en langage naturel
- 🎙️ **Reconnaissance vocale** — transcription audio via OpenAI Whisper
- 📡 **Streaming temps réel** — réponses streamées via WebSocket STOMP
- 🖼️ **Traitement d'images** — extraction et analyse de contenu visuel

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│                     NexRAG Platform                      │
├──────────────────┬──────────────────┬───────────────────┤
│   📥 Ingestion   │   🔍 Retrieval   │   💬 Streaming    │
│                  │                  │                   │
│  Upload fichiers │  Embeddings      │  WebSocket STOMP  │
│  Chunking        │  Recherche       │  LLM Streaming    │
│  Vectorisation   │  sémantique      │  Whisper ASR      │
└──────────────────┴──────────────────┴───────────────────┘
         │                  │                   │
┌────────▼──────────────────▼───────────────────▼────────┐
│              Spring Boot Backend (Java)                  │
├────────────────────────────────────────────────────────┤
│              Angular 21 Frontend                         │
└────────────────────────────────────────────────────────┘
```

---

## 🛠️ Stack Technique

### Backend
| Technologie | Usage |
|-------------|-------|
| **Java Spring Boot** | API REST, pipeline d'ingestion |
| **WebSocket STOMP** | Suivi temps réel de l'ingestion |
| **Spring AI / LangChain4j** | Pipeline RAG, embeddings |
| **OpenAI Whisper** | Transcription audio |
| **PostgreSQL / pgvector** | Stockage vectoriel |

### Frontend
| Technologie | Usage |
|-------------|-------|
| **Angular 21** | Interface standalone, signals |
| **NgRx** | State management |
| **STOMP.js** | Client WebSocket |
| **Bootstrap 5** | UI components |

### IA
| Technologie | Usage |
|-------------|-------|
| **RAG Pipeline** | Retrieval-Augmented Generation |
| **LLM Streaming** | Réponses en temps réel |
| **OpenAI Whisper** | Speech-to-Text |
| **Embeddings vectoriels** | Recherche sémantique |

---

## 🚀 Démarrage rapide

### Prérequis

- Java 21+
- Node.js 20+
- Angular CLI 17+
- Docker & Docker Compose

### Installation

```bash
# Cloner le projet
git clone https://github.com/votre-user/nexrag.git
cd nexrag

# Lancer l'infrastructure (DB, etc.)
docker-compose up -d

# Backend
cd backend
./mvnw spring-boot:run

# Frontend
cd frontend
npm install
ng serve
```

L'application est disponible sur `http://localhost:4200`

### Configuration

```yaml
# application.yml
openai:
  api:
    key: ${OPENAI_API_KEY}

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/nexrag
```

---

## 📡 API

### Ingestion

```http
POST /api/v1/documents/upload
Content-Type: multipart/form-data

audio: <file>
mode: async | sync
```

### Transcription vocale

```http
POST /api/v1/voice/transcribe
Content-Type: multipart/form-data

audio: <file>
language: fr
```

### Chat RAG

```http
POST /api/v1/chat/stream
Content-Type: application/json

{
  "question": "Quels sont les points clés du document ?",
  "conversationId": "uuid"
}
```

### WebSocket

```
ws://localhost:8090/ws
Topic: /topic/upload-progress/{batchId}
```

---


---

## 📁 Structure du projet

```
nexrag/
├── backend/                  # Spring Boot
│   ├── src/main/java/
│   │   ├── config/           # WebSocket, CORS, Security
│   │   ├── controller/       # REST API endpoints
│   │   ├── service/
│   │   │   ├── ingestion/    # Pipeline ingestion
│   │   │   ├── rag/          # RAG & retrieval
│   │   │   └── voice/        # Whisper transcription
│   │   └── websocket/        # STOMP handlers
│   └── pom.xml
│
├── frontend/                 # Angular 21
│   ├── src/app/
│   │   ├── core/
│   │   │   └── services/     # VoiceService, WebSocketService
│   │   ├── features/
│   │   │   ├── chat/         # Interface conversationnelle
│   │   │   └── upload/       # Ingestion documents
│   │   └── shared/
│   │       └── components/   # VoiceButton, MessageInput
│   ├── proxy.conf.json
│   └── angular.json
│
└── docker-compose.yml
```

---

## 🔧 Développement

```bash
# Dev avec proxy Angular → Backend
ng serve --proxy-config proxy.conf.json

# Build production
ng build --configuration production

# Tests
./mvnw test
ng test
```

### Proxy configuration (développement)

```json
{
  "/api": {
    "target": "http://localhost:8090",
    "secure": false,
    "changeOrigin": true
  },
  "/ws": {
    "target": "http://localhost:8090",
    "ws": true,
    "secure": false
  }
}
```

---

## 🐳 Docker

```bash
# Build et run
docker-compose up --build

# Production
docker-compose -f docker-compose.prod.yml up -d
```

---

<div align="center">

**NexRAG** — *From raw files to smart answers, instantly.*

Built with ☕ Java + 🅰️ Angular + 🤖 OpenAI

</div>