# Quick Start Guide - Running the E-Book Library Management System

## Prerequisites
- Java 21 JDK installed
- Maven installed
- Git (optional, for version control)

## Building the Project

```powershell
# Navigate to project directory
cd "d:\HKUST\Academics\COMP3111\COMP3111_Library_Management_System"

# Clean and compile
mvn clean compile

# Build package
mvn clean package
```

## Running the Application

### Method 1: Using Maven JavaFX Plugin (Recommended)
```powershell
mvn clean javafx:run
```

### Method 2: Using Maven Exec Plugin
```powershell
mvn clean compile exec:java -Dexec.mainClass="org.example.app.Main"
```

## Testing the Librarian Portal

### Accessing the Librarian Portal
1. Launch the application
2. Click the **"Librarian Portal"** button on the Welcome screen
3. You'll see two options: **Login** or **Register**

### Registering as a Librarian
1. Click **"Register"** button
2. Enter your details:
   - **Username:** e.g., `librarian1` (3-50 characters, alphanumeric + underscore)
   - **Full Name:** e.g., `John Smith`
   - **Password:** Minimum 6 characters (preferably with mixed case and numbers)
   - **Employee ID:** Optional, e.g., `EMP12345`
3. Click **"Register"**
4. Success message will appear, navigate back to login

### Logging In as a Librarian
1. From Librarian Portal, click **"Login"**
2. Enter your registered credentials
3. Click **"Login"**
4. Access the **Book Approval Dashboard**

### Approving/Rejecting Books
1. After login, you'll see pending book submissions
2. For each book submission:
   - Review title, author, genre, and summary
   - Add optional review notes in the text area
   - Click **"Approve"** to accept the book
   - Click **"Reject"** to deny the book
3. Confirm your action in the popup dialog
4. The book status will update and the screen refreshes
5. Click **"Logout"** to return to the portal

## Project Structure

```
src/main/java/org/example/
├── app/
│   ├── Main.java              # Application entry point
│   └── Navigator.java         # Screen navigation controller
├── db/
│   ├── Database.java          # SQLite connection management
│   ├── PendingDao.java        # Pending books database operations
│   ├── UserDao.java           # User database operations
│   └── [other DAOs...]
├── domain/
│   ├── User.java              # User entity
│   ├── Role.java              # User roles (STUDENT, STAFF, AUTHOR, LIBRARIAN)
│   ├── PendingBook.java       # Pending book submission entity
│   └── [other entities...]
├── service/
│   ├── AuthService.java       # Authentication and registration
│   └── [other services...]
├── security/
│   └── PasswordHasher.java    # Secure password hashing
├── ui/
│   ├── WelcomeScreen.java     # Main entry screen
│   ├── LibrarianEntryScreen.java    # Librarian portal entry (NEW)
│   ├── LibrarianRegisterScreen.java # Librarian registration (NEW)
│   ├── LibrarianLoginScreen.java    # Librarian login (NEW)
│   ├── LibrarianApprovalScreen.java # Book approval dashboard (NEW)
│   └── [other screens...]
└── util/
    ├── Validators.java
    └── ValidationException.java
```

## Key Features Implemented

### ✅ Task 3.1: Librarian Registration
- Unique username validation
- Full name input
- Secure password with strength indicator
- Optional Employee ID field
- Comprehensive error handling

### ✅ Task 3.2: Librarian Login
- Credential validation
- Account lockout protection
- Role-based access control
- User-friendly error messages

### ✅ Task 3.3: Book Approval Management
- View all pending submissions
- Detailed submission information display
- Approve with optional notes
- Reject with optional notes
- Status tracking and audit trail
- Confirmation dialogs for critical actions

## Database

The application uses **SQLite** for data persistence:
- Database file: `.db` (created automatically)
- Tables include:
  - `users` - User accounts
  - `pending_books` - Book submissions awaiting approval
  - `books` - Approved books in library
  - And others for borrows, publications, etc.

## Troubleshooting

### Build fails with "Cannot find symbol"
- Ensure all Java files are compiled: `mvn clean compile`
- Check that Maven dependencies are downloaded: `mvn clean install`

### Application won't start
- Verify Java 21 is installed: `java -version`
- Check if port/database is locked, restart the application

### Cannot login as librarian
- Verify username and password are correct (case-sensitive username)
- Check account lockout status (waits 15 minutes after 5 failed attempts)
- Ensure user was registered with LIBRARIAN role

### No pending books showing
- Verify books were submitted by authors and in "PENDING" status
- Check database connectivity
- Review logs for SQL errors

## Additional Commands

```powershell
# Clean build artifacts
mvn clean

# Run tests
mvn test

# Create executable JAR
mvn package

# View project information
mvn site

# Check for dependency issues
mvn dependency:tree
```

## Support

For issues or questions:
1. Check the implementation summary: `TASK3_IMPLEMENTATION_SUMMARY.md`
2. Review existing test guides: `docs/TASK1_TEST_GUIDE.md` and `docs/TASK2_TEST_GUIDE.md`
3. Examine similar components (e.g., AuthorLoginScreen for pattern reference)

---

**Last Updated:** March 9, 2026
**Implementation Status:** Complete ✅
