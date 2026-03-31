# Master test guide (Phase 2)

This document summarizes manual checks for Phase 2 features. Use `org.example.db.ResetDatabase` to wipe `data/library.db` when you need a clean slate (also clears related JSON under `data/` if present).

## Auto-return and due reminders

1. Borrow a PDF as Student/Staff with a short due date (or wait until after `due_at`).
2. Restart the app or wait for the background timer (about every 3 minutes): overdue loans should close and books become `AVAILABLE`.
3. Log in again: notification board should show deduped due reminders at 3 days, 1 day, and due day (when applicable).

## PDF reader (bookmark + highlights)

1. From **My Borrowed Books**, select an active PDF loan → **Read PDF (selected)**.
2. Change pages; progress is stored in `reading_progress`. Close and reopen: last page restores.
3. Select text in the right-hand extracted-text pane → **Save selection as highlight**; highlights persist in `reading_highlights`.

## Profile (Student/Staff and Author)

- **Student/Staff**: **Profile** from Available Books; change name; optional password change forces re-login via portal.
- **Author**: **Profile** from dashboard; bio + optional password (requires current password); password change logs out to author portal.

## Notifications

- **Student/Staff**: approvals not shown here; see due reminders, book-removal notices, and welcome announcement seed on first login.
- **Author**: approve/reject from librarian creates **AUTHOR_APPROVED** / **AUTHOR_REJECTED** notifications.
- **Librarian**: **Catalog / remove books** removes a title, notifies active borrowers, returns loans, deletes the row.

## Author published / pending screen

- Edit/delete **PENDING** rows; edit/delete **published** only when there is no active borrow on that `book_id`.

## Session recovery + crash test

- Navigate while logged in (session is written to `data/session.json`).
- From Welcome, **Crash test** halts the JVM after flushing session.
- Restart: app attempts restore to the last saved route for that user id.

## Nice-to-haves covered in UI

- **Available Books**: popular picks row, genre + publish date filters, multi-borrow via checkboxes + **Borrow checked books**.
- **Publish book**: optional cover image (JPG/PNG ≤ 2MB); debounced draft autosave in `publish_drafts`.

## Desktop push limitation

There is no OS-level push when the app is closed; reminders appear on next login. System-tray notifications are not implemented.
