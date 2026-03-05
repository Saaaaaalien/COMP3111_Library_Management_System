# Task 1 – Student/Staff: Test Guide

Use this checklist to verify all Task 1 features. **Reset the database first** so you start with no data.

---

## Reset the database (start clean)

1. **Close the main application** if it is running (so the DB file is not locked).
2. In IntelliJ: right-click **`org.example.db.ResetDatabase`** → **Run 'ResetDatabase.main()'**
   - Or from project root:  
     `mvn exec:java -Dexec.mainClass="org.example.db.ResetDatabase"`
3. Console should say: *"Database deleted"* or *"All saved data has been cleared."*
4. Start the main app again (**Run 'Main'**). A fresh empty DB will be created.

---

## 1.1 Student/Staff Registration

| Step | Action | Expected result                                                                                        |
|------|--------|--------------------------------------------------------------------------------------------------------|
| 1 | Run app → **Student/Staff Portal** → **Register** | Registration form with Username, First Name, Last Name, Password, Role (Student/Staff), Register, Back |
| 2 | Leave all fields empty → **Register** | Error: e.g. "Username is required."                                                                    |
| 3 | Username `ab`, First `Jane`, Last `Doe`, Password `short`, Role Student → **Register** | Error: Username must be at least 3 characters                                                          |
| 4 | Password `GoodPass1!` (watch strength meter) | Strength shows Weak/Medium/Strong as you type                                                          |
| 5 | Username `student1`, First `Jane`, Last `Doe`, Password `GoodPass1!`, Role **Student** → **Register** | Success message; screen switches to Login                                                              |
| 6 | **Back** → **Register** again with Username `staff1`, First `John`, Last `Smith`, Password `Secure2@`, Role **Staff** → **Register** | Success; switch to Login                                                                               |
| 7 | **Back** → **Register** with Username `student1` again (same as existing) | Error: "Username is already taken."                                                                    |

---

## 1.2 Student/Staff Login

| Step | Action | Expected result |
|------|--------|-----------------|
| 1 | **Login** with empty username/password → **Login** | Error: "Invalid username or password. Please check your username and password." |
| 2 | Username `student1`, wrong password `wrong` → **Login** | Same error message |
| 3 | Username `student1`, Password `GoodPass1!` → **Login** | Navigate to **Available Books**; header shows "Logged in as: Jane Doe (student1)" |
| 4 | **Logout** → **Login** with Username `staff1`, Password `Secure2@` | Navigate to Available Books; "Logged in as: John Smith (staff1)" |
| 5 | **Logout** → **Login** with Username `student1`, wrong password 5 times in a row | After 5th failure: "Account is temporarily locked... Try again after 15 minutes." |
| 6 | Try to login again immediately with correct password | Still locked (same message) |
| 7 | Wait 15 minutes **or** run **ResetDatabase** and re-register `student1`, then **Login** with wrong password once | One failure → no lock; counter should be 1, not lock after 1 try (if you didn’t reset, lockout may still be active until it expires) |

---

## 1.3 Available Book Screen

| Step | Action | Expected result |
|------|--------|-----------------|
| 1 | Login as `student1` | **Available Books** screen with table: Title, Author, Publish Date, Availability Status, Abstract/Summary |
| 2 | With no books in DB | Table is empty (or has rows if you added books via Author + Librarian) |
| 3 | Check column headers | Publish Date shows in **MM/DD/YYYY** when there is data |

**To get books in the list:** Use **Author Portal** to submit a book, then **Librarian Portal** to approve it. After that, that book appears here as available.

---

## 1.4 Borrow Book

| Step | Action | Expected result |
|------|--------|-----------------|
| 1 | On Available Books with at least one book, click **Borrow Selected Book** without selecting a row | Warning: "Please select a book to borrow." |
| 2 | Select one row → **Borrow Selected Book** | Confirmation dialog: "Borrow this book?" with Title and Author |
| 3 | **Cancel** | Dialog closes; no borrow; list unchanged |
| 4 | Select same row → **Borrow Selected Book** → **OK** | Success alert with "You have successfully borrowed: Title: ... Author: ..."; table refreshes and that book disappears from the list |
| 5 | Try to borrow again (e.g. from another tab/session if any) | That book no longer available or list empty; no double borrow |

---

## Session / Inactivity

| Step | Action | Expected result |
|------|--------|-----------------|
| 1 | Login as `student1` on Available Books | Timer starts (15 min) |
| 2 | Click or type in the window | Timer resets (another 15 min) |
| 3 | Open an alert (e.g. try Borrow without selection) and leave it open for a while | No logout while dialog is open |
| 4 | Do nothing on the Available Books screen for 15 minutes | Automatic logout back to Student/Staff Portal |

---

## Quick smoke test (minimal path)

1. **ResetDatabase** (app closed).
2. Run app → **Student/Staff Portal** → **Register**: `testuser` / `First` / `Last` / `TestPass1!` / Student → **Register**.
3. **Login**: `testuser` / `TestPass1!` → **Login** → see Available Books.
4. **Logout**.
5. (Optional) Add one book via Author + Librarian, then login again and **Borrow** it; confirm success and list update.

If all steps match the expected results, Task 1 (Student/Staff) is working end-to-end.
