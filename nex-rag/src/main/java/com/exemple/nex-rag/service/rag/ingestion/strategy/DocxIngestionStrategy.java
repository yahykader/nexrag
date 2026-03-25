package com.exemple.nexrag.service.rag.ingestion.strategy;

import com.exemple.nexrag.exception.IngestionException;
import com.exemple.nexrag.service.rag.ingestion.analyzer.ImageSaver;
import com.exemple.nexrag.service.rag.ingestion.analyzer.VisionAnalyzer;
import com.exemple.nexrag.dto.IngestionResult;
import com.exemple.nexrag.service.rag.ingestion.progress.ProgressNotifier;
import com.exemple.nexrag.service.rag.ingestion.strategy.commun.EmbeddingIndexer;
import com.exemple.nexrag.service.rag.ingestion.strategy.commun.IngestionLifecycle;
import com.exemple.nexrag.service.rag.ingestion.strategy.commun.TextChunker;
import com.exemple.nexrag.service.rag.ingestion.util.FileUtils;
import com.exemple.nexrag.service.rag.ingestion.util.MetadataSanitizer;
import com.exemple.nexrag.service.rag.ingestion.util.StreamingFileReader;
import com.exemple.nexrag.service.rag.metrics.RAGMetrics;
import com.exemple.nexrag.validation.FileSignatureValidator;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Stratégie d'ingestion pour fichiers DOCX.
 *
 * Principe SRP  : unique responsabilité → extraire le contenu d'un DOCX.
 *                 Le chunking est dans {@link TextChunker}.
 *                 L'indexation est dans {@link EmbeddingIndexer}.
 *                 Le cycle de vie est dans {@link IngestionLifecycle}.
 * Principe DIP  : dépend des abstractions des 3 services partagés,
 *                 pas de leurs implémentations.
 * Clean code    : 12 dépendances → 9. Supprime indexText(),
 *                 chunkAndIndexText(), truncate() et le bloc catch dupliqués.
 *
 * @author ayahyaoui
 * @version 2.0
 */
@Slf4j
@Component
public class DocxIngestionStrategy implements IngestionStrategy {

    // -------------------------------------------------------------------------
    // Dépendances spécifiques DOCX
    // -------------------------------------------------------------------------
    private final EmbeddingStore<TextSegment> textStore;
    private final EmbeddingStore<TextSegment> imageStore;
    private final VisionAnalyzer              visionAnalyzer;
    private final ImageSaver                  imageSaver;
    private final FileSignatureValidator      signatureValidator;
    private final MetadataSanitizer           sanitizer;
    private final RAGMetrics                  ragMetrics;

    // -------------------------------------------------------------------------
    // Services partagés (remplacent 6 dépendances dupliquées)
    // -------------------------------------------------------------------------
    private final EmbeddingIndexer   embeddingIndexer;
    private final TextChunker        textChunker;
    private final IngestionLifecycle lifecycle;

    @Autowired(required = false)
    private ProgressNotifier progressNotifier;

    @Value("${document.max-images-per-file:100}")
    private int maxImagesPerFile;

    @Value("${document.docx.timeout-seconds:300}")
    private int docxTimeoutSeconds;

    public DocxIngestionStrategy(
            @Qualifier("textEmbeddingStore")  EmbeddingStore<TextSegment> textStore,
            @Qualifier("imageEmbeddingStore") EmbeddingStore<TextSegment> imageStore,
            VisionAnalyzer         visionAnalyzer,
            ImageSaver             imageSaver,
            FileSignatureValidator signatureValidator,
            MetadataSanitizer      sanitizer,
            RAGMetrics             ragMetrics,
            EmbeddingIndexer       embeddingIndexer,
            TextChunker            textChunker,
            IngestionLifecycle     lifecycle) {

        this.textStore          = textStore;
        this.imageStore         = imageStore;
        this.visionAnalyzer     = visionAnalyzer;
        this.imageSaver         = imageSaver;
        this.signatureValidator = signatureValidator;
        this.sanitizer          = sanitizer;
        this.ragMetrics         = ragMetrics;
        this.embeddingIndexer   = embeddingIndexer;
        this.textChunker        = textChunker;
        this.lifecycle          = lifecycle;

        log.info("✅ [{}] Strategy initialisée", getName());
    }

    // -------------------------------------------------------------------------
    // IngestionStrategy API
    // -------------------------------------------------------------------------

    @Override
    public boolean canHandle(MultipartFile file, String extension) {
        return "docx".equals(extension);
    }

    @Override
    public IngestionResult ingest(MultipartFile file, String batchId)
            throws IOException, IngestionException {

        String filename  = file.getOriginalFilename();
        long   startTime = System.currentTimeMillis();

        try {
            notify(n -> n.uploadStarted(batchId, filename, file.getSize()));
            log.info("📘 [{}] Traitement DOCX : {} ({} MB)",
                getName(), filename, file.getSize() / 1_000_000);

            if (file.isEmpty() || file.getSize() == 0) {
                throw new IOException("Fichier DOCX vide : " + filename);
            }

            notify(n -> n.notifyProgress(batchId, filename, "VALIDATION", 8, "Validation..."));
            signatureValidator.validate(file, "docx");
            notify(n -> n.processingStarted(batchId, filename));

            IngestionResult result = StreamingFileReader.requiresStreaming(file)
                ? ingestWithStreaming(file, filename, batchId)
                : ingestNormal(file, filename, batchId);

            lifecycle.onSuccess(getName(), batchId, filename, result,
                System.currentTimeMillis() - startTime, progressNotifier);

            log.info("✅ [{}] DOCX traité : {} — text={} images={}",
                getName(), filename, result.textEmbeddings(), result.imageEmbeddings());

            return result;

        } catch (Exception e) {
            lifecycle.onError(getName(), batchId, filename, e, progressNotifier);
            throw new IngestionException("Erreur DOCX : " + e.getMessage(), e);
        }
    }

    @Override
    public String getName()     { return "DOCX"; }
    @Override
    public int    getPriority() { return 2; }

    // -------------------------------------------------------------------------
    // Routage streaming / normal
    // -------------------------------------------------------------------------

    private IngestionResult ingestNormal(MultipartFile file, String filename, String batchId)
            throws Exception {

        try (InputStream is = file.getInputStream()) {
            XWPFDocument doc = openDocxWithTimeout(is, filename);
            return processDocument(doc, filename, batchId);
        }
    }

    private IngestionResult ingestWithStreaming(MultipartFile file, String filename, String batchId)
            throws Exception {

        Path tempFile = null;
        try {
            notify(n -> n.notifyProgress(batchId, filename, "STREAMING", 18,
                "Chargement DOCX en streaming..."));

            tempFile = StreamingFileReader.saveToTempFileWithProgress(file, bytesWritten -> {
                if (bytesWritten % (50 * 1024 * 1024) == 0) {
                    log.info("📊 [{}] Sauvegardé : {} MB", getName(), bytesWritten / 1_000_000);
                }
            });

            try (FileInputStream fis = new FileInputStream(tempFile.toFile())) {
                XWPFDocument doc = openDocxWithTimeout(fis, filename);
                return processDocument(doc, filename, batchId);
            }

        } finally {
            if (tempFile != null) Files.deleteIfExists(tempFile);
        }
    }

    // -------------------------------------------------------------------------
    // Ouverture DOCX avec timeout
    // -------------------------------------------------------------------------

    private XWPFDocument openDocxWithTimeout(InputStream is, String filename)
            throws IOException, TimeoutException {

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<XWPFDocument> future = executor.submit(() -> {
                try { return new XWPFDocument(is); }
                catch (IOException e) { throw new RuntimeException(e); }
            });

            try {
                return future.get(docxTimeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new TimeoutException("Timeout ouverture DOCX : " + filename);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException) throw (IOException) cause;
                throw new IOException("Erreur ouverture DOCX", cause);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Ouverture DOCX interrompue", e);
        } finally {
            executor.shutdownNow();
        }
    }

    // -------------------------------------------------------------------------
    // Routage avec / sans images
    // -------------------------------------------------------------------------

    private IngestionResult processDocument(XWPFDocument doc, String filename, String batchId)
            throws Exception {

        try {
            return hasImagesInDocument(doc)
                ? processDocxWithImages(doc, filename, batchId)
                : processDocxTextOnly(doc, filename, batchId);
        } finally {
            try { doc.close(); }
            catch (IOException e) { log.warn("⚠️ Fermeture document : {}", e.getMessage()); }
        }
    }

    private boolean hasImagesInDocument(XWPFDocument doc) {
        try {
            for (XWPFParagraph p : doc.getParagraphs()) {
                for (XWPFRun run : p.getRuns()) {
                    List<XWPFPicture> pics = run.getEmbeddedPictures();
                    if (pics != null && !pics.isEmpty()) return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    // -------------------------------------------------------------------------
    // Traitement DOCX texte seulement
    // -------------------------------------------------------------------------

    private IngestionResult processDocxTextOnly(XWPFDocument doc, String filename, String batchId)
            throws Exception {

        StringBuilder fullText = new StringBuilder();

        for (XWPFParagraph p : doc.getParagraphs()) {
            String text = p.getText();
            if (text != null && !text.isBlank()) fullText.append(text).append("\n");
        }

        for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    String text = cell.getText();
                    if (text != null && !text.isBlank()) fullText.append(text).append(" ");
                }
                fullText.append("\n");
            }
        }

        if (fullText.isEmpty()) {
            throw new IngestionException("DOCX vide : " + filename);
        }

        TextChunker.ChunkResult chunks = textChunker.chunk(
            fullText.toString(), filename, "docx_text", batchId, textStore, progressNotifier
        );

        return new IngestionResult(chunks.indexed(), 0);
    }

    // -------------------------------------------------------------------------
    // Traitement DOCX avec images
    // -------------------------------------------------------------------------

    private IngestionResult processDocxWithImages(XWPFDocument doc, String filename, String batchId)
            throws Exception {

        int imageEmbeddings = 0;
        int totalImages     = 0;
        StringBuilder fullText = new StringBuilder();

        String baseName   = FileUtils.sanitizeFilename(FileUtils.removeExtension(filename));
        String batchShort = batchId.substring(0, Math.min(8, batchId.length()));

        notify(n -> n.notifyProgress(batchId, filename, "EXTRACTION", 20, "Extraction..."));

        for (XWPFParagraph paragraph : doc.getParagraphs()) {

            String text = paragraph.getText();
            if (text != null && !text.isBlank()) fullText.append(text).append("\n");

            for (XWPFRun run : paragraph.getRuns()) {
                List<XWPFPicture> pictures = run.getEmbeddedPictures();
                if (pictures == null) continue;

                for (XWPFPicture picture : pictures) {
                    if (totalImages >= maxImagesPerFile) break;

                    try {
                        XWPFPictureData data = picture.getPictureData();
                        if (data == null) continue;

                        BufferedImage image = ImageIO.read(new ByteArrayInputStream(data.getData()));
                        if (image == null) continue;

                        totalImages++;
                        final int imgNum = totalImages;
                        notify(n -> n.imageProgress(batchId, filename, imgNum, maxImagesPerFile));

                        String imageName = String.format("%s_batch%s_img%d",
                            baseName, batchShort, totalImages);
                        String savedPath = imageSaver.saveImage(image, imageName);

                        Map<String, Object> meta = buildImageMeta(
                            filename, totalImages, savedPath, batchId
                        );

                        String embeddingId = analyzeAndIndex(image, imageName, meta, batchId);
                        if (embeddingId != null) imageEmbeddings++;

                    } catch (Exception e) {
                        log.warn("⚠️ Extraction image DOCX : {}", e.getMessage());
                    }
                }
            }
        }

        notify(n -> n.notifyProgress(batchId, filename, "CHUNKING", 30, "Découpage..."));

        TextChunker.ChunkResult chunks = fullText.isEmpty()
            ? new TextChunker.ChunkResult(0, 0)
            : textChunker.chunk(
                fullText.toString(), filename, "docx_text", batchId, textStore, progressNotifier
              );

        return new IngestionResult(chunks.indexed(), imageEmbeddings);
    }

    // -------------------------------------------------------------------------
    // Vision AI + indexation image — délégue à EmbeddingIndexer
    // -------------------------------------------------------------------------

    @Retryable(
        value       = {IOException.class, TimeoutException.class},
        maxAttempts = 3,
        backoff     = @Backoff(delay = 1000, multiplier = 2)
    )
    private String analyzeAndIndex(BufferedImage image, String imageName,
                                    Map<String, Object> meta, String batchId)
            throws IOException {

        try {
            long   start       = System.currentTimeMillis();
            String description = visionAnalyzer.analyzeImage(image);
            ragMetrics.recordApiCall("vision_analyze", System.currentTimeMillis() - start);

            Map<String, Object> enriched = new HashMap<>(sanitizer.sanitize(meta));
            enriched.put("imageName", imageName);
            enriched.put("type",      "image");
            enriched.put("width",     image.getWidth());
            enriched.put("height",    image.getHeight());

            return embeddingIndexer.indexImageDescription(description, enriched, batchId, imageStore);

        } catch (Exception e) {
            ragMetrics.recordApiError("vision_analyze");
            if (e instanceof IOException || e instanceof TimeoutException) throw (IOException) e;
            throw new IOException("Vision API error", e);
        }
    }

    // -------------------------------------------------------------------------
    // Utilitaires
    // -------------------------------------------------------------------------

    private Map<String, Object> buildImageMeta(String filename, int imgNum,
                                                String savedPath, String batchId) {
        Map<String, Object> m = new HashMap<>();
        m.put("source",      "docx");
        m.put("filename",    filename);
        m.put("imageNumber", imgNum);
        m.put("savedPath",   savedPath);
        m.put("batchId",     batchId);
        return m;
    }

    @FunctionalInterface
    private interface NotifierAction {
        void execute(ProgressNotifier n);
    }

    private void notify(NotifierAction action) {
        if (progressNotifier != null) action.execute(progressNotifier);
    }
}