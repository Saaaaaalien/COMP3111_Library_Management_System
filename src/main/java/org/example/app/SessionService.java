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
    public record RestoreResult(boolean restored, boolean fallbackToWelcome, String message) {}

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
     * Restores last session route. Falls back to welcome when restore is impossible.
     */
    public static RestoreResult tryRestore(Navigator navigator) {
        Optional<Snapshot> snap = load();
        if (snap.isEmpty()) {
            navigator.showWelcome();
            return new RestoreResult(false, false, "No previous session found.");
        }
        Snapshot snapshot = snap.get();
        try {
            if (isPublicRoute(snapshot.route())) {
                applyPublicRoute(navigator, snapshot.route());
                return new RestoreResult(true, false, "Last session restored successfully.");
            }
            if (snapshot.userId() <= 0) {
                clear();
                navigator.showWelcome();
                return new RestoreResult(false, true, "Session restore failed: missing user.");
            }
            Optional<User> u = UserDao.findById(snapshot.userId());
            if (u.isEmpty()) {
                clear();
                navigator.showWelcome();
                return new RestoreResult(false, true, "Session restore failed: user no longer exists.");
            }
            User user = u.get();
            boolean restored = applyProtectedRoute(navigator, snapshot.route(), user);
            if (!restored) {
                navigator.showWelcome();
                return new RestoreResult(false, true, "Session restore failed: invalid route for user role.");
            }
            return new RestoreResult(true, false, "Last session restored successfully.");
        } catch (Exception e) {
            navigator.showWelcome();
            return new RestoreResult(false, true, "Session restore failed due to an unexpected error.");
        }
    }

    public static void simulateCrash() {
        Runtime.getRuntime().halt(1);
    }

    private static boolean isPublicRoute(String route) {
        return switch (route) {
            case "WELCOME",
                 "STUDENT_PORTAL", "STUDENT_LOGIN", "STUDENT_REGISTER",
                 "AUTHOR_PORTAL", "AUTHOR_LOGIN", "AUTHOR_REGISTER",
                 "LIBRARIAN_PORTAL", "LIBRARIAN_LOGIN", "LIBRARIAN_REGISTER" -> true;
            default -> false;
        };
    }

    private static void applyPublicRoute(Navigator navigator, String route) {
        switch (route) {
            case "WELCOME" -> navigator.showWelcome();
            case "STUDENT_PORTAL" -> navigator.showStudentStaffPortal();
            case "STUDENT_LOGIN" -> navigator.showStudentStaffLogin();
            case "STUDENT_REGISTER" -> navigator.showStudentStaffRegister();
            case "AUTHOR_PORTAL" -> navigator.showAuthorPortal();
            case "AUTHOR_LOGIN" -> navigator.showAuthorLogin();
            case "AUTHOR_REGISTER" -> navigator.showAuthorRegister();
            case "LIBRARIAN_PORTAL" -> navigator.showLibrarianPortal();
            case "LIBRARIAN_LOGIN" -> navigator.showLibrarianLogin();
            case "LIBRARIAN_REGISTER" -> navigator.showLibrarianRegister();
            default -> navigator.showWelcome();
        }
    }

    private static boolean applyProtectedRoute(Navigator navigator, String route, User user) {
        Role role = user.getRole();
        switch (route) {
            case "AVAILABLE_BOOKS" -> {
                if (role == Role.STUDENT || role == Role.STAFF) {
                    navigator.showAvailableBooks(user);
                } else {
                    return false;
                }
            }
            case "MY_BORROWS" -> {
                if (role == Role.STUDENT || role == Role.STAFF) {
                    navigator.showMyBorrowedBooks(user);
                } else {
                    return false;
                }
            }
            case "PROFILE_STUDENT" -> {
                if (role == Role.STUDENT || role == Role.STAFF) {
                    navigator.showStudentStaffProfile(user);
                } else {
                    return false;
                }
            }
            case "NOTIFICATIONS_STUDENT" -> {
                if (role == Role.STUDENT || role == Role.STAFF) {
                    navigator.showStudentStaffNotifications(user);
                } else {
                    return false;
                }
            }
            case "AUTHOR_DASH" -> {
                if (role == Role.AUTHOR) {
                    navigator.showAuthorDashboard(user);
                } else {
                    return false;
                }
            }
            case "AUTHOR_PUBLISH" -> {
                if (role == Role.AUTHOR) {
                    navigator.showPublishBook(user);
                } else {
                    return false;
                }
            }
            case "AUTHOR_PUBLISHED" -> {
                if (role == Role.AUTHOR) {
                    navigator.showAuthorPublishedBooks(user);
                } else {
                    return false;
                }
            }
            case "AUTHOR_PROFILE" -> {
                if (role == Role.AUTHOR) {
                    navigator.showAuthorProfile(user);
                } else {
                    return false;
                }
            }
            case "AUTHOR_NOTIFICATIONS" -> {
                if (role == Role.AUTHOR) {
                    navigator.showAuthorNotifications(user);
                } else {
                    return false;
                }
            }
            case "LIBRARIAN_APPROVAL" -> {
                if (role == Role.LIBRARIAN) {
                    navigator.showLibrarianApproval(user);
                } else {
                    return false;
                }
            }
            case "LIBRARIAN_CATALOG" -> {
                if (role == Role.LIBRARIAN) {
                    navigator.showLibrarianCatalog(user);
                } else {
                    return false;
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
