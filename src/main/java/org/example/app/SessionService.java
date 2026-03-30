package org.example.app;

import org.example.db.UserDao;
import org.example.domain.Role;
import org.example.domain.User;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Persists last screen and user id to {@code data/session.json} for crash/recovery demos.
 */
public final class SessionService {

    private static final Path SESSION_FILE = Paths.get("data", "session.json");
    private static final Pattern ROUTE_P = Pattern.compile("\"route\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern USER_P = Pattern.compile("\"userId\"\\s*:\\s*(-?\\d+)");

    private SessionService() {}

    public record Snapshot(String route, long userId) {}

    public static void save(String route, long userId) {
        try {
            Path dir = SESSION_FILE.getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
            String json = "{\"route\":\"" + route + "\",\"userId\":" + userId + "}\n";
            Files.writeString(SESSION_FILE, json, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static void clear() {
        try {
            Files.deleteIfExists(SESSION_FILE);
        } catch (IOException ignored) {
        }
    }

    public static Optional<Snapshot> load() {
        try {
            if (!Files.isRegularFile(SESSION_FILE)) {
                return Optional.empty();
            }
            String raw = Files.readString(SESSION_FILE, StandardCharsets.UTF_8);
            Matcher mr = ROUTE_P.matcher(raw);
            Matcher mu = USER_P.matcher(raw);
            if (!mr.find() || !mu.find()) {
                return Optional.empty();
            }
            return Optional.of(new Snapshot(mr.group(1), Long.parseLong(mu.group(1))));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Restores session if user still exists; otherwise welcome screen.
     */
    public static void tryRestore(Navigator navigator) {
        Optional<Snapshot> snap = load();
        if (snap.isEmpty() || snap.get().userId() <= 0) {
            navigator.showWelcome();
            return;
        }
        try {
            Optional<User> u = UserDao.findById(snap.get().userId());
            if (u.isEmpty()) {
                clear();
                navigator.showWelcome();
                return;
            }
            User user = u.get();
            applyRoute(navigator, snap.get().route(), user);
        } catch (Exception e) {
            navigator.showWelcome();
        }
    }

    private static void applyRoute(Navigator navigator, String route, User user) {
        Role role = user.getRole();
        switch (route) {
            case "AVAILABLE_BOOKS" -> {
                if (role == Role.STUDENT || role == Role.STAFF) {
                    navigator.showAvailableBooks(user);
                } else {
                    navigator.showWelcome();
                }
            }
            case "MY_BORROWS" -> {
                if (role == Role.STUDENT || role == Role.STAFF) {
                    navigator.showMyBorrowedBooks(user);
                } else {
                    navigator.showWelcome();
                }
            }
            case "PROFILE_STUDENT" -> {
                if (role == Role.STUDENT || role == Role.STAFF) {
                    navigator.showStudentStaffProfile(user);
                } else {
                    navigator.showWelcome();
                }
            }
            case "NOTIFICATIONS_STUDENT" -> {
                if (role == Role.STUDENT || role == Role.STAFF) {
                    navigator.showStudentStaffNotifications(user);
                } else {
                    navigator.showWelcome();
                }
            }
            case "AUTHOR_DASH" -> {
                if (role == Role.AUTHOR) {
                    navigator.showAuthorDashboard(user);
                } else {
                    navigator.showWelcome();
                }
            }
            case "AUTHOR_PUBLISH" -> {
                if (role == Role.AUTHOR) {
                    navigator.showPublishBook(user);
                } else {
                    navigator.showWelcome();
                }
            }
            case "AUTHOR_PUBLISHED" -> {
                if (role == Role.AUTHOR) {
                    navigator.showAuthorPublishedBooks(user);
                } else {
                    navigator.showWelcome();
                }
            }
            case "AUTHOR_PROFILE" -> {
                if (role == Role.AUTHOR) {
                    navigator.showAuthorProfile(user);
                } else {
                    navigator.showWelcome();
                }
            }
            case "AUTHOR_NOTIFICATIONS" -> {
                if (role == Role.AUTHOR) {
                    navigator.showAuthorNotifications(user);
                } else {
                    navigator.showWelcome();
                }
            }
            case "LIBRARIAN_APPROVAL" -> {
                if (role == Role.LIBRARIAN) {
                    navigator.showLibrarianApproval(user);
                } else {
                    navigator.showWelcome();
                }
            }
            case "LIBRARIAN_CATALOG" -> {
                if (role == Role.LIBRARIAN) {
                    navigator.showLibrarianCatalog(user);
                } else {
                    navigator.showWelcome();
                }
            }
            default -> navigator.showWelcome();
        }
    }
}
