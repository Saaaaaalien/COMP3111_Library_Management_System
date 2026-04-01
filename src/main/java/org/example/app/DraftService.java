package org.example.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Simple draft persistence for public forms. Stores and loads Properties files
 * under data/drafts/{route}.props. Passwords are intentionally not persisted.
 */
public final class DraftService {

    private static final Path DRAFT_DIR = Paths.get("data", "drafts");

    private DraftService() {}

    public static void saveDraft(String route, Map<String, String> fields) {
        try {
            Files.createDirectories(DRAFT_DIR);
            Path file = DRAFT_DIR.resolve(route + ".props");
            Properties props = new Properties();
            props.putAll(fields);
            try (OutputStream os = Files.newOutputStream(file)) {
                props.store(os, "Draft for " + route);
            }
        } catch (IOException ignored) {
            // Best-effort only; do not disturb user flow on failures
        }
    }

    public static Map<String, String> loadDraft(String route) {
        Path file = DRAFT_DIR.resolve(route + ".props");
        if (!Files.isRegularFile(file)) return Map.of();
        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(file)) {
            props.load(is);
            Map<String, String> result = new HashMap<>();
            for (String name : props.stringPropertyNames()) {
                result.put(name, props.getProperty(name));
            }
            return result;
        } catch (IOException ignored) {
            return Map.of();
        }
    }

    public static void clearDraft(String route) {
        try {
            Path file = DRAFT_DIR.resolve(route + ".props");
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }
}
