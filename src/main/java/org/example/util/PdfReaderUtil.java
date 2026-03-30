package org.example.util;

import javafx.scene.image.Image;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PDF page rendering and per-page text for the borrowed-book reader (image + text selection fallback).
 */
public final class PdfReaderUtil {

    private PdfReaderUtil() {}

    public static int getPageCount(Path pdfPath) throws IOException {
        if (!Files.isRegularFile(pdfPath)) {
            return 0;
        }
        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
            return doc.getNumberOfPages();
        }
    }

    public static String extractPageText(Path pdfPath, int pageIndex0) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
            if (pageIndex0 < 0 || pageIndex0 >= doc.getNumberOfPages()) {
                return "";
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(pageIndex0 + 1);
            stripper.setEndPage(pageIndex0 + 1);
            return stripper.getText(doc);
        }
    }

    public static Image renderPage(Path pdfPath, int pageIndex0, float scale) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
            if (pageIndex0 < 0 || pageIndex0 >= doc.getNumberOfPages()) {
                return null;
            }
            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage bim = renderer.renderImage(pageIndex0, scale);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bim, "png", baos);
            return new Image(new ByteArrayInputStream(baos.toByteArray()));
        }
    }
}
