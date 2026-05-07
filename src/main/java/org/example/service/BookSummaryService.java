package org.example.service;

import org.example.app.AppConfig;
import org.example.util.BookPreviewUtil;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BookSummaryService {
    private static final int SUMMARY_SOURCE_MAX_PAGES = 25;
    private static final int MAX_PROMPT_CHARS = 2400;
    private static final int DETAILED_PROMPT_CHARS = 5200;
    private static final int FALLBACK_MAX_CHARS = 900;
    private static final int FALLBACK_MIN_SENTENCES = 2;
    private static final int FALLBACK_MAX_SENTENCES = 4;
    private static final int SHORT_MIN_LENGTH = 10;
    private static final int SHORT_MAX_LENGTH = 45;
    private static final int MEDIUM_MIN_LENGTH = 45;
    private static final int MEDIUM_MAX_LENGTH = 100;
    private static final int DETAILED_MIN_LENGTH = 100;
    private static final int DETAILED_MAX_LENGTH = 220;
    private static final int REMOTE_NO_REPEAT_NGRAM_SIZE = 3;
    private static final int REMOTE_ENCODER_NO_REPEAT_NGRAM_SIZE = 3;
    private static final double REMOTE_REPETITION_PENALTY = 3.5;
    private static final int REMOTE_NUM_BEAMS = 4;
    private static final Pattern ARRAY_SUMMARY_PATTERN =
            Pattern.compile("\"summary_text\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
    private static final Pattern OBJECT_SUMMARY_PATTERN =
            Pattern.compile("\"generated_text\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
    private static final Pattern OLLAMA_SUMMARY_PATTERN =
            Pattern.compile("\"response\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
    private static final Pattern OLLAMA_MODEL_PATTERN =
            Pattern.compile("\"name\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
    private static final Pattern GUTENDEX_SUMMARIES_ARRAY_PATTERN =
            Pattern.compile("\"summaries\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
    private static final Pattern JSON_STRING_PATTERN =
            Pattern.compile("\"(.*?)\"", Pattern.DOTALL);
    private static final Pattern ERROR_PATTERN =
            Pattern.compile("\"error\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
    private static volatile List<String> sharedOllamaModelsCache;
    private static volatile long sharedOllamaModelsFetchedAtMs;
    private static final HttpClient LOOKUP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private final Function<RequestPayload, ApiResponse> apiCaller;
    private final BooleanSupplier hfConfiguredChecker;

    public enum SummaryStyle {
        SHORT("Short"),
        MEDIUM("Medium"),
        DETAILED("Detailed");

        private final String label;

        SummaryStyle(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public BookSummaryService() {
        this(new HttpApiCaller(), AppConfig::isSummaryRemoteConfigured);
    }

    BookSummaryService(Function<RequestPayload, ApiResponse> apiCaller) {
        this(apiCaller, AppConfig::isHfConfigured);
    }

    BookSummaryService(Function<RequestPayload, ApiResponse> apiCaller, BooleanSupplier hfConfiguredChecker) {
        this.apiCaller = Objects.requireNonNull(apiCaller);
        this.hfConfiguredChecker = Objects.requireNonNull(hfConfiguredChecker);
    }

    public SummaryResult generateSummaryFromBookFile(String filePath) {
        return summarizeDirect(filePath, SummaryStyle.MEDIUM, "", "");
    }

    public SummaryResult generateSummaryFromBookFile(String filePath, SummaryStyle style) {
        return summarizeDirect(filePath, style == null ? SummaryStyle.MEDIUM : style, "", "");
    }

    public SummaryResult generateSummaryFromBookFile(
            String filePath,
            SummaryStyle style,
            String titleHint,
            String authorHint) {
        return summarizeDirect(
                filePath,
                style == null ? SummaryStyle.MEDIUM : style,
                titleHint,
                authorHint);
    }

    private SummaryResult summarizeDirect(
            String filePath,
            SummaryStyle selectedStyle,
            String titleHint,
            String authorHint) {
        String rawPreview = extractRepresentativeContent(filePath, selectedStyle);
        if (rawPreview == null || rawPreview.isBlank()) {
            return SummaryResult.failure(
                    "Could not extract readable text from the selected file. You can still write the summary manually.");
        }
        String normalizedPreview = normalizeWhitespace(rawPreview);
        if (normalizedPreview.isBlank()) {
            return SummaryResult.failure(
                    "Extracted text was empty after cleanup. You can still write the summary manually.");
        }
        if (!hfConfiguredChecker.getAsBoolean()) {
            return SummaryResult.failure(
                    "LLM summary generation is unavailable: no remote provider configured.");
        }

        String modelInput = buildModelInput(normalizedPreview, selectedStyle, titleHint, authorHint);
        ApiResponse response = apiCaller.apply(new RequestPayload(modelInput, selectedStyle));
        if (!response.ok) {
            String error = extractJsonString(response.body, ERROR_PATTERN);
            String reason = error != null && !error.isBlank() ? normalizeWhitespace(error) : response.reason;
            return SummaryResult.failure("Summary generation failed: " + reason + ". You can refine manually.");
        }
        String summary = parseSummary(response.body);
        if (summary == null || summary.isBlank()) {
            String localFallback = buildLocalExtractiveSummary(normalizedPreview, selectedStyle);
            if (localFallback.isBlank()) {
                return SummaryResult.failure(
                        "Direct LLM returned no usable text from " + response.provider + ". Please regenerate.");
            }
            return SummaryResult.success(
                    localFallback,
                    selectedStyle.label() + " summary generated locally after remote empty response.",
                    "local",
                    "remote-empty-response",
                    response.model);
        }
        String refinedSummary = adjustSummaryByStyle(stripPromptEcho(summary), normalizedPreview, selectedStyle);
        if (isLowQualitySummary(refinedSummary)) {
            String localFallback = buildLocalExtractiveSummary(normalizedPreview, selectedStyle);
            if (!localFallback.isBlank()) {
                return SummaryResult.success(
                        localFallback,
                        selectedStyle.label() + " summary generated locally after low-quality remote response.",
                        "local",
                        "remote-low-quality",
                        response.model);
            }
        }
        return SummaryResult.success(
                refinedSummary,
                selectedStyle.label() + " summary generated by " + response.provider + " (direct mode).",
                response.provider,
                null,
                response.model);
    }

    private static String extractRepresentativeContent(String filePath, SummaryStyle style) {
        int fastPages = style == SummaryStyle.DETAILED ? 10 : 6;
        String firstPages = normalizeWhitespace(BookPreviewUtil.readTextContentFirstPages(filePath, fastPages));
        int targetChars = style == SummaryStyle.DETAILED ? DETAILED_PROMPT_CHARS : MAX_PROMPT_CHARS;
        if (!firstPages.isBlank()) {
            if (firstPages.length() <= targetChars) {
                return firstPages;
            }
            return firstPages.substring(0, targetChars);
        }
        String full = normalizeWhitespace(BookPreviewUtil.readTextContent(filePath));
        if (!full.isBlank()) {
            if (full.length() <= targetChars) {
                return full;
            }
            int slice = Math.max(700, targetChars / 3);
            int middleStart = Math.max(0, (full.length() / 2) - (slice / 2));
            int endStart = Math.max(0, full.length() - slice);
            String start = full.substring(0, Math.min(slice, full.length()));
            String middle = full.substring(middleStart, Math.min(middleStart + slice, full.length()));
            String end = full.substring(endStart);
            return normalizeWhitespace(start + " " + middle + " " + end);
        }
        return "";
    }

    static String buildModelInput(String previewText) {
        return buildModelInput(previewText, SummaryStyle.MEDIUM);
    }

    static String buildModelInput(String previewText, SummaryStyle style) {
        return buildModelInput(previewText, style, "", "");
    }

    static String buildModelInput(String previewText, SummaryStyle style, String titleHint, String authorHint) {
        SummaryStyle selectedStyle = style == null ? SummaryStyle.MEDIUM : style;
        String clean = normalizeWhitespace(previewText);
        int promptLimit = selectedStyle == SummaryStyle.DETAILED ? DETAILED_PROMPT_CHARS : MAX_PROMPT_CHARS;
        if (clean.length() > promptLimit) {
            clean = clean.substring(0, promptLimit);
        }
        String title = titleHint == null ? "" : normalizeWhitespace(titleHint);
        String author = authorHint == null ? "" : normalizeWhitespace(authorHint);
        if (isLikelyTextbookOrNonfiction(clean, title)) {
            String embeddedTitle = inferEmbeddedTitle(clean);
            String resolvedTitle = !embeddedTitle.isBlank() ? embeddedTitle : title;
            String tocSnippet = extractTableOfContentsSnippet(clean);
            return buildNonfictionPrompt(clean, selectedStyle, resolvedTitle, author, tocSnippet);
        }
        String lengthTarget = switch (selectedStyle) {
            case SHORT -> "one compact paragraph";
            case MEDIUM -> "two concise paragraphs";
            case DETAILED -> "two fuller paragraphs";
        };
        String metadata = "";
        if (!title.isBlank() || !author.isBlank()) {
            metadata = "Metadata: title=" + (title.isBlank() ? "Unknown" : title)
                    + ", author=" + (author.isBlank() ? "Unknown" : author) + ". ";
        }
        return "Write a factual " + lengthTarget + " synopsis of this book. "
                + metadata
                + "Focus only on what is supported by the excerpt. "
                + "Return only plain summary text with no bullets, headings, or meta-commentary.\n\n"
                + "Content excerpt:\n" + clean;
    }

    private static String buildNonfictionPrompt(
            String clean,
            SummaryStyle style,
            String resolvedTitle,
            String author,
            String tocSnippet) {
        String lengthTarget = switch (style == null ? SummaryStyle.MEDIUM : style) {
            case SHORT -> "one compact paragraph";
            case MEDIUM -> "two concise paragraphs";
            case DETAILED -> "three concise paragraphs";
        };
        String titlePart = resolvedTitle == null || resolvedTitle.isBlank() ? "" : ("Detected title: " + resolvedTitle + ". ");
        String authorPart = author == null || author.isBlank() ? "" : ("Author: " + author + ". ");
        String tocPart = tocSnippet == null || tocSnippet.isBlank()
                ? ""
                : ("Table-of-contents excerpt: " + tocSnippet + ". ");
        return "Write a factual " + lengthTarget + " summary for a nonfiction or textbook-style book. "
                + titlePart
                + authorPart
                + tocPart
                + "Base the summary on the detected title and content structure, highlighting the main topics and progression. "
                + "Do not invent narrative elements, characters, or plot arcs. "
                + "Return only plain summary text with no meta-commentary, bullets, or headings.\n\n"
                + "Content excerpt:\n" + clean;
    }

    private static boolean isLikelyTextbookOrNonfiction(String preview, String titleHint) {
        String title = normalizeWhitespace(titleHint).toLowerCase(Locale.ROOT);
        String text = normalizeWhitespace(preview).toLowerCase(Locale.ROOT);
        String[] titleSignals = {
                "textbook", "introduction to", "principles of", "fundamentals of", "handbook",
                "manual", "guide", "edition", "study", "course", "theory", "practice"
        };
        for (String signal : titleSignals) {
            if (title.contains(signal)) {
                return true;
            }
        }
        int structureSignals = 0;
        if (text.contains("table of contents") || text.contains("contents")) structureSignals++;
        if (text.contains("chapter 1") || text.contains("chapter one")) structureSignals++;
        if (text.contains("learning objectives")) structureSignals++;
        if (text.contains("exercise") || text.contains("review questions")) structureSignals++;
        if (text.contains("isbn")) structureSignals++;
        return structureSignals >= 2;
    }

    private static String inferEmbeddedTitle(String preview) {
        String clean = normalizeWhitespace(preview);
        if (clean.isBlank()) {
            return "";
        }
        int scanEnd = Math.min(clean.length(), 220);
        String head = clean.substring(0, scanEnd).trim();
        int byIndex = head.toLowerCase(Locale.ROOT).indexOf(" by ");
        if (byIndex > 12) {
            return normalizeWhitespace(head.substring(0, byIndex));
        }
        int chapterIndex = head.toLowerCase(Locale.ROOT).indexOf(" chapter ");
        if (chapterIndex > 10) {
            return normalizeWhitespace(head.substring(0, chapterIndex));
        }
        int sentenceEnd = head.indexOf('.');
        if (sentenceEnd > 10 && sentenceEnd < 140) {
            return normalizeWhitespace(head.substring(0, sentenceEnd));
        }
        String[] words = head.split("\\s+");
        if (words.length <= 3) {
            return "";
        }
        int take = Math.min(words.length, 12);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < take; i++) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(words[i]);
        }
        return normalizeWhitespace(out.toString());
    }

    private static String extractTableOfContentsSnippet(String preview) {
        String clean = normalizeWhitespace(preview);
        if (clean.isBlank()) {
            return "";
        }
        String lower = clean.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf("table of contents");
        if (idx < 0) {
            idx = lower.indexOf("contents");
        }
        if (idx < 0) {
            return "";
        }
        int start = Math.max(0, idx);
        int end = Math.min(clean.length(), start + 420);
        return normalizeWhitespace(clean.substring(start, end));
    }

    private static String buildChunkPrompt(String chunk, SummaryStyle style, int index, int total) {
        String targetRange = switch (style == null ? SummaryStyle.MEDIUM : style) {
            case SHORT -> "one concise sentence";
            case MEDIUM -> "two to three sentences";
            case DETAILED -> "four to five sentences";
        };
        return "Summarize this book chunk (" + index + "/" + total + ") in " + targetRange
                + ". Focus on key events, characters, and context. Return only plain summary text.\n\n"
                + normalizeWhitespace(chunk);
    }

    private String mergeChunkSummaries(List<String> partials, SummaryStyle style) {
        String joined = String.join(" ", partials);
        String mergePrompt = "Combine the following partial summaries into a single "
                + (style == null ? "medium" : style.label().toLowerCase(Locale.ROOT))
                + " book summary. Keep it coherent, concise, and factual. Return only summary text.\n\n"
                + joined;
        ApiResponse mergedResponse = apiCaller.apply(new RequestPayload(mergePrompt, style == null ? SummaryStyle.MEDIUM : style));
        if (!mergedResponse.ok) {
            return "";
        }
        String parsed = parseSummary(mergedResponse.body);
        if (parsed == null || parsed.isBlank()) {
            return "";
        }
        return adjustSummaryByStyle(stripPromptEcho(parsed), joined, style);
    }

    private static List<String> splitIntoChunks(String text, int maxCharsPerChunk, int maxChunks) {
        String clean = normalizeWhitespace(text);
        if (clean.isBlank()) {
            return List.of();
        }
        String[] sentences = clean.split("(?<=[.!?])\\s+");
        ArrayList<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            String s = normalizeWhitespace(sentence);
            if (s.isBlank()) {
                continue;
            }
            if (current.length() > 0 && current.length() + 1 + s.length() > maxCharsPerChunk) {
                chunks.add(current.toString());
                current = new StringBuilder();
                if (chunks.size() >= maxChunks) {
                    break;
                }
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(s);
        }
        if (!current.isEmpty() && chunks.size() < maxChunks) {
            chunks.add(current.toString());
        }
        if (chunks.isEmpty()) {
            int end = Math.min(clean.length(), Math.max(200, maxCharsPerChunk));
            return List.of(clean.substring(0, end));
        }
        return chunks;
    }

    private SummaryResult tryCatalogLookupSummary(String titleHint, String authorHint, SummaryStyle style) {
        String title = titleHint == null ? "" : titleHint.trim();
        if (title.isBlank()) {
            return null;
        }
        String author = authorHint == null ? "" : authorHint.trim();
        String query = author.isBlank() ? title : (title + " " + author);
        String url = "https://gutendex.com/books/?search=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(4))
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = LOOKUP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            String body = response.body() == null ? "" : response.body();
            Matcher summariesMatcher = GUTENDEX_SUMMARIES_ARRAY_PATTERN.matcher(body);
            if (!summariesMatcher.find()) {
                return null;
            }
            String summariesArray = summariesMatcher.group(1);
            Matcher entryMatcher = JSON_STRING_PATTERN.matcher(summariesArray);
            while (entryMatcher.find()) {
                String candidate = trimToSentenceEnd(normalizeWhitespace(unescapeJson(entryMatcher.group(1))));
                if (!candidate.isBlank() && !isLowQualitySummary(candidate)) {
                    String adjusted = adjustSummaryByStyle(candidate, candidate, style);
                    return SummaryResult.success(
                            adjusted,
                            (style == null ? SummaryStyle.MEDIUM : style).label()
                                    + " summary found from catalog metadata. Review and edit before finalizing.",
                            "catalog",
                            null,
                            "gutendex");
                }
            }
            return null;
        } catch (IOException | InterruptedException ignored) {
            if (ignored instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    static String parseSummary(String jsonBody) {
        if (jsonBody == null || jsonBody.isBlank()) {
            return null;
        }

        String summary = extractJsonString(jsonBody, ARRAY_SUMMARY_PATTERN);
        if (summary == null) {
            summary = extractJsonString(jsonBody, OBJECT_SUMMARY_PATTERN);
        }
        if (summary == null) {
            summary = extractJsonString(jsonBody, OLLAMA_SUMMARY_PATTERN);
        }
        if (summary == null) {
            return null;
        }
        return trimToSentenceEnd(normalizeWhitespace(summary));
    }

    private static String extractJsonString(String jsonBody, Pattern pattern) {
        Matcher matcher = pattern.matcher(jsonBody);
        if (!matcher.find()) {
            return null;
        }
        return unescapeJson(matcher.group(1));
    }

    private static String normalizeWhitespace(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r", " ")
                .replace("\n", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private static String unescapeJson(String escaped) {
        if (escaped == null) {
            return "";
        }
        return escaped
                .replace("\\n", " ")
                .replace("\\r", " ")
                .replace("\\t", " ")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    static String trimToSentenceEnd(String text) {
        String clean = normalizeWhitespace(text);
        if (clean.isBlank()) {
            return clean;
        }
        int cut = -1;
        for (char marker : new char[]{'.', '!', '?'}) {
            int idx = clean.lastIndexOf(marker);
            if (idx > cut) {
                cut = idx;
            }
        }
        if (cut <= 0) {
            return clean;
        }
        return clean.substring(0, cut + 1).trim();
    }

    private static String stripPromptEcho(String summary) {
        String clean = normalizeWhitespace(summary);
        int markerIdx = clean.toLowerCase(Locale.ROOT).indexOf("return only the summarized text");
        if (markerIdx >= 0) {
            int afterMarker = clean.indexOf('.', markerIdx);
            if (afterMarker > markerIdx && afterMarker < clean.length() - 1) {
                clean = clean.substring(afterMarker + 1).trim();
            }
        }
        return trimToSentenceEnd(clean);
    }

    private static boolean isLowQualitySummary(String summary) {
        String clean = normalizeWhitespace(summary);
        if (clean.isBlank()) {
            return true;
        }
        int wordCount = clean.split("\\s+").length;
        if (wordCount < 8) {
            return true;
        }
        if (wordCount >= 14) {
            return false;
        }
        return !(clean.endsWith(".") || clean.endsWith("!") || clean.endsWith("?"));
    }

    private static boolean isTimeoutReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return false;
        }
        return reason.toLowerCase(Locale.ROOT).contains("timeout");
    }

    static String escapeJson(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            switch (ch) {
                case '\"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        return out.toString();
    }

    static String toInferenceBody(String input) {
        return toInferenceBody(input, SummaryStyle.MEDIUM);
    }

    static String toInferenceBody(String input, SummaryStyle style) {
        SummaryStyle selectedStyle = style == null ? SummaryStyle.MEDIUM : style;
        int minLength = switch (selectedStyle) {
            case SHORT -> SHORT_MIN_LENGTH;
            case MEDIUM -> MEDIUM_MIN_LENGTH;
            case DETAILED -> DETAILED_MIN_LENGTH;
        };
        int maxLength = switch (selectedStyle) {
            case SHORT -> SHORT_MAX_LENGTH;
            case MEDIUM -> MEDIUM_MAX_LENGTH;
            case DETAILED -> DETAILED_MAX_LENGTH;
        };
        String escapedInput = escapeJson(input);
        return "{"
                + "\"inputs\":\"" + escapedInput + "\","
                + "\"parameters\":{"
                + "\"min_length\":" + minLength + ","
                + "\"max_length\":" + maxLength + ","
                + "\"no_repeat_ngram_size\":" + REMOTE_NO_REPEAT_NGRAM_SIZE + ","
                + "\"encoder_no_repeat_ngram_size\":" + REMOTE_ENCODER_NO_REPEAT_NGRAM_SIZE + ","
                + "\"repetition_penalty\":" + REMOTE_REPETITION_PENALTY + ","
                + "\"num_beams\":" + REMOTE_NUM_BEAMS + ","
                + "\"early_stopping\":true"
                + "},"
                + "\"options\":{"
                + "\"wait_for_model\":true"
                + "}"
                + "}";
    }

    static String toOllamaBody(String input, SummaryStyle style, String modelName) {
        SummaryStyle selectedStyle = style == null ? SummaryStyle.MEDIUM : style;
        int numPredict = switch (selectedStyle) {
            case SHORT -> 48;
            case MEDIUM -> 90;
            case DETAILED -> 140;
        };
        String escapedPrompt = escapeJson(input);
        String selectedModel = modelName == null || modelName.isBlank() ? AppConfig.OLLAMA_MODEL : modelName;
        return "{"
                + "\"model\":\"" + escapeJson(selectedModel) + "\","
                + "\"prompt\":\"" + escapedPrompt + "\","
                + "\"stream\":false,"
                + "\"options\":{"
                + "\"temperature\":0.2,"
                + "\"num_predict\":" + numPredict
                + "}"
                + "}";
    }

    static String buildLocalExtractiveSummary(String previewText) {
        return buildLocalExtractiveSummary(previewText, SummaryStyle.MEDIUM);
    }

    static String adjustSummaryByStyle(String generatedSummary, String sourceText, SummaryStyle style) {
        SummaryStyle selectedStyle = style == null ? SummaryStyle.MEDIUM : style;
        String cleanGenerated = trimToSentenceEnd(normalizeWhitespace(generatedSummary));
        String base = cleanGenerated;
        if (base.isBlank()) {
            return "";
        }

        return switch (selectedStyle) {
            case SHORT -> clampByWords(base, 30);
            case MEDIUM -> keepFirstSentences(base, 2);
            case DETAILED -> base;
        };
    }

    private static String clampByWords(String text, int maxWords) {
        String clean = normalizeWhitespace(text);
        if (clean.isBlank() || maxWords <= 0) {
            return "";
        }
        String[] words = clean.split("\\s+");
        if (words.length <= maxWords) {
            return clean;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < maxWords; i++) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(words[i]);
        }
        String shortened = out.toString().trim();
        if (!shortened.endsWith(".") && !shortened.endsWith("!") && !shortened.endsWith("?")) {
            shortened += "...";
        }
        return shortened;
    }

    private static String keepFirstSentences(String text, int count) {
        String clean = normalizeWhitespace(text);
        if (clean.isBlank() || count <= 0) {
            return "";
        }
        String[] sentences = clean.split("(?<=[.!?])\\s+");
        if (sentences.length <= count) {
            return clean;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(normalizeWhitespace(sentences[i]));
        }
        return trimToSentenceEnd(out.toString());
    }

    static String buildLocalExtractiveSummary(String previewText, SummaryStyle style) {
        SummaryStyle selectedStyle = style == null ? SummaryStyle.MEDIUM : style;
        String clean = normalizeWhitespace(previewText);
        if (clean.isBlank()) {
            return "";
        }
        String[] rawSentences = clean.split("(?<=[.!?])\\s+");
        if (rawSentences.length == 0) {
            return "";
        }

        ArrayList<String> sentences = new ArrayList<>();
        for (String s : rawSentences) {
            String normalized = normalizeWhitespace(s);
            if (!normalized.isBlank()) {
                sentences.add(normalized);
            }
        }
        if (sentences.isEmpty()) {
            return "";
        }

        Map<String, Integer> freq = new HashMap<>();
        for (String sentence : sentences) {
            for (String token : sentence.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
                if (token.length() < 3 || STOP_WORDS.contains(token)) {
                    continue;
                }
                freq.merge(token, 1, Integer::sum);
            }
        }

        ArrayList<SentenceScore> scored = new ArrayList<>();
        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            double score = scoreSentence(sentence, i, freq);
            scored.add(new SentenceScore(i, sentence, score));
        }

        int targetCount = switch (selectedStyle) {
            case SHORT -> Math.min(1, sentences.size());
            case MEDIUM -> Math.max(FALLBACK_MIN_SENTENCES, Math.min(FALLBACK_MAX_SENTENCES, sentences.size()));
            case DETAILED -> Math.min(6, sentences.size());
        };
        int maxChars = switch (selectedStyle) {
            case SHORT -> 260;
            case MEDIUM -> FALLBACK_MAX_CHARS;
            case DETAILED -> 1400;
        };
        scored.sort(Comparator.comparingDouble((SentenceScore s) -> s.score).reversed());
        ArrayList<SentenceScore> selected = new ArrayList<>(scored.subList(0, targetCount));
        selected.sort(Comparator.comparingInt(s -> s.index));

        StringBuilder out = new StringBuilder();
        for (SentenceScore s : selected) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(s.text);
            if (out.length() >= maxChars) {
                break;
            }
        }
        String summary = out.toString().trim();
        if (summary.length() > maxChars) {
            summary = summary.substring(0, maxChars).trim();
            if (!summary.endsWith(".") && !summary.endsWith("!") && !summary.endsWith("?")) {
                summary += "...";
            }
        }
        return summary;
    }

    private static double scoreSentence(String sentence, int index, Map<String, Integer> freq) {
        String[] tokens = sentence.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        double score = 0.0;
        int contentTokens = 0;
        for (String token : tokens) {
            if (token.length() < 3 || STOP_WORDS.contains(token)) {
                continue;
            }
            score += freq.getOrDefault(token, 0);
            contentTokens++;
        }
        if (contentTokens > 0) {
            score /= contentTokens;
        }
        if (index == 0) {
            score += 1.0;
        } else if (index < 3) {
            score += 0.5;
        }
        int len = sentence.length();
        if (len < 25) {
            score -= 0.3;
        } else if (len > 320) {
            score -= 0.4;
        }
        return score;
    }

    private static final Set<String> STOP_WORDS = new HashSet<>(Set.of(
            "the", "and", "for", "with", "that", "this", "from", "are", "was", "were", "have",
            "has", "had", "into", "onto", "over", "under", "than", "then", "they", "them", "their",
            "there", "about", "also", "while", "where", "when", "which", "what", "your", "you",
            "its", "it's", "his", "her", "she", "him", "our", "out", "all", "any", "can", "could",
            "would", "should", "not", "but", "because", "through", "very", "more", "most", "some"
    ));

    private static final class SentenceScore {
        private final int index;
        private final String text;
        private final double score;

        private SentenceScore(int index, String text, double score) {
            this.index = index;
            this.text = text;
            this.score = score;
        }
    }

    record RequestPayload(String input, SummaryStyle style) {}

    record ApiResponse(boolean ok, String reason, String body, String provider, String model) {}

    public record SummaryResult(
            boolean success,
            String summary,
            String message,
            String provider,
            String fallbackReason,
            String modelUsed) {
        static SummaryResult success(String summary, String message) {
            return new SummaryResult(true, summary, message, "unknown", null, null);
        }

        static SummaryResult success(String summary, String message, String provider, String fallbackReason, String modelUsed) {
            return new SummaryResult(true, summary, message, provider, fallbackReason, modelUsed);
        }

        static SummaryResult failure(String message) {
            return new SummaryResult(false, "", message, "none", null, null);
        }
    }

    private static final class HttpApiCaller implements Function<RequestPayload, ApiResponse> {
        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(AppConfig.OLLAMA_API_TIMEOUT_SECONDS, 5)))
                .build();

        @Override
        public ApiResponse apply(RequestPayload payload) {
            // Respect configured provider to avoid long multi-provider waits.
            if (AppConfig.preferOllamaSummary()) {
                return tryOllama(payload);
            }
            return tryHuggingFace(payload);
        }

        private ApiResponse tryOllama(RequestPayload payload) {
            if (!AppConfig.isOllamaConfigured()) {
                return new ApiResponse(false, "ollama not configured", "{\"error\":\"Ollama not configured\"}", "ollama", null);
            }
            String resolvedModel = resolveOllamaModel();
            if (resolvedModel == null || resolvedModel.isBlank()) {
                return new ApiResponse(
                        false,
                        "ollama reachable but no installed model found (checked /api/tags)",
                        "{\"error\":\"No installed Ollama model found\"}",
                        "ollama",
                        null);
            }
            String endpoint = AppConfig.getOllamaGenerateEndpoint();
            String body = toOllamaBody(payload.input(), payload.style(), resolvedModel);
            return sendJsonRequest(endpoint, body, false, "ollama", resolvedModel, payload.style());
        }

        private ApiResponse tryHuggingFace(RequestPayload payload) {
            if (!AppConfig.isHfConfigured()) {
                return new ApiResponse(false, "hf not configured", "{\"error\":\"HF not configured\"}", "hf", AppConfig.HF_MODEL_ID);
            }
            String endpoint = AppConfig.getHfInferenceEndpoint();
            String body = toInferenceBody(payload.input(), payload.style());
            return sendJsonRequest(endpoint, body, true, "hf", AppConfig.HF_MODEL_ID, payload.style());
        }

        private ApiResponse sendJsonRequest(
                String endpoint,
                String body,
                boolean useAuthHeader,
                String provider,
                String model,
                SummaryStyle style) {
            int requestTimeoutSeconds = "ollama".equals(provider)
                    ? AppConfig.OLLAMA_API_TIMEOUT_SECONDS
                    : AppConfig.HF_API_TIMEOUT_SECONDS;
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            boolean noTimeoutForOllama = "ollama".equals(provider);
            if (!noTimeoutForOllama) {
                builder.timeout(Duration.ofSeconds(requestTimeoutSeconds));
            }
            if (useAuthHeader) {
                builder.header("Authorization", "Bearer " + AppConfig.HF_API_TOKEN);
            }
            HttpRequest request = builder.build();
            try {
                HttpResponse<String> response = client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                );
                boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
                String reason = ok ? "ok" : ("HTTP " + response.statusCode());
                String bodyText = response.body() == null ? "" : response.body();
                if (!ok && "ollama".equals(provider)) {
                    String apiError = extractJsonString(bodyText, ERROR_PATTERN);
                    if (apiError != null && !apiError.isBlank()) {
                        reason = "ollama error: " + normalizeWhitespace(apiError);
                    }
                }
                return new ApiResponse(ok, reason, bodyText, provider, model);
            } catch (HttpTimeoutException e) {
                return new ApiResponse(
                        false,
                        "timeout after " + requestTimeoutSeconds + "s",
                        "{\"error\":\"Request timed out\"}",
                        provider,
                        model);
            } catch (IOException e) {
                String reason = e.getMessage() == null || e.getMessage().isBlank()
                        ? "network error"
                        : ("network error: " + normalizeWhitespace(e.getMessage()));
                return new ApiResponse(false, reason, "{\"error\":\"Network error\"}", provider, model);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new ApiResponse(false, "interrupted", "{\"error\":\"Request interrupted\"}", provider, model);
            }
        }

        private String resolveOllamaModel() {
            List<String> models = readOrRefreshSharedOllamaModels();
            if (models.isEmpty()) {
                return null;
            }
            String configured = AppConfig.OLLAMA_MODEL == null ? "" : AppConfig.OLLAMA_MODEL.trim();
            if (!configured.isBlank()) {
                for (String model : models) {
                    if (configured.equalsIgnoreCase(model)) {
                        return model;
                    }
                }
            }
            return models.get(0);
        }

        private List<String> readOrRefreshSharedOllamaModels() {
            long now = System.currentTimeMillis();
            long ttlMs = Math.max(0, AppConfig.OLLAMA_MODEL_CACHE_SECONDS) * 1000L;
            List<String> models = sharedOllamaModelsCache;
            long fetchedAt = sharedOllamaModelsFetchedAtMs;
            if (models != null && !models.isEmpty() && now - fetchedAt <= ttlMs) {
                return models;
            }
            synchronized (HttpApiCaller.class) {
                models = sharedOllamaModelsCache;
                fetchedAt = sharedOllamaModelsFetchedAtMs;
                if (models != null && !models.isEmpty() && now - fetchedAt <= ttlMs) {
                    return models;
                }
                List<String> fresh = fetchOllamaModels();
                sharedOllamaModelsCache = fresh;
                sharedOllamaModelsFetchedAtMs = System.currentTimeMillis();
                return fresh;
            }
        }

        private List<String> fetchOllamaModels() {
            String endpoint = AppConfig.OLLAMA_BASE.endsWith("/")
                    ? AppConfig.OLLAMA_BASE.substring(0, AppConfig.OLLAMA_BASE.length() - 1) + "/api/tags"
                    : AppConfig.OLLAMA_BASE + "/api/tags";
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(Math.max(3, AppConfig.OLLAMA_API_TIMEOUT_SECONDS)))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    return List.of();
                }
                String body = response.body() == null ? "" : response.body();
                Matcher matcher = OLLAMA_MODEL_PATTERN.matcher(body);
                ArrayList<String> models = new ArrayList<>();
                while (matcher.find()) {
                    String name = unescapeJson(matcher.group(1));
                    if (name != null) {
                        String trimmed = name.trim();
                        if (!trimmed.isBlank() && !models.contains(trimmed)) {
                            models.add(trimmed);
                        }
                    }
                }
                return models;
            } catch (IOException | InterruptedException ignored) {
                if (ignored instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                return List.of();
            }
        }
    }
}
