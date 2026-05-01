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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for downloading books from online sources.
 * Supports searching and downloading from open book repositories like:
 * - Project Gutenberg
 * - Open Library
 * - Standard Ebooks
 */
public final class BookDownloaderService {

    private static final String DOWNLOAD_DIR = "data/downloaded_books";
    private static final int TIMEOUT_SECONDS = 30;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .build();

    private BookDownloaderService() {}

    /**
     * Result of a download attempt
     */
    public static class DownloadResult {
        public final boolean success;
        public final String filePath;
        public final String fileName;
        public final String errorMessage;

        public DownloadResult(boolean success, String filePath, String fileName, String errorMessage) {
            this.success = success;
            this.filePath = filePath;
            this.fileName = fileName;
            this.errorMessage = errorMessage;
        }

        public static DownloadResult failure(String errorMessage) {
            return new DownloadResult(false, null, null, errorMessage);
        }

        public static DownloadResult success(String filePath, String fileName) {
            return new DownloadResult(true, filePath, fileName, null);
        }
    }

    /**
     * Attempts to download a book from various online sources
     */
    public static DownloadResult downloadBook(String title, String author) {
        ensureDownloadDirExists();

        // Try multiple sources in order
        DownloadResult result = tryProjectGutenberg(title, author);
        if (result.success) return result;

        result = tryOpenLibrary(title, author);
        if (result.success) return result;

        result = tryStandardEbooks(title, author);
        if (result.success) return result;

        return DownloadResult.failure(
                "Could not find the book in available online repositories. " +
                "Try searching: Project Gutenberg, Open Library, or Standard Ebooks manually.");
    }

    /**
     * Searches Project Gutenberg for the book and attempts to download it
     */
    private static DownloadResult tryProjectGutenberg(String title, String author) {
        try {
            String searchQuery = title.replaceAll("[^a-zA-Z0-9 ]", "").trim();
            if (searchQuery.isEmpty()) {
                return DownloadResult.failure("Invalid title for search");
            }

            // Project Gutenberg API search
            String encodedQuery = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8);
            String apiUrl = "https://gutendex.com/books?search=" + encodedQuery;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return DownloadResult.failure("Project Gutenberg search failed: HTTP " + response.statusCode());
            }

            // Parse JSON response to find book and download link
            String bookId = extractBookIdFromGutenberg(response.body());
            if (bookId == null || bookId.isEmpty()) {
                return DownloadResult.failure("Book not found on Project Gutenberg");
            }

            // Download the book (try EPUB first, then HTML)
            return downloadFromGutenberg(bookId, title);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return DownloadResult.failure("Download interrupted: " + e.getMessage());
        } catch (Exception e) {
            return DownloadResult.failure("Project Gutenberg search error: " + e.getMessage());
        }
    }

    /**
     * Downloads a book from Project Gutenberg by ID
     */
    private static DownloadResult downloadFromGutenberg(String bookId, String title) {
        try {
            // Try EPUB format first
            String epubUrl = "https://www.gutenberg.org/cache/epub/" + bookId + "/pg" + bookId + ".epub";
            DownloadResult result = downloadFile(epubUrl, title + ".epub");
            if (result.success) return result;

            // Try HTML format
            String htmlUrl = "https://www.gutenberg.org/files/" + bookId + "/" + bookId + "-h/" + bookId + "-h.html";
            result = downloadFile(htmlUrl, title + ".html");
            if (result.success) return result;

            // Try TXT format
            String txtUrl = "https://www.gutenberg.org/cache/epub/" + bookId + "/pg" + bookId + ".txt";
            return downloadFile(txtUrl, title + ".txt");

        } catch (Exception e) {
            return DownloadResult.failure("Failed to download from Project Gutenberg: " + e.getMessage());
        }
    }

    /**
     * Tries Open Library as an alternative source
     */
    private static DownloadResult tryOpenLibrary(String title, String author) {
        try {
            String searchQuery = title.replaceAll("[^a-zA-Z0-9 ]", "").trim();
            if (searchQuery.isEmpty()) {
                return DownloadResult.failure("Invalid title for search");
            }

            String encodedQuery = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8);
            String apiUrl = "https://openlibrary.org/search.json?title=" + encodedQuery + "&limit=1";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return DownloadResult.failure("Open Library search failed");
            }

            // Extract book key and try to download
            String bookKey = extractOpenLibraryKey(response.body());
            if (bookKey == null || bookKey.isEmpty()) {
                return DownloadResult.failure("Book not found on Open Library");
            }

            // Try to get the readable version
            String downloadUrl = "https://openlibrary.org" + bookKey + "/read";
            return DownloadResult.failure("Open Library books require browser access - manual download recommended");

        } catch (Exception e) {
            return DownloadResult.failure("Open Library search error: " + e.getMessage());
        }
    }

    /**
     * Tries Standard Ebooks as an alternative source
     */
    private static DownloadResult tryStandardEbooks(String title, String author) {
        try {
            String searchQuery = title.replaceAll("[^a-zA-Z0-9 ]", "").trim();
            if (searchQuery.isEmpty()) {
                return DownloadResult.failure("Invalid title for search");
            }

            String encodedQuery = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8);
            String apiUrl = "https://standardebooks.org/api/v1/ebooks/?query=" + encodedQuery;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return DownloadResult.failure("Standard Ebooks search failed");
            }

            String downloadUrl = extractStandardEbooksUrl(response.body());
            if (downloadUrl == null || downloadUrl.isEmpty()) {
                return DownloadResult.failure("Book not found on Standard Ebooks");
            }

            return downloadFile(downloadUrl, title + ".epub");

        } catch (Exception e) {
            return DownloadResult.failure("Standard Ebooks search error: " + e.getMessage());
        }
    }

    /**
     * Downloads a file from a given URL
     */
    private static DownloadResult downloadFile(String urlString, String fileName) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlString))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                return DownloadResult.failure("Download failed: HTTP " + response.statusCode());
            }

            // Sanitize filename
            fileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path filePath = Paths.get(DOWNLOAD_DIR, fileName);

            // Write to file
            try (InputStream in = response.body();
                 FileOutputStream out = new FileOutputStream(filePath.toFile())) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            return DownloadResult.success(filePath.toString(), fileName);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return DownloadResult.failure("Download interrupted");
        } catch (Exception e) {
            return DownloadResult.failure("Download failed: " + e.getMessage());
        }
    }

    /**
     * Extracts book ID from Project Gutenberg search response
     */
    private static String extractBookIdFromGutenberg(String jsonResponse) {
        try {
            // Simple regex to find the first book id in results
            Pattern pattern = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
            Matcher matcher = pattern.matcher(jsonResponse);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Extracts book key from Open Library search response
     */
    private static String extractOpenLibraryKey(String jsonResponse) {
        try {
            Pattern pattern = Pattern.compile("\"key\"\\s*:\\s*\"(/works/[^\"]+)\"");
            Matcher matcher = pattern.matcher(jsonResponse);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Extracts EPUB download URL from Standard Ebooks response
     */
    private static String extractStandardEbooksUrl(String jsonResponse) {
        try {
            // Look for .epub download URL
            Pattern pattern = Pattern.compile("\"([^\"]*\\.epub)\"");
            Matcher matcher = pattern.matcher(jsonResponse);
            if (matcher.find()) {
                String url = matcher.group(1);
                if (!url.startsWith("http")) {
                    url = "https://standardebooks.org" + url;
                }
                return url;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Ensures the download directory exists
     */
    private static void ensureDownloadDirExists() {
        try {
            Files.createDirectories(Paths.get(DOWNLOAD_DIR));
        } catch (IOException ignored) {
        }
    }

    /**
     * Gets the list of downloaded books
     */
    public static List<String> getDownloadedBooks() {
        List<String> books = new ArrayList<>();
        try {
            File dir = new File(DOWNLOAD_DIR);
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles((d, name) ->
                        name.endsWith(".pdf") || name.endsWith(".epub") || name.endsWith(".txt"));
                if (files != null) {
                    for (File file : files) {
                        books.add(file.getName());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return books;
    }
}
