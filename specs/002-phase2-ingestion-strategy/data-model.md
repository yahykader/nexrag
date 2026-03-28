# Data Model: Phase 2 — Ingestion : Stratégies, Cache & Orchestration

**Date**: 2026-03-27
**Branch**: `002-phase2-ingestion-strategy`

---

## Entités Clés

### 1. `IngestionStrategy` (interface)

| Champ / Méthode | Type | Règle |
|---|---|---|
| `canHandle(file, extension)` | `boolean` | Retourne `true` si la stratégie supporte ce type MIME/extension |
| `ingest(file, batchId)` | `IngestionResult` | Lance `IngestionException` si contenu vide ou erreur parsing |
| `getName()` | `String` | Nom lisible pour logs et métriques |
| `getPriority()` | `int` | Valeur basse = priorité haute ; défaut = 5 (fallback Tika) |

**Règle d'identité** : une stratégie est unique par type MIME supporté.
**Règle OCP** : `IngestionConfig` trie les strategies par `getPriority()` — l'orchestrateur ne connaît que l'interface.

---

### 2. `IngestionResult` (record)

| Champ | Type | Règle |
|---|---|---|
| `textEmbeddings` | `int` | ≥ 1 si ingestion réussie (contenu non vide) |
| `imageEmbeddings` | `int` | ≥ 0 (0 si document sans images) |
| `totalEmbeddings()` | `int` | `textEmbeddings + imageEmbeddings` |

**Invariant** : si `textEmbeddings == 0` et `imageEmbeddings == 0`, une `EmptyContentException` doit avoir été levée avant ce point.

---

### 3. `TextChunker.ChunkResult` (record)

| Champ | Type | Règle |
|---|---|---|
| `indexed` | `int` | Nombre de chunks effectivement indexés |
| `duplicates` | `int` | Chunks ignorés car déjà vus (dédup texte) |
| `total()` | `int` | `indexed + duplicates` |

**Règle de découpage** :
- Si `text.length() <= chunkSize` → 1 chunk max
- Sinon : `start += max(1, chunkSize - overlap)` à chaque itération
- Chunks < `MIN_CHUNK_LENGTH` (10 chars) sont ignorés silencieusement

---

### 4. `BatchEmbeddings` (tracker interne)

| Champ | Type | Règle |
|---|---|---|
| `batchId` | `String` | UUID unique par ingestion |
| `textEmbeddingIds` | `List<String>` | IDs stockés dans `textEmbeddingStore` (pgvector) |
| `imageEmbeddingIds` | `List<String>` | IDs stockés dans `imageEmbeddingStore` (pgvector) |

**Transition d'état** : STARTED → COMPLETED (si succès) / FAILED (si exception) → supprimé du registre après rollback ou nettoyage.

---

### 5. `BatchInfo` (DTO)

| Champ | Type | Règle |
|---|---|---|
| `batchId` | `String` | Identifiant unique |
| `filename` | `String` | Nom du fichier source |
| `mimeType` | `String` | Type MIME détecté |
| `textEmbeddingIds` | `List<String>` | Copie défensive |
| `imageEmbeddingIds` | `List<String>` | Copie défensive |
| `timestamp` | `Instant` | Horodatage de création |

---

### 6. Cache d'embedding

| Clé Redis | Format | TTL |
|---|---|---|
| `emb:<sha256-hash>` | vecteur sérialisé (Base64 JSON) | 7 jours (configurable) |
| `batch:emb:<batchId>` | Redis Set d'empreintes texte | 7 jours (configurable) |

**Règle de clé** : toutes les clés Redis sont définies dans `EmbeddingCacheRedisKeys` — aucune chaîne brute dans les services.
**Règle de TTL** : TTL exprimé en heures dans `EmbeddingCacheProperties.ttlHours` (défaut = 168 h = 7 jours).

---

### 7. `EmbeddingCompressor`

| Champ config | Valeur défaut | Contrainte |
|---|---|---|
| `embedding.compression.enabled` | `false` | Si `false`, vecteur retourné identique (no-op) |
| `embedding.compression.method` | `INT8` | Enum : `NONE`, `INT8`, `INT16` |
| `embedding.compression.dimensions` | `1536` | Dimension du modèle OpenAI text-embedding-3-small |

**Règle qualité** : perte MSE INT8 ≤ 2 %, similarity cosinus ≥ 0.98 après quantization.

---

## Transitions d'état du Batch

```
                   ┌──────────────────────────────────────────┐
  file soumis ───► │ STARTED (batchId créé, tracking actif)   │
                   └──────────────┬───────────────────────────┘
                                  │
              ┌───────────────────┼────────────────────────┐
              │ succès            │ exception               │
              ▼                   ▼                         │
  ┌────────────────────┐  ┌──────────────────────────┐      │
  │ COMPLETED          │  │ FAILED                   │      │
  │ clearBatch()       │  │ rollbackBatch()           │      │
  │ (IDs libérés,      │  │ (embeddings supprimés,    │      │
  │  métadonnées       │  │  batch retiré du registre)│      │
  │  conservées)       │  └──────────────────────────┘      │
  └────────────────────┘                                     │
                                                             │
  Note: DuplicateFileException → pas de rollback (rien indexé)
```

---

## Dépendances entre Entités (Phase 2)

```
IngestionOrchestrator
  ├── IngestionStrategy (liste triée par IngestionConfig)
  │     ├── PdfIngestionStrategy     → TextChunker → EmbeddingIndexer
  │     ├── DocxIngestionStrategy    → LibreOfficeConverter → TextChunker → EmbeddingIndexer
  │     ├── XlsxIngestionStrategy    → TextChunker → EmbeddingIndexer
  │     ├── ImageIngestionStrategy   → VisionAnalyzer → EmbeddingIndexer
  │     ├── TextIngestionStrategy    → TextChunker → EmbeddingIndexer
  │     └── TikaIngestionStrategy    → TextChunker → EmbeddingIndexer (fallback)
  │
  ├── IngestionTracker
  │     ├── BatchEmbeddingRegistry   → BatchEmbeddings
  │     ├── BatchInfoRegistry        → BatchInfo
  │     └── RollbackExecutor         → EmbeddingStore (text + image)
  │
  └── [Phase 1] AntivirusGuard, DeduplicationService (déjà testés)

EmbeddingIndexer
  ├── EmbeddingCache (L1 Caffeine + L2 Redis)
  │     └── EmbeddingCacheStore      → RedisTemplate
  ├── EmbeddingModel                 → OpenAI API (mocqué en tests)
  ├── EmbeddingCompressor            → INT8/INT16 quantization
  └── TextDeduplicationService       (déjà testé Phase 1)
```
