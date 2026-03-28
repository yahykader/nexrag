# Contrats de Test — Phase 2

**Date**: 2026-03-27
**Branch**: `002-phase2-ingestion-strategy`

Ces contrats définissent le comportement attendu (interfaces comportementales) de chaque composant Phase 2, indépendamment de l'implémentation. Ils guident la rédaction des `*Spec.java`.

---

## Contrat 1 : `IngestionStrategy` (interface)

```
canHandle(file, extension) → boolean
  GIVEN fichier PDF + extension "pdf"   → true   (PdfIngestionStrategy)
  GIVEN fichier DOCX + extension "docx" → true   (DocxIngestionStrategy)
  GIVEN fichier XLSX + extension "xlsx" → true   (XlsxIngestionStrategy)
  GIVEN image JPEG + extension "jpg"    → true   (ImageIngestionStrategy)
  GIVEN fichier txt + extension "txt"   → true   (TextIngestionStrategy)
  GIVEN type MIME inconnu               → true   (TikaIngestionStrategy, fallback)
  GIVEN extension "pdf" sur TikaStrategy → false (priorité plus basse, jamais sélectionné en premier)

ingest(file, batchId) → IngestionResult
  GIVEN PDF 3 pages                     → textEmbeddings ≥ 3
  GIVEN DOCX corrompu                   → IngestionException (message contient le nom du fichier)
  GIVEN JPEG valide                     → VisionAnalyzer appelé (vérifié par mock)
  GIVEN texte vide extrait              → EmptyContentException (rollback déclenché)
```

---

## Contrat 2 : `TextChunker`

```
chunk(text, chunkSize, overlap) → ChunkResult
  GIVEN texte 1000 chars, chunkSize=200, overlap=50 → indexed ≥ 6
  GIVEN texte 50 chars, chunkSize=500, overlap=50   → indexed == 1
  GIVEN deux chunks consécutifs                     → fin(chunk[n])[-overlap:] == début(chunk[n+1])[:overlap]
  GIVEN texte vide ("")                             → indexed == 0
  GIVEN chunk < MIN_CHUNK_LENGTH (10 chars)         → chunk ignoré silencieusement
```

---

## Contrat 3 : `EmbeddingIndexer`

```
indexText(text, metadata, batchId, store) → String | null
  GIVEN texte non dupliqué        → embedding calculé, stocké, ID retourné non-null
  GIVEN texte déjà vu (dédup)     → null retourné, store.add() NON appelé
  GIVEN texte en cache            → embeddingModel.embed() NON appelé, cache.getAndTrack() appelé
  GIVEN texte absent du cache     → embeddingModel.embed() appelé UNE SEULE fois, résultat mis en cache

indexImageDescription(desc, metadata, batchId, store) → String
  GIVEN description Vision AI     → pas de déduplication texte, store.add() appelé
```

---

## Contrat 4 : `EmbeddingCacheStore`

```
get(textHash) → String | null
  GIVEN hash présent dans Redis   → sérialization retournée
  GIVEN hash absent               → null

save(textHash, serialized, ttlHours)
  GIVEN ttlHours=168             → clé Redis expire après 168 heures

deleteByBatchId(batchId) → int
  GIVEN batch avec 3 embeddings  → 3 clés supprimées, clé batch supprimée
  GIVEN batch inexistant         → 0 (pas d'exception)
```

---

## Contrat 5 : `EmbeddingCompressor`

```
quantizeInt8(embedding) → Embedding
  GIVEN compression désactivée   → vecteur original retourné identique
  GIVEN compression activée      → perte MSE ≤ 2%, similarity cosinus ≥ 0.98
  GIVEN vecteur 1536 dims        → vecteur retourné toujours 1536 dims (Float32 après dequantization)

calculateStats(original, compressed) → CompressionStats
  GIVEN INT8 méthode             → reductionPercent ≈ 75%
```

---

## Contrat 6 : `IngestionTracker` + `RollbackExecutor`

```
rollbackBatch(batchId) → int
  GIVEN batch avec 5 text + 2 image embeddings → 7 suppressions, 0 orphelins
  GIVEN batch inexistant                       → 0 retourné, pas d'exception
  GIVEN store.remove() lève une exception      → log warning, suppression continue (best-effort)
  GIVEN rollback appelé deux fois              → deuxième appel retourne 0 (idempotent)

addTextEmbeddingId(batchId, embeddingId)
  GIVEN batchId ou embeddingId blank → ignoré silencieusement (pas d'exception)
```

---

## Contrat 7 : `IngestionOrchestrator` (séquencement)

```
ingestFile(file, batchId) — ordre strict validé par InOrder Mockito
  1. antivirusGuard.assertClean(file)             si antivirus activé
  2. selectStrategy(file, extension)
  3. deduplicationService.computeHash(bytes)
  4. strategy.ingest(file, batchId)
  5. ragMetrics.recordIngestionSuccess(...)

Exception dans strategy.ingest()
  → rollbackSafely(batchId) appelé
  → ragMetrics.recordIngestionError() appelé
  → exception re-propagée

EmptyContentException (IngestionResult avec 0 embeddings)
  → traité comme erreur d'ingestion → rollback déclenché
```
