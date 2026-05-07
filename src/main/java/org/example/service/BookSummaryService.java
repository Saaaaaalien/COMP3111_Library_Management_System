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
    private static final int MAX_PROMPT_CHARS = 3200;
    private static final int RETRY_PROMPT_CHARS = 1800;
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
            String retrySummary = trySecondPassSummary(normalizedPreview, selectedStyle);
            if (retrySummary.isBlank()) {
                return SummaryResult.failure(
                        "Direct LLM returned no usable text from " + response.provider + ". Please regenerate.");
            }
            return SummaryResult.success(
                    retrySummary,
                    selectedStyle.label() + " summary generated by " + response.provider + " (direct mode).",
                    response.provider,
                    null,
                    response.model);
        }
        String refinedSummary = adjustSummaryByStyle(stripPromptEcho(summary), normalizedPreview, selectedStyle);
        if (isLowQualitySummary(refinedSummary)) {
            String retrySummary = trySecondPassSummary(normalizedPreview, selectedStyle);
            if (!retrySummary.isBlank()) {
                return SummaryResult.success(
                        retrySummary,
                        selectedStyle.label() + " summary generated by " + response.provider + " (direct mode).",
                        response.provider,
                        null,
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
        String full = BookPreviewUtil.readTextContent(filePath);
        String cleanFull = normalizeWhitespace(full);
        if (!cleanFull.isBlank()) {
            int targetChars = style == SummaryStyle.DETAILED ? 9000 : 3200;
            if (cleanFull.length() <= targetChars) {
                return cleanFull;
            }
            int slice = Math.max(800, targetChars / 3);
            int middleStart = Math.max(0, (cleanFull.length() / 2) - (slice / 2));
            int endStart = Math.max(0, cleanFull.length() - slice);
            String start = cleanFull.substring(0, Math.min(slice, cleanFull.length()));
            String middle = cleanFull.substring(middleStart, Math.min(middleStart + slice, cleanFull.length()));
            String end = cleanFull.substring(endStart);
            return normalizeWhitespace(start + " " + middle + " " + end);
        }
        return BookPreviewUtil.readTextContentFirstPages(filePath, SUMMARY_SOURCE_MAX_PAGES);
    }

    private SummaryResult summarizeByChunks(String normalizedPreview, SummaryStyle style) {
        List<String> chunks = splitIntoChunks(normalizedPreview, 1200, style == SummaryStyle.DETAILED ? 6 : 4);
        if (chunks.isEmpty()) {
            return SummaryResult.failure("No usable content chunks found for summarization.");
        }

        ArrayList<String> partials = new ArrayList<>();
        String provider = "ollama";
        String model = null;
        String firstError = null;
        for (int i = 0; i < chunks.size(); i++) {
            String chunkPrompt = buildChunkPrompt(chunks.get(i), style, i + 1, chunks.size());
            ApiResponse response = apiCaller.apply(new RequestPayload(chunkPrompt, style));
            if (!response.ok) {
                if (firstError == null) {
                    firstError = response.reason;
                }
                continue;
            }
            String partial = parseSummary(response.body);
            if (partial == null || partial.isBlank()) {
                continue;
            }
            partials.add(stripPromptEcho(partial));
            provider = response.provider == null ? provider : response.provider;
            model = response.model;
        }

        if (partials.isEmpty()) {
            String reason = firstError == null ? "no usable partial summary from chunks" : firstError;
            return SummaryResult.failure("Chunked summary failed: " + reason + ".");
        }

        String merged = mergeChunkSummaries(partials, style);
        if (merged == null || merged.isBlank()) {
            String joined = String.join(" ", partials);
            merged = adjustSummaryByStyle(joined, normalizedPreview, style);
        }
        if (merged == null || merged.isBlank()) {
            return SummaryResult.failure("Chunk merge failed. Please try regenerate.");
        }
        return SummaryResult.success(
                merged,
                style.label() + " summary generated by " + provider + " using chunked summarization.",
                provider,
                null,
                model);
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
        int promptLimit = selectedStyle == SummaryStyle.DETAILED ? 9000 : MAX_PROMPT_CHARS;
        if (clean.length() > promptLimit) {
            clean = clean.substring(0, promptLimit);
        }
        String title = titleHint == null ? "" : normalizeWhitespace(titleHint);
        String author = authorHint == null ? "" : normalizeWhitespace(authorHint);
        String targetRange = switch (selectedStyle) {
            case SHORT -> "one to three sentences";
            case MEDIUM -> "four to six sentences";
            case DETAILED -> "eight to ten sentences";
        };
        String metadataLine = "";
        if (!title.isBlank() || !author.isBlank()) {
            metadataLine = "Book metadata:\n"
                    + "Title: " + (title.isBlank() ? "Unknown" : title) + "\n"
                    + "Author: " + (author.isBlank() ? "Unknown" : author) + "\n\n";
        }
        if (selectedStyle == SummaryStyle.DETAILED) {
            return "Write a polished book synopsis in exactly two short paragraphs.\n\n"
                    + "Paragraph 1 requirements:\n"
                    + "- Introduce the book identity (title/author/publication context when available).\n"
                    + "- State the setting and introduce narrator or principal protagonist.\n\n"
                    + "Paragraph 2 requirements:\n"
                    + "- Explain the central conflict, motivation, and stakes.\n"
                    + "- Focus on major relationship dynamics and core plot arc only.\n\n"
                    + "Style rules:\n"
                    + "- Present tense, objective tone, concise prose.\n"
                    + "- Paraphrase in your own words.\n"
                    + "- No bullets or headings in the final output.\n"
                    + "- At most one short quote only if it works as a thematic hook.\n"
                    + "- Cover the overall arc (beginning, middle, and ending direction) rather than early chapters only.\n"
                    + "- Do not invent unsupported facts, timelines, or character backstories.\n"
                    + "- If metadata conflicts with the excerpt, prefer the excerpt.\n\n"
                    + metadataLine + "Content excerpt:\n" + clean;
        }
        if (selectedStyle == SummaryStyle.MEDIUM) {
            return "Write a polished book synopsis in two concise paragraphs.\n\n"
                    + "Paragraph 1 requirements:\n"
                    + "- Briefly introduce the book identity (title/author context when available).\n"
                    + "- Establish setting and main narrator/protagonist.\n\n"
                    + "Paragraph 2 requirements:\n"
                    + "- Explain the central conflict and key stakes only.\n"
                    + "- Exclude minor subplots and secondary details.\n\n"
                    + "Style rules:\n"
                    + "- Present tense, objective tone, concise prose.\n"
                    + "- Paraphrase in your own words.\n"
                    + "- No bullets or headings in final output.\n"
                    + "- At most one short quote only if thematic.\n"
                    + "- Do not invent unsupported facts.\n\n"
                    + metadataLine + "Content excerpt:\n" + clean;
        }
        return "Write a polished short synopsis in one compact paragraph. "
                + "Include book identity context (title/author when available), main setting/protagonist, and the core conflict/stakes only. "
                + "Write in present tense, objective tone, and paraphrase in your own words. "
                + "Do not include minor subplots, detailed examples, bullets, or headings. "
                + "Avoid direct quotes unless one very short thematic hook is essential. "
                + "Do not invent unsupported facts.\n\n"
                + metadataLine + "Content excerpt:\n" + clean;
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

    private String trySecondPassSummary(String normalizedPreview, SummaryStyle style) {
        String retryPreview = normalizedPreview.length() > RETRY_PROMPT_CHARS
                ? normalizedPreview.substring(0, RETRY_PROMPT_CHARS)
                : normalizedPreview;
        String retryInput = buildModelInput(retryPreview, style);
        try {
            ApiResponse retryResponse = apiCaller.apply(new RequestPayload(retryInput, style));
            if (!retryResponse.ok) {
                return "";
            }
            String parsedRetry = parseSummary(retryResponse.body);
            if (parsedRetry == null || parsedRetry.isBlank()) {
                return "";
            }
            String refinedRetry = adjustSummaryByStyle(stripPromptEcho(parsedRetry), normalizedPreview, style);
            return isLowQualitySummary(refinedRetry) ? "" : refinedRetry;
        } catch (Exception ignored) {
            return "";
        }
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
