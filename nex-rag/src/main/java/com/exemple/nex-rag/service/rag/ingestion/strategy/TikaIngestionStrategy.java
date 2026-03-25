package com.exemple.nexrag.service.rag.ingestion.strategy;

import com.exemple.nexrag.exception.IngestionException;
import com.exemple.nexrag.dto.IngestionResult;
import com.exemple.nexrag.service.rag.ingestion.progress.ProgressNotifier;
import com.exemple.nexrag.service.rag.ingestion.strategy.commun.EmbeddingIndexer;
import com.exemple.nexrag.service.rag.ingestion.strategy.commun.IngestionLifecycle;
import com.exemple.nexrag.service.rag.ingestion.util.MetadataSanitizer;
import com.exemple.nexrag.service.rag.ingestion.util.StreamingFileReader;
import com.exemple.nexrag.service.rag.metrics.RAGMetrics;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Stratégie d'ingestion universelle Apache Tika — fallback 1000+ formats.
 *
 * Principe SRP  : unique responsabilité → parser un document via Tika
 *                 et extraire ses métadonnées enrichies.
 *                 L'indexation est dans {@link EmbeddingIndexer}.
 *                 Le cycle de vie est dans {@link IngestionLifecycle}.
 * Principe DIP  : dépend des abstractions des services partagés.
 * Clean code    : supprime {@code indexText()}, {@code truncate()},
 *                 le bloc catch et le bloc post-ingestion dupliqués.
 *
 * @author ayahyaoui
 * @version 2.0
 */
@Slf4j
@Component
public class TikaIngestionStrategy implements IngestionStrategy {

    // -------------------------------------------------------------------------
    // Dépendances spécifiques TIKA
    // -------------------------------------------------------------------------
    private final EmbeddingStore<TextSegment> textStore;
    private final MetadataSanitizer           sanitizer;
    private final RAGMetrics                  ragMetrics;
    private final ApacheTikaDocumentParser    tikaParser;

    // -------------------------------------------------------------------------
    // Services partagés
    // -------------------------------------------------------------------------
    private final EmbeddingIndexer   embeddingIndexer;
    private final IngestionLifecycle lifecycle;

    @Autowired(required = false)
    private ProgressNotifier progressNotifier;

    public TikaIngestionStrategy(
            @Qualifier("textEmbeddingStore") EmbeddingStore<TextSegment> textStore,
            MetadataSanitizer  sanitizer,
            RAGMetrics         ragMetrics,
            EmbeddingIndexer   embeddingIndexer,
            IngestionLifecycle lifecycle) {

        this.textStore       = textStore;
        this.sanitizer       = sanitizer;
        this.ragMetrics      = ragMetrics;
        this.embeddingIndexer = embeddingIndexer;
        this.lifecycle       = lifecycle;
        this.tikaParser      = new ApacheTikaDocumentParser();

        log.info("✅ [{}] Strategy initialisée (fallback 1000+ formats)", getName());
    }

    // -------------------------------------------------------------------------
    // IngestionStrategy API
    // -------------------------------------------------------------------------

    @Override
    public boolean canHandle(MultipartFile file, String extension) {
        return true; // fallback universel
    }

    @Override
    public IngestionResult ingest(MultipartFile file, String batchId)
            throws IOException, IngestionException {

        String filename  = file.getOriginalFilename();
        String extension = getExtension(filename);
        long   startTime = System.currentTimeMillis();

        try {
            notify(n -> n.uploadStarted(batchId, filename, file.getSize()));
            log.info("🔧 [{}] Traitement TIKA (fallback) : {} ({} MB, ext: {})",
                getName(), filename, file.getSize() / 1_000_000, extension.toUpperCase());

            if (file.isEmpty() || file.getSize() == 0) {
                throw new IOException("Fichier vide : " + filename);
            }

            notify(n -> n.uploadCompleted(batchId, filename));
            notify(n -> n.processingStarted(batchId, filename));

            IngestionResult result = StreamingFileReader.requiresStreaming(file)
                ? ingestWithStreaming(file, filename, extension, batchId, file.getSize())
                : ingestNormal(file, filename, extension, batchId);

            lifecycle.onSuccess(getName(), batchId, filename, result,
                System.currentTimeMillis() - startTime, progressNotifier);

            log.info("✅ [{}] TIKA traité : {} — {} chunks",
                getName(), filename, result.textEmbeddings());

            return result;

        } catch (Exception e) {
            lifecycle.onError(getName(), batchId, filename, e, progressNotifier);
            throw new IngestionException("Erreur TIKA : " + e.getMessage(), e);
        }
    }

    @Override
    public String getName()     { return "TIKA"; }
    @Override
    public int    getPriority() { return 10; } // fallback — priorité la plus basse

    // -------------------------------------------------------------------------
    // Routage streaming / normal
    // -------------------------------------------------------------------------

    private IngestionResult ingestNormal(MultipartFile file, String filename,
                                          String extension, String batchId) throws Exception {

        notify(n -> n.notifyProgress(batchId, filename, "TIKA_PARSING", 20,
            "Extraction Apache Tika..."));

        long     start    = System.currentTimeMillis();
        Document document = parseWithRetry(file);
        ragMetrics.recordApiCall("tika_parse", System.currentTimeMillis() - start);

        return processDocument(document, filename, extension, batchId);
    }

    private IngestionResult ingestWithStreaming(MultipartFile file, String filename,
                                                 String extension, String batchId,
                                                 long fileSize) throws Exception {

        Path tempFile = null;
        try {
            notify(n -> n.notifyProgress(batchId, filename, "STREAMING", 15,
                "Chargement en streaming..."));

            tempFile = StreamingFileReader.saveToTempFileWithProgress(file, bytesWritten -> {
                if (bytesWritten % (50 * 1024 * 1024) == 0 && progressNotifier != null) {
                    int pct = 15 + (int)((bytesWritten / (double)fileSize) * 10);
                    progressNotifier.notifyProgress(batchId, filename, "STREAMING", pct,
                        String.format("Chargement : %d MB", bytesWritten / 1_000_000));
                }
            });

            notify(n -> n.notifyProgress(batchId, filename, "TIKA_PARSING", 25,
                "Extraction Apache Tika..."));

            long     start    = System.currentTimeMillis();
            Document document = parseFromFileWithRetry(tempFile);
            ragMetrics.recordApiCall("tika_parse", System.currentTimeMillis() - start);

            return processDocument(document, filename, extension, batchId);

        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); }
                catch (IOException e) { log.warn("⚠️ Impossible de supprimer le temp : {}", e.getMessage()); }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Traitement du document Tika
    // -------------------------------------------------------------------------

    private IngestionResult processDocument(Document document, String filename,
                                             String extension, String batchId) throws Exception {

        if (document == null || document.text() == null || document.text().isBlank()) {
            throw new IngestionException("Tika n'a pas pu extraire le contenu : " + filename);
        }

        String content = document.text();
        log.debug("📝 [{}] {} caractères extraits", getName(), content.length());

        notify(n -> n.notifyProgress(batchId, filename, "METADATA", 35,
            "Extraction métadonnées..."));

        Map<String, Object> tikaMetadata = extractTikaMetadata(
            document.metadata(), filename, extension, batchId
        );

        if (tikaMetadata.containsKey("mimeType")) {
            log.info("🔍 [{}] MIME détecté : {}", getName(), tikaMetadata.get("mimeType"));
        }

        notify(n -> n.notifyProgress(batchId, filename, "CHUNKING", 40, "Découpage..."));

        ChunkResult chunks = chunkAndIndex(content, tikaMetadata, filename, batchId);

        if (chunks.duplicates() > 0) {
            log.info("⏭️ [Dedup] {} doublons ignorés, {} indexés",
                chunks.duplicates(), chunks.indexed());
        }

        Map<String, Object> resultMeta = new HashMap<>(tikaMetadata);
        resultMeta.put("strategy",   getName());
        resultMeta.put("parser",     "apache-tika");
        resultMeta.put("characters", content.length());

        return new IngestionResult(chunks.indexed(), 0, resultMeta);
    }

    // -------------------------------------------------------------------------
    // Chunking + indexation via EmbeddingIndexer
    // -------------------------------------------------------------------------

    private ChunkResult chunkAndIndex(String text, Map<String, Object> baseMeta,
                                       String filename, String batchId) {

        final int CHUNK_SIZE = 1000;
        final int OVERLAP    = 100;
        int indexed    = 0;
        int duplicates = 0;
        int chunkIndex = 0;
        int estimated  = text.length() <= CHUNK_SIZE ? 1
            : (int) Math.ceil(text.length() / (double)(CHUNK_SIZE - OVERLAP));

        if (text.length() <= CHUNK_SIZE) {
            notify(n -> n.notifyProgress(batchId, filename, "EMBEDDING", 50, "Embedding..."));
            String id = indexChunk(text.trim(), baseMeta, 0, batchId);
            if (id != null) {
                notify(n -> n.embeddingProgress(batchId, filename, 1, 1));
                return new ChunkResult(1, 0);
            }
            return new ChunkResult(0, 1);
        }

        int start = 0;
        while (start < text.length()) {
            int    end   = Math.min(start + CHUNK_SIZE, text.length());
            String chunk = text.substring(start, end).trim();

            if (chunk.length() > 10) {
                String id = indexChunk(chunk, baseMeta, chunkIndex, batchId);
                if (id != null) {
                    indexed++;
                    if (indexed % 10 == 0 || indexed == estimated) {
                        final int cur = indexed, tot = estimated;
                        notify(n -> n.embeddingProgress(batchId, filename, cur, tot));
                    }
                } else {
                    duplicates++;
                }
                chunkIndex++;
            }

            start += Math.max(1, CHUNK_SIZE - OVERLAP);
        }

        log.info("✅ [{}] {} chunks indexés ({} doublons ignorés)", getName(), indexed, duplicates);
        return new ChunkResult(indexed, duplicates);
    }

    /**
     * Indexe un chunk via {@link EmbeddingIndexer} avec les métadonnées Tika enrichies.
     */
    private String indexChunk(String chunk, Map<String, Object> baseMeta,
                               int chunkIndex, String batchId) {

        Map<String, Object> meta = new HashMap<>(baseMeta);
        meta.put("type",       "tika_text");
        meta.put("chunkIndex", chunkIndex);
        meta.put("batchId",    batchId);

        Metadata metadata = Metadata.from(sanitizer.sanitize(meta));
        return embeddingIndexer.indexText(chunk, metadata, batchId, textStore);
    }

    // -------------------------------------------------------------------------
    // Parsing Tika avec retry
    // -------------------------------------------------------------------------

    @Retryable(
        value       = {IOException.class, TimeoutException.class},
        maxAttempts = 3,
        backoff     = @Backoff(delay = 1000, multiplier = 2)
    )
    private Document parseWithRetry(MultipartFile file) throws IOException {
        try {
            return tikaParser.parse(file.getInputStream());
        } catch (Exception e) {
            ragMetrics.recordApiError("tika_parse");
            if (e instanceof IOException || e instanceof TimeoutException) throw (IOException) e;
            throw new IOException("Erreur parsing Tika", e);
        }
    }

    @Retryable(
        value       = {IOException.class, TimeoutException.class},
        maxAttempts = 3,
        backoff     = @Backoff(delay = 1000, multiplier = 2)
    )
    private Document parseFromFileWithRetry(Path path) throws IOException {
        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            return tikaParser.parse(fis);
        } catch (Exception e) {
            ragMetrics.recordApiError("tika_parse");
            if (e instanceof IOException || e instanceof TimeoutException) throw (IOException) e;
            throw new IOException("Erreur parsing Tika (fichier)", e);
        }
    }

    // -------------------------------------------------------------------------
    // Extraction métadonnées Tika
    // -------------------------------------------------------------------------

    private Map<String, Object> extractTikaMetadata(Metadata tika, String filename,
                                                      String extension, String batchId) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("filename",  filename);
        meta.put("extension", extension);
        meta.put("batchId",   batchId);
        meta.put("source",    "tika");

        if (tika == null) return meta;

        putIfPresent(meta, "mimeType",     tika, "Content-Type");
        putIfPresent(meta, "title",        tika, "title", "dc:title");
        putIfPresent(meta, "author",       tika, "author", "dc:creator", "Author", "creator");
        putIfPresent(meta, "creator",      tika, "Creator", "Application-Name");
        putIfPresent(meta, "creationDate", tika, "Creation-Date", "dcterms:created", "meta:creation-date");
        putIfPresent(meta, "modifiedDate", tika, "Last-Modified", "dcterms:modified", "Last-Save-Date");
        putIfPresent(meta, "language",     tika, "language", "dc:language", "meta:language");
        putIfPresent(meta, "keywords",     tika, "Keywords", "dc:subject", "meta:keyword");

        parseIntIfPresent(meta, "pageCount", tika, "xmpTPg:NPages", "Page-Count", "meta:page-count");
        parseIntIfPresent(meta, "wordCount", tika, "meta:word-count", "Word-Count");

        log.debug("📋 [{}] {} métadonnées Tika extraites", getName(), meta.size());
        return meta;
    }

    private void putIfPresent(Map<String, Object> target, String key,
                               Metadata tika, String... tikaKeys) {
        for (String k : tikaKeys) {
            String val = tika.get(k);
            if (val != null && !val.isBlank()) { target.put(key, val.trim()); return; }
        }
    }

    private void parseIntIfPresent(Map<String, Object> target, String key,
                                    Metadata tika, String... tikaKeys) {
        for (String k : tikaKeys) {
            String val = tika.get(k);
            if (val != null && !val.isBlank()) {
                try { target.put(key, Integer.parseInt(val.trim())); return; }
                catch (NumberFormatException ignored) {}
            }
        }
    }

    // -------------------------------------------------------------------------
    // Utilitaires
    // -------------------------------------------------------------------------

    private String getExtension(String filename) {
        if (filename == null || filename.isBlank()) return "unknown";
        int dot = filename.lastIndexOf('.');
        return (dot == -1 || dot == filename.length() - 1)
            ? "unknown"
            : filename.substring(dot + 1).toLowerCase();
    }

    @FunctionalInterface
    private interface NotifierAction { void execute(ProgressNotifier n); }

    private void notify(NotifierAction action) {
        if (progressNotifier != null) action.execute(progressNotifier);
    }

    // -------------------------------------------------------------------------
    // Records internes
    // -------------------------------------------------------------------------

    private record ChunkResult(int indexed, int duplicates) {}

    // -------------------------------------------------------------------------
    // Informations statiques
    // -------------------------------------------------------------------------

    public static String[] getSupportedFormatExamples() {
        return new String[]{
            "doc", "ppt", "xls", "vsd",
            "odt", "ods", "odp", "odg",
            "pages", "numbers", "key",
            "epub", "mobi", "azw", "fb2",
            "zip", "rar", "7z", "tar", "gz",
            "tex", "bib", "rtf", "dwg", "dxf",
            "mp3", "mp4", "msg", "eml", "pst"
        };
    }

    public static String getCapabilities() {
        return "Apache Tika fallback — 1000+ formats, Office legacy, " +
               "OpenOffice, iWork, eBooks, archives, CAD, audio/video metadata.";
    }
}