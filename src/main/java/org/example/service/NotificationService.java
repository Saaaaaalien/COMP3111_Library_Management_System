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
    private static final int URGENT_PRIORITY = 8;

    public static final String CAT_DUE_REMINDER = "DUE_REMINDER";
    public static final String CAT_BOOK_REMOVED = "BOOK_REMOVED";
    public static final String CAT_ANNOUNCEMENT = "ANNOUNCEMENT";
    public static final String CAT_BORROW_EVENT = "BORROW_EVENT";
    public static final String CAT_RETURN_EVENT = "RETURN_EVENT";
    public static final String CAT_ACCOUNT_UPDATED = "ACCOUNT_UPDATED";
    public static final String CAT_ACCOUNT_STATUS = "ACCOUNT_STATUS";
    public static final String CAT_AUTHOR_APPROVED = "AUTHOR_APPROVED";
    public static final String CAT_AUTHOR_REJECTED = "AUTHOR_REJECTED";
    /** Librarian removed the author's book from the catalog. */
    public static final String CAT_AUTHOR_BOOK_REMOVED = "AUTHOR_BOOK_REMOVED";
    /** Librarian updated details of the author's published book. */
    public static final String CAT_AUTHOR_BOOK_UPDATED = "AUTHOR_BOOK_UPDATED";
    /** Author replied to a user review. */
    public static final String CAT_AUTHOR_REVIEW_REPLY = "AUTHOR_REVIEW_REPLY";
    /** Author flagged review and receives confirmation. */
    public static final String CAT_AUTHOR_REVIEW_FLAGGED = "AUTHOR_REVIEW_FLAGGED";
    /** A reader posted a new review for the author's book. */
    public static final String CAT_AUTHOR_NEW_REVIEW = "AUTHOR_NEW_REVIEW";
    /** Librarian feed: user borrowed a book. */
    public static final String CAT_LIB_BORROW_ACTIVITY = "LIB_BORROW_ACTIVITY";
    /** Librarian feed: user returned (or auto-returned) a book. */
    public static final String CAT_LIB_RETURN_ACTIVITY = "LIB_RETURN_ACTIVITY";
    /** Librarian feed: user updated account/profile details. */
    public static final String CAT_LIB_USER_PROFILE_UPDATED = "LIB_USER_PROFILE_UPDATED";
    /** Student/staff: book request lifecycle (approved, rejected, processed). */
    public static final String CAT_BOOK_REQUEST = "BOOK_REQUEST";

    private NotificationService() {}

    /** True for urgent items that should be visually highlighted (rejection, removal, high numeric priority). */
    public static boolean isUrgentHighlight(AppNotification n) {
        if (n == null) return false;
        if (n.getPriority() >= URGENT_PRIORITY) return true;
        String c = n.getCategory();
        return CAT_AUTHOR_REJECTED.equals(c)
                || CAT_BOOK_REMOVED.equals(c)
            || CAT_AUTHOR_BOOK_REMOVED.equals(c)
            || CAT_ACCOUNT_STATUS.equals(c);
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
            int priority = days == 0 ? URGENT_PRIORITY : (days == 1 ? 2 : 1);
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

    public static void notifyAuthorBookUpdatedByLibrarian(long authorUserId, String bookTitle) throws SQLException {
        NotificationDao.insert(
            authorUserId,
            CAT_AUTHOR_BOOK_UPDATED,
            "Book details updated by librarian",
            "A librarian updated details for your book \"" + bookTitle + "\".",
            Instant.now().toString(),
            3,
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

    /**
     * Notifies the borrower that a return completed (manual or automatic at due time).
     *
     * @param dueAtIso optional due instant (ISO-8601) for auto-return copy; ignored for manual returns
     */
    public static void notifyReturnSuccess(long borrowerUserId, String bookTitle, boolean autoReturn, long borrowId,
                                          String dueAtIso) throws SQLException {
        String title = autoReturn ? "Book auto-returned (due date reached)" : "Return confirmed";
        String dueLine = "";
        if (autoReturn && dueAtIso != null && !dueAtIso.isBlank()) {
            try {
                LocalDate dueDay = Instant.parse(dueAtIso).atZone(ZoneId.systemDefault()).toLocalDate();
                dueLine = " Original due date: " + dueDay + ".";
            } catch (Exception ignored) {
                dueLine = " Original due: " + dueAtIso.substring(0, Math.min(10, dueAtIso.length())) + ".";
            }
        }
        String body = autoReturn
                ? "Your loan for \"" + bookTitle + "\" reached its due time and was closed automatically."
                        + " The copy is available in the catalog again; you can borrow it if it is still listed as available."
                        + dueLine
                        + " (Duplicate auto-return notices for the same loan are suppressed.)"
                : "You returned \"" + bookTitle + "\" successfully.";
        // One notification per loan per return path; prevents spam if sync runs repeatedly the same day.
        String dedupe = "RETURN_USER:" + borrowerUserId + ":BORROW:" + borrowId + ":" + (autoReturn ? "AUTO" : "MANUAL");
        String now = Instant.now().toString();
        NotificationDao.insertOrIgnoreDeduped(
                borrowerUserId,
                CAT_RETURN_EVENT,
                title,
                body,
                now,
                autoReturn ? URGENT_PRIORITY : 1,
                dedupe
        );
    }

    /** Notifies a student/staff user about a book request status update from a librarian. */
    public static void notifyBookRequestUpdate(long userId, String title, String body) throws SQLException {
        NotificationDao.insert(
                userId,
                CAT_BOOK_REQUEST,
                title,
                body,
                Instant.now().toString(),
                6,
                null
        );
    }

            public static void notifyUserAccountUpdatedByLibrarian(long userId, String details) throws SQLException {
            String body = (details == null || details.isBlank())
                ? "A librarian updated your account details."
                : details;
            NotificationDao.insert(
                userId,
                CAT_ACCOUNT_UPDATED,
                "Account updated by librarian",
                body,
                Instant.now().toString(),
                5,
                null
            );
            }

            public static void notifyUserAccountStatusChangedByLibrarian(long userId, boolean active) throws SQLException {
            String title = active ? "Account reactivated" : "Account deactivated";
            String body = active
                ? "A librarian reactivated your account."
                : "A librarian deactivated your account. You may be unable to sign in until reactivated.";
            NotificationDao.insert(
                userId,
                CAT_ACCOUNT_STATUS,
                title,
                body,
                Instant.now().toString(),
                active ? 5 : 10,
                null
            );
            }

            public static void notifyLibrariansBorrowActivity(long borrowId, long borrowerUserId,
                                      String bookTitle, String dueAt) throws SQLException {
            String actor = "User #" + borrowerUserId;
            String roleName = "USER";
            var borrower = UserDao.findById(borrowerUserId);
            if (borrower.isPresent()) {
                actor = borrower.get().getUsername();
                roleName = borrower.get().getRole().name();
            }
            String body = actor + " (" + roleName + ") borrowed \"" + bookTitle + "\"."
                + (dueAt != null && !dueAt.isBlank() ? " Due: " + dueAt : "");
            notifyAllLibrarians(
                CAT_LIB_BORROW_ACTIVITY,
                "Borrow activity",
                body,
                4,
                "LIB_BORROW:" + borrowId
            );
            }

            public static void notifyLibrariansReturnActivity(long borrowId, long borrowerUserId,
                                      String bookTitle, boolean autoReturn) throws SQLException {
            String actor = "User #" + borrowerUserId;
            String roleName = "USER";
            var borrower = UserDao.findById(borrowerUserId);
            if (borrower.isPresent()) {
                actor = borrower.get().getUsername();
                roleName = borrower.get().getRole().name();
            }
            String body = autoReturn
                ? actor + " (" + roleName + ") had \"" + bookTitle + "\" auto-returned after due date."
                : actor + " (" + roleName + ") returned \"" + bookTitle + "\".";
            notifyAllLibrarians(
                CAT_LIB_RETURN_ACTIVITY,
                autoReturn ? "Auto-return activity" : "Return activity",
                body,
                autoReturn ? URGENT_PRIORITY : 4,
                "LIB_RETURN:" + borrowId + ":" + (autoReturn ? "AUTO" : "MANUAL")
            );
            }

            public static void notifyLibrariansUserProfileUpdated(long userId, String username, String roleName) throws SQLException {
            String who = (username == null || username.isBlank()) ? ("User #" + userId) : username;
            String role = (roleName == null || roleName.isBlank()) ? "USER" : roleName;
            String body = who + " (" + role + ") updated account/profile details.";
            notifyAllLibrarians(
                CAT_LIB_USER_PROFILE_UPDATED,
                "User account updated",
                body,
                3,
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
        notifyAllLibrarians(CAT_NEW_SUBMISSION, title, body, URGENT_PRIORITY,
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
            notifyAllLibrarians(librarians, CAT_NEW_SUBMISSION, title, body, URGENT_PRIORITY,
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
            notifyAllLibrarians(librarians, CAT_OVERDUE_BORROW, title, body, URGENT_PRIORITY, dedupeBase);
        }
    }
}
