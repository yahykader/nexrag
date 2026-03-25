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
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Stratégie d'ingestion pour fichiers PDF.
 *
 * Principe SRP  : unique responsabilité → extraire le contenu d'un PDF.
 *                 Le chunking est dans {@link TextChunker}.
 *                 L'indexation est dans {@link EmbeddingIndexer}.
 *                 Le cycle de vie est dans {@link IngestionLifecycle}.
 * Principe DIP  : dépend des abstractions des 3 services partagés,
 *                 pas de leurs implémentations.
 * Clean code    : 12 dépendances → 9 (les 3 services remplacent
 *                 embeddingModel + embeddingCache + textDeduplicationService
 *                 + tracker + ragMetrics + sanitizer soit 6 → 3).
 *
 * @author ayahyaoui
 * @version 2.0
 */
@Slf4j
@Component
public class PdfIngestionStrategy implements IngestionStrategy {

    // -------------------------------------------------------------------------
    // Dépendances spécifiques PDF
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

    @Value("${document.max-pages:100}")
    private int maxPages;

    @Value("${document.max-images-per-file:100}")
    private int maxImagesPerFile;

    public PdfIngestionStrategy(
            @Qualifier("textEmbeddingStore")  EmbeddingStore<TextSegment> textStore,
            @Qualifier("imageEmbeddingStore") EmbeddingStore<TextSegment> imageStore,
            VisionAnalyzer           visionAnalyzer,
            ImageSaver               imageSaver,
            FileSignatureValidator   signatureValidator,
            MetadataSanitizer        sanitizer,
            RAGMetrics               ragMetrics,
            EmbeddingIndexer         embeddingIndexer,
            TextChunker              textChunker,
            IngestionLifecycle       lifecycle) {

        this.textStore        = textStore;
        this.imageStore       = imageStore;
        this.visionAnalyzer   = visionAnalyzer;
        this.imageSaver       = imageSaver;
        this.signatureValidator = signatureValidator;
        this.sanitizer        = sanitizer;
        this.ragMetrics       = ragMetrics;
        this.embeddingIndexer = embeddingIndexer;
        this.textChunker      = textChunker;
        this.lifecycle        = lifecycle;

        log.info("✅ [{}] Strategy initialisée", getName());
    }

    // -------------------------------------------------------------------------
    // IngestionStrategy API
    // -------------------------------------------------------------------------

    @Override
    public boolean canHandle(MultipartFile file, String extension) {
        return "pdf".equals(extension);
    }

    @Override
    public IngestionResult ingest(MultipartFile file, String batchId)
            throws IOException, IngestionException {

        String filename  = file.getOriginalFilename();
        long   startTime = System.currentTimeMillis();

        try {
            notify(n -> n.uploadStarted(batchId, filename, file.getSize()));
            log.info("📕 [{}] Traitement PDF : {} ({} MB)",
                getName(), filename, file.getSize() / 1_000_000);

            notify(n -> n.notifyProgress(batchId, filename, "VALIDATION", 8, "Validation..."));
            signatureValidator.validate(file, "pdf");
            notify(n -> n.uploadCompleted(batchId, filename));
            notify(n -> n.processingStarted(batchId, filename));

            IngestionResult result = StreamingFileReader.requiresStreaming(file)
                ? ingestWithStreaming(file, batchId)
                : ingestNormal(file, batchId);

            lifecycle.onSuccess(getName(), batchId, filename, result,
                System.currentTimeMillis() - startTime, progressNotifier);

            log.info("✅ [{}] PDF traité : {} — text={} images={}",
                getName(), filename, result.textEmbeddings(), result.imageEmbeddings());

            return result;

        } catch (Exception e) {
            lifecycle.onError(getName(), batchId, filename, e, progressNotifier);
            throw new IngestionException("Erreur PDF : " + e.getMessage(), e);
        }
    }

    @Override
    public String getName()    { return "PDF"; }
    @Override
    public int    getPriority(){ return 1;     }

    // -------------------------------------------------------------------------
    // Routage streaming / normal
    // -------------------------------------------------------------------------

    private IngestionResult ingestNormal(MultipartFile file, String batchId) throws Exception {
        boolean hasImages = pdfHasImages(file);
        try (InputStream is = file.getInputStream();
             RandomAccessReadBuffer buf = new RandomAccessReadBuffer(is);
             PDDocument doc = Loader.loadPDF(buf)) {
            return hasImages
                ? processPdfWithImages(doc, file.getOriginalFilename(), batchId)
                : processPdfTextOnly(doc, file.getOriginalFilename(), batchId);
        }
    }

    private IngestionResult ingestWithStreaming(MultipartFile file, String batchId)
            throws Exception {

        String filename = file.getOriginalFilename();
        Path tempFile = null;
        try {
            notify(n -> n.notifyProgress(batchId, filename, "STREAMING", 15,
                "Chargement en streaming..."));

            tempFile = StreamingFileReader.saveToTempFileWithProgress(file, bytesWritten -> {
                if (bytesWritten % (50 * 1024 * 1024) == 0 && progressNotifier != null) {
                    int pct = 15 + (int)((bytesWritten / (double)file.getSize()) * 10);
                    progressNotifier.notifyProgress(batchId, filename, "STREAMING", pct,
                        String.format("Chargement : %d MB", bytesWritten / 1_000_000));
                }
            });

            notify(n -> n.processingStarted(batchId, filename));

            boolean hasImages = pdfHasImagesFromFile(tempFile.toFile());
            return hasImages
                ? ingestPdfWithImagesFromFile(tempFile.toFile(), filename, batchId)
                : ingestPdfTextOnlyFromFile(tempFile.toFile(), filename, batchId);

        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); }
                catch (IOException e) { log.warn("⚠️ Impossible de supprimer le temp : {}", e.getMessage()); }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Traitement PDF texte seulement
    // -------------------------------------------------------------------------

    private IngestionResult processPdfTextOnly(PDDocument doc, String filename, String batchId)
            throws Exception {

        notify(n -> n.notifyProgress(batchId, filename, "EXTRACTION", 30, "Extraction texte..."));

        PDFTextStripper stripper = new PDFTextStripper();
        String fullText = stripper.getText(doc);

        if (fullText == null || fullText.isBlank()) {
            throw new IngestionException("PDF sans texte : " + filename);
        }

        notify(n -> n.notifyProgress(batchId, filename, "CHUNKING", 40, "Découpage..."));

        TextChunker.ChunkResult chunks = textChunker.chunk(
            fullText, filename, "pdf_text", batchId, textStore, progressNotifier
        );

        return new IngestionResult(chunks.indexed(), 0);
    }

    private IngestionResult ingestPdfTextOnly(MultipartFile file, String batchId) throws Exception {
        try (InputStream is = file.getInputStream();
             RandomAccessReadBuffer buf = new RandomAccessReadBuffer(is);
             PDDocument doc = Loader.loadPDF(buf)) {
            return processPdfTextOnly(doc, file.getOriginalFilename(), batchId);
        }
    }

    private IngestionResult ingestPdfTextOnlyFromFile(File file, String filename, String batchId)
            throws Exception {
        try (PDDocument doc = Loader.loadPDF(file)) {
            return processPdfTextOnly(doc, filename, batchId);
        }
    }

    // -------------------------------------------------------------------------
    // Traitement PDF avec images
    // -------------------------------------------------------------------------

    private IngestionResult processPdfWithImages(PDDocument doc, String filename, String batchId)
            throws Exception {

        int textEmbeddings = 0, imageEmbeddings = 0, totalImages = 0;
        int totalPages = doc.getNumberOfPages();

        if (totalPages > maxPages) {
            throw new IngestionException(
                String.format("PDF trop volumineux : %d pages (max : %d)", totalPages, maxPages)
            );
        }

        notify(n -> n.notifyProgress(batchId, filename, "EXTRACTION", 30, "Extraction..."));

        PDFTextStripper stripper  = new PDFTextStripper();
        PDFRenderer     renderer  = new PDFRenderer(doc);
        String          baseName  = FileUtils.sanitizeFilename(FileUtils.removeExtension(filename));

        for (int pageIdx = 0; pageIdx < totalPages; pageIdx++) {

            if (totalImages >= maxImagesPerFile) break;

            int pageNum = pageIdx + 1;

            // Texte de la page
            stripper.setStartPage(pageNum);
            stripper.setEndPage(pageNum);
            String pageText = stripper.getText(doc);

            if (pageText != null && pageText.length() > 10) {
                Map<String, Object> extra = Map.of(
                    "page", pageNum, "totalPages", totalPages, "batchId", batchId
                );
                TextChunker.ChunkResult chunks = textChunker.chunk(
                    pageText, filename, "pdf_page_" + pageNum, batchId, textStore,
                    progressNotifier, extra
                );
                textEmbeddings += chunks.indexed();
            }

            // Images embarquées
            PDPage      page      = doc.getPage(pageIdx);
            PDResources resources = page.getResources();
            int         imgOnPage = 0;

            for (COSName name : resources.getXObjectNames()) {
                if (totalImages >= maxImagesPerFile) break;
                PDXObject xObj = resources.getXObject(name);
                if (!(xObj instanceof PDImageXObject imgXObj)) continue;

                BufferedImage img = imgXObj.getImage();
                if (img == null) continue;

                totalImages++;
                imgOnPage++;

                String imageName = FileUtils.generateImageName(baseName, batchId,
                    pageNum * 100 + imgOnPage);
                String savedPath = imageSaver.saveImage(img, imageName);

                Map<String, Object> meta = buildImageMeta(
                    filename, pageNum, totalPages, totalImages, savedPath, batchId, "pdf_embedded"
                );

                String embeddingId = analyzeAndIndex(img, imageName, meta, batchId);
                if (embeddingId != null) imageEmbeddings++;
            }

            // Rendu page complète
            if (totalImages < maxImagesPerFile) {
                try {
                    BufferedImage pageImg    = renderer.renderImageWithDPI(pageIdx, 150);
                    String        pageName   = FileUtils.generatePageName(baseName, batchId, pageNum);
                    String        savedPath  = imageSaver.saveImage(pageImg, pageName + "_render");

                    Map<String, Object> meta = buildImageMeta(
                        filename, pageNum, totalPages, ++totalImages, savedPath, batchId, "pdf_rendered"
                    );

                    String embeddingId = analyzeAndIndex(pageImg, pageName, meta, batchId);
                    if (embeddingId != null) imageEmbeddings++;
                } catch (Exception e) {
                    log.warn("⚠️ Rendu page {} : {}", pageNum, e.getMessage());
                }
            }

            if (pageIdx % 10 == 0 && pageIdx > 0) {
                System.gc();
                Thread.sleep(50);
            }
        }

        return new IngestionResult(textEmbeddings, imageEmbeddings);
    }

    private IngestionResult ingestPdfWithImages(MultipartFile file, String batchId) throws Exception {
        try (InputStream is = file.getInputStream();
             RandomAccessReadBuffer buf = new RandomAccessReadBuffer(is);
             PDDocument doc = Loader.loadPDF(buf)) {
            return processPdfWithImages(doc, file.getOriginalFilename(), batchId);
        }
    }

    private IngestionResult ingestPdfWithImagesFromFile(File file, String filename, String batchId)
            throws Exception {
        try (PDDocument doc = Loader.loadPDF(file)) {
            return processPdfWithImages(doc, filename, batchId);
        }
    }

    // -------------------------------------------------------------------------
    // Vision AI + indexation image — délégue à EmbeddingIndexer
    // -------------------------------------------------------------------------

    @Retryable(
        value     = {IOException.class, TimeoutException.class},
        maxAttempts = 3,
        backoff   = @Backoff(delay = 1000, multiplier = 2)
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
    // Détection images
    // -------------------------------------------------------------------------

    private boolean pdfHasImages(MultipartFile file) {
        try (InputStream is = file.getInputStream();
             RandomAccessReadBuffer buf = new RandomAccessReadBuffer(is);
             PDDocument doc = Loader.loadPDF(buf)) {
            return pdfHasImagesInternal(doc);
        } catch (Exception e) { return false; }
    }

    private boolean pdfHasImagesFromFile(File file) {
        try (PDDocument doc = Loader.loadPDF(file)) {
            return pdfHasImagesInternal(doc);
        } catch (Exception e) { return false; }
    }

    private boolean pdfHasImagesInternal(PDDocument doc) throws IOException {
        int pages = Math.min(3, doc.getNumberOfPages());
        for (int i = 0; i < pages; i++) {
            if (doc.getPage(i).getResources().getXObjectNames().iterator().hasNext()) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Utilitaires
    // -------------------------------------------------------------------------

    private Map<String, Object> buildImageMeta(String filename, int page, int totalPages,
                                                int imgNum, String savedPath, String batchId,
                                                String source) {
        Map<String, Object> m = new HashMap<>();
        m.put("page",        page);
        m.put("totalPages",  totalPages);
        m.put("source",      source);
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


    // Dans PdfIngestionStrategy — méthode sans notification finale
    public IngestionResult ingestSilent(MultipartFile file, String batchId)
            throws IOException, IngestionException {

        // Même logique que ingest() MAIS sans appel à lifecycle.onSuccess()
        // ni lifecycle.onError() — c'est XlsxIngestionStrategy qui gère le cycle de vie
        String filename  = file.getOriginalFilename();
        long   startTime = System.currentTimeMillis();

        try {
            IngestionResult result = StreamingFileReader.requiresStreaming(file)
                ? ingestWithStreaming(file, batchId)
                : ingestNormal(file, batchId);

            // ✅ Pas de lifecycle.onSuccess() ici
            log.info("✅ [{}] PDF traité (silent) : {} — text={} images={}",
                getName(), filename, result.textEmbeddings(), result.imageEmbeddings());

            return result;

        } catch (Exception e) {
            // ✅ Pas de lifecycle.onError() ici — XLSX gère l'erreur globale
            log.error("❌ [{}] Erreur PDF (silent) : {}", getName(), filename, e);
            throw new IngestionException("Erreur PDF : " + e.getMessage(), e);
        }
    }
}