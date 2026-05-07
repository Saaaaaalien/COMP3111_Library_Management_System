package org.example.util;

import javafx.scene.image.Image;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;

/**
 * Utility to read a short preview of a book file for "Quick Review" before borrowing.
 * Supports plain text (.txt), PDF (.pdf), and Word (.doc, .docx) files.
 */
public final class BookPreviewUtil {

    /** Maximum number of characters to include in a text preview (roughly first few pages). */
    private static final int MAX_PREVIEW_CHARS = 5000;
    private static final int APPROX_CHARS_PER_PAGE = 2200;

    private BookPreviewUtil() {}

    /**
     * Returns the number of pages in a PDF on disk, or 0 if missing or not a readable PDF.
     * Used by the borrowed-book WebView reader (PDFBox stays scoped to book-file utilities / preview flows).
     */
    public static int getPdfPageCount(Path path) {
        if (path == null || !Files.isRegularFile(path) || !Files.isReadable(path)) {
            return 0;
        }
        if (!path.toString().toLowerCase().endsWith(".pdf")) {
            return 0;
        }
        try (PDDocument doc = Loader.loadPDF(path.toFile())) {
            return doc.getNumberOfPages();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Reads a preview of the book content from the file at the given path.
     * Supported formats: .txt (plain text), .pdf (PDFBox), .docx and .doc (Apache POI).
     * Returns null if the format is unsupported, the file is missing/unreadable, or extraction fails.
     *
     * @param filePath absolute path to the book file (may be null or empty)
     * @return preview text (first ~N characters), or null if not available
     */
    public static String readTextPreview(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        Path path = Paths.get(filePath);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            return null;
        }
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".txt")) {
            return readTxtPreview(path);
        }
        if (lower.endsWith(".pdf")) {
            return readPdfPreview(path);
        }
        if (lower.endsWith(".docx")) {
            return readDocxPreview(path);
        }
        if (lower.endsWith(".doc")) {
            return readDocPreview(path);
        }
        if (lower.endsWith(".epub")) {
            try {
                String t = readEpubAsPlainText(path, MAX_PREVIEW_CHARS);
                return t == null || t.isBlank() ? null : t;
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Reads the full text content from a supported file type.
     * Supported formats: .txt, .pdf, .docx, .doc.
     *
     * @param filePath absolute path to the book file (may be null or empty)
     * @return full text content, or null if unavailable/unsupported
     */
    public static String readTextContent(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        Path path = Paths.get(filePath);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            return null;
        }
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".txt")) {
            return readTxtContent(path);
        }
        if (lower.endsWith(".pdf")) {
            return readPdfContent(path);
        }
        if (lower.endsWith(".docx")) {
            return readDocxContent(path);
        }
        if (lower.endsWith(".doc")) {
            return readDocContent(path);
        }
        if (lower.endsWith(".epub")) {
            try {
                String t = readEpubAsPlainText(path, Integer.MAX_VALUE);
                return t == null || t.isBlank() ? null : t;
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Reads roughly the first {@code maxPages} pages worth of content.
     * For PDF, this uses real page boundaries. For text/Word, it truncates by
     * an approximate characters-per-page budget.
     *
     * @param filePath absolute path to the book file (may be null or empty)
     * @param maxPages max number of pages to read (must be > 0)
     * @return extracted text for the first pages, or null if unavailable/unsupported
     */
    public static String readTextContentFirstPages(String filePath, int maxPages) {
        if (filePath == null || filePath.isBlank() || maxPages <= 0) {
            return null;
        }
        Path path = Paths.get(filePath);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            return null;
        }
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return readPdfContentFirstPages(path, maxPages);
        }
        int maxChars = maxPages * APPROX_CHARS_PER_PAGE;
        String fullText;
        if (lower.endsWith(".txt")) {
            fullText = readTxtContent(path);
        } else if (lower.endsWith(".docx")) {
            fullText = readDocxContent(path);
        } else if (lower.endsWith(".doc")) {
            fullText = readDocContent(path);
        } else if (lower.endsWith(".epub")) {
            try {
                fullText = readEpubAsPlainText(path, maxChars);
            } catch (IOException e) {
                return null;
            }
        } else {
            return null;
        }
        if (fullText == null || fullText.isBlank()) {
            return null;
        }
        return truncateAtBoundary(fullText.trim(), maxChars);
    }

    private static String readTxtPreview(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content == null || content.isEmpty()) {
                return null;
            }
            return truncateAtBoundary(content, MAX_PREVIEW_CHARS);
        } catch (IOException e) {
            return null;
        }
    }

    private static String readTxtContent(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content == null || content.isEmpty()) {
                return null;
            }
            return content;
        } catch (IOException e) {
            return null;
        }
    }

    private static String readPdfPreview(Path path) {
        try {
            PDDocument document = Loader.loadPDF(path.toFile());
            try {
                org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                stripper.setEndPage(Math.min(5, document.getNumberOfPages()));
                String text = stripper.getText(document);
                if (text == null || text.isBlank()) {
                    return null;
                }
                return truncateAtBoundary(text.trim(), MAX_PREVIEW_CHARS);
            } finally {
                document.close();
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static String readPdfContent(Path path) {
        try {
            PDDocument document = Loader.loadPDF(path.toFile());
            try {
                org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                String text = stripper.getText(document);
                if (text == null || text.isBlank()) {
                    return null;
                }
                return text.trim();
            } finally {
                document.close();
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static String readPdfContentFirstPages(Path path, int maxPages) {
        try {
            PDDocument document = Loader.loadPDF(path.toFile());
            try {
                org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                stripper.setEndPage(Math.min(maxPages, document.getNumberOfPages()));
                String text = stripper.getText(document);
                if (text == null || text.isBlank()) {
                    return null;
                }
                return text.trim();
            } finally {
                document.close();
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static String readDocxPreview(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            org.apache.poi.xwpf.usermodel.XWPFDocument document = new org.apache.poi.xwpf.usermodel.XWPFDocument(in);
            org.apache.poi.xwpf.extractor.XWPFWordExtractor extractor = new org.apache.poi.xwpf.extractor.XWPFWordExtractor(document);
            try {
                String text = extractor.getText();
                if (text == null || text.isBlank()) {
                    return null;
                }
                return truncateAtBoundary(text.trim(), MAX_PREVIEW_CHARS);
            } finally {
                extractor.close();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String readDocxContent(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            org.apache.poi.xwpf.usermodel.XWPFDocument document = new org.apache.poi.xwpf.usermodel.XWPFDocument(in);
            org.apache.poi.xwpf.extractor.XWPFWordExtractor extractor = new org.apache.poi.xwpf.extractor.XWPFWordExtractor(document);
            try {
                String text = extractor.getText();
                if (text == null || text.isBlank()) {
                    return null;
                }
                return text.trim();
            } finally {
                extractor.close();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String readDocPreview(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            org.apache.poi.hwpf.HWPFDocument document = new org.apache.poi.hwpf.HWPFDocument(in);
            org.apache.poi.hwpf.extractor.WordExtractor extractor = new org.apache.poi.hwpf.extractor.WordExtractor(document);
            try {
                String text = extractor.getText();
                if (text == null || text.isBlank()) {
                    return null;
                }
                return truncateAtBoundary(text.trim(), MAX_PREVIEW_CHARS);
            } finally {
                extractor.close();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String readDocContent(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            org.apache.poi.hwpf.HWPFDocument document = new org.apache.poi.hwpf.HWPFDocument(in);
            org.apache.poi.hwpf.extractor.WordExtractor extractor = new org.apache.poi.hwpf.extractor.WordExtractor(document);
            try {
                String text = extractor.getText();
                if (text == null || text.isBlank()) {
                    return null;
                }
                return text.trim();
            } finally {
                extractor.close();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * For PDF files, renders up to maxPages pages as JavaFX Images for visual preview.
     * Returns an empty list if rendering fails.
     */
    public static List<Image> readPdfPreviewImages(String filePath, int maxPages) {
        List<Image> images = new ArrayList<>();
        if (filePath == null || filePath.isBlank()) {
            return images;
        }
        if (!filePath.toLowerCase().endsWith(".pdf")) {
            return images;
        }
        Path path = Paths.get(filePath);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            return images;
        }
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = Math.min(maxPages, document.getNumberOfPages());
            for (int i = 0; i < pageCount; i++) {
                BufferedImage buffered = renderer.renderImageWithDPI(i, 120);
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    ImageIO.write(buffered, "png", baos);
                    baos.flush();
                    byte[] data = baos.toByteArray();
                    try (ByteArrayInputStream bais = new ByteArrayInputStream(data)) {
                        Image fxImage = new Image(bais);
                        images.add(fxImage);
                    }
                }
            }
        } catch (IOException e) {
            // ignore, return whatever we have (likely empty)
        }
        return images;
    }

    /**
     * Truncates text to at most maxChars characters, breaking at a line or word boundary when possible.
     */
    private static String truncateAtBoundary(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        String preview = text.substring(0, maxChars);
        int lastNewline = preview.lastIndexOf('\n');
        if (lastNewline > maxChars / 2) {
            return preview.substring(0, lastNewline + 1) + "\n\n[...]";
        }
        int lastSpace = preview.lastIndexOf(' ');
        if (lastSpace > maxChars / 2) {
            return preview.substring(0, lastSpace + 1) + "\n\n[...]";
        }
        return preview + "\n\n[...]";
    }

    /**
     * Returns whether the file at the given path is a supported type for content preview.
     */
    public static boolean isSupportedPreviewType(String filePath) {
        if (filePath == null || filePath.isBlank()) return false;
        String lower = filePath.toLowerCase();
        return lower.endsWith(".txt") || lower.endsWith(".pdf")
                || lower.endsWith(".doc") || lower.endsWith(".docx")
                || lower.endsWith(".epub");
    }

    /**
     * Extracts readable plain text from XHTML/HTML inside an EPUB (ZIP).
     *
     * @param maxChars stop after roughly this many characters (use {@link Integer#MAX_VALUE} for full text)
     */
    public static String readEpubAsPlainText(Path epubPath, int maxChars) throws IOException {
        if (epubPath == null || !Files.isRegularFile(epubPath) || !Files.isReadable(epubPath)) {
            throw new IOException("EPUB not readable: " + epubPath);
        }
        StringBuilder sb = new StringBuilder();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(epubPath))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase();
                if ((name.endsWith(".html") || name.endsWith(".xhtml") || name.endsWith(".htm"))
                        && !name.contains("toc") && !name.contains("nav")) {
                    byte[] raw = zip.readAllBytes();
                    String html = new String(raw, StandardCharsets.UTF_8);
                    String text = stripHtmlTagsFromEpub(html);
                    if (!text.isBlank()) {
                        sb.append(text).append("\n\n");
                        if (sb.length() >= maxChars) break;
                    }
                }
                zip.closeEntry();
            }
        }
        String out = sb.toString().trim();
        if (out.isEmpty()) {
            throw new IOException("EPUB yielded no readable chapter text.");
        }
        if (maxChars < Integer.MAX_VALUE && out.length() > maxChars) {
            out = truncateAtBoundary(out.substring(0, maxChars).trim(), maxChars);
        }
        return out;
    }

    private static String stripHtmlTagsFromEpub(String html) {
        String t = html.replaceAll("(?si)<(script|style)[^>]*>.*?</\\1>", "");
        t = t.replaceAll("(?i)</(p|div|h[1-6]|tr|li)>", "\n");
        t = t.replaceAll("(?i)<br\\s*/?>", "\n");
        t = t.replaceAll("<[^>]+>", "");
        t = t.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&mdash;", "-").replace("&ndash;", "-").replace("&hellip;", "...");
        return t.replaceAll("\\n{3,}", "\n\n").trim();
    }
}
