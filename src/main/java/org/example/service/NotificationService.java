package org.example.service;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.example.db.BorrowDao;
import org.example.db.NotificationDao;
import org.example.db.PendingDao;
import org.example.db.UserDao;
import org.example.domain.AppNotification;
import org.example.domain.PendingBook;
import org.example.domain.Role;
import org.example.domain.User;

/**
 * Creates in-app notifications (due reminders, catalog removal, author workflow, announcements).
 */
public final class NotificationService {

    public static final String CAT_DUE_REMINDER = "DUE_REMINDER";
    public static final String CAT_BOOK_REMOVED = "BOOK_REMOVED";
    public static final String CAT_ANNOUNCEMENT = "ANNOUNCEMENT";
    public static final String CAT_BORROW_EVENT = "BORROW_EVENT";
    public static final String CAT_RETURN_EVENT = "RETURN_EVENT";
    public static final String CAT_AUTHOR_APPROVED = "AUTHOR_APPROVED";
    public static final String CAT_AUTHOR_REJECTED = "AUTHOR_REJECTED";
    /** Librarian removed the author's book from the catalog. */
    public static final String CAT_AUTHOR_BOOK_REMOVED = "AUTHOR_BOOK_REMOVED";
    /** Author replied to a user review. */
    public static final String CAT_AUTHOR_REVIEW_REPLY = "AUTHOR_REVIEW_REPLY";
    /** Author flagged review and receives confirmation. */
    public static final String CAT_AUTHOR_REVIEW_FLAGGED = "AUTHOR_REVIEW_FLAGGED";

    private NotificationService() {}

    /** True for urgent items that should be visually highlighted (rejection, removal, high numeric priority). */
    public static boolean isUrgentHighlight(AppNotification n) {
        if (n == null) return false;
        if (n.getPriority() >= 8) return true;
        String c = n.getCategory();
        return CAT_AUTHOR_REJECTED.equals(c)
                || CAT_BOOK_REMOVED.equals(c)
                || CAT_AUTHOR_BOOK_REMOVED.equals(c);
    }

    /**
     * Inserts due-date reminders for borrows due in 3, 1, or 0 days (deduped per borrow and day bucket).
     */
    public static void syncDueReminders() throws SQLException {
        var zone = ZoneId.systemDefault();
        var today = Instant.now().atZone(zone).toLocalDate();
        String now = Instant.now().toString();
        for (BorrowDao.ActiveBorrowDueRow row : BorrowDao.findAllActiveWithDue()) {
            if (row.dueAt() == null || row.dueAt().isBlank()) {
                continue;
            }
            LocalDate dueDay;
            try {
                dueDay = Instant.parse(row.dueAt()).atZone(zone).toLocalDate();
            } catch (Exception e) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(today, dueDay);
            if (days != 0 && days != 1 && days != 3) {
                continue;
            }
            String bucket = days + "d";
            String dedupe = "DUE:" + row.borrowId() + ":" + bucket + ":" + today;
            int priority = days == 0 ? 3 : (days == 1 ? 2 : 1);
            String title = days == 0 ? "Due today" : (days == 1 ? "Due tomorrow" : "Due in 3 days");
            String body = "\"" + row.bookTitle() + "\" is due on " + dueDay + ".";
            NotificationDao.insertDeduped(
                row.borrowerUserId(),
                CAT_DUE_REMINDER,
                title,
                body,
                now,
                priority,
                dedupe
            );
        }
    }

    public static void notifyBorrowerBookRemovedFromCatalog(long borrowerUserId, String bookTitle) throws SQLException {
        NotificationDao.insert(
            borrowerUserId,
            CAT_BOOK_REMOVED,
            "Book removed from catalog",
            "The library removed \"" + bookTitle + "\" from the catalog. If you had it borrowed, the loan has been closed.",
            Instant.now().toString(),
            9,
            null
        );
    }

    public static void notifyAuthorBookRemovedByLibrarian(long authorUserId, String bookTitle) throws SQLException {
        NotificationDao.insert(
            authorUserId,
            CAT_AUTHOR_BOOK_REMOVED,
            "Book removed by librarian",
            "The library removed your book \"" + bookTitle + "\" from the catalog.",
            Instant.now().toString(),
            9,
            null
        );
    }

    public static void notifyBorrowSuccess(long borrowerUserId, String bookTitle, String dueAt) throws SQLException {
        String body = "You borrowed \"" + bookTitle + "\" successfully.";
        if (dueAt != null && !dueAt.isBlank()) {
            body += "\nDue date: " + dueAt;
        }
        NotificationDao.insert(
                borrowerUserId,
                CAT_BORROW_EVENT,
                "Borrow confirmed",
                body,
                Instant.now().toString(),
                1,
                null
        );
    }

    public static void notifyReturnSuccess(long borrowerUserId, String bookTitle, boolean autoReturn) throws SQLException {
        String title = autoReturn ? "Book auto-returned" : "Return confirmed";
        String body = autoReturn
                ? "Your borrow for \"" + bookTitle + "\" expired and was auto-returned."
                : "You returned \"" + bookTitle + "\" successfully.";
        NotificationDao.insert(
                borrowerUserId,
                CAT_RETURN_EVENT,
                title,
                body,
                Instant.now().toString(),
                autoReturn ? 3 : 1,
                null
        );
    }

    public static void notifyAuthorSubmissionRejected(long authorUserId, String title, String reviewNotes) throws SQLException {
        String body = "Your submission \"" + title + "\" was rejected.";
        if (reviewNotes != null && !reviewNotes.isBlank()) {
            body += "\nNotes: " + reviewNotes;
        }
        NotificationDao.insert(
            authorUserId,
            CAT_AUTHOR_REJECTED,
            "Submission rejected",
            body,
            Instant.now().toString(),
            10,
            null
        );
    }

    public static void notifyAuthorSubmissionApproved(long authorUserId, String title) throws SQLException {
        NotificationDao.insert(
            authorUserId,
            CAT_AUTHOR_APPROVED,
            "Submission approved",
            "Your book \"" + title + "\" was approved and is now in the catalog.",
            Instant.now().toString(),
            2,
            null
        );
    }

    /**
     * Demo announcement to all student/staff users (run once with dedupe per user).
     */
    public static void seedWelcomeAnnouncementIfNeeded(long userId) throws SQLException {
        String dedupe = "WELCOME_ANN:v1";
        NotificationDao.insertDeduped(
            userId,
            CAT_ANNOUNCEMENT,
            "Welcome to the library",
            "You can manage your profile, view notifications, and read borrowed PDFs from My Borrowed Books.",
            Instant.now().toString(),
            0,
            dedupe
        );
    }

    // ── Librarian-targeted notifications ─────────────────────────────────────

    /** Category: a new book submission is awaiting librarian approval. */
    public static final String CAT_NEW_SUBMISSION  = "NEW_SUBMISSION";
    /** Category: a new user account was created (student, staff, or author). */
    public static final String CAT_USER_REGISTERED = "USER_REGISTERED";
    /** Category: an active borrow is overdue – librarian awareness alert. */
    public static final String CAT_OVERDUE_BORROW  = "OVERDUE_BORROW";

    /**
     * Sends a notification to every librarian account.
     * Uses INSERT OR IGNORE so the same event is never duplicated across refreshes.
     * Fetches the librarian list itself – use only when a single notification is sent
     * (e.g. from event handlers).  For batch/sync loops prefer the overload that
     * accepts a pre-fetched list to avoid repeated DB queries.
     */
    private static void notifyAllLibrarians(String category, String title,
                                            String body, int priority,
                                            String dedupeKeyBase) throws SQLException {
        notifyAllLibrarians(UserDao.findAllByRole(Role.LIBRARIAN), category, title, body, priority, dedupeKeyBase);
    }

    /**
     * Low-level overload used by sync methods: accepts a pre-fetched librarian list
     * so the caller can avoid O(N) repeated queries inside loops.
     */
    private static void notifyAllLibrarians(List<User> librarians, String category, String title,
                                            String body, int priority,
                                            String dedupeKeyBase) throws SQLException {
        String now = Instant.now().toString();
        for (User lib : librarians) {
            String dedupe = dedupeKeyBase == null ? null : dedupeKeyBase + ":" + lib.getId();
            NotificationDao.insertOrIgnoreDeduped(lib.getId(), category, title, body, now, priority, dedupe);
        }
    }

    /**
     * Called immediately after a book submission is persisted.
     * Sends a "New book submission" notification to all librarians (deduped by submission id).
     */
    public static void notifyLibrariansNewSubmission(long submissionId, String bookTitle,
                                                     String authorName) throws SQLException {
        String title = "New book submission";
        String body  = "\"" + bookTitle + "\" submitted by " + authorName + " is awaiting approval.";
        notifyAllLibrarians(CAT_NEW_SUBMISSION, title, body, 7,
                "NEW_SUB:" + submissionId);
    }

    /**
     * Called immediately after a new user registers.
     * Sends a "New user registered" notification to all librarians (deduped by user id).
     */
    public static void notifyLibrariansUserRegistered(long newUserId, String username,
                                                      String roleName) throws SQLException {
        String title = "New user registered";
        String body  = username + " (" + roleName + ") created a new account.";
        notifyAllLibrarians(CAT_USER_REGISTERED, title, body, 4,
                "USER_REG:" + newUserId);
    }

    /**
     * Seeds "New submission" notifications for every PENDING book that has not yet
     * generated a notification.  Idempotent — uses dedupe keys.
     * Fetches the librarian list once to avoid repeated DB queries per pending book.
     */
    public static void syncPendingSubmissionNotifications() throws SQLException {
        List<PendingBook> pending = PendingDao.findAllPending();
        if (pending.isEmpty()) return;
        List<User> librarians = UserDao.findAllByRole(Role.LIBRARIAN);
        if (librarians.isEmpty()) return;
        for (PendingBook pb : pending) {
            String authorName = pb.getAuthorFullName() != null ? pb.getAuthorFullName() : "Unknown author";
            String title = "New book submission";
            String body  = "\"" + pb.getTitle() + "\" submitted by " + authorName + " is awaiting approval.";
            notifyAllLibrarians(librarians, CAT_NEW_SUBMISSION, title, body, 7,
                    "NEW_SUB:" + pb.getId());
        }
    }

    /**
     * Seeds "Overdue borrow" notifications for all currently-overdue active borrows.
     * Deduped per borrow id + day so each new calendar day generates at most one alert.
     * Fetches the librarian list once to avoid repeated DB queries per overdue borrow.
     */
    public static void syncOverdueBorrowNotificationsForLibrarians() throws SQLException {
        List<BorrowDao.ActiveBorrowDueRow> rows = BorrowDao.findAllActiveWithDue();
        if (rows.isEmpty()) return;
        List<User> librarians = UserDao.findAllByRole(Role.LIBRARIAN);
        if (librarians.isEmpty()) return;
        String nowIso = Instant.now().toString();
        String today  = nowIso.substring(0, 10); // YYYY-MM-DD
        for (BorrowDao.ActiveBorrowDueRow row : rows) {
            if (row.dueAt() == null || row.dueAt().isBlank()) continue;
            if (row.dueAt().compareTo(nowIso) >= 0) continue; // not yet overdue
            String dedupeBase = "OVERDUE:" + row.borrowId() + ":" + today;
            String title = "Overdue borrow";
            String body  = "\"" + row.bookTitle() + "\" is overdue (due " + row.dueAt().substring(0, 10) + ").";
            notifyAllLibrarians(librarians, CAT_OVERDUE_BORROW, title, body, 8, dedupeBase);
        }
    }
}
