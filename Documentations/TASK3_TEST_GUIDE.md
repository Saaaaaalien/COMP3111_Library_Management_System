# Task 3: Librarian Portal - Test Guide

## Test Setup

### Prerequisites
- Project must be built successfully: `mvn clean compile`
- Application must run without errors: `mvn javafx:run`
- Database initialized with tables

### Test Accounts for Manual Testing
You can use these accounts to test if they were created in previous tasks:
- **Sample Student:** username=`student1`, password=`password` (if created)
- **Sample Author:** username=`author1`, password=`password` (if created)
- **Sample Librarian:** Create via registration screen

---

## Test Cases for Task 3.1: Librarian Registration

### TC 3.1.1 - Successful Registration
**Objective:** Verify that a new librarian can successfully register with valid data

**Steps:**
1. Launch application
2. Click "Librarian Portal" → "Register"
3. Enter the following:
   - Username: `testlib001`
   - Full Name: `Test Librarian`
   - Employee ID: `EMP001`
   - Password: `Test@123`
4. Click "Register"

**Expected Result:**
- Success message appears: "You can now log in with your librarian username and password"
- Screen returns to Librarian Portal
- User can login with these credentials in next test

**Actual Result:** _____ (Pass/Fail)

---

### TC 3.1.2 - Duplicate Username Prevention
**Objective:** Verify that duplicate usernames are rejected

**Steps:**
1. Click "Librarian Portal" → "Register"
2. Use same username from TC 3.1.1: `testlib001`
3. Enter different data:
   - Full Name: `Another Librarian`
   - Password: `Test@123`
4. Click "Register"

**Expected Result:**
- Error message: "Username is already taken"
- Registration fails
- User remains on registration screen

**Actual Result:** _____ (Pass/Fail)

---

### TC 3.1.3 - Invalid Username Format
**Objective:** Verify that invalid username formats are rejected

**Steps:**
1. Click "Librarian Portal" → "Register"
2. Enter invalid username: `ab` (too short, needs 3+ chars)
3. Fill other fields with valid data
4. Click "Register"

**Expected Result:**
- Error message about invalid username format
- Registration fails

**Actual Result:** _____ (Pass/Fail)

---

### TC 3.1.4 - Weak Password Rejection
**Objective:** Verify that weak passwords are rejected

**Steps:**
1. Click "Librarian Portal" → "Register"
2. Enter:
   - Username: `testlib002`
   - Full Name: `Test Lib 2`
   - Password: `123` (weak - too short)
3. Click "Register"

**Expected Result:**
- Error message about password strength requirements
- Registration fails

**Actual Result:** _____ (Pass/Fail)

---

### TC 3.1.5 - Empty Field Validation
**Objective:** Verify that empty required fields are handled

**Steps:**
1. Click "Librarian Portal" → "Register"
2. Leave Username empty
3. Fill other fields
4. Click "Register"

**Expected Result:**
- Error message about missing username
- Registration fails

**Actual Result:** _____ (Pass/Fail)

---

### TC 3.1.6 - Optional Employee ID
**Objective:** Verify that Employee ID is optional

**Steps:**
1. Click "Librarian Portal" → "Register"
2. Enter:
   - Username: `testlib003`
   - Full Name: `Lib Without ID`
   - Password: `ValidPassword123`
   - Employee ID: (leave empty)
3. Click "Register"

**Expected Result:**
- Registration succeeds
- Success message appears
- User can login without Employee ID

**Actual Result:** _____ (Pass/Fail)

---

## Test Cases for Task 3.2: Librarian Login

### TC 3.2.1 - Successful Login
**Objective:** Verify that registered librarian can login successfully

**Steps:**
1. Click "Librarian Portal" → "Login"
2. Enter credentials from TC 3.1.1:
   - Username: `testlib001`
   - Password: `Test@123`
3. Click "Login"

**Expected Result:**
- Login succeeds
- Navigates to Book Approval Dashboard
- Displays librarian's name
- Displays pending books (if any exist)

**Actual Result:** _____ (Pass/Fail)

---

### TC 3.2.2 - Invalid Password
**Objective:** Verify that wrong password is rejected

**Steps:**
1. Click "Librarian Portal" → "Login"
2. Enter:
   - Username: `testlib001`
   - Password: `WrongPassword`
3. Click "Login"

**Expected Result:**
- Error message: "Invalid username or password"
- Login fails
- Remains on login screen

**Actual Result:** _____ (Pass/Fail)

---

### TC 3.2.3 - Non-existent Username
**Objective:** Verify that non-existent usernames are rejected

**Steps:**
1. Click "Librarian Portal" → "Login"
2. Enter:
   - Username: `nonexistent`
   - Password: `TestPassword`
3. Click "Login"

**Expected Result:**
- Error message: "Invalid username or password"
- Login fails

**Actual Result:** _____ (Pass/Fail)

---

### TC 3.2.4 - Case-Sensitive Username
**Objective:** Verify that username is case-sensitive

**Steps:**
1. Click "Librarian Portal" → "Login"
2. Enter:
   - Username: `TestLib001` (wrong case)
   - Password: `Test@123`
3. Click "Login"

**Expected Result:**
- Error message: "Invalid username or password"
- Login fails (case matters)

**Actual Result:** _____ (Pass/Fail)

---

### TC 3.2.5 - Student Cannot Access Librarian Portal
**Objective:** Verify role-based access control

**Prerequisites:** Assume a student account exists: `student1`

**Steps:**
1. Click "Librarian Portal" → "Login"
2. Enter student credentials:
   - Username: `student1`
   - Password: `password`
3. Click "Login"

**Expected Result:**
- Error message: "This portal is for librarians only"
- Login fails
- Student is not granted access

**Actual Result:** _____ (Pass/Fail)

---

### TC 3.2.6 - Account Lockout Protection
**Objective:** Verify account lockout after multiple failed attempts

**Steps:**
1. Click "Librarian Portal" → "Login"
2. Attempt login 5 times with wrong password for `testlib001`
3. Try login with correct password

**Expected Result:**
- After 5 failed attempts: "Account is temporarily locked due to too many failed attempts. Try again after 15 minutes."
- Correct password also fails during lockout
- Can retry after 15 minutes

**Actual Result:** _____ (Pass/Fail)

---

## Test Cases for Task 3.3: Book Approval Screen

### Prerequisites
- Must have at least one pending book submission in database
- Can test by having an author submit a book (Task 2)
- Or manually insert test data into pending_books table

### TC 3.3.1 - View Pending Books List
**Objective:** Verify pending books are displayed correctly

**Steps:**
1. Login as librarian (testlib001)
2. Navigate to Book Approval Dashboard

**Expected Result:**
- Dashboard displays
- Shows count of pending submissions
- Displays list of pending books
- Each book shows: Title, Author, Genre, Summary, Submitted Date
- Status shows "PENDING" for all

**Actual Result:** _____ (Pass/Fail)

---

### TC 3.3.2 - Book Card Display
**Objective:** Verify all required information is shown for each book

**Steps:**
1. Login and view Book Approval Dashboard
2. Examine first book card

**Expected Result:**
- Title is visible and prominent
- Author Full Name is displayed
- Genre is shown
- Summary is provided
- Submitted date is formatted correctly
- Status shows "PENDING"
- Approve and Reject buttons are present
- Review Notes text area is present

**Actual Result:** _____ (Pass/Fail)

---

### TC 3.3.3 - Approve Book with Notes
**Objective:** Verify book approval with optional review notes

**Steps:**
1. Login as librarian
2. Find a pending book
3. Enter review notes: "Great content, well written"
4. Click "Approve" button
5. Confirm in dialog

**Expected Result:**
- Confirmation dialog appears asking to confirm approval
- After confirming: Success message appears
- Book is removed from pending list
- Status updated to "APPROVED" in database
- Review notes are saved

**Actual Result:** _____ (Pass/Fail)

---

### TC 3.3.4 - Approve Book without Notes
**Objective:** Verify approval works without mandatory notes

**Steps:**
1. Login as librarian
2. Find a pending book
3. Leave Review Notes empty
4. Click "Approve"
5. Confirm in dialog

**Expected Result:**
- Approval succeeds
- Book is removed from pending list
- Empty review notes are saved (or NULL)

**Actual Result:** _____ (Pass/Fail)

---

### TC 3.3.5 - Reject Book with Notes
**Objective:** Verify book rejection with optional review notes

**Steps:**
1. Login as librarian
2. Find a pending book
3. Enter review notes: "Content violates library standards"
4. Click "Reject" button
5. Confirm in dialog

**Expected Result:**
- Confirmation dialog appears asking to confirm rejection
- After confirming: Success message appears
- Book is removed from pending list
- Status updated to "REJECTED" in database
- Review notes are saved

**Actual Result:** _____ (Pass/Fail)

---

### TC 3.3.6 - Cancel Approval
**Objective:** Verify ability to cancel approval before finalizing

**Steps:**
1. Login as librarian
2. Find a pending book
3. Click "Approve" button
4. In confirmation dialog, click "Cancel"

**Expected Result:**
- Dialog closes
- Book remains pending
- No changes are made
- User returned to book card

**Actual Result:** _____ (Pass/Fail)

---

### TC 3.3.7 - Empty Pending List
**Objective:** Verify graceful handling when no pending books exist

**Steps:**
1. Login as librarian
2. Approve/Reject all pending books
3. Logout and login again
4. View dashboard

**Expected Result:**
- Message displays: "No pending book submissions awaiting approval"
- No book cards shown
- Dashboard is still functional
- Logout button is available

**Actual Result:** _____ (Pass/Fail)

---

### TC 3.3.8 - Dashboard Refresh
**Objective:** Verify dashboard reflects changes after approval/rejection

**Steps:**
1. Login as librarian (testlib001)
2. Note the count of pending books
3. Approve one book
4. Confirm action

**Expected Result:**
- Screen automatically refreshes
- Approved book is no longer in list
- Count decreases by 1
- Remaining books are still shown

**Actual Result:** _____ (Pass/Fail)

---

### TC 3.3.9 - Logout from Dashboard
**Objective:** Verify logout functionality from approval dashboard

**Steps:**
1. Login as librarian
2. View Book Approval Dashboard
3. Click "Logout" button

**Expected Result:**
- Returns to Librarian Portal
- Clears session
- Must login again to access dashboard

**Actual Result:** _____ (Pass/Fail)

---

### TC 3.3.10 - Long Summary Handling
**Objective:** Verify long summaries are displayed properly

**Steps:**
1. Create a pending book with a very long summary (>500 characters)
2. Login as librarian
3. View the book card

**Expected Result:**
- Summary text wraps properly
- TextArea is scrollable
- All text is readable
- Does not break layout

**Actual Result:** _____ (Pass/Fail)

---

### TC 3.3.11 - Special Characters in Notes
**Objective:** Verify review notes can contain special characters

**Steps:**
1. Login as librarian
2. Enter review notes: "Book has errors: <syntax>, missing @author, etc."
3. Approve the book

**Expected Result:**
- Special characters are properly saved
- Notes are retrieved correctly
- No SQL injection occurs

**Actual Result:** _____ (Pass/Fail)

---

## Integration Tests

### TC 3.4.1 - Complete User Journey
**Objective:** Test the full workflow from registration to approval

**Steps:**
1. Register new librarian account
2. Login with new account
3. View pending books dashboard
4. Approve one book with notes
5. Verify book status changes
6. Logout

**Expected Result:**
- All steps complete successfully
- No errors occur
- Data persists correctly

**Actual Result:** _____ (Pass/Fail)

---

### TC 3.4.2 - Navigation Consistency
**Objective:** Verify navigation between screens works correctly

**Steps:**
1. From Welcome → Librarian Portal → Register
2. Click Back → should go to Librarian Portal
3. From Librarian Portal → Login
4. Click Back → should go to Librarian Portal
5. Login successfully
6. Click Logout → should go to Librarian Portal

**Expected Result:**
- All navigation works as expected
- Back buttons return to correct screens
- No lost context or errors

**Actual Result:** _____ (Pass/Fail)

---

## Performance Tests

### TC 3.5.1 - Large Pending List Performance
**Objective:** Verify UI handles many pending books

**Setup:**
1. Insert 50+ pending books into database

**Steps:**
1. Login as librarian
2. Observe dashboard load time
3. Scroll through list

**Expected Result:**
- Dashboard loads in reasonable time (<2 seconds)
- Scrolling is smooth
- No UI freezing or lag
- All books are visible and accessible

**Actual Result:** _____ (Pass/Fail)

---

## Error Handling Tests

### TC 3.6.1 - Database Connection Error
**Objective:** Verify graceful handling of database errors

**Steps:**
1. Login as librarian
2. Simulate database disconnection (stop database)
3. Try to approve a book

**Expected Result:**
- Error message displayed: "A database error occurred"
- Application does not crash
- User can return to previous screen

**Actual Result:** _____ (Pass/Fail)

---

### TC 3.6.2 - Network Error During Approval
**Objective:** Verify handling of network/DB errors during action

**Steps:**
1. Login as librarian
2. Click Approve
3. Simulate network failure during approval
4. Check database state

**Expected Result:**
- Error message is shown
- Book status is not partially updated
- Data consistency maintained

**Actual Result:** _____ (Pass/Fail)

---

## Summary

| Test Case | Result | Notes |
|-----------|--------|-------|
| TC 3.1.1 - Successful Registration | | |
| TC 3.1.2 - Duplicate Username | | |
| TC 3.1.3 - Invalid Username | | |
| TC 3.1.4 - Weak Password | | |
| TC 3.1.5 - Empty Fields | | |
| TC 3.1.6 - Optional Employee ID | | |
| TC 3.2.1 - Successful Login | | |
| TC 3.2.2 - Invalid Password | | |
| TC 3.2.3 - Non-existent User | | |
| TC 3.2.4 - Case-Sensitive Username | | |
| TC 3.2.5 - Role-Based Access | | |
| TC 3.2.6 - Account Lockout | | |
| TC 3.3.1 - View Pending Books | | |
| TC 3.3.2 - Book Card Display | | |
| TC 3.3.3 - Approve with Notes | | |
| TC 3.3.4 - Approve without Notes | | |
| TC 3.3.5 - Reject with Notes | | |
| TC 3.3.6 - Cancel Approval | | |
| TC 3.3.7 - Empty Pending List | | |
| TC 3.3.8 - Dashboard Refresh | | |
| TC 3.3.9 - Logout | | |
| TC 3.3.10 - Long Summary | | |
| TC 3.3.11 - Special Characters | | |
| TC 3.4.1 - Complete Journey | | |
| TC 3.4.2 - Navigation | | |
| TC 3.5.1 - Performance | | |
| TC 3.6.1 - DB Error | | |
| TC 3.6.2 - Network Error | | |

---

**Total Test Cases:** 28
**Date:** March 9, 2026
**Tester:** _______________
**Overall Result:** _______________
