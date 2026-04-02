package org.example.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.example.db.BookDao;
import org.example.db.BorrowDao;
import org.example.db.UserDao;
import org.example.domain.Book;
import org.example.domain.Borrow;
import org.example.domain.Role;
import org.example.domain.User;
import org.example.ui.PdfReaderScreen;

/**
 * Persists last screen (and optional PDF-reader context) to {@code data/session.json}
 * for crash recovery and the Crash Test control.
 */
public final class SessionService {

    private static final Path SESSION_FILE = Paths.get("data", "session.json");
    private static final Pattern ROUTE_P = Pattern.compile("\"route\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern USER_P = Pattern.compile("\"userId\"\\s*:\\s*(-?\\d+)");
    private static final Pattern BORROW_ID_P = Pattern.compile("\"borrowId\"\\s*:\\s*(-?\\d+)");
    private static final Pattern BOOK_ID_P = Pattern.compile("\"bookId\"\\s*:\\s*(-?\\d+)");
    private static final Pattern PAGE_INDEX_P = Pattern.compile("\"pageIndex0\"\\s*:\\s*(-?\\d+)");
    private static final Pattern ZOOM_P = Pattern.compile("\"zoomPercent\"\\s*:\\s*(-?\\d+)");

    private SessionService() {}

    public record Snapshot(String route, long userId,
                             Long borrowId,
                             Long bookId,
                             Integer pageIndex0,
                             Integer zoomPercent) {}
    public record RestoreResult(boolean restored, boolean fallbackToWelcome, String message) {}

    public static void save(String route, long userId) {
        save(route, userId, null, null, null);
    }

    /**
     * @param parentRoute screen to return to after closing the PDF reader (e.g. MY_BORROWS)
     */
    public static void save(String route, long userId, Long borrowId, Long bookId, String parentRoute) {
        try {
            Path dir = SESSION_FILE.getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
            StringBuilder sb = new StringBuilder();
            sb.append("{\"route\":\"").append(escapeJson(route)).append("\",\"userId\":").append(userId);
            if (borrowId != null) {
                sb.append(",\"borrowId\":").append(borrowId);
            }
            if (bookId != null) {
                sb.append(",\"bookId\":").append(bookId);
            }
            if (parentRoute != null && !parentRoute.isBlank()) {
                sb.append(",\"parentRoute\":\"").append(escapeJson(parentRoute)).append("\"");
            }
            sb.append("}\n");
            Files.writeString(SESSION_FILE, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    /**
     * Saves a PDF reader checkpoint snapshot for crash recovery.
     * Stores only coarse reader state (page + zoom) suitable for restoration.
     */
    public static void saveReaderSession(long userId,
                                          long borrowId,
                                          long bookId,
                                          int pageIndex0,
                                          int zoomPercent) {
        try {
            Path dir = SESSION_FILE.getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
            String json = "{\"route\":\"PDF_READER\",\"userId\":" + userId
                    + ",\"borrowId\":" + borrowId
                    + ",\"bookId\":" + bookId
                    + ",\"pageIndex0\":" + pageIndex0
                    + ",\"zoomPercent\":" + zoomPercent
                    + "}\n";
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
            Long borrowId = null;
            Long bookId = null;
            Integer pageIndex0 = null;
            Integer zoomPercent = null;

            Matcher mb = BORROW_ID_P.matcher(raw);
            if (mb.find()) borrowId = Long.parseLong(mb.group(1));
            Matcher mBk = BOOK_ID_P.matcher(raw);
            if (mBk.find()) bookId = Long.parseLong(mBk.group(1));
            Matcher mp = PAGE_INDEX_P.matcher(raw);
            if (mp.find()) pageIndex0 = Integer.parseInt(mp.group(1));
            Matcher mz = ZOOM_P.matcher(raw);
            if (mz.find()) zoomPercent = Integer.parseInt(mz.group(1));

            return Optional.of(new Snapshot(
                    mr.group(1),
                    Long.parseLong(mu.group(1)),
                    borrowId,
                    bookId,
                    pageIndex0,
                    zoomPercent
            ));
            String route = mr.group(1);
            long userId = Long.parseLong(mu.group(1));

            Long borrowId = null;
            Matcher mb = BORROW_P.matcher(raw);
            if (mb.find()) {
                borrowId = Long.parseLong(mb.group(1));
            }
            Long bookId = null;
            Matcher mk = BOOK_P.matcher(raw);
            if (mk.find()) {
                bookId = Long.parseLong(mk.group(1));
            }
            String parentRoute = null;
            Matcher mp = PARENT_P.matcher(raw);
            if (mp.find()) {
                parentRoute = mp.group(1);
            }
            return Optional.of(new Snapshot(route, userId, borrowId, bookId, parentRoute));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Restores last session route. On failure with a known user, opens portal home; on total failure, welcome.
     */
    public static RestoreResult tryRestore(Navigator navigator) {
        Optional<Snapshot> snap = load();
        if (snap.isEmpty()) {
            navigator.showWelcome();
            return new RestoreResult(RestoreOutcome.NONE, "");
        }
        Snapshot snapshot = snap.get();
        try {
            if (isPublicRoute(snapshot.route())) {
                applyPublicRoute(navigator, snapshot.route());
                return new RestoreResult(RestoreOutcome.SUCCESS, "Your last session was restored successfully.");
            }
            if (snapshot.userId() <= 0) {
                clear();
                navigator.showWelcome();
                return new RestoreResult(RestoreOutcome.FAILURE,
                        "Session could not be restored (missing account). The welcome screen was opened.");
            }
            Optional<User> u = UserDao.findById(snapshot.userId());
            if (u.isEmpty()) {
                clear();
                navigator.showWelcome();
                return new RestoreResult(RestoreOutcome.FAILURE,
                        "Session could not be restored (account no longer exists). The welcome screen was opened.");
            }
            User user = u.get();
            boolean restored = applyProtectedRoute(navigator, snapshot, user);
            if (!restored) {
                navigator.showWelcome();
                return new RestoreResult(false, true, "Session restore failed: invalid route for user role.");
            }
            navigator.showHomeForUser(user);
            return new RestoreResult(RestoreOutcome.PARTIAL,
                    "The previous screen could not be restored. Your portal home was opened instead.");
        } catch (Exception e) {
            clear();
            navigator.showWelcome();
            return new RestoreResult(RestoreOutcome.FAILURE,
                    "Session restore failed: " + (e.getMessage() != null ? e.getMessage() : "unexpected error")
                            + ". The welcome screen was opened.");
        }
    }

    /**
     * Abrupt exit without JVM shutdown hooks (simulates kill / crash). Session file must already be up to date.
     */
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

    private static boolean applyProtectedRoute(Navigator navigator, Snapshot snapshot, User user) {
        String route = snapshot.route();
        Role role = user.getRole();
        String route = snap.route();
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
            case "PDF_READER" -> {
                if (role != Role.STUDENT && role != Role.STAFF) {
                    return false;
                }
                Long bid = snap.borrowId();
                Long bookId = snap.bookId();
                if (bid == null || bookId == null) {
                    return false;
                }
                try {
                    Optional<Borrow> borOpt = BorrowDao.findById(bid);
                    if (borOpt.isEmpty()) {
                        return false;
                    }
                    Borrow b = borOpt.get();
                    if (b.getBorrowerUserId() != user.getId() || b.getBookId() != bookId) {
                        return false;
                    }
                    if (b.getReturnedAt() != null && !b.getReturnedAt().isEmpty()) {
                        return false;
                    }
                    Optional<Book> bookOpt = BookDao.findById(bookId);
                    if (bookOpt.isEmpty()) {
                        return false;
                    }
                    Book book = bookOpt.get();
                    String parent = snap.parentRoute() != null ? snap.parentRoute() : "MY_BORROWS";
                    if ("AVAILABLE_BOOKS".equals(parent)) {
                        navigator.showAvailableBooks(user);
                    } else {
                        navigator.showMyBorrowedBooks(user);
                    }
                    PdfReaderScreen.open(navigator, user, bid, bookId, book.getTitle(), book.getFilePath(), parent);
                } catch (Exception e) {
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
            case "LIBRARIAN_MANAGE_USERS" -> {
                if (role == Role.LIBRARIAN) {
                    navigator.showLibrarianManageUsers(user);
                } else {
                    return false;
                }
            }
            case "LIBRARIAN_PROFILE" -> {
                if (role == Role.LIBRARIAN) {
                    navigator.showLibrarianProfile(user);
                } else {
                    return false;
                }
            }
            case "LIBRARIAN_BORROW_RECORDS" -> {
                if (role == Role.LIBRARIAN) {
                    navigator.showLibrarianBorrowRecords(user);
                } else {
                    return false;
                }
            }
            case "LIBRARIAN_NOTIFICATIONS" -> {
                if (role == Role.LIBRARIAN) {
                    navigator.showLibrarianNotifications(user);
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
            case "PDF_READER" -> {
                if (role == Role.STUDENT || role == Role.STAFF) {
                    if (snapshot.borrowId() == null || snapshot.bookId() == null) {
                        return false;
                    }
                    navigator.showPdfReader(
                            user,
                            snapshot.borrowId(),
                            snapshot.bookId(),
                            snapshot.pageIndex0(),
                            snapshot.zoomPercent()
                    );
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
