package org.example.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads public-domain books from online repositories.
 *
 * Sources tried in order:
 *   1. Project Gutenberg via the gutendex.com JSON API
 *      — uses actual download URLs from the API's `formats` map,
 *        not manually-constructed cache paths.
 *   2. Internet Archive — queries the advanced search API, then fetches
 *      per-item metadata to discover real filenames.
 *   3. Standard Ebooks — scrapes the search-results HTML for EPUB links.
 */
public final class BookDownloaderService {

    private static final String DOWNLOAD_DIR = "data/downloaded_books";
    private static final int    TIMEOUT_SEC  = 30;
    private static final int    DL_TIMEOUT   = 60;

    // Follow redirects so that Gutenberg CDN redirects resolve correctly.
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SEC))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; LibraryApp/1.0)";

    private BookDownloaderService() {}

    // ── Public API ────────────────────────────────────────────────────────────

    public static class DownloadResult {
        public final boolean success;
        public final String filePath;
        public final String fileName;
        public final String errorMessage;

        public DownloadResult(boolean success, String filePath,
                              String fileName, String errorMessage) {
            this.success      = success;
            this.filePath     = filePath;
            this.fileName     = fileName;
            this.errorMessage = errorMessage;
        }

        public static DownloadResult failure(String msg) {
            return new DownloadResult(false, null, null, msg);
        }

        public static DownloadResult success(String filePath, String fileName) {
            return new DownloadResult(true, filePath, fileName, null);
        }
    }

    /** A downloadable public-domain book candidate. */
    public record BookCandidate(String title, String author, String epubUrl) {}

    /** Holds search results split into a top match and a list of alternatives. */
    public record SearchResults(BookCandidate topResult, List<BookCandidate> alternatives) {
        public boolean isEmpty() { return topResult == null && alternatives.isEmpty(); }
    }

    /**
     * Downloads a public-domain book. Returns a DownloadResult indicating
     * success (with file path) or failure (with explanation).
     */
    public static DownloadResult downloadBook(String title, String author) {
        ensureDownloadDir();

        boolean titleBlank  = title  == null || title.isBlank();
        boolean authorBlank = author == null || author.isBlank();
        if (titleBlank && authorBlank) {
            return DownloadResult.failure("Book title and author cannot both be empty.");
        }

        String ct = cleanForSearch(title);
        String ca = cleanForSearch(author);

        DownloadResult r = tryGutenberg(ct, ca);
        if (r.success) return convertToPdfIfNeeded(r);

        r = tryInternetArchive(ct, ca);
        if (r.success) return convertToPdfIfNeeded(r);

        r = tryStandardEbooks(ct, ca);
        if (r.success) return convertToPdfIfNeeded(r);

        String label = titleBlank ? author : title;
        return DownloadResult.failure(
                "Could not find \"" + label + "\" in any public-domain repository. " +
                "Searched: Project Gutenberg, Internet Archive, Standard Ebooks. " +
                "Only public-domain books are available for automatic download.");
    }

    /**
     * Searches online sources for books similar to the requested title.
     * Used when the exact book cannot be downloaded, to offer the librarian alternatives.
     */
    public static List<BookCandidate> findOnlineAlternatives(String requestedTitle, String author) {
        String ct = cleanForSearch(requestedTitle);
        String ca = cleanForSearch(author);

        List<String> queries = new ArrayList<>();
        if (!ct.isBlank()) queries.add(ct);
        if (!ct.isBlank() && !ca.isBlank()) queries.add(ct + " " + ca);
        if (!ca.isBlank()) queries.add(ca);
        if (!ct.isBlank()) {
            for (String w : ct.split("\\s+")) {
                if (w.length() >= 4) { queries.add(w); break; }
            }
        }

        Set<String> seenTitles = new HashSet<>();
        List<BookCandidate> result = new ArrayList<>();
        for (String q : queries) {
            for (BookCandidate c : extractGutendexCandidates(q)) {
                if (seenTitles.add(c.title().toLowerCase())) result.add(c);
            }
            if (result.size() >= 5) break;
        }
        return result.subList(0, Math.min(5, result.size()));
    }

    /**
     * Searches online for downloadable candidates matching the given title/author without
     * downloading anything.  Returns the top Gutenberg result separately from the
     * remaining alternatives so the UI can display them in two labelled sections.
     */
    public static SearchResults searchForCandidates(String title, String author) {
        String ct = cleanForSearch(title);
        String ca = cleanForSearch(author);

        Set<String> seenTitles = new HashSet<>();
        List<BookCandidate> candidates = new ArrayList<>();
        for (String q : buildQueries(ct, ca)) {
            for (BookCandidate c : extractGutendexCandidates(q)) {
                if (seenTitles.add(c.title().toLowerCase())) candidates.add(c);
            }
            if (candidates.size() >= 6) break;
        }

        if (candidates.isEmpty()) return new SearchResults(null, List.of());
        BookCandidate top = candidates.get(0);
        List<BookCandidate> alts = new ArrayList<>(candidates.subList(1, Math.min(candidates.size(), 6)));
        return new SearchResults(top, alts);
    }

    /** Downloads a specific candidate book chosen by the librarian. */
    public static DownloadResult downloadCandidate(BookCandidate candidate) {
        ensureDownloadDir();
        DownloadResult r = downloadFile(candidate.epubUrl(), safeFilename(candidate.title()) + ".epub");
        return convertToPdfIfNeeded(r);
    }

    public static List<String> getDownloadedBooks() {
        List<String> books = new ArrayList<>();
        try {
            File dir = new File(DOWNLOAD_DIR);
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles((d, n) ->
                        n.endsWith(".epub") || n.endsWith(".txt") ||
                        n.endsWith(".pdf")  || n.endsWith(".html"));
                if (files != null) for (File f : files) books.add(f.getName());
            }
        } catch (Exception ignored) {}
        return books;
    }

    // ── String helpers ────────────────────────────────────────────────────────

    /** Removes punctuation and collapses whitespace for search queries. */
    private static String cleanForSearch(String s) {
        if (s == null || s.isBlank()) return "";
        return s.replaceAll("[^a-zA-Z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    /** Makes a string safe for use as a filename (no path separators, no spaces). */
    private static String safeFilename(String s) {
        if (s == null || s.isBlank()) return "book";
        return s.replaceAll("[^a-zA-Z0-9._\\-]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_+|_+$", "");
    }

    // ── 1. Project Gutenberg via gutendex ─────────────────────────────────────

    private static DownloadResult tryGutenberg(String title, String author) {
        for (String q : buildQueries(title, author)) {
            DownloadResult r = queryGutendex(q, title.isBlank() ? author : title);
            if (r.success) return r;
        }
        return DownloadResult.failure("Not found on Project Gutenberg");
    }

    private static List<String> buildQueries(String title, String author) {
        List<String> qs = new ArrayList<>();
        // Combined query is most specific and usually best.
        if (!title.isBlank() && !author.isBlank()) qs.add(title + " " + author);
        if (!title.isBlank()) {
            qs.add(title);
            // Shorter variant for long titles with possible subtitles.
            String[] words = title.split("\\s+");
            if (words.length > 3)
                qs.add(words[0] + " " + words[1] + " " + words[2]);
            else if (words.length > 2)
                qs.add(words[0] + " " + words[1]);
        }
        if (!author.isBlank()) qs.add(author);
        return qs;
    }

    /**
     * Queries gutendex.com and extracts real download URLs from the `formats`
     * JSON map returned by the API. This avoids the common bug of constructing
     * cache URLs that do not always exist.
     *
     * Example formats entry:
     *   "application/epub+zip": "https://www.gutenberg.org/ebooks/1342.epub.noimages"
     *   "text/plain; charset=utf-8": "https://www.gutenberg.org/files/1342/1342-0.txt"
     */
    private static DownloadResult queryGutendex(String query, String displayTitle) {
        try {
            String url = "https://gutendex.com/books?search=" +
                    URLEncoder.encode(query, StandardCharsets.UTF_8);

            HttpResponse<String> resp = sendGet(url);
            if (resp == null || resp.statusCode() != 200)
                return DownloadResult.failure("Gutendex HTTP " +
                        (resp == null ? "null" : resp.statusCode()));

            String json = resp.body();

            // Detect empty result set before trying to extract URLs.
            if (!json.contains("\"results\"") ||
                    Pattern.compile("\"count\"\\s*:\\s*0").matcher(json).find()) {
                return DownloadResult.failure("No Gutenberg results for: " + query);
            }

            // Reject false positives: if the top result's title is too different from what
            // was requested, treat this search as failed so alternatives can be offered.
            // Example: "The Idiot Brain" vs returned "The Idiot" → rejected.
            String firstTitle = extractFirstResultTitle(json);
            if (firstTitle != null && !isTitleSimilarEnough(displayTitle, firstTitle)) {
                return DownloadResult.failure(
                        "Best match \"" + firstTitle + "\" is not similar enough to \"" + displayTitle + "\"");
            }

            String base = safeFilename(displayTitle);

            // Try native PDF first (some Gutenberg books have one).
            String pdfUrl = jsonValue(json, "application/pdf");
            if (pdfUrl != null) {
                DownloadResult r = downloadFile(pdfUrl, base + ".pdf");
                if (r.success) return r;
            }

            // EPUB — will be converted to PDF by the caller.
            String epubUrl = jsonValue(json, "application/epub\\+zip");
            if (epubUrl != null) {
                DownloadResult r = downloadFile(epubUrl, base + ".epub");
                if (r.success) return r;
            }

            // Plain text — will be converted to PDF by the caller.
            String txtUrl = jsonValuePrefix(json, "text/plain");
            if (txtUrl != null) {
                DownloadResult r = downloadFile(txtUrl, base + ".txt");
                if (r.success) return r;
            }

            return DownloadResult.failure("Gutenberg: book found but no format downloaded");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return DownloadResult.failure("Interrupted");
        } catch (Exception e) {
            return DownloadResult.failure("Gutenberg error: " + e.getMessage());
        }
    }

    // ── 2. Internet Archive ───────────────────────────────────────────────────

    private static DownloadResult tryInternetArchive(String title, String author) {
        List<String> queries = new ArrayList<>();
        if (!title.isBlank()) {
            queries.add("title:(\"" + title + "\") mediatype:texts language:English" +
                    (author.isBlank() ? "" : " creator:(\"" + author + "\")"));
            queries.add("title:(" + title + ") mediatype:texts");
        }
        if (!author.isBlank()) {
            queries.add("creator:(\"" + author + "\") mediatype:texts subject:accessible_book");
        }

        for (String q : queries) {
            DownloadResult r = searchInternetArchive(q, title.isBlank() ? author : title);
            if (r.success) return r;
        }
        return DownloadResult.failure("Not found on Internet Archive");
    }

    private static DownloadResult searchInternetArchive(String query, String displayTitle) {
        try {
            String url = "https://archive.org/advancedsearch.php?q=" +
                    URLEncoder.encode(query, StandardCharsets.UTF_8) +
                    "&fl=identifier&output=json&rows=5&sort=downloads+desc";

            HttpResponse<String> resp = sendGet(url);
            if (resp == null || resp.statusCode() != 200)
                return DownloadResult.failure("IA HTTP error");

            Pattern p = Pattern.compile("\"identifier\"\\s*:\\s*\"([^\"]+)\"");
            Matcher m = p.matcher(resp.body());
            while (m.find()) {
                String id = m.group(1);
                DownloadResult r = downloadFromArchiveItem(id, displayTitle);
                if (r.success) return r;
            }
            return DownloadResult.failure("No downloadable IA items found");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return DownloadResult.failure("Interrupted");
        } catch (Exception e) {
            return DownloadResult.failure("IA search error: " + e.getMessage());
        }
    }

    /**
     * Fetches the item-level metadata for an IA identifier to discover actual
     * filenames, then downloads the best available format.
     * Guessing URLs from the identifier alone does not work reliably because
     * IA items use the original upload filename, not the identifier.
     */
    private static DownloadResult downloadFromArchiveItem(String identifier, String displayTitle) {
        try {
            HttpResponse<String> metaResp =
                    sendGet("https://archive.org/metadata/" + identifier);
            if (metaResp == null || metaResp.statusCode() != 200)
                return DownloadResult.failure("IA metadata HTTP error");

            String meta  = metaResp.body();
            String base  = safeFilename(displayTitle);

            // Try formats in preference order.
            for (String[] fmt : new String[][]{{"epub", ".epub"}, {"pdf", ".pdf"}, {"txt", ".txt"}}) {
                String fileName = findFileByExt(meta, fmt[0]);
                if (fileName != null) {
                    String fileUrl = "https://archive.org/download/" + identifier + "/" +
                            URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                                    .replace("+", "%20");
                    DownloadResult r = downloadFile(fileUrl, base + fmt[1]);
                    if (r.success) return r;
                }
            }
            return DownloadResult.failure("No suitable file in IA item: " + identifier);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return DownloadResult.failure("Interrupted");
        } catch (Exception e) {
            return DownloadResult.failure("IA item error: " + e.getMessage());
        }
    }

    /**
     * Finds the first filename with the given extension in IA metadata JSON,
     * skipping known derivative/auxiliary files.
     */
    private static String findFileByExt(String metaJson, String ext) {
        Pattern p = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+\\." + ext + ")\"",
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(metaJson);
        while (m.find()) {
            String name = m.group(1);
            if (!name.contains("_abbyy") && !name.contains("_djvu")
                    && !name.contains("_orig") && !name.contains("chocr")
                    && !name.contains("_bw.")) {
                return name;
            }
        }
        return null;
    }

    // ── 3. Standard Ebooks ────────────────────────────────────────────────────

    private static DownloadResult tryStandardEbooks(String title, String author) {
        String query = title.isBlank() ? author
                : (title + (author.isBlank() ? "" : " " + author));
        return searchStandardEbooks(query, title.isBlank() ? author : title);
    }

    /**
     * Scrapes Standard Ebooks search results for EPUB download links.
     * Their search page returns HTML with hrefs matching:
     *   /ebooks/{author}/{title}/downloads/{author}_{title}.epub
     */
    private static DownloadResult searchStandardEbooks(String query, String displayTitle) {
        try {
            String url = "https://standardebooks.org/ebooks?query=" +
                    URLEncoder.encode(query, StandardCharsets.UTF_8) + "&sort=relevance";

            HttpResponse<String> resp = sendGet(url);
            if (resp == null || resp.statusCode() != 200)
                return DownloadResult.failure("Standard Ebooks HTTP error");

            String html  = resp.body();
            String base  = safeFilename(displayTitle);

            Pattern p = Pattern.compile("href=\"(/ebooks/[^\"]+/downloads/[^\"]+\\.epub)\"");
            Matcher m = p.matcher(html);
            while (m.find()) {
                String epubUrl = "https://standardebooks.org" + m.group(1);
                DownloadResult r = downloadFile(epubUrl, base + ".epub");
                if (r.success) return r;
            }
            return DownloadResult.failure("Not found on Standard Ebooks");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return DownloadResult.failure("Interrupted");
        } catch (Exception e) {
            return DownloadResult.failure("Standard Ebooks error: " + e.getMessage());
        }
    }

    // ── HTTP layer ────────────────────────────────────────────────────────────

    private static HttpResponse<String> sendGet(String url)
            throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(TIMEOUT_SEC))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, text/html, */*")
                .GET().build();
        return HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static DownloadResult downloadFile(String urlStr, String fileName) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(urlStr))
                    .timeout(Duration.ofSeconds(DL_TIMEOUT))
                    .header("User-Agent", USER_AGENT)
                    .GET().build();

            HttpResponse<InputStream> resp =
                    HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());

            int status = resp.statusCode();
            if (status == 403 || status == 404 || status == 401) {
                closeQuietly(resp.body());
                return DownloadResult.failure("HTTP " + status + " for " + urlStr);
            }
            if (status != 200) {
                closeQuietly(resp.body());
                return DownloadResult.failure("HTTP " + status);
            }

            // Use only the extension from fileName; base comes from safeFilename(title).
            String safeFile = fileName.replaceAll("[^a-zA-Z0-9._\\-]", "_");
            Path dest = Paths.get(DOWNLOAD_DIR, safeFile);

            try (InputStream in = resp.body();
                 FileOutputStream out = new FileOutputStream(dest.toFile())) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }

            long size = dest.toFile().length();
            if (size < 512) {
                // Treat tiny files as error pages and discard.
                Files.deleteIfExists(dest);
                return DownloadResult.failure(
                        "Downloaded file too small (" + size + " B) — likely an error page");
            }

            return DownloadResult.success(dest.toString(), safeFile);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return DownloadResult.failure("Download interrupted");
        } catch (IOException e) {
            return DownloadResult.failure("Download I/O error: " + e.getMessage());
        }
    }

    private static void closeQuietly(InputStream s) {
        try { if (s != null) s.close(); } catch (IOException ignored) {}
    }

    // ── JSON extraction helpers ───────────────────────────────────────────────

    /**
     * Extracts the first URL value for the given MIME-type JSON key.
     * Example: jsonValue(json, "application/epub\\+zip") extracts the EPUB URL.
     */
    private static String jsonValue(String json, String mimeKeyRegex) {
        try {
            Pattern p = Pattern.compile("\"" + mimeKeyRegex + "\"\\s*:\\s*\"([^\"]+)\"");
            Matcher m = p.matcher(json);
            return m.find() ? m.group(1) : null;
        } catch (Exception e) { return null; }
    }

    /**
     * Extracts the first URL value for a JSON key whose MIME type starts with
     * the given prefix (handles charset suffixes like "text/plain; charset=utf-8").
     */
    private static String jsonValuePrefix(String json, String mimePrefix) {
        try {
            Pattern p = Pattern.compile(
                    "\"" + Pattern.quote(mimePrefix) + "[^\"]*\"\\s*:\\s*\"([^\"]+)\"");
            Matcher m = p.matcher(json);
            return m.find() ? m.group(1) : null;
        } catch (Exception e) { return null; }
    }

    // ── EPUB / TXT → PDF conversion ───────────────────────────────────────────

    /**
     * If the download is an EPUB or TXT, converts it to PDF (required for the app's
     * reader and summary service), deletes the original, and returns a new result
     * pointing at the PDF.  Falls back to the original file if conversion fails.
     */
    private static DownloadResult convertToPdfIfNeeded(DownloadResult r) {
        if (!r.success || r.filePath == null) return r;
        String lower = r.filePath.toLowerCase();
        try {
            String pdfPath, pdfName;
            if (lower.endsWith(".epub")) {
                pdfPath = convertEpubToPdf(r.filePath);
                pdfName = r.fileName.replaceFirst("\\.epub$", ".pdf");
            } else if (lower.endsWith(".txt")) {
                pdfPath = convertTxtToPdf(r.filePath);
                pdfName = r.fileName.replaceFirst("\\.txt$", ".pdf");
            } else {
                return r;
            }
            Files.deleteIfExists(Paths.get(r.filePath));
            return DownloadResult.success(pdfPath, pdfName);
        } catch (Exception e) {
            System.err.println("[PDF conv] " + e.getMessage());
            return r;
        }
    }

    /** Extracts text from every HTML/XHTML chapter inside an EPUB ZIP and writes a PDF. */
    private static String convertEpubToPdf(String epubPath) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(epubPath))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase();
                if ((name.endsWith(".html") || name.endsWith(".xhtml") || name.endsWith(".htm"))
                        && !name.contains("toc") && !name.contains("nav")) {
                    String html = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    String text = stripHtmlTags(html);
                    if (!text.isBlank()) sb.append(text).append("\n\n");
                }
                zip.closeEntry();
            }
        }
        if (sb.length() < 50) throw new IOException("EPUB yielded insufficient text");
        String pdfPath = epubPath.replaceFirst("\\.epub$", ".pdf");
        writeToPdf(sb.toString(), pdfPath);
        return pdfPath;
    }

    /** Reads a plain-text file and writes a PDF from its content. */
    private static String convertTxtToPdf(String txtPath) throws IOException {
        String text = Files.readString(Paths.get(txtPath), StandardCharsets.UTF_8);
        if (text == null || text.isBlank()) throw new IOException("TXT file is empty");
        String pdfPath = txtPath.replaceFirst("\\.txt$", ".pdf");
        writeToPdf(text, pdfPath);
        return pdfPath;
    }

    /** Lays out {@code text} across A4 pages (Helvetica 11pt) and saves as a PDF. */
    private static void writeToPdf(String text, String outputPath) throws IOException {
        try (org.apache.pdfbox.pdmodel.PDDocument doc =
                     new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.font.PDType1Font font =
                    new org.apache.pdfbox.pdmodel.font.PDType1Font(
                            org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA);
            float fontSize = 11f;
            float margin   = 50f;
            float leading  = fontSize * 1.5f;
            org.apache.pdfbox.pdmodel.common.PDRectangle pageSize =
                    org.apache.pdfbox.pdmodel.common.PDRectangle.A4;
            float usableW = pageSize.getWidth()  - 2 * margin;
            float pageH   = pageSize.getHeight();

            List<String> lines = wrapText(text, font, fontSize, usableW);

            org.apache.pdfbox.pdmodel.PDPage page =
                    new org.apache.pdfbox.pdmodel.PDPage(pageSize);
            doc.addPage(page);
            org.apache.pdfbox.pdmodel.PDPageContentStream cs =
                    new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page);
            cs.setFont(font, fontSize);
            cs.beginText();
            cs.newLineAtOffset(margin, pageH - margin);
            float y = pageH - margin;

            for (String line : lines) {
                if (y - leading < margin) {
                    cs.endText(); cs.close();
                    page = new org.apache.pdfbox.pdmodel.PDPage(pageSize);
                    doc.addPage(page);
                    cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page);
                    cs.setFont(font, fontSize);
                    cs.beginText();
                    y = pageH - margin;
                    cs.newLineAtOffset(margin, y);
                }
                if (!line.isEmpty()) cs.showText(line);
                cs.newLineAtOffset(0, -leading);
                y -= leading;
            }
            cs.endText(); cs.close();
            doc.save(outputPath);
        }
    }

    /** Word-wraps {@code text} into lines that fit within {@code maxWidth} points. */
    private static List<String> wrapText(String text,
                                          org.apache.pdfbox.pdmodel.font.PDType1Font font,
                                          float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String para : text.split("\\n")) {
            if (para.isBlank()) { lines.add(""); continue; }
            // PDType1Font supports Latin-1 (0x20–0xFF) only.
            String safe = para.replaceAll("[^\\x20-\\xFF]", "");
            String[] words = safe.split("\\s+");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                if (word.isEmpty()) continue;
                String candidate = line.length() == 0 ? word : line + " " + word;
                float w = font.getStringWidth(candidate) / 1000f * fontSize;
                if (w > maxWidth && line.length() > 0) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    if (line.length() > 0) line.append(' ');
                    line.append(word);
                }
            }
            if (line.length() > 0) lines.add(line.toString());
        }
        return lines;
    }

    /** Strips HTML tags and decodes common entities from EPUB chapter content. */
    private static String stripHtmlTags(String html) {
        String t = html.replaceAll("(?si)<(script|style)[^>]*>.*?</\\1>", "");
        t = t.replaceAll("(?i)</(p|div|h[1-6]|tr|li)>", "\n");
        t = t.replaceAll("(?i)<br\\s*/?>", "\n");
        t = t.replaceAll("<[^>]+>", "");
        t = t.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
             .replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#39;", "'")
             .replace("&mdash;", "-").replace("&ndash;", "-").replace("&hellip;", "...");
        return t.replaceAll("\\n{3,}", "\n\n").trim();
    }

    // ── Title verification & alternatives ────────────────────────────────────

    /**
     * Returns true when enough of the requested title's significant words (length ≥ 4)
     * appear in the returned title — preventing false-positive downloads such as
     * downloading "The Idiot" when "The Idiot Brain" was requested.
     */
    private static boolean isTitleSimilarEnough(String requested, String returned) {
        String req = requested.toLowerCase().replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        String ret = returned.toLowerCase().replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        if (req.equals(ret) || ret.contains(req)) return true;
        Set<String> reqWords = new HashSet<>();
        for (String w : req.split("\\s+")) { if (w.length() >= 4) reqWords.add(w); }
        if (reqWords.isEmpty()) return true;
        Set<String> retWords = new HashSet<>();
        for (String w : ret.split("\\s+")) retWords.add(w);
        int matches = 0;
        for (String w : reqWords) { if (retWords.contains(w)) matches++; }
        return (double) matches / reqWords.size() >= 0.9;
    }

    /** Extracts the title of the first result entry from a gutendex JSON response. */
    private static String extractFirstResultTitle(String json) {
        int idx = json.indexOf("\"results\"");
        if (idx < 0) return null;
        Matcher m = Pattern.compile("\"title\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        m.region(idx, json.length());
        return m.find() ? m.group(1) : null;
    }

    /**
     * Queries gutendex for {@code query} and extracts up to 8 downloadable candidates.
     * No title-similarity filter is applied — the goal is a broad set of alternatives.
     */
    private static List<BookCandidate> extractGutendexCandidates(String query) {
        List<BookCandidate> list = new ArrayList<>();
        try {
            String url = "https://gutendex.com/books?search=" +
                    URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpResponse<String> resp = sendGet(url);
            if (resp == null || resp.statusCode() != 200) return list;
            String json = resp.body();
            if (!json.contains("\"results\"") ||
                    Pattern.compile("\"count\"\\s*:\\s*0").matcher(json).find()) return list;

            // Split on result-object boundaries (each result starts with "id":<number>).
            String[] blocks = json.split("\"id\"\\s*:\\s*\\d+");
            Pattern titlePat  = Pattern.compile("\"title\"\\s*:\\s*\"([^\"]+)\"");
            Pattern authorPat = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
            Pattern epubPat   = Pattern.compile("\"application/epub\\+zip\"\\s*:\\s*\"([^\"]+)\"");
            for (int i = 1; i < blocks.length && list.size() < 8; i++) {
                String block = blocks[i];
                Matcher tm = titlePat.matcher(block);
                if (!tm.find()) continue;
                String title  = tm.group(1);
                Matcher am = authorPat.matcher(block);
                String author = am.find() ? am.group(1) : "Unknown";
                Matcher em = epubPat.matcher(block);
                if (!em.find()) continue;
                list.add(new BookCandidate(title, author, em.group(1)));
            }
        } catch (Exception e) {
            System.err.println("[Alternatives] " + e.getMessage());
        }
        return list;
    }

    // ── Filesystem ────────────────────────────────────────────────────────────

    private static void ensureDownloadDir() {
        try { Files.createDirectories(Paths.get(DOWNLOAD_DIR)); }
        catch (IOException ignored) {}
    }
}
