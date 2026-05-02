package org.example.service;

import org.example.domain.AuthorStatsDashboardPrefs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Persists {@link AuthorStatsDashboardPrefs} per author under {@code data/author_stats_prefs_{userId}.properties}.
 */
public final class AuthorStatsPrefsStore {

    private static final String DATA_DIR = "data";
    private static final String FILE_PREFIX = "author_stats_prefs_";
    private static final String FILE_SUFFIX = ".properties";

    private AuthorStatsPrefsStore() {}

    public static Path pathFor(long authorUserId) {
        return Paths.get(DATA_DIR).resolve(FILE_PREFIX + authorUserId + FILE_SUFFIX);
    }

    public static AuthorStatsDashboardPrefs load(long authorUserId) throws IOException {
        Path path = pathFor(authorUserId);
        if (!Files.isRegularFile(path)) {
            return AuthorStatsDashboardPrefs.ALL_VISIBLE;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            p.load(in);
        }
        return AuthorStatsDashboardPrefs.fromProperties(p);
    }

    public static void save(long authorUserId, AuthorStatsDashboardPrefs prefs) throws IOException {
        Path path = pathFor(authorUserId);
        Path parent = path.getParent();
        if (parent != null && !Files.isDirectory(parent)) {
            Files.createDirectories(parent);
        }
        Properties p = new Properties();
        prefs.storeInto(p);
        try (OutputStream out = Files.newOutputStream(path)) {
            p.store(out, "Author stats dashboard visibility");
        }
    }
}
