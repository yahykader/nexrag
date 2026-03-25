package com.exemple.nexrag.service.rag.ingestion.strategy;

import com.exemple.nexrag.exception.IngestionException;
import com.exemple.nexrag.dto.IngestionResult;
import com.exemple.nexrag.service.rag.ingestion.progress.ProgressNotifier;
import com.exemple.nexrag.service.rag.ingestion.strategy.commun.EmbeddingIndexer;
import com.exemple.nexrag.service.rag.ingestion.strategy.commun.IngestionLifecycle;
import com.exemple.nexrag.service.rag.ingestion.util.MetadataSanitizer;
import com.exemple.nexrag.service.rag.ingestion.util.StreamingFileReader;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Stratégie d'ingestion pour fichiers texte (40+ formats).
 *
 * Principe SRP  : unique responsabilité → lire, détecter le type et découper
 *                 le contenu texte. Le chunking adaptatif et les métadonnées
 *                 par type sont propres à cette stratégie — ils restent ici.
 *                 L'indexation embeddings est dans {@link EmbeddingIndexer}.
 *                 Le cycle de vie est dans {@link IngestionLifecycle}.
 * Principe DIP  : dépend des abstractions des services partagés.
 * Clean code    : supprime {@code indexText()}, {@code truncate()},
 *                 le bloc catch et le bloc post-ingestion dupliqués.
 *                 {@code chunkAndIndexText()} est conservé car les métadonnées
 *                 par chunk sont dynamiques (linesOfCode, hasHeaders, rows…)
 *                 et ne peuvent pas être portées par {@link com.exemple.nexrag.service.rag.ingestion.strategy.commun.TextChunker}.
 *
 * @author ayhayoui
 * @version 2.0
 */
@Slf4j
@Component
public class TextIngestionStrategy implements IngestionStrategy {

    // -------------------------------------------------------------------------
    // Dépendances spécifiques TEXT
    // -------------------------------------------------------------------------
    private final EmbeddingStore<TextSegment> textStore;
    private final MetadataSanitizer           sanitizer;

    // -------------------------------------------------------------------------
    // Services partagés
    // -------------------------------------------------------------------------
    private final EmbeddingIndexer   embeddingIndexer;
    private final IngestionLifecycle lifecycle;

    @Autowired(required = false)
    private ProgressNotifier progressNotifier;

    // -------------------------------------------------------------------------
    // Formats supportés
    // -------------------------------------------------------------------------

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
        "txt", "text", "log",
        "md", "markdown",
        "csv", "tsv", "json", "xml", "yaml", "yml",
        "html", "htm", "css",
        "java", "py", "js", "ts", "jsx", "tsx",
        "c", "cpp", "h", "hpp",
        "go", "rs", "rb", "php",
        "swift", "kt", "kts",
        "sql", "sh", "bash", "zsh",
        "properties", "conf", "config", "ini", "env",
        "rst", "adoc", "tex"
    );

    private static final Set<String> CODE_EXTENSIONS = Set.of(
        "java", "py", "js", "ts", "jsx", "tsx",
        "c", "cpp", "h", "hpp", "go", "rs", "rb",
        "php", "swift", "kt", "kts", "sql"
    );

    public TextIngestionStrategy(
            @Qualifier("textEmbeddingStore") EmbeddingStore<TextSegment> textStore,
            MetadataSanitizer  sanitizer,
            EmbeddingIndexer   embeddingIndexer,
            IngestionLifecycle lifecycle) {

        this.textStore       = textStore;
        this.sanitizer       = sanitizer;
        this.embeddingIndexer = embeddingIndexer;
        this.lifecycle       = lifecycle;

        log.info("✅ [{}] Strategy initialisée (40+ formats)", getName());
    }

    // -------------------------------------------------------------------------
    // IngestionStrategy API
    // -------------------------------------------------------------------------

    @Override
    public boolean canHandle(MultipartFile file, String extension) {
        return SUPPORTED_EXTENSIONS.contains(extension.toLowerCase());
    }

    @Override
    public IngestionResult ingest(MultipartFile file, String batchId)
            throws IOException, IngestionException {

        String filename  = file.getOriginalFilename();
        String extension = getExtension(filename);
        long   startTime = System.currentTimeMillis();

        try {
            notify(n -> n.uploadStarted(batchId, filename, file.getSize()));
            log.info("📄 [{}] Traitement texte : {} ({} MB, ext: {})",
                getName(), filename, file.getSize() / 1_000_000, extension.toUpperCase());

            if (file.isEmpty() || file.getSize() == 0) {
                throw new IOException("Fichier texte vide : " + filename);
            }

            notify(n -> n.uploadCompleted(batchId, filename));
            notify(n -> n.processingStarted(batchId, filename));

            IngestionResult result = StreamingFileReader.requiresStreaming(file)
                ? ingestWithStreaming(file, filename, extension, batchId, file.getSize())
                : ingestNormal(file, filename, extension, batchId, file.getSize());

            lifecycle.onSuccess(getName(), batchId, filename, result,
                System.currentTimeMillis() - startTime, progressNotifier);

            log.info("✅ [{}] Texte traité : {} — {} chunks",
                getName(), filename, result.textEmbeddings());

            return result;

        } catch (Exception e) {
            lifecycle.onError(getName(), batchId, filename, e, progressNotifier);
            throw new IngestionException("Erreur TEXT : " + e.getMessage(), e);
        }
    }

    @Override
    public String getName()     { return "TEXT"; }
    @Override
    public int    getPriority() { return 8; }

    // -------------------------------------------------------------------------
    // Utilitaires statiques
    // -------------------------------------------------------------------------

    public static Set<String> getSupportedExtensions() { return Set.copyOf(SUPPORTED_EXTENSIONS); }
    public static boolean     isSupported(String ext)  { return SUPPORTED_EXTENSIONS.contains(ext.toLowerCase()); }

    // -------------------------------------------------------------------------
    // Routage streaming / normal
    // -------------------------------------------------------------------------

    private IngestionResult ingestNormal(MultipartFile file, String filename,
                                          String extension, String batchId, long fileSize)
            throws Exception {

        notify(n -> n.notifyProgress(batchId, filename, "READING", 20, "Lecture..."));
        String content = readWithEncodingDetection(file.getBytes(), filename);

        if (content == null || content.isBlank()) {
            throw new IngestionException("Fichier sans contenu : " + filename);
        }

        return processContent(content, filename, extension, batchId);
    }

    private IngestionResult ingestWithStreaming(MultipartFile file, String filename,
                                                 String extension, String batchId, long fileSize)
            throws Exception {

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

            notify(n -> n.notifyProgress(batchId, filename, "READING", 25, "Lecture..."));
            String content = readWithEncodingDetection(Files.readAllBytes(tempFile), filename);

            if (content == null || content.isBlank()) {
                throw new IngestionException("Fichier sans contenu : " + filename);
            }

            return processContent(content, filename, extension, batchId);

        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); }
                catch (IOException e) { log.warn("⚠️ Impossible de supprimer le temp : {}", e.getMessage()); }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Traitement du contenu
    // -------------------------------------------------------------------------

    private IngestionResult processContent(String content, String filename,
                                            String extension, String batchId) {

        notify(n -> n.notifyProgress(batchId, filename, "ANALYSIS", 30, "Analyse..."));
        String contentType = detectContentType(content, extension);
        log.info("🔍 [{}] Type détecté : {}", getName(), contentType);

        notify(n -> n.notifyProgress(batchId, filename, "CHUNKING", 40, "Découpage..."));
        ChunkConfig config = getChunkConfig(contentType);

        log.debug("📏 [{}] Config : taille={} overlap={} (type: {})",
            getName(), config.size(), config.overlap(), contentType);

        ChunkResult result = chunkAndIndex(
            content, filename, extension, contentType, config, batchId
        );

        if (result.duplicates() > 0) {
            log.info("⏭️ [Dedup] {} doublons ignorés, {} indexés",
                result.duplicates(), result.indexed());
        }

        return new IngestionResult(result.indexed(), 0, Map.of(
            "strategy",    getName(),
            "filename",    filename,
            "extension",   extension,
            "contentType", contentType,
            "characters",  content.length()
        ));
    }

    // -------------------------------------------------------------------------
    // Chunking adaptatif avec métadonnées par chunk dynamiques
    //
    // Note : TextChunker n'est pas utilisé ici car les métadonnées par chunk
    // dépendent du contenu du chunk (linesOfCode, hasHeaders, rows…).
    // EmbeddingIndexer est utilisé pour l'indexation.
    // -------------------------------------------------------------------------

    private ChunkResult chunkAndIndex(String content, String filename,
                                       String extension, String contentType,
                                       ChunkConfig config, String batchId) {

        int indexed    = 0;
        int duplicates = 0;
        int chunkIndex = 0;
        int estimated  = content.length() <= config.size() ? 1
            : (int) Math.ceil(content.length() / (double)(config.size() - config.overlap()));

        if (content.length() <= config.size()) {
            notify(n -> n.notifyProgress(batchId, filename, "EMBEDDING", 50, "Embedding..."));
            String id = indexChunk(content.trim(), filename, extension, contentType, 0, batchId);
            if (id != null) {
                notify(n -> n.embeddingProgress(batchId, filename, 1, 1));
                return new ChunkResult(1, 0);
            }
            return new ChunkResult(0, 1);
        }

        int start = 0;
        while (start < content.length()) {
            int    end   = Math.min(start + config.size(), content.length());
            String chunk = content.substring(start, end).trim();

            if (chunk.length() > 10) {
                String id = indexChunk(chunk, filename, extension, contentType, chunkIndex, batchId);
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

            start += Math.max(1, config.size() - config.overlap());
        }

        log.info("✅ [{}] {} chunks indexés ({} doublons ignorés)", getName(), indexed, duplicates);
        return new ChunkResult(indexed, duplicates);
    }

    /**
     * Indexe un chunk via {@link EmbeddingIndexer} avec les métadonnées enrichies
     * par type de contenu (spécifique à TEXT — dynamique par chunk).
     */
    private String indexChunk(String chunk, String filename, String extension,
                               String contentType, int chunkIndex, String batchId) {

        Map<String, Object> meta = new HashMap<>();
        meta.put("source",     filename);
        meta.put("extension",  extension);
        meta.put("type",       contentType);
        meta.put("chunkIndex", chunkIndex);
        meta.put("batchId",    batchId);

        addTypeSpecificMetadata(meta, chunk, contentType);

        Metadata metadata = Metadata.from(sanitizer.sanitize(meta));
        return embeddingIndexer.indexText(chunk, metadata, batchId, textStore);
    }

    // -------------------------------------------------------------------------
    // Détection encodage
    // -------------------------------------------------------------------------

    private String readWithEncodingDetection(byte[] bytes, String filename) throws IOException {
        try {
            String content = new String(bytes, StandardCharsets.UTF_8);
            if (!content.contains("\uFFFD")) {
                log.debug("✓ [{}] Encodage : UTF-8", getName());
                return content;
            }
        } catch (Exception ignored) {}

        try {
            String content = new String(bytes, "ISO-8859-1");
            log.debug("✓ [{}] Encodage : ISO-8859-1 (fallback)", getName());
            return content;
        } catch (Exception e) {
            throw new IOException("Impossible de lire le fichier : " + filename, e);
        }
    }

    // -------------------------------------------------------------------------
    // Détection type de contenu
    // -------------------------------------------------------------------------

    private String detectContentType(String content, String extension) {
        String trimmed = content.trim();

        if ("json".equals(extension) || trimmed.startsWith("{") || trimmed.startsWith("["))
            return "json";
        if ("xml".equals(extension) || "html".equals(extension) || "htm".equals(extension)
                || trimmed.startsWith("<"))
            return "html".equals(extension) || "htm".equals(extension) ? "html" : "xml";
        if ("csv".equals(extension)  || "tsv".equals(extension))  return "csv";
        if ("md".equals(extension)   || "markdown".equals(extension)) return "markdown";
        if (CODE_EXTENSIONS.contains(extension)) return "code_" + extension;
        if ("yaml".equals(extension) || "yml".equals(extension))   return "yaml";
        if (Set.of("properties", "conf", "config", "ini", "env").contains(extension))
            return "config";
        if (Set.of("rst", "adoc", "tex").contains(extension)) return "documentation";
        return "text";
    }

    // -------------------------------------------------------------------------
    // Configuration chunking adaptative par type
    // -------------------------------------------------------------------------

    private ChunkConfig getChunkConfig(String contentType) {
        if (contentType.startsWith("code_"))                          return new ChunkConfig(1500, 200);
        if ("json".equals(contentType) || "xml".equals(contentType)) return new ChunkConfig(800,  100);
        if ("csv".equals(contentType))                                return new ChunkConfig(1200, 50);
        if ("markdown".equals(contentType))                           return new ChunkConfig(1000, 100);
        return new ChunkConfig(1000, 100);
    }

    // -------------------------------------------------------------------------
    // Métadonnées dynamiques par chunk (spécifiques TEXT — dépendent du contenu)
    // -------------------------------------------------------------------------

    private void addTypeSpecificMetadata(Map<String, Object> meta, String chunk, String contentType) {
        if (contentType.startsWith("code_")) {
            meta.put("language",    contentType.substring(5));
            meta.put("linesOfCode", chunk.split("\n").length);
        }
        if ("csv".equals(contentType)) {
            meta.put("rows",   chunk.split("\n").length);
            meta.put("format", "csv");
        }
        if ("json".equals(contentType))     meta.put("format",     "json");
        if ("markdown".equals(contentType)) meta.put("hasHeaders", chunk.contains("#"));
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

    private record ChunkConfig(int size, int overlap) {}
    private record ChunkResult(int indexed, int duplicates) {}
}