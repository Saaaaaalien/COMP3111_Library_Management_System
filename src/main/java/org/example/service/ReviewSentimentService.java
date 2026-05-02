package org.example.service;

import org.example.app.AppConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies review text (and star rating as tie-break / fallback) into
 * {@code positive}, {@code neutral}, or {@code negative} using the Hugging Face
 * Inference API when configured, otherwise a deterministic star-rating heuristic.
 */
public final class ReviewSentimentService {

    public static final String SOURCE_AI = "ai";
    public static final String SOURCE_HEURISTIC = "heuristic";

    private static final Pattern LABEL_SCORE_OBJECT = Pattern.compile(
            "\\{\\s*\"label\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"score\"\\s*:\\s*([-+0-9.eE]+)\\s*\\}");
    private static final Pattern SCORE_LABEL_OBJECT = Pattern.compile(
            "\\{\\s*\"score\"\\s*:\\s*([-+0-9.eE]+)\\s*,\\s*\"label\"\\s*:\\s*\"([^\"]+)\"\\s*\\}");

    private final Function<String, SentimentApiResponse> apiCaller;
    private final BooleanSupplier hfConfiguredChecker;

    public ReviewSentimentService() {
        this(new HttpSentimentCaller(), AppConfig::isHfConfigured);
    }

    ReviewSentimentService(Function<String, SentimentApiResponse> apiCaller, BooleanSupplier hfConfiguredChecker) {
        this.apiCaller = Objects.requireNonNull(apiCaller);
        this.hfConfiguredChecker = Objects.requireNonNull(hfConfiguredChecker);
    }

    /**
     * Classifies a single review. When HF is not configured, the request fails, or the response cannot be parsed,
     * returns heuristic labels with {@link #SOURCE_HEURISTIC}.
     */
    public SentimentResult classify(String reviewText, int rating) {
        int clampedRating = Math.max(1, Math.min(5, rating));
        if (!hfConfiguredChecker.getAsBoolean()) {
            return new SentimentResult(heuristicLabel(clampedRating), SOURCE_HEURISTIC);
        }
        String inputs = buildClassificationInput(reviewText, clampedRating);
        String bodyJson = toSentimentInferenceBody(inputs);
        try {
            SentimentApiResponse response = apiCaller.apply(bodyJson);
            if (!response.ok()) {
                return new SentimentResult(heuristicLabel(clampedRating), SOURCE_HEURISTIC);
            }
            String mapped = mapTopClassificationLabel(response.body());
            if (mapped != null) {
                return new SentimentResult(mapped, SOURCE_AI);
            }
        } catch (Exception ignored) {
            // fall through to heuristic
        }
        return new SentimentResult(heuristicLabel(clampedRating), SOURCE_HEURISTIC);
    }

    /**
     * Builds the string sent as {@code inputs} to the classification model: prefers review text, augments with
     * rating when text is short so the model has context (HF still used when token is set).
     */
    static String buildClassificationInput(String reviewText, int clampedRating) {
        String text = reviewText == null ? "" : reviewText.trim();
        if (text.isEmpty()) {
            return "Star rating: " + clampedRating + " out of 5. (No written review.)";
        }
        if (text.length() < 24) {
            return text + " [Rating: " + clampedRating + "/5]";
        }
        return text;
    }

    static String heuristicLabel(int clampedRating) {
        if (clampedRating <= 2) {
            return "negative";
        }
        if (clampedRating == 3) {
            return "neutral";
        }
        return "positive";
    }

    static String toSentimentInferenceBody(String inputs) {
        return "{\"inputs\":\"" + escapeJson(inputs) + "\",\"options\":{\"wait_for_model\":true}}";
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

    /**
     * Parses HF classification JSON (nested arrays of {@code label}/{@code score} objects), picks the highest score,
     * and maps vendor labels to {@code positive}/{@code neutral}/{@code negative}.
     */
    static String mapTopClassificationLabel(String jsonBody) {
        if (jsonBody == null || jsonBody.isBlank()) {
            return null;
        }
        List<LabelScore> pairs = new ArrayList<>();
        extractLabelScorePairs(jsonBody, LABEL_SCORE_OBJECT, true, pairs);
        extractLabelScorePairs(jsonBody, SCORE_LABEL_OBJECT, false, pairs);
        if (pairs.isEmpty()) {
            return null;
        }
        LabelScore best = pairs.getFirst();
        for (int i = 1; i < pairs.size(); i++) {
            if (pairs.get(i).score > best.score) {
                best = pairs.get(i);
            }
        }
        return normalizeVendorLabel(best.label);
    }

    private static void extractLabelScorePairs(
            String jsonBody, Pattern pattern, boolean labelFirst, List<LabelScore> out) {
        Matcher m = pattern.matcher(jsonBody);
        while (m.find()) {
            try {
                if (labelFirst) {
                    out.add(new LabelScore(m.group(1), Double.parseDouble(m.group(2))));
                } else {
                    out.add(new LabelScore(m.group(2), Double.parseDouble(m.group(1))));
                }
            } catch (NumberFormatException ignored) {
                // skip malformed fragment
            }
        }
    }

    static String normalizeVendorLabel(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.contains("positive") || "label_2".equals(lower) || "pos".equals(lower)) {
            return "positive";
        }
        if (lower.contains("negative") || "label_0".equals(lower) || "neg".equals(lower)) {
            return "negative";
        }
        if (lower.contains("neutral") || "label_1".equals(lower)) {
            return "neutral";
        }
        return null;
    }

    public record SentimentResult(String label, String source) {}

    record SentimentApiResponse(boolean ok, String reason, String body) {}

    private record LabelScore(String label, double score) {}

    private static final class HttpSentimentCaller implements Function<String, SentimentApiResponse> {
        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(AppConfig.HF_API_TIMEOUT_SECONDS))
                .build();

        @Override
        public SentimentApiResponse apply(String jsonBody) {
            String endpoint = AppConfig.getHfSentimentInferenceEndpoint();
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(AppConfig.HF_API_TIMEOUT_SECONDS))
                    .header("Authorization", "Bearer " + AppConfig.HF_API_TOKEN)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();
            try {
                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
                String reason = ok ? "ok" : ("HTTP " + response.statusCode());
                return new SentimentApiResponse(ok, reason, response.body());
            } catch (IOException e) {
                return new SentimentApiResponse(false, "network error", "{\"error\":\"Network error\"}");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new SentimentApiResponse(false, "interrupted", "{\"error\":\"Request interrupted\"}");
            }
        }
    }
}
