# COMP3111 Library Management System

JavaFX + SQLite library management application (Phase 1+). Supports registration, login, available books, borrowing, and role-based flows for Student/Staff, Author, and Librarian.

---

## Features

- **Task 1 (Student/Staff):** Registration, login, available books list, borrow book, My Borrowed Books (view and return). Borrow limit (5 per user), due date (14 days), Quick Review (first few pages), Read Summary pop-up. 15‑minute inactivity timer; lockout after 5 failed login attempts.
- **Task 2 (Author):** Registration, login, publish book (file upload, multi-genre, description/summary, preview, submit for librarian approval).
- **Task 3 (Librarian):** Registration, login, approval dashboard (pending submissions, approve/reject with optional review notes, confirmation dialogs). Approved books are added to the catalog for students to borrow.
- **Security:** PBKDF2 password hashing with per-user salt; SQLite database at `data/library.db`.


---

## Documentation

- **Task 1 testing:** `docs/TASK1_TEST_GUIDE.md`
- **Task 2 testing:** `docs/TASK2_TEST_GUIDE.md`
- **Task 3 (Librarian):** `Documentations/README_TASK3.md`, `Documentations/TASK3_QUICK_START.md`, `Documentations/TASK3_TEST_GUIDE.md`

---

## How to Open and Run the Project

### Prerequisites

- **Java 21**
- **Maven 3.6+**

### Open in an IDE (e.g. IntelliJ IDEA)

1. **Open / Import**: Open the project root folder (`COMP3111_Library_Management_System`) in your IDE, or use **File → Open** and select the folder. If prompted, import as a **Maven** project.
2. **Refresh Maven**: Let the IDE resolve dependencies (e.g. IntelliJ: right-click `pom.xml` → Maven → Reload Project).

### Run the Application

**From command line (project root):**

```bash
mvn clean javafx:run
```

Or without clean:

```bash
mvn javafx:run
```

**From IntelliJ:**

- Run the main class: `org.example.app.Main`  
  (Right-click `src/main/java/org/example/app/Main.java` → Run 'Main.main()'.)

---

## How to Reset the Database

The app uses a local SQLite database at `data/library.db`. To clear all saved data (users, books, borrows, etc.) and get a fresh database on the next run:

**From command line (project root):**

```bash
mvn exec:java -Dexec.mainClass="org.example.db.ResetDatabase"
```

**From IntelliJ:**

- Right-click `src/main/java/org/example/db/ResetDatabase.java` → Run 'ResetDatabase.main()'.

**If reset doesn’t clear data:** Main and ResetDatabase may be using different working directories. In **Run → Edit Configurations**, set the **Working directory** for both **Main** and **ResetDatabase** to the same value (e.g. the project root or `$MODULE_WORKING_DIR$`).

---

## TA / Grading Notes

- **Build & run**: From project root, `mvn clean javafx:run` should build and launch the JavaFX app without errors.
- **Main class**: `org.example.app.Main`.
- **Database**: SQLite file at `data/library.db` (created on first run). Reset via `org.example.db.ResetDatabase` as above.
- **Tech stack**: Java 21, JavaFX 21, Maven, SQLite (org.xerial:sqlite-jdbc).
- **Tests**: `mvn test` to run JUnit tests (if any).

---

## Supplementary Notes: Running on Any OS (Windows / macOS / Linux)

- **Java & Maven**: Install Java 21 and Maven 3.6+ from your OS package manager or official downloads (ensure `java -version` and `mvn -version` work in a terminal).
- **Clone the repo**:
  - `git clone <repo-url>`
  - `cd COMP3111_Library_Management_System`
- **Line endings (shared repo)**:
  - The project includes a `.gitattributes` file so Git normalizes text files; no extra steps are needed on most setups.
  - Recommended Git setting per developer:
    - Windows: `git config --global core.autocrlf true`
    - macOS/Linux: `git config --global core.autocrlf input`
- **Run from command line (all platforms)**:
  - `mvn clean javafx:run` (downloads dependencies and starts the JavaFX app).
- **Run from IntelliJ / other IDEs**:
  - Import as a Maven project, ensure the project SDK is **Java 21**, then run `org.example.app.Main`.

