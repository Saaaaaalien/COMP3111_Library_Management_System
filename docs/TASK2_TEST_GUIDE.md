# Task 2 – Author Portal: Test Guide

Use this checklist to verify all Task 2 features. **Reset the database first** so you start with no data.

---

## Reset the database (start clean)

1. **Close the main application** if it is running (so the DB file is not locked).
2. In IntelliJ: right-click **`org.example.db.ResetDatabase`** → **Run 'ResetDatabase.main()'**
   - Or from project root:  
     `mvn exec:java -Dexec.mainClass="org.example.db.ResetDatabase"`
3. Console should say: *"Database deleted"* or *"All saved data has been cleared."*
4. Start the main app again (**Run 'Main'**). A fresh empty DB will be created.

---

## 2.1 Author Registration

| Step | Action | Expected result |
|------|--------|-----------------|
| 1 | Run app → **Author Portal** → **Register** | Registration form with Username, First Name, Last Name, Password, Bio (optional), Register, Back buttons |
| 2 | Leave all fields empty → **Register** | Error: "Username is required." (or specific field error) |
| 3 | Username `a`, First `Jane`, Last `Doe`, Password `short`, Bio `Test bio` → **Register** | Error: Username must be at least 3 characters |
| 4 | Username `author1`, First `Jane`, Last `Doe`, Password `weak` → **Register** | Error: Password must meet strength requirements (min 8 chars, 1 uppercase, 1 number, 1 special) |
| 5 | Password `GoodPass1!` (watch strength meter if implemented) | Strength shows Weak/Medium/Strong as you type |
| 6 | Username `author1`, First `Jane`, Last `Doe`, Password `GoodPass1!`, Bio `I love writing fiction` → **Register** | Success message: "Registration successful! You can now log in." Screen switches to Author Login |
| 7 | **Back** → **Register** again with Username `author2`, First `John`, Last `Smith`, Password `Secure2@`, Bio empty → **Register** | Success; switch to Login |
| 8 | **Back** → **Register** with Username `author1` again (same as existing) | Error: "Username is already taken." |
| 9 | Username `test@user`, First `Test`, Last `User`, Password `TestPass1!` → **Register** | Should accept or reject based on your username validation rules (letters/numbers/underscore only?) |

---

## 2.2 Author Login

| Step | Action | Expected result |
|------|--------|-----------------|
| 1 | From Author Portal → **Login** | Login form with Username, Password fields, Login and Back buttons |
| 2 | Leave both fields empty → **Login** | Error: "Invalid username or password." (generic message for security) |
| 3 | Username `nonexistent`, Password `anypass` → **Login** | Same error: "Invalid username or password." |
| 4 | Username `author1`, wrong password `wrongpass` → **Login** | Same error message |
| 5 | Username `author1`, Password `GoodPass1!` → **Login** | Navigate to **Author Dashboard**; welcome message shows "Welcome, Jane Doe!" |
| 6 | **Logout** → **Login** with Username `author2`, Password `Secure2@` → **Login** | Navigate to Author Dashboard; "Welcome, John Smith!" |
| 7 | **Logout** → **Login** with Username `author1`, wrong password 5 times in a row | After 5th failure: "Account is temporarily locked... Try again after 15 minutes." |
| 8 | Try to login again immediately with correct password | Still locked (same message) |
| 9 | Wait 15 minutes **or** run **ResetDatabase** and re-register `author1`, then test lockout counter | After reset, wrong password once → one failure only, no lock |

---

## 2.3 Publish New Book

| Step | Action | Expected result |
|------|--------|-----------------|
| 1 | Login as `author1` → From Dashboard click **Start Publishing ->** (or card) | Publish Book form with: Title, Author Full Name (pre-filled, read-only), Genre, Description, File chooser button, Submit and Cancel buttons |
| 2 | Verify Author Name field | Shows "Jane Doe" (from registration) and is grayed out (non-editable) |
| 3 | Leave all fields empty → **Submit for Approval** | Error: "Book title is required." |
| 4 | Enter Title only, no genre, no description, no file → **Submit** | Error: "Genre is required." |
| 5 | Enter Title and Genre, no description, no file → **Submit** | Error: "Description is required." |
| 6 | Enter Title, Genre, Description, no file → **Submit** | Error: "Please select a book file to upload." |
| 7 | Click **Choose File** → select a file that's **not** PDF/TXT/DOC/DOCX (e.g., .exe, .zip) | Error: "Invalid file type. Please upload PDF, TXT, or DOC/DOCX files." |
| 8 | Choose a file larger than 10MB (if you have one) | Error: "File size must be less than 10MB." |
| 9 | Enter valid data: Title `My First Book`, Genre `Fiction`, Description `This is a test book.`, choose a valid PDF file → **Submit for Approval** | Success message: "Book submitted successfully! Waiting for librarian approval." |
| 10 | Check the form after success | Fields should clear/reset |
| 11 | Submit another book: Title `Second Book`, Genre `Science Fiction`, Description `Another test`, choose another file → **Submit** | Success again |
| 12 | Try to submit with extremely long Title (500+ chars) | Should truncate or show error |
| 13 | Try to submit with Title containing special characters (`@#$%`) | Should accept or handle gracefully |
| 14 | Try to submit duplicate Title (same as first book) | Should allow |
| 15 | **Cancel** button | Returns to Author Dashboard without saving |

---

## Author Dashboard Verification

| Step | Action | Expected result |
|------|--------|-----------------|
| 1 | After login as `author1`, check dashboard | Welcome message shows correct name |
| 2 | Dashboard should show | Publish New Book card, Logout button |
| 3 | Click **Logout** | Returns to Welcome screen |

---

## Database Verification (Manual)

| Step | Action | Expected result |
|------|--------|-----------------|
| 1 | Check `authors` table in DB | Should have 2 authors: `author1` and `author2` with hashed passwords (not plain text), bio stored correctly |
| 2 | Check `pending_books` table | Should have 2 pending books with status = "PENDING", author_id links to `author1`, file paths stored correctly |
| 3 | Verify file upload directory | Files should exist at the stored paths with unique filenames |

---

## Edge Cases & Security

| Test Case | Expected behavior |
|-----------|-------------------|
| ✅ SQL injection in username field | Should be blocked by parameterized queries |
| ✅ XSS in bio/description | Should be escaped or stored safely |
| ✅ Duplicate submission (click Submit twice quickly) | Should prevent duplicate records |
| ✅ Submit while offline | Graceful error message |
| ✅ Very long bio (10,000+ chars) | Truncate or show error |
| ✅ Unicode characters in fields (中文, español) | Should store and display correctly |
| ✅ Session timeout on publish form | Redirect to login, preserve form data? |
| ✅ Back button after submission | Should not resubmit form |

---

## Quick Smoke Test (Minimal Path)

1. **ResetDatabase** (app closed).
2. Run app → **Author Portal** → **Register**: `testauthor` / `Test` / `Author` / `TestPass1!` / Bio `Testing` → **Register**.
3. **Login**: `testauthor` / `TestPass1!` → **Login** → see Author Dashboard.
4. **Publish New Book**:
   - Title: `Smoke Test Book`
   - Genre: `Fiction`
   - Description: `This is a smoke test.`
   - File: Choose any small PDF
   - **Submit** → Success message.
5. **Logout**.
6. (Optional) Check database to confirm pending book exists.

---

If all steps match the expected results, **Task 2 (Author Portal)** is working end-to-end!
