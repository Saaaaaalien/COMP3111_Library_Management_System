package org.example.app;

public final class AppConfig {

    private AppConfig() {}

    /** Set {@code -Dapp.devMode=false} to hide Crash Test controls for graduation demos. Default on for coursework. */
    public static final boolean DEV_MODE =
            Boolean.parseBoolean(System.getProperty("app.devMode", "true"));

    public static final String HF_API_TOKEN = env("HF_API_TOKEN", "");
    public static final String HF_MODEL_ID = env("HF_MODEL_ID", "facebook/bart-large-cnn");
    /**
     * Hugging Face Inference API model id for text classification (review sentiment).
     * Override with env {@code HF_SENTIMENT_MODEL_ID}; default is a common 3-label RoBERTa classifier.
     */
    public static final String HF_SENTIMENT_MODEL_ID =
            env("HF_SENTIMENT_MODEL_ID", "cardiffnlp/twitter-roberta-base-sentiment-latest");
    public static final String HF_API_BASE = env("HF_API_BASE", "https://router.huggingface.co/hf-inference");
    public static final int HF_API_TIMEOUT_SECONDS = envInt("HF_API_TIMEOUT_SECONDS", 120);

    public static boolean isHfConfigured() {
        return !HF_API_TOKEN.isBlank();
    }

    public static String getHfInferenceEndpoint() {
        String base = HF_API_BASE.endsWith("/") ? HF_API_BASE.substring(0, HF_API_BASE.length() - 1) : HF_API_BASE;
        return base + "/models/" + HF_MODEL_ID;
    }

    /** Inference endpoint for the sentiment classification model (separate from summarization {@link #HF_MODEL_ID}). */
    public static String getHfSentimentInferenceEndpoint() {
        String base = HF_API_BASE.endsWith("/") ? HF_API_BASE.substring(0, HF_API_BASE.length() - 1) : HF_API_BASE;
        return base + "/models/" + HF_SENTIMENT_MODEL_ID;
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null ? defaultValue : value.trim();
    }

    private static int envInt(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
