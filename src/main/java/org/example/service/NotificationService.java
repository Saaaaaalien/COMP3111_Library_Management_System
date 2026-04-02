package org.example.service;

import org.example.db.BorrowDao;
import org.example.db.NotificationDao;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

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

    private NotificationService() {}

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
            5,
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
}
