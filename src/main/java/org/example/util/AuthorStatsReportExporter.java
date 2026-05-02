package org.example.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.db.AuthorStatsDao.AuthorStatsSnapshot;
import org.example.db.AuthorStatsDao.BookBorrowStat;
import org.example.db.AuthorStatsDao.BorrowStatusStat;
import org.example.db.AuthorStatsDao.BorrowTrendPoint;
import org.example.db.AuthorStatsDao.GenreStat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exports a full author statistics report (all KPIs and charts data) to PDF or Excel.
 */
public final class AuthorStatsReportExporter {

    private static final String SUBTITLE = "Generated from Library Management System - Author statistics";
    private static final ZoneId HONG_KONG = ZoneId.of("Asia/Hong_Kong");
    private static final DateTimeFormatter REPORT_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(HONG_KONG);

    private AuthorStatsReportExporter() {}

    private static String generatedAtHongKong() {
        return REPORT_TIME.format(Instant.now());
    }

    public static void exportExcel(
            Path path,
            AuthorStatsSnapshot snapshot,
            List<BorrowTrendPoint> weeklyTrend,
            List<BorrowTrendPoint> monthlyTrend
    ) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            writeKpiSheet(wb.createSheet("KPIs"), snapshot);
            writeTopBooksSheet(wb.createSheet("Top borrowed"), snapshot.topBorrowedBooks());
            writeGenreSheet(wb.createSheet("Genre"), snapshot.genreDistribution());
            writeBorrowStatusSheet(wb.createSheet("Borrow status"), snapshot.borrowStatusDistribution());
            writeTrendSheet(wb.createSheet("Trend weekly"), weeklyTrend);
            writeTrendSheet(wb.createSheet("Trend monthly"), monthlyTrend);
            try (OutputStream out = Files.newOutputStream(path)) {
                wb.write(out);
            }
        }
    }

    private static void writeKpiSheet(XSSFSheet sheet, AuthorStatsSnapshot s) {
        Row meta = sheet.createRow(0);
        meta.createCell(0).setCellValue("Generated at (Hong Kong)");
        meta.createCell(1).setCellValue(generatedAtHongKong());
        Row h = sheet.createRow(1);
        h.createCell(0).setCellValue("Metric");
        h.createCell(1).setCellValue("Value");
        int r = 2;
        row(sheet, r++, "Published books", s.publishedBooks());
        row(sheet, r++, "Total reads", s.totalReads());
        row(sheet, r++, "Total borrows", s.totalBorrows());
        row(sheet, r++, "Active borrows", s.activeBorrows());
        row(sheet, r++, "Unique readers", s.distinctReaders());
        row(sheet, r++, "Average rating", s.averageRating());
        row(sheet, r++, "Review count", s.reviewCount());
        sheet.setColumnWidth(0, 24 * 256);
        sheet.setColumnWidth(1, 18 * 256);
    }

    private static void row(XSSFSheet sheet, int index, String label, double value) {
        Row row = sheet.createRow(index);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
    }

    private static void row(XSSFSheet sheet, int index, String label, int value) {
        Row row = sheet.createRow(index);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
    }

    private static void writeTopBooksSheet(XSSFSheet sheet, List<BookBorrowStat> rows) {
        Row h = sheet.createRow(0);
        h.createCell(0).setCellValue("Title");
        h.createCell(1).setCellValue("Borrow count");
        int r = 1;
        for (BookBorrowStat b : rows) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(b.title() == null ? "" : b.title());
            row.createCell(1).setCellValue(b.borrowCount());
        }
        sheet.setColumnWidth(0, 48 * 256);
    }

    private static void writeGenreSheet(XSSFSheet sheet, List<GenreStat> rows) {
        Row h = sheet.createRow(0);
        h.createCell(0).setCellValue("Genre");
        h.createCell(1).setCellValue("Count");
        int r = 1;
        for (GenreStat g : rows) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(g.genre() == null ? "" : g.genre());
            row.createCell(1).setCellValue(g.count());
        }
    }

    private static void writeBorrowStatusSheet(XSSFSheet sheet, List<BorrowStatusStat> rows) {
        Row h = sheet.createRow(0);
        h.createCell(0).setCellValue("Status");
        h.createCell(1).setCellValue("Count");
        int r = 1;
        for (BorrowStatusStat s : rows) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(s.status() == null ? "" : s.status());
            row.createCell(1).setCellValue(s.count());
        }
    }

    private static void writeTrendSheet(XSSFSheet sheet, List<BorrowTrendPoint> rows) {
        Row h = sheet.createRow(0);
        h.createCell(0).setCellValue("Period");
        h.createCell(1).setCellValue("Borrows");
        int r = 1;
        for (BorrowTrendPoint p : rows) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(p.periodLabel() == null ? "" : p.periodLabel());
            row.createCell(1).setCellValue(p.borrowCount());
        }
    }

    public static void exportPdf(
            Path path,
            AuthorStatsSnapshot snapshot,
            List<BorrowTrendPoint> weeklyTrend,
            List<BorrowTrendPoint> monthlyTrend
    ) throws IOException {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        float fontSize = 11;
        float margin = 50;
        float lineHeight = 14;

        try (PDDocument document = new PDDocument()) {
            PdfWriter writer = new PdfWriter(document, font, fontSize, margin, lineHeight);
            writer.title("Author book statistics");
            writer.line(SUBTITLE);
            writer.line("Generated at (Hong Kong): " + generatedAtHongKong());
            writer.blank();
            writer.section("Key metrics");
            writer.line("Published books: " + snapshot.publishedBooks());
            writer.line("Total reads: " + snapshot.totalReads());
            writer.line("Total borrows: " + snapshot.totalBorrows());
            writer.line("Active borrows: " + snapshot.activeBorrows());
            writer.line("Unique readers: " + snapshot.distinctReaders());
            writer.line("Average rating: " + snapshot.averageRating());
            writer.line("Review count: " + snapshot.reviewCount());
            writer.blank();
            writer.section("Top borrowed books");
            for (BookBorrowStat b : snapshot.topBorrowedBooks()) {
                writer.line(asciiSafe(b.title()) + " — " + b.borrowCount());
            }
            writer.blank();
            writer.section("Genre distribution");
            for (GenreStat g : snapshot.genreDistribution()) {
                writer.line(asciiSafe(g.genre()) + ": " + g.count());
            }
            writer.blank();
            writer.section("Borrow status");
            for (BorrowStatusStat s : snapshot.borrowStatusDistribution()) {
                writer.line(asciiSafe(s.status()) + ": " + s.count());
            }
            writer.blank();
            writer.section("Borrow trend (weekly)");
            for (BorrowTrendPoint p : weeklyTrend) {
                writer.line(asciiSafe(p.periodLabel()) + ": " + p.borrowCount());
            }
            writer.blank();
            writer.section("Borrow trend (monthly)");
            for (BorrowTrendPoint p : monthlyTrend) {
                writer.line(asciiSafe(p.periodLabel()) + ": " + p.borrowCount());
            }
            writer.finish();
            document.save(path.toFile());
        }
    }

    private static String asciiSafe(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 32 && c <= 126) {
                b.append(c);
            } else {
                b.append('?');
            }
        }
        return b.toString();
    }

    private static final class PdfWriter {
        private final PDDocument document;
        private final PDType1Font font;
        private final float fontSize;
        private final float margin;
        private final float lineHeight;
        private PDPage page;
        private PDPageContentStream contentStream;
        private float y;

        private PdfWriter(PDDocument document, PDType1Font font, float fontSize, float margin, float lineHeight)
                throws IOException {
            this.document = document;
            this.font = font;
            this.fontSize = fontSize;
            this.margin = margin;
            this.lineHeight = lineHeight;
            newPage();
        }

        private void newPage() throws IOException {
            if (contentStream != null) {
                contentStream.close();
            }
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            contentStream = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - margin;
        }

        private void title(String text) throws IOException {
            contentStream.beginText();
            contentStream.setFont(font, fontSize + 4);
            contentStream.newLineAtOffset(margin, y);
            contentStream.showText(asciiSafe(text));
            contentStream.endText();
            y -= lineHeight + 6;
        }

        private void section(String text) throws IOException {
            blank();
            contentStream.beginText();
            contentStream.setFont(font, fontSize + 2);
            contentStream.newLineAtOffset(margin, y);
            contentStream.showText(asciiSafe(text));
            contentStream.endText();
            y -= lineHeight + 4;
        }

        private void line(String text) throws IOException {
            if (y < margin + lineHeight) {
                newPage();
            }
            String t = asciiSafe(text);
            if (t.length() <= 95) {
                contentStream.beginText();
                contentStream.setFont(font, fontSize);
                contentStream.newLineAtOffset(margin, y);
                contentStream.showText(t);
                contentStream.endText();
            } else {
                int start = 0;
                while (start < t.length()) {
                    if (y < margin + lineHeight) {
                        newPage();
                    }
                    int end = Math.min(start + 95, t.length());
                    contentStream.beginText();
                    contentStream.setFont(font, fontSize);
                    contentStream.newLineAtOffset(margin, y);
                    contentStream.showText(t.substring(start, end));
                    contentStream.endText();
                    y -= lineHeight;
                    start = end;
                }
                return;
            }
            y -= lineHeight;
        }

        private void blank() throws IOException {
            y -= lineHeight / 2;
            if (y < margin + lineHeight) {
                newPage();
            }
        }

        private void finish() throws IOException {
            if (contentStream != null) {
                contentStream.close();
                contentStream = null;
            }
        }
    }
}
