package com.exemple.nexrag.service.rag.integration.fixture;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generates valid test fixture files for integration tests.
 * Creates minimal but valid PDF, DOCX, XLSX, TXT, and JPEG files.
 */
public class FixtureGenerator {

    private static final String FIXTURES_DIR = "src/test/resources/fixtures";

    public static void main(String[] args) throws IOException {
        generateFixtures();
        System.out.println("✅ All fixture files generated successfully");
    }

    public static void generateFixtures() throws IOException {
        Path fixturesPath = Paths.get(FIXTURES_DIR);
        Files.createDirectories(fixturesPath);

        generateSamplePdf(fixturesPath);
        generateSampleDocx(fixturesPath);
        generateSampleXlsx(fixturesPath);
        generateSampleTxt(fixturesPath);
        generateSampleJpeg(fixturesPath);
    }

    // =========================================================================
    // PDF Generation
    // =========================================================================

    private static void generateSamplePdf(Path fixturesPath) throws IOException {
        File file = fixturesPath.resolve("sample.pdf").toFile();

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            // Just create a minimal valid PDF with empty page
            document.save(file);
            System.out.println("✅ Generated sample.pdf (" + file.length() + " bytes)");
        }
    }

    // =========================================================================
    // DOCX Generation
    // =========================================================================

    private static void generateSampleDocx(Path fixturesPath) throws IOException {
        File file = fixturesPath.resolve("sample.docx").toFile();

        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph p1 = document.createParagraph();
            p1.createRun().setText("NexRAG Integration Test Document");

            XWPFParagraph p2 = document.createParagraph();
            p2.createRun().setText("This is a valid DOCX file used for testing document ingestion.");

            XWPFParagraph p3 = document.createParagraph();
            p3.createRun().setText("It contains sample text for RAG pipeline validation.");

            try (FileOutputStream out = new FileOutputStream(file)) {
                document.write(out);
            }

            System.out.println("✅ Generated sample.docx (" + file.length() + " bytes)");
        }
    }

    // =========================================================================
    // XLSX Generation
    // =========================================================================

    private static void generateSampleXlsx(Path fixturesPath) throws IOException {
        File file = fixturesPath.resolve("sample.xlsx").toFile();

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Data");

            var row0 = sheet.createRow(0);
            row0.createCell(0).setCellValue("Column 1");
            row0.createCell(1).setCellValue("Column 2");
            row0.createCell(2).setCellValue("Column 3");

            var row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("NexRAG Test");
            row1.createCell(1).setCellValue("Integration");
            row1.createCell(2).setCellValue("Document");

            var row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("Value A");
            row2.createCell(1).setCellValue("Value B");
            row2.createCell(2).setCellValue("Value C");

            try (FileOutputStream out = new FileOutputStream(file)) {
                workbook.write(out);
            }

            System.out.println("✅ Generated sample.xlsx (" + file.length() + " bytes)");
        }
    }

    // =========================================================================
    // TXT Generation
    // =========================================================================

    private static void generateSampleTxt(Path fixturesPath) throws IOException {
        File file = fixturesPath.resolve("sample.txt").toFile();

        String content = "NexRAG Integration Test Document\n" +
                        "==================================\n\n" +
                        "This is a valid text file used for testing document ingestion.\n" +
                        "It contains sample text for RAG pipeline validation.\n\n" +
                        "The content is meaningful and will be processed by the ingestion pipeline.\n" +
                        "Multiple lines ensure adequate text for embedding generation and retrieval.\n";

        Files.write(file.toPath(), content.getBytes());
        System.out.println("✅ Generated sample.txt (" + file.length() + " bytes)");
    }

    // =========================================================================
    // JPEG Generation (minimal valid JPEG)
    // =========================================================================

    private static void generateSampleJpeg(Path fixturesPath) throws IOException {
        File file = fixturesPath.resolve("sample.jpg").toFile();

        // Minimal valid JPEG structure (1x1 white pixel)
        byte[] jpegData = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, (byte) 0x00, (byte) 0x10, (byte) 0x4A, (byte) 0x46,
            (byte) 0x49, (byte) 0x46, (byte) 0x00, (byte) 0x01, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x01,
            (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0xFF, (byte) 0xDB, (byte) 0x00, (byte) 0x43, (byte) 0x00, (byte) 0x08,
            (byte) 0x06, (byte) 0x06, (byte) 0x07, (byte) 0x06, (byte) 0x05, (byte) 0x08, (byte) 0x07, (byte) 0x07,
            (byte) 0x07, (byte) 0x09, (byte) 0x09, (byte) 0x08, (byte) 0x0A, (byte) 0x0C, (byte) 0x14, (byte) 0x0D,
            (byte) 0x0C, (byte) 0x0B, (byte) 0x0B, (byte) 0x0C, (byte) 0x19, (byte) 0x12, (byte) 0x13, (byte) 0x0F,
            (byte) 0x14, (byte) 0x1D, (byte) 0x1A, (byte) 0x1F, (byte) 0x1E, (byte) 0x1D, (byte) 0x1A, (byte) 0x1C,
            (byte) 0x1C, (byte) 0x20, (byte) 0x24, (byte) 0x2E, (byte) 0x27, (byte) 0x20, (byte) 0x22, (byte) 0x2C,
            (byte) 0x23, (byte) 0x1C, (byte) 0x1C, (byte) 0x28, (byte) 0x37, (byte) 0x29, (byte) 0x2C, (byte) 0x30,
            (byte) 0x31, (byte) 0x34, (byte) 0x34, (byte) 0x34, (byte) 0x1F, (byte) 0x27, (byte) 0x39, (byte) 0x3D,
            (byte) 0x38, (byte) 0x32, (byte) 0x3C, (byte) 0x2E, (byte) 0x33, (byte) 0x34, (byte) 0x32, (byte) 0xFF,
            (byte) 0xC0, (byte) 0x00, (byte) 0x0B, (byte) 0x08, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x01,
            (byte) 0x01, (byte) 0x01, (byte) 0x11, (byte) 0x00, (byte) 0xFF, (byte) 0xC4, (byte) 0x00, (byte) 0x1F,
            (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x05, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x01,
            (byte) 0x01, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06,
            (byte) 0x07, (byte) 0x08, (byte) 0x09, (byte) 0x0A, (byte) 0x0B, (byte) 0xFF, (byte) 0xC4, (byte) 0x00,
            (byte) 0xB5, (byte) 0x10, (byte) 0x00, (byte) 0x02, (byte) 0x01, (byte) 0x03, (byte) 0x03, (byte) 0x02,
            (byte) 0x04, (byte) 0x03, (byte) 0x05, (byte) 0x05, (byte) 0x04, (byte) 0x04, (byte) 0x00, (byte) 0x00,
            (byte) 0x01, (byte) 0x7D, (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x00, (byte) 0x04, (byte) 0x11,
            (byte) 0x05, (byte) 0x12, (byte) 0x21, (byte) 0x31, (byte) 0x41, (byte) 0x06, (byte) 0x13, (byte) 0x51,
            (byte) 0x61, (byte) 0x07, (byte) 0x22, (byte) 0x71, (byte) 0x14, (byte) 0x32, (byte) 0x81, (byte) 0x91,
            (byte) 0xA1, (byte) 0x08, (byte) 0x23, (byte) 0x42, (byte) 0xB1, (byte) 0xC1, (byte) 0x15, (byte) 0x52,
            (byte) 0xD1, (byte) 0xF0, (byte) 0x24, (byte) 0x33, (byte) 0x62, (byte) 0x72, (byte) 0x82, (byte) 0x09,
            (byte) 0x0A, (byte) 0x16, (byte) 0x17, (byte) 0x18, (byte) 0x19, (byte) 0x1A, (byte) 0x25, (byte) 0x26,
            (byte) 0x27, (byte) 0x28, (byte) 0x29, (byte) 0x2A, (byte) 0x34, (byte) 0x35, (byte) 0x36, (byte) 0x37,
            (byte) 0x38, (byte) 0x39, (byte) 0x3A, (byte) 0x43, (byte) 0x44, (byte) 0x45, (byte) 0x46, (byte) 0x47,
            (byte) 0x48, (byte) 0x49, (byte) 0x4A, (byte) 0x53, (byte) 0x54, (byte) 0x55, (byte) 0x56, (byte) 0x57,
            (byte) 0x58, (byte) 0x59, (byte) 0x5A, (byte) 0x63, (byte) 0x64, (byte) 0x65, (byte) 0x66, (byte) 0x67,
            (byte) 0x68, (byte) 0x69, (byte) 0x6A, (byte) 0x73, (byte) 0x74, (byte) 0x75, (byte) 0x76, (byte) 0x77,
            (byte) 0x78, (byte) 0x79, (byte) 0x7A, (byte) 0x83, (byte) 0x84, (byte) 0x85, (byte) 0x86,
            (byte) 0x87, (byte) 0x88, (byte) 0x89, (byte) 0x8A, (byte) 0x92, (byte) 0x93,
            (byte) 0x94, (byte) 0x95, (byte) 0x96, (byte) 0x97, (byte) 0x98, (byte) 0x99,
            (byte) 0x9A, (byte) 0xA2, (byte) 0xA3, (byte) 0xA4, (byte) 0xA5, (byte) 0xA6,
            (byte) 0xA7, (byte) 0xA8, (byte) 0xA9, (byte) 0xAA, (byte) 0xB2, (byte) 0xB3,
            (byte) 0xB4, (byte) 0xB5, (byte) 0xB6, (byte) 0xB7, (byte) 0xB8, (byte) 0xB9,
            (byte) 0xBA, (byte) 0xC2, (byte) 0xC3, (byte) 0xC4, (byte) 0xC5, (byte) 0xC6,
            (byte) 0xC7, (byte) 0xC8, (byte) 0xC9, (byte) 0xCA, (byte) 0xD2, (byte) 0xD3,
            (byte) 0xD4, (byte) 0xD5, (byte) 0xD6, (byte) 0xD7, (byte) 0xD8, (byte) 0xD9,
            (byte) 0xDA, (byte) 0xE1, (byte) 0xE2, (byte) 0xE3, (byte) 0xE4, (byte) 0xE5,
            (byte) 0xE6, (byte) 0xE7, (byte) 0xE8, (byte) 0xE9, (byte) 0xEA, (byte) 0xF1,
            (byte) 0xF2, (byte) 0xF3, (byte) 0xF4, (byte) 0xF5, (byte) 0xF6, (byte) 0xF7,
            (byte) 0xF8, (byte) 0xF9, (byte) 0xFA, (byte) 0xFF, (byte) 0xDA, (byte) 0x00, (byte) 0x08,
            (byte) 0x01, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x3F, (byte) 0x00, (byte) 0xFB, (byte) 0xD4, (byte) 0xFF,
            (byte) 0xD9
        };

        Files.write(file.toPath(), jpegData);
        System.out.println("✅ Generated sample.jpg (" + file.length() + " bytes)");
    }
}
