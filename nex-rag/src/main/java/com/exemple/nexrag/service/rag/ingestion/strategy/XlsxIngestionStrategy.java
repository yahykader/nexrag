package com.exemple.nexrag.service.rag.ingestion.strategy;

import com.exemple.nexrag.exception.IngestionException;
import com.exemple.nexrag.service.rag.ingestion.analyzer.ImageSaver;
import com.exemple.nexrag.service.rag.ingestion.analyzer.VisionAnalyzer;
import com.exemple.nexrag.dto.IngestionResult;
import com.exemple.nexrag.service.rag.ingestion.progress.ProgressNotifier;
import com.exemple.nexrag.service.rag.ingestion.strategy.commun.EmbeddingIndexer;
import com.exemple.nexrag.service.rag.ingestion.strategy.commun.IngestionLifecycle;
import com.exemple.nexrag.service.rag.ingestion.strategy.commun.LibreOfficeConverter;
import com.exemple.nexrag.service.rag.ingestion.strategy.commun.TextChunker;
import com.exemple.nexrag.service.rag.ingestion.strategy.commun.XlsxProperties;
import com.exemple.nexrag.service.rag.ingestion.tracker.IngestionTracker;
import com.exemple.nexrag.service.rag.ingestion.util.FileUtils;
import com.exemple.nexrag.service.rag.ingestion.util.InMemoryMultipartFile;
import com.exemple.nexrag.service.rag.ingestion.util.MetadataSanitizer;
import com.exemple.nexrag.service.rag.ingestion.util.StreamingFileReader;
import com.exemple.nexrag.service.rag.metrics.RAGMetrics;
import com.exemple.nexrag.validation.FileSignatureValidator;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.ooxml.POIXMLDocumentPart;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeoutException;

/**
 * Stratégie d'ingestion pour fichiers XLSX.
 *
 * Principe SRP  : unique responsabilité → extraire le contenu d'un XLSX.
 *                 La conversion LibreOffice est dans {@link LibreOfficeConverter}.
 *                 Le chunking est dans {@link TextChunker}.
 *                 L'indexation est dans {@link EmbeddingIndexer}.
 *                 Le cycle de vie est dans {@link IngestionLifecycle}.
 * Principe DIP  : dépend des abstractions des services partagés.
 * Clean code    : {@code evaluateFormulas} et {@code detectCharts} conditionnent
 *                 le comportement via {@link XlsxProperties}.
 *                 L'extraction cellules est factorisée dans {@code extractSheetText()}.
 *
 * @author ayahyaoui
 * @version 3.0
 */
@Slf4j
@Component
public class XlsxIngestionStrategy implements IngestionStrategy {

    // -------------------------------------------------------------------------
    // Dépendances spécifiques XLSX
    // -------------------------------------------------------------------------
    private final EmbeddingStore<TextSegment> textStore;
    private final EmbeddingStore<TextSegment> imageStore;
    private final VisionAnalyzer              visionAnalyzer;
    private final ImageSaver                  imageSaver;
    private final IngestionTracker            tracker;
    private final MetadataSanitizer           sanitizer;
    private final FileSignatureValidator      signatureValidator;
    private final RAGMetrics                  ragMetrics;
    private final XlsxProperties             props;
    private final LibreOfficeConverter        libreOfficeConverter;

    // -------------------------------------------------------------------------
    // Services partagés
    // -------------------------------------------------------------------------
    private final EmbeddingIndexer   embeddingIndexer;
    private final TextChunker        textChunker;
    private final IngestionLifecycle lifecycle;

    private final PdfIngestionStrategy pdfIngestionStrategy;

    @Autowired(required = false)
    private ProgressNotifier progressNotifier;

    public XlsxIngestionStrategy(
            @Qualifier("textEmbeddingStore")  EmbeddingStore<TextSegment> textStore,
            @Qualifier("imageEmbeddingStore") EmbeddingStore<TextSegment> imageStore,
            VisionAnalyzer         visionAnalyzer,
            ImageSaver             imageSaver,
            IngestionTracker       tracker,
            MetadataSanitizer      sanitizer,
            FileSignatureValidator signatureValidator,
            RAGMetrics             ragMetrics,
            XlsxProperties        props,
            LibreOfficeConverter   libreOfficeConverter,
            EmbeddingIndexer       embeddingIndexer,
            TextChunker            textChunker,
            IngestionLifecycle     lifecycle,
            PdfIngestionStrategy   pdfIngestionStrategy) {

        this.textStore            = textStore;
        this.imageStore           = imageStore;
        this.visionAnalyzer       = visionAnalyzer;
        this.imageSaver           = imageSaver;
        this.tracker              = tracker;
        this.sanitizer            = sanitizer;
        this.signatureValidator   = signatureValidator;
        this.ragMetrics           = ragMetrics;
        this.props                = props;
        this.libreOfficeConverter = libreOfficeConverter;
        this.embeddingIndexer     = embeddingIndexer;
        this.textChunker          = textChunker;
        this.lifecycle            = lifecycle;
        this.pdfIngestionStrategy = pdfIngestionStrategy;

        log.info("✅ [{}] Strategy initialisée (detectCharts={}, evaluateFormulas={})",
            getName(), props.isDetectCharts(), props.isEvaluateFormulas());
    }

    // -------------------------------------------------------------------------
    // IngestionStrategy API
    // -------------------------------------------------------------------------

    @Override
    public boolean canHandle(MultipartFile file, String extension) {
        return "xlsx".equals(extension);
    }

    @Override
    public IngestionResult ingest(MultipartFile file, String batchId)
            throws IOException, IngestionException {

        String filename  = file.getOriginalFilename();
        long   startTime = System.currentTimeMillis();

        try {
            notify(n -> n.uploadStarted(batchId, filename, file.getSize()));
            log.info("📗 [{}] Traitement XLSX : {} ({} MB)",
                getName(), filename, file.getSize() / 1_000_000);

            if (file.isEmpty() || file.getSize() == 0) {
                throw new IOException("Fichier XLSX vide : " + filename);
            }

            notify(n -> n.notifyProgress(batchId, filename, "VALIDATION", 8, "Validation..."));
            signatureValidator.validate(file, "xlsx");
            notify(n -> n.uploadCompleted(batchId, filename));
            notify(n -> n.processingStarted(batchId, filename));

            IngestionResult result = StreamingFileReader.requiresStreaming(file)
                ? ingestWithStreaming(file, batchId)
                : ingestNormal(file, batchId);

            lifecycle.onSuccess(getName(), batchId, filename, result,
                System.currentTimeMillis() - startTime, progressNotifier);

            log.info("✅ [{}] XLSX traité : {} — text={} images={}",
                getName(), filename, result.textEmbeddings(), result.imageEmbeddings());

            return result;

        } catch (Exception e) {
            lifecycle.onError(getName(), batchId, filename, e, progressNotifier);
            throw new IngestionException("Erreur XLSX : " + e.getMessage(), e);
        }
    }

    @Override
    public String getName()     { return "XLSX"; }
    @Override
    public int    getPriority() { return 3; }

    // -------------------------------------------------------------------------
    // Routage streaming / normal
    // -------------------------------------------------------------------------

    private IngestionResult ingestNormal(MultipartFile file, String batchId) throws Exception {
        String filename = file.getOriginalFilename();
        byte[] bytes    = file.getBytes();

        if (bytes.length < 2 || bytes[0] != 'P' || bytes[1] != 'K') {
            throw new IOException("Fichier XLSX invalide (pas ZIP OOXML) : " + filename);
        }

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            return processWorkbook(wb, filename, batchId, bytes);
        }
    }

    private IngestionResult ingestWithStreaming(MultipartFile file, String batchId)
            throws Exception {

        String filename = file.getOriginalFilename();
        Path   tempFile = null;

        try {
            notify(n -> n.notifyProgress(batchId, filename, "STREAMING", 15,
                "Chargement XLSX en streaming..."));

            tempFile = StreamingFileReader.saveToTempFileWithProgress(file, bytesWritten -> {
                if (bytesWritten % (50 * 1024 * 1024) == 0 && progressNotifier != null) {
                    int pct = 15 + (int)((bytesWritten / (double)file.getSize()) * 10);
                    progressNotifier.notifyProgress(batchId, filename, "STREAMING", pct,
                        String.format("Chargement : %d MB", bytesWritten / 1_000_000));
                }
            });

            notify(n -> n.processingStarted(batchId, filename));
            byte[] bytes = Files.readAllBytes(tempFile);

            try (FileInputStream fis = new FileInputStream(tempFile.toFile());
                 XSSFWorkbook wb     = new XSSFWorkbook(fis)) {
                return processWorkbook(wb, filename, batchId, bytes);
            }

        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); }
                catch (IOException e) { log.warn("⚠️ Impossible de supprimer le temp : {}", e.getMessage()); }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Analyse du workbook et routage
    // -------------------------------------------------------------------------

    private IngestionResult processWorkbook(XSSFWorkbook wb, String filename,
                                             String batchId, byte[] xlsxBytes) throws Exception {

        notify(n -> n.notifyProgress(batchId, filename, "ANALYSIS", 25,
            "Analyse du contenu XLSX..."));

        boolean hasImages          = hasImagesInXlsx(wb);
        boolean requiresLibreOffice = false;

        // ✅ detectCharts conditionne la détection — court-circuité si false
        if (props.isDetectCharts()) {
            int     chartCount  = countChartsRobust(wb);
            boolean hasDrawings = hasAnyDrawingInXlsx(wb);
            requiresLibreOffice = (chartCount > 0 || hasDrawings) && !hasImages;

            log.info("🔍 [{}] XLSX : sheets={} charts={} images={} drawings={}",
                getName(), wb.getNumberOfSheets(), chartCount, hasImages, hasDrawings);
        } else {
            log.info("🔍 [{}] XLSX : sheets={} images={} (detectCharts=false)",
                getName(), wb.getNumberOfSheets(), hasImages);
        }

        if (requiresLibreOffice) {
            log.info("📊 [{}] Charts/Drawings détectés → Conversion PDF", getName());
            return processWithLibreOfficeFallback(xlsxBytes, filename, batchId);
        }

        return hasImages
            ? processXlsxWithImages(wb, filename, batchId)
            : processXlsxTextOnly(wb, filename, batchId);
    }

    // -------------------------------------------------------------------------
    // Voie LibreOffice : XLSX → PDF → Texte + Vision AI
    // -------------------------------------------------------------------------

    private IngestionResult processWithLibreOfficeFallback(byte[] xlsxBytes,
                                                            String filename,
                                                            String batchId) throws Exception {

        if (!libreOfficeConverter.isEnabled()) {
            log.warn("⚠️ [{}] LibreOffice désactivé → texte uniquement", getName());
            try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
                return processXlsxTextOnly(wb, filename, batchId);
            }
        }

        log.info("🔄 [{}] Conversion XLSX → PDF via LibreOffice", getName());
        notify(n -> n.notifyProgress(batchId, filename, "LIBREOFFICE", 30, "Conversion en PDF..."));

        String baseName = FileUtils.sanitizeFilename(FileUtils.removeExtension(filename));
        byte[] pdfBytes = libreOfficeConverter.convert(xlsxBytes, baseName);

        notify(n -> n.notifyProgress(batchId, filename, "PDF_GENERATED", 50, "PDF généré, analyse..."));

        String savedPdfPath     = savePdfToDisk(pdfBytes, baseName, batchId);
        int    visionEmbeddings = 0;

        if (props.getPdf().isAnalyzeWithVision()) {
            visionEmbeddings = analyzePdfWithVision(pdfBytes, baseName, batchId, savedPdfPath, filename);
        }

        IngestionResult pdfResult = pdfIngestionStrategy.ingestSilent(
            new InMemoryMultipartFile("file", baseName + ".pdf", "application/pdf", pdfBytes),
            batchId
        );

        return new IngestionResult(
            pdfResult.textEmbeddings(),
            pdfResult.imageEmbeddings() + visionEmbeddings,
            Map.of(
                "originalFormat",   "xlsx",
                "conversionMethod", "libreoffice",
                "xlsxFilename",     filename,
                "pdfSavedPath",     String.valueOf(savedPdfPath),
                "pdfPagesAnalyzed", visionEmbeddings
            )
        );
    }

    // -------------------------------------------------------------------------
    // Vision AI sur pages PDF générées
    // -------------------------------------------------------------------------

    private int analyzePdfWithVision(byte[] pdfBytes, String baseName, String batchId,
                                      String savedPdfPath, String filename) throws Exception {

        int    analyzed   = 0;
        String batchShort = batchId.substring(0, Math.min(8, batchId.length()));
        Path   tempPdf    = Files.createTempFile("xlsx_pdf_", ".pdf");

        try {
            Files.write(tempPdf, pdfBytes);

            try (PDDocument doc = Loader.loadPDF(tempPdf.toFile())) {
                PDFRenderer renderer = new PDFRenderer(doc);
                int         total    = doc.getNumberOfPages();
                // ✅ maxPdfPagesToAnalyze lu depuis props
                int         maxPages = Math.min(total, props.getMaxPdfPagesToAnalyze());

                log.info("📄 [{}] PDF : {} pages ({} analysées)", getName(), total, maxPages);

                for (int i = 0; i < maxPages; i++) {
                    try {
                        // ✅ renderDpi lu depuis props.getPdf()
                        BufferedImage img       = renderer.renderImageWithDPI(i, props.getPdf().getRenderDpi());
                        String        imgName   = String.format("%s_batch%s_page%d", baseName, batchShort, i + 1);
                        String        savedPath = imageSaver.saveImage(img, imgName);

                        Map<String, Object> meta = Map.of(
                            "source",           "xlsx_to_pdf",
                            "pdfPath",          String.valueOf(savedPdfPath),
                            "pageNumber",       i + 1,
                            "totalPages",       total,
                            "savedPath",        savedPath,
                            "batchId",          batchId,
                            "type",             "pdf_page_chart",
                            "conversionMethod", "libreoffice",
                            "renderDpi",        props.getPdf().getRenderDpi()
                        );

                        String id = analyzeAndIndex(img, imgName, meta, batchId);
                        if (id != null) { tracker.addImageEmbeddingId(batchId, id); analyzed++; }

                        if ((i + 1) % 3 == 0 || (i + 1) == maxPages) {
                            final int cur = i + 1, tot = maxPages;
                            notify(n -> n.notifyProgress(batchId, filename, "PDF_VISION",
                                50 + (int)((cur / (double)tot) * 30),
                                String.format("Vision AI : page %d/%d", cur, tot)));
                        }

                    } catch (Exception e) {
                        log.warn("⚠️ [{}] Erreur analyse page {} : {}", getName(), i + 1, e.getMessage());
                    }
                }
            }

        } finally {
            try { Files.deleteIfExists(tempPdf); } catch (IOException ignored) {}
        }

        return analyzed;
    }

    // -------------------------------------------------------------------------
    // Sauvegarde PDF sur disque
    // -------------------------------------------------------------------------

    private String savePdfToDisk(byte[] pdfBytes, String baseName, String batchId)
            throws IOException {

        // ✅ saveGenerated lu depuis props.getPdf()
        if (!props.getPdf().isSaveGenerated()) return null;

        // ✅ generatedPdfDir lu depuis props.getPdf()
        Path pdfDir = Paths.get(props.getPdf().getGeneratedPdfDir());
        Files.createDirectories(pdfDir);

        String ts      = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String pdfName = String.format("%s_batch%s_%s.pdf",
            baseName, batchId.substring(0, Math.min(8, batchId.length())), ts);

        Path path = pdfDir.resolve(pdfName);
        Files.write(path, pdfBytes);
        log.debug("💾 [{}] PDF sauvegardé : {} ({} KB)", getName(), pdfName, pdfBytes.length / 1024);
        return path.toAbsolutePath().toString();
    }

    // -------------------------------------------------------------------------
    // Extraction XLSX texte seulement
    // -------------------------------------------------------------------------

    private IngestionResult processXlsxTextOnly(XSSFWorkbook wb, String filename,
                                                  String batchId) throws Exception {

        notify(n -> n.notifyProgress(batchId, filename, "EXTRACTION", 30, "Extraction texte..."));

        // ✅ evaluateFormulas conditionne la création du FormulaEvaluator
        StringBuilder fullText = extractAllSheetsText(wb, createEvaluator(wb));

        if (fullText.isEmpty()) throw new IngestionException("XLSX vide : " + filename);

        notify(n -> n.notifyProgress(batchId, filename, "CHUNKING", 50, "Découpage..."));

        TextChunker.ChunkResult chunks = textChunker.chunk(
            fullText.toString(), filename, "xlsx_text", batchId, textStore, progressNotifier
        );

        return new IngestionResult(chunks.indexed(), 0, Map.of(
            "strategy", getName(), "filename", filename,
            "sheets",   wb.getNumberOfSheets(), "hasImages", false
        ));
    }

    // -------------------------------------------------------------------------
    // Extraction XLSX avec images
    // -------------------------------------------------------------------------

    private IngestionResult processXlsxWithImages(XSSFWorkbook wb, String filename,
                                                    String batchId) throws Exception {

        notify(n -> n.notifyProgress(batchId, filename, "EXTRACTION", 30, "Extraction..."));

        // ✅ evaluateFormulas conditionne la création du FormulaEvaluator
        FormulaEvaluator eval         = createEvaluator(wb);
        DataFormatter    fmt          = new DataFormatter();
        StringBuilder    fullText     = new StringBuilder();
        int              imgEmbeddings = 0;
        int              totalImages   = 0;
        String           baseName      = FileUtils.sanitizeFilename(FileUtils.removeExtension(filename));
        String           batchShort    = batchId.substring(0, Math.min(8, batchId.length()));

        for (int si = 0; si < wb.getNumberOfSheets(); si++) {

            // ✅ maxImagesPerFile lu depuis props
            if (totalImages >= props.getMaxImagesPerFile()) break;

            XSSFSheet sheet     = wb.getSheetAt(si);
            String    sheetName = sheet.getSheetName();

            // ✅ extraction cellules factorisée — plus de duplication avec extractAllSheetsText
            fullText.append(extractSheetText(sheet, fmt, eval, sheetName));

            XSSFDrawing drawing = resolveDrawing(sheet);
            if (drawing == null) continue;

            int imgInSheet = 0;
            for (XSSFShape shape : drawing.getShapes()) {
                if (totalImages >= props.getMaxImagesPerFile()) break;
                if (!(shape instanceof XSSFPicture pic)) continue;

                try {
                    XSSFPictureData data = pic.getPictureData();
                    if (data == null) continue;

                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(data.getData()));
                    if (img == null) continue;

                    totalImages++;
                    imgInSheet++;
                    final int imgNum = totalImages;
                    if (imgNum % 5 == 0) {
                        notify(n -> n.imageProgress(batchId, filename, imgNum, props.getMaxImagesPerFile()));
                    }

                    String imageName = String.format("%s_batch%s_sheet%d_img%d",
                        baseName, batchShort, si + 1, imgInSheet);
                    String savedPath = imageSaver.saveImage(img, imageName);

                    Map<String, Object> meta = new HashMap<>(Map.of(
                        "sheetName",   sheetName,   "sheetIndex", si + 1,
                        "imageNumber", totalImages, "source",     "xlsx",
                        "filename",    filename,    "savedPath",  savedPath,
                        "batchId",     batchId
                    ));

                    String id = analyzeAndIndex(img, imageName, meta, batchId);
                    if (id != null) imgEmbeddings++;

                } catch (Exception e) {
                    log.warn("⚠️ [{}] Erreur extraction image sheet {} : {}", getName(), sheetName, e.getMessage());
                }
            }
        }

        notify(n -> n.notifyProgress(batchId, filename, "CHUNKING", 70, "Découpage..."));

        TextChunker.ChunkResult chunks = fullText.isEmpty()
            ? new TextChunker.ChunkResult(0, 0)
            : textChunker.chunk(fullText.toString(), filename, "xlsx_text", batchId, textStore, progressNotifier);

        return new IngestionResult(chunks.indexed(), imgEmbeddings, Map.of(
            "strategy", getName(), "filename", filename,
            "sheets",   wb.getNumberOfSheets(), "hasImages", true
        ));
    }

    // -------------------------------------------------------------------------
    // Vision AI + indexation image
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
            enriched.put("type",      enriched.getOrDefault("type", "image"));
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
    // Détection charts / images / drawings
    // -------------------------------------------------------------------------

    private int countChartsRobust(XSSFWorkbook wb) {
        int charts = 0;
        try {
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                if (sheet instanceof XSSFChartSheet) { charts++; continue; }
                if (!(sheet instanceof XSSFSheet xs)) continue;

                XSSFDrawing drawing = resolveDrawing(xs);
                if (drawing == null) continue;

                try {
                    List<XSSFChart> embedded = drawing.getCharts();
                    if (embedded != null) { charts += embedded.size(); continue; }
                } catch (NoSuchMethodError | Exception ignored) {}

                charts += (int) drawing.getRelations().stream()
                    .filter(r -> r instanceof XSSFChart).count();
            }
        } catch (Exception e) {
            log.warn("⚠️ [{}] Erreur comptage charts : {}", getName(), e.getMessage());
        }
        return charts;
    }

    private boolean hasImagesInXlsx(XSSFWorkbook wb) {
        try {
            if (!wb.getAllPictures().isEmpty()) return true;
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                if (!(wb.getSheetAt(i) instanceof XSSFSheet xs)) continue;
                XSSFDrawing d = resolveDrawing(xs);
                if (d != null && d.getShapes().stream().anyMatch(s -> s instanceof XSSFPicture)) return true;
            }
        } catch (Exception e) {
            log.warn("⚠️ [{}] Erreur détection images : {}", getName(), e.getMessage());
        }
        return false;
    }

    private boolean hasAnyDrawingInXlsx(XSSFWorkbook wb) {
        try {
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                if (sheet instanceof XSSFChartSheet) return true;
                if (!(sheet instanceof XSSFSheet xs)) continue;
                XSSFDrawing d = resolveDrawing(xs);
                try { if (d != null && !d.getCharts().isEmpty()) return true; }
                catch (NoSuchMethodError | Exception ignored) {}
            }
        } catch (Exception e) {
            log.warn("⚠️ [{}] Erreur détection drawings : {}", getName(), e.getMessage());
        }
        return false;
    }

    private XSSFDrawing resolveDrawing(XSSFSheet sheet) {
        XSSFDrawing d = sheet.getDrawingPatriarch();
        if (d != null) return d;
        return sheet.getRelations().stream()
            .filter(r -> r instanceof XSSFDrawing)
            .map(r -> (XSSFDrawing) r)
            .findFirst().orElse(null);
    }

    // -------------------------------------------------------------------------
    // Utilitaires extraction texte
    // -------------------------------------------------------------------------

    /**
     * Crée un {@link FormulaEvaluator} si {@code document.xlsx.evaluate-formulas=true},
     * sinon retourne {@code null} — {@link DataFormatter#formatCellValue} accepte null.
     */
    private FormulaEvaluator createEvaluator(XSSFWorkbook wb) {
        if (props.isEvaluateFormulas()) {
            log.debug("🔢 [{}] FormulaEvaluator activé", getName());
            return wb.getCreationHelper().createFormulaEvaluator();
        }
        log.debug("🔢 [{}] FormulaEvaluator désactivé (evaluate-formulas=false)", getName());
        return null;
    }

    /**
     * Extrait le texte de toutes les feuilles.
     * Utilisé par {@code processXlsxTextOnly} — élimine la duplication
     * avec la boucle identique dans {@code processXlsxWithImages}.
     */
    private StringBuilder extractAllSheetsText(XSSFWorkbook wb, FormulaEvaluator eval) {
        StringBuilder sb  = new StringBuilder();
        DataFormatter fmt = new DataFormatter();
        for (int si = 0; si < wb.getNumberOfSheets(); si++) {
            XSSFSheet sheet = wb.getSheetAt(si);
            sb.append(extractSheetText(sheet, fmt, eval, sheet.getSheetName()));
        }
        return sb;
    }

    /**
     * Extrait le texte d'une feuille (cellules et en-tête).
     * Point unique de la logique cellule — supprime la duplication entre
     * {@code processXlsxTextOnly} et {@code processXlsxWithImages}.
     */
    private StringBuilder extractSheetText(Sheet sheet, DataFormatter fmt,
                                            FormulaEvaluator eval, String sheetName) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== Sheet: ").append(sheetName).append(" ===\n");

        for (Row row : sheet) {
            boolean any = false;
            for (Cell cell : row) {
                try {
                    String val = fmt.formatCellValue(cell, eval);
                    if (val != null && !val.isBlank()) {
                        if (any) sb.append(" | ");
                        sb.append(val.trim());
                        any = true;
                    }
                } catch (Exception ignored) {}
            }
            if (any) sb.append('\n');
        }

        sb.append('\n');
        return sb;
    }

    // -------------------------------------------------------------------------
    // Utilitaires
    // -------------------------------------------------------------------------

    @FunctionalInterface
    private interface NotifierAction { void execute(ProgressNotifier n); }

    private void notify(NotifierAction action) {
        if (progressNotifier != null) action.execute(progressNotifier);
    }
}