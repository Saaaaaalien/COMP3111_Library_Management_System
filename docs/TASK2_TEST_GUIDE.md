# Task 2 – Author Portal: Complete Test Guide

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

| Step | Action                                                                                                                           | Expected result                                                                                          |
|------|----------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| 1    | Run app → **Author Portal** → **Register**                                                                                       | Registration form with Username, First Name, Last Name, Password, Bio (optional), Register, Back buttons |
| 2    | Leave all fields empty → **Register**                                                                                            | Error: "Username is required." (or specific field error)                                                 |
| 3    | Username `a`, First `Jane`, Last `Doe`, Password `short`, Bio `Test bio` → **Register**                                          | Error: Username must be at least 3 characters                                                            |
| 4    | Username `author1`, First `Jane`, Last `Doe`, Password `weak` → **Register**                                                     | Error: Password must meet strength requirements (min 8 chars, 1 uppercase, 1 number, 1 special)          |
| 5    | Password `GoodPass1!` (watch strength meter if implemented)                                                                      | Strength shows Weak/Medium/Strong as you type                                                            |
| 6    | Username `author1`, First `Jane`, Last `Doe`, Password `GoodPass1!`, Bio `I love writing fiction` → **Register**                 | Success message: "Registration successful! You can now log in." Screen switches to Author Login          |
| 7    | **Back** → **Register** again with Username `author2`, First `John`, Last `Smith`, Password `Secure2@`, Bio empty → **Register** | Success; switch to Login                                                                                 |
| 8    | **Back** → **Register** with Username `author1` again (same as existing)                                                         | Error: "Username is already taken."                                                                      |
| 9    | Username `test@user`, First `Test`, Last `User`, Password `TestPass1!` → **Register**                                            | Should accept or reject based on your username validation rules (letters/numbers/underscore only?)       |

---

## 2.2 Author Login

| Step | Action                                                                                            | Expected result                                                                   |
|------|---------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|
| 1    | From Author Portal → **Login**                                                                    | Login form with Username, Password fields, Login and Back buttons                 |
| 2    | Leave both fields empty → **Login**                                                               | Error: "Invalid username or password." (generic message for security)             |
| 3    | Username `nonexistent`, Password `anypass` → **Login**                                            | Same error: "Invalid username or password."                                       |
| 4    | Username `author1`, wrong password `wrongpass` → **Login**                                        | Same error message                                                                |
| 5    | Username `author1`, Password `GoodPass1!` → **Login**                                             | Navigate to **Author Dashboard**; welcome message shows "Welcome, Jane Doe!"      |
| 6    | **Logout** → **Login** with Username `author2`, Password `Secure2@` → **Login**                   | Navigate to Author Dashboard; "Welcome, John Smith!"                              |
| 7    | **Logout** → **Login** with Username `author1`, wrong password 5 times in a row                   | After 5th failure: "Account is temporarily locked... Try again after 15 minutes." |
| 8    | Try to login again immediately with correct password                                              | Still locked (same message)                                                       |
| 9    | Wait 15 minutes **or** run **ResetDatabase** and re-register `author1`, then test lockout counter | After reset, wrong password once → one failure only, no lock                      |

---

## 2.3 Publish New Book

### Form Layout & Display

| Step | Action                                                           | Expected result                                               |
|------|------------------------------------------------------------------|---------------------------------------------------------------|
| 1    | Login as `author1` → From Dashboard click **Start Publishing →** | Publish Book form with proper layout and scrollable if needed |
| 2    | Verify form fills the screen                                     | Content is scrollable when window is resized smaller          |
| 3    | Verify Author Name field                                         | Shows "Jane Doe" (from registration)                          |

### Multi-Genre Selection

| Step | Action                                                       | Expected result                                                                     |
|------|--------------------------------------------------------------|-------------------------------------------------------------------------------------|
| 4    | Check Genre section                                          | List of genres displayed with adequate spacing                                      |
| 5    | Select a single genre (click once)                           | Selected genre appears in green below the list as "1 genre selected: Fiction"       |
| 6    | Hold **Ctrl** (or **Cmd** on Mac) and select multiple genres | Selected genres appear below list as "X genres selected: Fiction, Fantasy, Mystery" |
| 7    | Click **Clear All Selections** button                        | All genre selections cleared; display shows "None selected" in grey italic text     |
| 8    | Select several genres again                                  | Display updates correctly with count and list                                       |

### File Selection

| Step | Action                                      | Expected result                                                           |
|------|---------------------------------------------|---------------------------------------------------------------------------|
| 9    | Click **Choose File** button                | File chooser dialog opens                                                 |
| 10   | Navigate and select a valid PDF file        | File name appears next to button in green; below shows filename with size |
| 11   | Click **Clear** button next to file display | File selection cleared; shows "None selected"                             |
| 12   | Select a file again                         | File selection works again                                                |

### Validation Testing

| Step | Action                                                                                   | Expected result                                                        |
|------|------------------------------------------------------------------------------------------|------------------------------------------------------------------------|
| 13   | Leave all fields empty → **Submit for Approval**                                         | Error: "Book title is required."                                       |
| 14   | Enter Title only, no genre, no description, no file → **Submit**                         | Error: "Please select at least one genre"                              |
| 15   | Enter Title and select genres, no description, no file → **Submit**                      | Error: "Description is required"                                       |
| 16   | Enter Title, select genres, enter Description, no file → **Submit**                      | Error: "Please select a book file"                                     |
| 17   | Click **Choose File** → select a file that's **not** PDF/TXT/DOC/DOCX (e.g., .exe, .zip) | Error: "Invalid file type. Please upload PDF, TXT, or DOC/DOCX files." |
| 18   | Choose a file larger than 10MB (if you have one)                                         | Error: "File size must be less than 10MB"                              |

### Preview & Submission

| Step | Action                                                                                                                                                                          | Expected result                                                                           |
|------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| 19   | Enter valid data: Title `My First Book`, select multiple genres (e.g., Fiction, Fantasy), Description `This is a test book.`, choose a valid PDF file → **Submit for Approval** | **Preview dialog appears** showing all entered information                                |
| 20   | In preview dialog, verify all details                                                                                                                                           | Title, Author, Genres (as list), File name with size, Description all displayed correctly |
| 21   | Click **Cancel** in preview dialog                                                                                                                                              | Dialog closes; form remains with all data intact                                          |
| 22   | Click **Submit for Approval** again → In preview dialog click **Yes, Submit**                                                                                                   | Success message: "Book submitted successfully! Waiting for librarian approval."           |
| 23   | Check form after success                                                                                                                                                        | All fields cleared: Title empty, no genres selected, description empty, file cleared      |
| 24   | Submit another book: Title `Second Book`, select different genres, Description `Another test`, choose another file → **Submit** → Confirm                                       | Success again                                                                             |

---

## Author Dashboard Verification

| Step | Action                                    | Expected result                                                 |
|------|-------------------------------------------|-----------------------------------------------------------------|
| 1    | After login as `author1`, check dashboard | Welcome message shows "Welcome, Jane Doe!"                      |
| 2    | Dashboard should show                     | Prominent green **Start Publishing →** button/card              |
| 3    | Verify dashboard scrolls                  | If window is small, content scrolls                             |
| 4    | Click **Logout**                          | Confirmation dialog appears: "Are you sure you want to logout?" |
| 5    | Click **Cancel**                          | Stays on dashboard                                              |
| 6    | Click **Logout** → **OK**                 | Returns to Welcome screen                                       |

---

## Quick Smoke Test (Minimal Path)

1. **ResetDatabase** (app closed).
2. Run app → **Author Portal** → **Register**: `testauthor` / `Test` / `Author` / `TestPass1!` / Bio `Testing` → **Register**.
3. **Login**: `testauthor` / `TestPass1!` → **Login** → see Author Dashboard.
4. **Publish New Book**:
   - Title: `Smoke Test Book`
   - Select multiple genres: `Fiction`, `Fantasy`
   - Description: `This is a smoke test.`
   - File: Choose any small PDF
   - **Submit** → Review preview → **Yes, Submit** → Success message.
5. **Logout** (confirm dialog → OK).
6. Check database to confirm pending book exists.

---

If all steps match the expected results, **Task 2 (Author Portal)** is working end-to-end with all enhancements:
