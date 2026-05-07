package org.example.service;

import java.io.File;
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
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.example.util.BookPreviewUtil;

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
    /** Timeout for search/metadata HTTP requests (Gutendex/IA/StandardEbooks HTML). */
    private static final int    TIMEOUT_SEC  = 10;
    private static final int    DL_TIMEOUT   = 60;
    /** Overall budget across all candidate-search calls (sequential queries + browse pages). */
    private static final long   SEARCH_TIME_LIMIT_MS = 45_000L;

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
    public record BookCandidate(String title, String author, String epubUrl, String catalogSummary) {
        /** @param catalogSummary short description from the catalog API (e.g. Gutendex), if any */
        public BookCandidate(String title, String author, String epubUrl) {
            this(title, author, epubUrl, null);
        }
    }

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

        // If the user pasted a Gutenberg URL (or numeric ebook id), bypass Gutendex and download directly.
        Integer gutenbergId = extractGutenbergEbookId(title);
        if (gutenbergId == null) gutenbergId = extractGutenbergEbookId(author);
        if (gutenbergId != null) {
            String label = (titleBlank ? ("gutenberg-" + gutenbergId) : title);
            DownloadResult direct = tryDirectGutenbergById(gutenbergId, label);
            if (direct.success) return convertToPdfIfNeeded(direct);
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
        long startedAtMs = System.currentTimeMillis();

        // Gutenberg direct URL / id input: treat it as an explicit top candidate.
        Integer gid = extractGutenbergEbookId(title);
        if (gid == null) gid = extractGutenbergEbookId(author);
        if (gid != null) {
            String fallbackTitle = (title == null || title.isBlank()) ? ("Project Gutenberg #" + gid) : title.trim();
            String epubUrl = "https://www.gutenberg.org/ebooks/" + gid + ".epub.noimages";
            BookCandidate top = new BookCandidate(fallbackTitle, (author == null || author.isBlank()) ? "Unknown" : author.trim(), epubUrl,
                    "Direct Project Gutenberg link (ebook #" + gid + ").");
            return new SearchResults(top, List.of());
        }

        Set<String> seenTitles = new HashSet<>();
        List<BookCandidate> candidates = new ArrayList<>();
        for (String q : buildQueries(ct, ca)) {
            if (isSearchTimedOut(startedAtMs)) break;
            for (BookCandidate c : extractGutendexCandidates(q)) {
                if (seenTitles.add(c.title().toLowerCase())) candidates.add(c);
            }
            if (candidates.size() >= 6) break;
        }

        // Librarian flow expects a primary pick plus at least one distinct alternative.
        if (candidates.size() < 2 && !isSearchTimedOut(startedAtMs)) {
            fillUntilMinCandidateCount(seenTitles, candidates, ct, ca, 2, 6, startedAtMs);
        }

        if (candidates.isEmpty()) return new SearchResults(null, List.of());
        candidates.sort(
                Comparator.comparingInt((BookCandidate c) -> candidateRelevanceScore(c, ct, ca)).reversed()
                        .thenComparing(BookCandidate::title, String.CASE_INSENSITIVE_ORDER));
        BookCandidate top = candidates.get(0);
        List<BookCandidate> alts = new ArrayList<>(candidates.subList(1, Math.min(candidates.size(), 6)));
        return new SearchResults(top, alts);
    }

    /**
     * Scores alternative candidates so same-author and similar-title books appear first.
     */
    private static int candidateRelevanceScore(BookCandidate c, String requestedTitle, String requestedAuthor) {
        int score = 0;
        String candidateAuthor = cleanForSearch(c.author()).toLowerCase();
        String candidateTitle = cleanForSearch(c.title()).toLowerCase();
        String reqAuthor = requestedAuthor.toLowerCase();
        String reqTitle = requestedTitle.toLowerCase();

        if (!reqAuthor.isBlank() && !candidateAuthor.isBlank()) {
            if (candidateAuthor.equals(reqAuthor)) score += 100;
            else if (candidateAuthor.contains(reqAuthor) || reqAuthor.contains(candidateAuthor)) score += 70;
            else {
                for (String token : reqAuthor.split("\\s+")) {
                    if (token.length() >= 3 && candidateAuthor.contains(token)) {
                        score += 20;
                    }
                }
            }
        }

        if (!reqTitle.isBlank() && !candidateTitle.isBlank()) {
            if (candidateTitle.equals(reqTitle)) score += 80;
            else if (isTitleSimilarEnough(reqTitle, candidateTitle)) score += 60;
            else {
                for (String token : reqTitle.split("\\s+")) {
                    if (token.length() >= 4 && candidateTitle.contains(token)) {
                        score += 10;
                    }
                }
            }
        }
        return score;
    }

    /**
     * Adds Gutendex hits (broad search terms, then paginated browse) until
     * {@code targetCount} unique EPUB candidates are collected or sources are exhausted.
     */
    private static void fillUntilMinCandidateCount(
            Set<String> seenTitles,
            List<BookCandidate> candidates,
            String titleHint,
            String authorHint,
            int targetCount,
            int maxTotal,
            long startedAtMs) {
        if (candidates.size() >= targetCount || candidates.size() >= maxTotal) return;

        for (String q : broadGutendexQueries(titleHint, authorHint)) {
            if (isSearchTimedOut(startedAtMs)) return;
            for (BookCandidate c : extractGutendexCandidates(q)) {
                if (seenTitles.add(c.title().toLowerCase())) {
                    candidates.add(c);
                    if (candidates.size() >= targetCount || candidates.size() >= maxTotal) return;
                }
            }
        }

        for (int page = 1; page <= 5 && candidates.size() < targetCount && candidates.size() < maxTotal; page++) {
            if (isSearchTimedOut(startedAtMs)) return;
            for (BookCandidate c : gutendexBrowsePageCandidates(page)) {
                if (seenTitles.add(c.title().toLowerCase())) {
                    candidates.add(c);
                    if (candidates.size() >= targetCount || candidates.size() >= maxTotal) return;
                }
            }
        }
    }

    private static boolean isSearchTimedOut(long startedAtMs) {
        return System.currentTimeMillis() - startedAtMs >= SEARCH_TIME_LIMIT_MS;
    }

    /** Extra search strings beyond {@link #buildQueries} — single tokens and author parts. */
    private static List<String> broadGutendexQueries(String title, String author) {
        List<String> qs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (!title.isBlank()) {
            for (String w : title.split("\\s+")) {
                if (w.length() >= 3 && seen.add(w.toLowerCase())) qs.add(w);
            }
        }
        if (!author.isBlank()) {
            for (String w : author.split("\\s+")) {
                if (w.length() >= 3 && seen.add(w.toLowerCase())) qs.add(w);
            }
        }
        return qs;
    }

    /** One page of the default Gutendex listing (popular works), EPUB-ready entries only. */
    private static List<BookCandidate> gutendexBrowsePageCandidates(int page) {
        try {
            String url = "https://gutendex.com/books/?page=" + page;
            HttpResponse<String> resp = sendGet(url);
            if (resp == null || resp.statusCode() != 200) return List.of();
            return parseGutendexResultsJson(resp.body(), 40);
        } catch (Exception e) {
            System.err.println("[Gutendex browse] " + e.getMessage());
            return List.of();
        }
    }

    /** Downloads a specific candidate book chosen by the librarian. */
    public static DownloadResult downloadCandidate(BookCandidate candidate) {
        ensureDownloadDir();
        if (candidate == null || candidate.epubUrl() == null || candidate.epubUrl().isBlank()) {
            return DownloadResult.failure("Invalid candidate download URL.");
        }
        String url = candidate.epubUrl();
        String lowerUrl = url.toLowerCase();
        String ext = lowerUrl.contains(".pdf") ? ".pdf" : ".epub";
        DownloadResult downloaded = downloadFile(url, safeFilename(candidate.title()) + ext);
        if (!downloaded.success) {
            return downloaded;
        }
        DownloadResult asPdf = convertToPdfIfNeeded(downloaded);
        if (!asPdf.success || asPdf.filePath == null || !asPdf.filePath.toLowerCase().endsWith(".pdf")) {
            return DownloadResult.failure(
                    "Downloaded file is not publishable: only PDF is supported. "
                            + "Please choose another source/title.");
        }
        return asPdf;
    }

    /**
     * Ensures there is a PDF next to {@code epubPathStr} for the in-app reader ({@code <name>.reader.pdf}).
     * Regenerates when the EPUB changes.
     */
    public static String ensureReaderPdfFromEpub(String epubPathStr) throws IOException {
        Path epub = Paths.get(epubPathStr).toAbsolutePath().normalize();
        if (!Files.isRegularFile(epub) || !epub.getFileName().toString().toLowerCase().endsWith(".epub")) {
            throw new IOException("Not a readable EPUB file: " + epubPathStr);
        }
        String stem = epub.getFileName().toString().replaceFirst("(?i)\\.epub$", "");
        Path pdfOut = epub.getParent().resolve(stem + ".reader.pdf").toAbsolutePath().normalize();

        boolean need = !Files.isRegularFile(pdfOut);
        if (!need) {
            need = Files.getLastModifiedTime(epub).toMillis() > Files.getLastModifiedTime(pdfOut).toMillis();
        }
        if (need) {
            convertEpubToPdf(epub.toString(), pdfOut.toString());
        }
        return pdfOut.toString();
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

    /**
     * Tries to download a Gutenberg ebook directly from gutenberg.org using its numeric id.
     * This is a fallback when Gutendex is unavailable or when the user pasted a Gutenberg URL.
     */
    private static DownloadResult tryDirectGutenbergById(int ebookId, String displayTitle) {
        String base = safeFilename(displayTitle == null || displayTitle.isBlank()
                ? ("gutenberg-" + ebookId)
                : displayTitle);

        // Prefer EPUB (convertable to PDF). Gutenberg's ebooks endpoint serves redirects to actual files.
        String[] urls = new String[] {
                "https://www.gutenberg.org/ebooks/" + ebookId + ".epub.noimages",
                "https://www.gutenberg.org/ebooks/" + ebookId + ".epub.images",
                // Some titles only expose plain text reliably:
                "https://www.gutenberg.org/ebooks/" + ebookId + ".txt.utf-8",
                "https://www.gutenberg.org/ebooks/" + ebookId + ".txt"
        };

        for (String u : urls) {
            String lower = u.toLowerCase();
            String ext = lower.contains(".epub") ? ".epub" : ".txt";
            DownloadResult r = downloadFile(u, base + ext);
            if (r.success) return r;
        }
        return DownloadResult.failure("Gutenberg direct download failed for ebook #" + ebookId);
    }

    /**
     * Extracts a Project Gutenberg numeric ebook id from a string that may be:
     * - a Gutenberg URL like https://www.gutenberg.org/ebooks/1260
     * - a partial path like /ebooks/1260
     * - a plain number like 1260
     */
    private static Integer extractGutenbergEbookId(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;

        Matcher m = Pattern.compile("(?i)gutenberg\\.org\\s*/\\s*ebooks\\s*/\\s*(\\d+)").matcher(t);
        if (m.find()) return parsePositiveIntOrNull(m.group(1));

        m = Pattern.compile("(?i)/\\s*ebooks\\s*/\\s*(\\d+)").matcher(t);
        if (m.find()) return parsePositiveIntOrNull(m.group(1));

        // Plain numeric id
        if (t.matches("\\d{1,7}")) return parsePositiveIntOrNull(t);
        return null;
    }

    private static Integer parsePositiveIntOrNull(String digits) {
        try {
            int v = Integer.parseInt(digits);
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
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
        CompletableFuture<HttpResponse<String>> f =
                HTTP_CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString());
        try {
            // Enforce an additional hard timeout and actively cancel the underlying request.
            return f.get(TIMEOUT_SEC + 1L, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            f.cancel(true);
            throw new IOException("HTTP request timed out", e);
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw ie;
            }
            if (c instanceof IOException ioe) throw ioe;
            throw new IOException(c == null ? e : c);
        }
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

    /** Extracts EPUB chapter text via {@link BookPreviewUtil} and writes a PDF. */
    private static String convertEpubToPdf(String epubPath) throws IOException {
        String pdfOut = epubPath.replaceFirst("(?i)\\.epub$", ".pdf");
        return convertEpubToPdf(epubPath, pdfOut);
    }

    private static String convertEpubToPdf(String epubPath, String pdfOutputPath) throws IOException {
        String text = BookPreviewUtil.readEpubAsPlainText(Paths.get(epubPath), Integer.MAX_VALUE);
        if (text == null || text.length() < 50) {
            throw new IOException("EPUB yielded insufficient text");
        }
        writeToPdf(text, pdfOutputPath);
        return pdfOutputPath;
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
        try {
            String url = "https://gutendex.com/books?search=" +
                    URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpResponse<String> resp = sendGet(url);
            if (resp == null || resp.statusCode() != 200) return List.of();
            return parseGutendexResultsJson(resp.body(), 8);
        } catch (Exception e) {
            System.err.println("[Alternatives] " + e.getMessage());
            return List.of();
        }
    }

    /** Parses Gutendex /books JSON: keeps only volumes that expose an EPUB URL in {@code formats}. */
    private static List<BookCandidate> parseGutendexResultsJson(String json, int max) {
        List<BookCandidate> list = new ArrayList<>();
        if (json == null || !json.contains("\"results\"")) return list;
        if (Pattern.compile("\"count\"\\s*:\\s*0").matcher(json).find()) return list;

        String[] blocks = json.split("\"id\"\\s*:\\s*\\d+");
        Pattern titlePat  = Pattern.compile("\"title\"\\s*:\\s*\"([^\"]+)\"");
        Pattern authorPat = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
        Pattern epubPat   = Pattern.compile("\"application/epub\\+zip\"\\s*:\\s*\"([^\"]+)\"");
        for (int i = 1; i < blocks.length && list.size() < max; i++) {
            String block = blocks[i];
            Matcher tm = titlePat.matcher(block);
            if (!tm.find()) continue;
            String tit = tm.group(1);
            Matcher am = authorPat.matcher(block);
            String au = am.find() ? am.group(1) : "Unknown";
            Matcher em = epubPat.matcher(block);
            if (!em.find()) continue;
            String gist = summarizeFromGutendexBlock(block);
            list.add(new BookCandidate(tit, au, em.group(1), gist));
        }
        return list;
    }

    /**
     * Parses the {@code summaries} JSON array inside one Gutendex result object fragment.
     * Gutendex provides Gutenberg-style blurbs (often one string starting with quoted title…).
     */
    private static String summarizeFromGutendexBlock(String block) {
        String raw = extractFirstJsonSummariesString(block);
        return normalizeCatalogSummary(raw);
    }

    private static String normalizeCatalogSummary(String raw) {
        if (raw == null) return null;
        String t = raw.replace('\r', ' ').replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        String suffix = "(This is an automatically generated summary.)";
        if (t.endsWith(suffix)) {
            t = t.substring(0, t.length() - suffix.length()).trim();
        }
        return t.isEmpty() ? null : t;
    }

    /**
     * After {@code "summaries": }, reads the first JSON string literal in the array, if present.
     */
    private static String extractFirstJsonSummariesString(String block) {
        int key = block.indexOf("\"summaries\"");
        if (key < 0) return null;
        int bracket = block.indexOf('[', key);
        if (bracket < 0) return null;
        int i = bracket + 1;
        int n = block.length();
        while (i < n && Character.isWhitespace(block.charAt(i))) {
            i++;
        }
        if (i >= n || block.charAt(i) != '"') return null;
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < n) {
            char c = block.charAt(i);
            if (c == '"') {
                break;
            }
            if (c == '\\' && i + 1 < n) {
                char e = block.charAt(i + 1);
                switch (e) {
                    case '"', '\\', '/' -> sb.append(e);
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 6 <= n) {
                            try {
                                int cp = Integer.parseInt(block.substring(i + 2, i + 6), 16);
                                sb.append((char) cp);
                                i += 6;
                                continue;
                            } catch (NumberFormatException ignored) {
                                sb.append(e);
                            }
                        } else {
                            sb.append(e);
                        }
                    }
                    default -> sb.append(e);
                }
                i += 2;
                continue;
            }
            sb.append(c);
            i++;
        }
        String s = sb.toString().trim();
        return s.isEmpty() ? null : s;
    }

    // ── Filesystem ────────────────────────────────────────────────────────────

    private static void ensureDownloadDir() {
        try { Files.createDirectories(Paths.get(DOWNLOAD_DIR)); }
        catch (IOException ignored) {}
    }
}
