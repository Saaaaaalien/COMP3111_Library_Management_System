package org.example.service;

import org.example.app.AppConfig;
import org.example.util.BookPreviewUtil;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BookSummaryService {
    private static final int MAX_PROMPT_CHARS = 3500;
    private static final int FALLBACK_MAX_CHARS = 900;
    private static final int FALLBACK_MIN_SENTENCES = 2;
    private static final int FALLBACK_MAX_SENTENCES = 4;
    private static final int REMOTE_MIN_LENGTH = 16;
    private static final int REMOTE_MAX_LENGTH = 256;
    private static final int REMOTE_NO_REPEAT_NGRAM_SIZE = 3;
    private static final int REMOTE_ENCODER_NO_REPEAT_NGRAM_SIZE = 3;
    private static final double REMOTE_REPETITION_PENALTY = 3.5;
    private static final int REMOTE_NUM_BEAMS = 4;
    private static final Pattern ARRAY_SUMMARY_PATTERN =
            Pattern.compile("\"summary_text\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
    private static final Pattern OBJECT_SUMMARY_PATTERN =
            Pattern.compile("\"generated_text\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
    private static final Pattern ERROR_PATTERN =
            Pattern.compile("\"error\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);

    private final Function<RequestPayload, ApiResponse> apiCaller;
    private final BooleanSupplier hfConfiguredChecker;

    public BookSummaryService() {
        this(new HttpApiCaller(), AppConfig::isHfConfigured);
    }

    BookSummaryService(Function<RequestPayload, ApiResponse> apiCaller) {
        this(apiCaller, AppConfig::isHfConfigured);
    }

    BookSummaryService(Function<RequestPayload, ApiResponse> apiCaller, BooleanSupplier hfConfiguredChecker) {
        this.apiCaller = Objects.requireNonNull(apiCaller);
        this.hfConfiguredChecker = Objects.requireNonNull(hfConfiguredChecker);
    }

    public SummaryResult generateSummaryFromBookFile(String filePath) {
        String rawPreview = BookPreviewUtil.readTextPreview(filePath);
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
            String fallback = buildLocalExtractiveSummary(normalizedPreview);
            if (fallback.isBlank()) {
                return SummaryResult.failure(
                        "Summary generation is unavailable right now. You can still write/edit the summary manually.");
            }
            return SummaryResult.success(
                    fallback,
                    "Summary generated with local demo mode. Review and edit before finalizing.");
        }

        String modelInput = buildModelInput(normalizedPreview);
        try {
            ApiResponse response = apiCaller.apply(new RequestPayload(modelInput));
            if (!response.ok) {
                String fallback = buildLocalExtractiveSummary(normalizedPreview);
                if (!fallback.isBlank()) {
                    return SummaryResult.success(
                            fallback,
                            "Cloud summary unavailable. Generated a local demo summary; please review/edit before finalizing.");
                }
                String error = extractJsonString(response.body, ERROR_PATTERN);
                String reason = error != null && !error.isBlank() ? normalizeWhitespace(error) : response.reason;
                return SummaryResult.failure("Summary generation failed: " + reason + ". You can refine manually.");
            }

            String summary = parseSummary(response.body);
            if (summary == null || summary.isBlank()) {
                String fallback = buildLocalExtractiveSummary(normalizedPreview);
                if (!fallback.isBlank()) {
                    return SummaryResult.success(
                            fallback,
                            "Cloud summary returned no usable text. Generated a local demo summary; please review/edit before finalizing.");
                }
                return SummaryResult.failure(
                        "Summary service returned no usable text. You can still write/edit the summary manually.");
            }
            return SummaryResult.success(summary, "Summary generated. Review and edit before finalizing.");
        } catch (Exception ex) {
            String fallback = buildLocalExtractiveSummary(normalizedPreview);
            if (!fallback.isBlank()) {
                return SummaryResult.success(
                        fallback,
                        "Cloud summary unavailable (" + ex.getClass().getSimpleName() + "). Generated a local demo summary; please review/edit.");
            }
            return SummaryResult.failure(
                    "Unable to generate summary right now (" + ex.getClass().getSimpleName() + "). You can continue manually.");
        }
    }

    static String buildModelInput(String previewText) {
        String clean = normalizeWhitespace(previewText);
        if (clean.length() > MAX_PROMPT_CHARS) {
            clean = clean.substring(0, MAX_PROMPT_CHARS);
        }
        return "Summarize the following book excerpt in 110-160 words. " +
                "Focus on core topic, key themes, and target audience. " +
                "Return only the summary text without bullets or headers.\n\n" + clean;
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
        String escapedInput = escapeJson(input);
        return "{"
                + "\"inputs\":\"" + escapedInput + "\","
                + "\"parameters\":{"
                + "\"min_length\":" + REMOTE_MIN_LENGTH + ","
                + "\"max_length\":" + REMOTE_MAX_LENGTH + ","
                + "\"no_repeat_ngram_size\":" + REMOTE_NO_REPEAT_NGRAM_SIZE + ","
                + "\"encoder_no_repeat_ngram_size\":" + REMOTE_ENCODER_NO_REPEAT_NGRAM_SIZE + ","
                + "\"repetition_penalty\":" + REMOTE_REPETITION_PENALTY + ","
                + "\"num_beams\":" + REMOTE_NUM_BEAMS + ","
                + "\"early_stopping\":true"
                + "}"
                + "}";
    }

    static String buildLocalExtractiveSummary(String previewText) {
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

        int targetCount = Math.max(FALLBACK_MIN_SENTENCES, Math.min(FALLBACK_MAX_SENTENCES, sentences.size()));
        scored.sort(Comparator.comparingDouble((SentenceScore s) -> s.score).reversed());
        ArrayList<SentenceScore> selected = new ArrayList<>(scored.subList(0, targetCount));
        selected.sort(Comparator.comparingInt(s -> s.index));

        StringBuilder out = new StringBuilder();
        for (SentenceScore s : selected) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(s.text);
            if (out.length() >= FALLBACK_MAX_CHARS) {
                break;
            }
        }
        String summary = out.toString().trim();
        if (summary.length() > FALLBACK_MAX_CHARS) {
            summary = summary.substring(0, FALLBACK_MAX_CHARS).trim();
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

    record RequestPayload(String input) {}

    record ApiResponse(boolean ok, String reason, String body) {}

    public record SummaryResult(boolean success, String summary, String message) {
        static SummaryResult success(String summary, String message) {
            return new SummaryResult(true, summary, message);
        }

        static SummaryResult failure(String message) {
            return new SummaryResult(false, "", message);
        }
    }

    private static final class HttpApiCaller implements Function<RequestPayload, ApiResponse> {
        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(AppConfig.HF_API_TIMEOUT_SECONDS))
                .build();

        @Override
        public ApiResponse apply(RequestPayload payload) {
            String endpoint = AppConfig.getHfInferenceEndpoint();
            String body = toInferenceBody(payload.input());
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(AppConfig.HF_API_TIMEOUT_SECONDS))
                    .header("Authorization", "Bearer " + AppConfig.HF_API_TOKEN)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
                String reason = ok ? "ok" : ("HTTP " + response.statusCode());
                return new ApiResponse(ok, reason, response.body());
            } catch (IOException e) {
                return new ApiResponse(false, "network error", "{\"error\":\"Network error\"}");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new ApiResponse(false, "interrupted", "{\"error\":\"Request interrupted\"}");
            }
        }
    }
}
