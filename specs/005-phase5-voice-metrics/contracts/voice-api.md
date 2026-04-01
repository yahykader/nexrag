# API Contract: Voice Transcription Endpoints

**Base path**: `/api/v1/voice`  
**Auth**: same mechanism as other NexRAG endpoints  
**Rate limit**: 10 requests/minute per authenticated user (voice bucket)

---

## POST /api/v1/voice/transcribe

Transcribes an audio file to text.

### Request

```
Content-Type: multipart/form-data

audio     (file, required)   — Audio file. Accepted: webm, mp3, wav, mp4.
                               Max size: 25 MB.
language  (string, optional) — ISO 639-1 language code (e.g., "fr", "en").
                               Default: "fr". Omit for auto-detection.
```

### Responses

**200 OK** — Transcription succeeded
```json
{
  "transcript": "Bonjour, quelle est la date de la dernière réunion ?",
  "language":   "fr",
  "durationMs": 1240
}
```

**400 Bad Request** — Invalid input (null, empty, unsupported format)
```json
{
  "error": "INVALID_AUDIO",
  "message": "Données audio vides ou absentes"
}
```

**413 Payload Too Large** — File exceeds 25 MB
```json
{
  "error": "AUDIO_TOO_LARGE",
  "message": "La taille du fichier audio dépasse la limite de 25 MB"
}
```

**429 Too Many Requests** — Rate limit exceeded
```json
{
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "Limite atteinte : 10 requêtes par minute. Réessayez dans 30s.",
  "retryAfterSeconds": 30
}
```

**503 Service Unavailable** — Transcription service unreachable after 3 retries
```json
{
  "error": "TRANSCRIPTION_SERVICE_UNAVAILABLE",
  "message": "Le service de transcription est temporairement indisponible"
}
```

### Privacy contract

- The `transcript` value in the response body is returned to the caller in full.
- Transcript content MUST NOT appear in server-side application logs.
- Raw audio bytes MUST NOT be logged at any verbosity level.

---

## GET /api/v1/voice/health

Returns the availability status of the transcription service.

### Responses

**200 OK**
```json
{
  "available": true,
  "model":     "whisper-1"
}
```

---

## GET /api/actuator/prometheus

Returns all RAG pipeline metrics in Prometheus exposition format (scraped by Prometheus server).

**Content-Type**: `text/plain`  
**Auth**: internal/ops network only

### Key metric families exposed

| Metric | Type | Labels |
|---|---|---|
| `rag_ingestion_files_total` | counter | strategy, status |
| `rag_cache_hits_total` | counter | cache |
| `rag_cache_misses_total` | counter | cache |
| `rag_api_calls_total` | counter | service, operation |
| `rag_api_duration_seconds` | histogram | service, operation |
| `rag_tokens_generated_total` | counter | — |
| `rag_active_ingestions` | gauge | component |
| `rag_active_queries` | gauge | component |
