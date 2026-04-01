package org.example.util;

import java.nio.file.Path;
import java.io.IOException;

/**
 * Placeholder for file-to-PDF conversion.
 * Currently disabled due to PDFBox 3.x font loading complexity.
 * Non-PDF files (TXT, DOCX, DOC) are stored as-is and kept in their original format.
 * Future: integrate a reliable PDF conversion library or service if needed.
 */
public class FileToPdfConverter {

    private FileToPdfConverter() {}

    /**
     * Returns the original source path unchanged.
     * Non-PDF files are not converted; they remain in their original format.
     */
    public static Path convertToPdf(Path src) throws IOException {
        if (src == null) throw new IllegalArgumentException("src is null");
        // For now, return the original file path unchanged.
        // Authors can still upload TXT/DOCX/DOC; they are stored as-is.
        return src;
    }
}
