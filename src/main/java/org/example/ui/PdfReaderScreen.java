package org.example.ui;

import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.example.app.Navigator;
import org.example.app.SessionService;
import org.example.db.BorrowDao;
import org.example.db.ReadingHighlightDao;
import org.example.db.ReadingProgressDao;
import org.example.domain.Borrow;
import org.example.domain.User;
import org.example.service.BorrowService;
import org.example.util.BookPreviewUtil;

import netscape.javascript.JSObject;

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.OutputStream;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.Executors;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Borrowed-book PDF reader using an embedded {@link WebView} so the platform PDF plug-in handles
 * rendering and native text selection (Preview-like). One page at a time: arrows / buttons only.
 * Zoom is a percentage passed via the PDF open fragment where supported. External navigations are blocked.
 * Reading progress is saved per borrow; PDF files are not modified. Apache PDFBox is not used here
 * (only {@link BookPreviewUtil} keeps PDFBox for catalog quick preview).
 */
public final class PdfReaderScreen {

    private PdfReaderScreen() {}

    /**
     * JS->Java bridge used by {@code pdf-reader/host.html} to persist user selection highlights.
     */
    private static final class HighlightBridge {
        private final long borrowId;
        private final long userId;

        private HighlightBridge(long borrowId, long userId) {
            this.borrowId = borrowId;
            this.userId = userId;
        }

        /**
         * @param highlightText selected text (plain text)
         * @param pageNumber   1-based page number
         * @param rectsJson    JSON array of normalized rects (geometry for persistence)
         */
        public void onHighlight(String highlightText, int pageNumber, String rectsJson) {
            try {
                if (highlightText == null) return;
                String t = highlightText.trim();
                if (t.isEmpty()) return;
                int pageIndex = Math.max(0, pageNumber - 1); // DAO expects 0-based page index
                ReadingHighlightDao.insert(
                        borrowId,
                        userId,
                        pageIndex,
                        t,
                        rectsJson,
                        Instant.now().toString()
                );
            } catch (Exception ignored) {
                // Never break reader interactions, but we want visibility in logs.
                System.err.println("[PdfReaderScreen] Failed to persist highlight: " + ignored);
                ignored.printStackTrace();
            }
        }
    }

    public static void open(Navigator navigator, User user, long borrowId, long bookId, String title, String filePath) {
        open(navigator, user, borrowId, bookId, title, filePath, null, null);
    }

    public static void open(Navigator navigator, User user, long borrowId, long bookId, String title, String filePath,
                             Integer startPageIndex0,
                             Integer startZoomPercent) {
        if (filePath == null || !filePath.toLowerCase().endsWith(".pdf")) {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setContentText("Only PDF files can be opened in the reader.");
            a.showAndWait();
            return;
        }
        Path pdfPath = Path.of(filePath).toAbsolutePath().normalize();
        if (!java.nio.file.Files.isRegularFile(pdfPath)) {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setContentText("Book file not found on disk.");
            a.showAndWait();
            return;
        }

        // Authorization: ensure the logged-in user owns this borrow (and it matches this book).
        // This prevents reading/writing another user's reading progress/highlights via a forged borrowId.
        try {
            var borrowOpt = BorrowDao.findById(borrowId);
            if (borrowOpt.isEmpty()) {
                Alert a = new Alert(Alert.AlertType.ERROR);
                a.setContentText("Borrow record not found.");
                a.showAndWait();
                return;
            }
            Borrow borrow = borrowOpt.get();
            if (borrow.getBorrowerUserId() != user.getId() || borrow.getBookId() != bookId) {
                Alert a = new Alert(Alert.AlertType.ERROR);
                a.setContentText("You do not have permission to read this loan.");
                a.showAndWait();
                return;
            }
            if (borrow.getReturnedAt() != null && !borrow.getReturnedAt().isEmpty()) {
                Alert a = new Alert(Alert.AlertType.INFORMATION);
                a.setContentText("This loan has already been returned.");
                a.showAndWait();
                return;
            }
        } catch (SQLException ignored) {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setContentText("Could not verify loan permissions.");
            a.showAndWait();
            return;
        }

        int pageCount = BookPreviewUtil.getPdfPageCount(pdfPath);
        if (pageCount <= 0) {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setContentText("Could not open PDF or it has no pages.");
            a.showAndWait();
            return;
        }

        URL hostResource = PdfReaderScreen.class.getResource("/pdf-reader/host.html");
        if (hostResource == null) {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setContentText("Reader shell missing from resources (pdf-reader/host.html).");
            a.showAndWait();
            return;
        }
        final String hostLocation = hostResource.toExternalForm();

        // Some WebView/PDF plug-ins are unreliable with `file:` URLs; serve the PDF locally over HTTP.
        // This avoids breaking on "dark background / blank embed" issues while keeping everything local.
        HttpServer pdfServer = null;
        String pdfHttpBase = null; // e.g. http://127.0.0.1:12345/pdf
        ExecutorService httpExecutor = null;
        try {
            pdfServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            pdfServer.createContext("/pdf", (HttpExchange exchange) -> {
                // Only commit `200 OK` after we can actually open and stream the PDF bytes.
                // Otherwise the viewer may receive HTTP 200 with an empty body (silent failure).
                try (var in = Files.newInputStream(pdfPath);
                     OutputStream os = exchange.getResponseBody()) {
                    exchange.getResponseHeaders().set("Content-Type", "application/pdf");
                    exchange.sendResponseHeaders(200, 0);
                    in.transferTo(os);
                } catch (java.nio.file.NoSuchFileException nsf) {
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                    exchange.sendResponseHeaders(404, 0);
                } catch (Exception ex) {
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                    exchange.sendResponseHeaders(500, 0);
                } finally {
                    try {
                        exchange.close();
                    } catch (Exception ignored) {}
                }
            });
            httpExecutor = Executors.newSingleThreadExecutor();
            pdfServer.setExecutor(httpExecutor);
            pdfServer.start();
            pdfHttpBase = "http://127.0.0.1:" + pdfServer.getAddress().getPort() + "/pdf";
        } catch (Exception e) {
            // Fallback: use file:// directly if local HTTP cannot start.
            pdfHttpBase = pdfPath.toUri().toString();
            if (pdfServer != null) {
                try {
                    pdfServer.stop(0);
                } catch (Exception ignored) {}
            }
            pdfServer = null;
            if (httpExecutor != null) {
                try {
                    httpExecutor.shutdownNow();
                } catch (Exception ignored) {}
            }
            httpExecutor = null;
        }
        // Copy to effectively-final references so lambdas can safely capture them.
        final HttpServer pdfServerRef = pdfServer;
        final String pdfHttpBaseRef = pdfHttpBase;
        final ExecutorService httpExecutorRef = httpExecutor;

        Stage readerStage = new Stage();
        readerStage.initOwner(navigator.getStage());
        readerStage.setTitle("Read: " + title);
        // Popup over the current window (not a fullscreen experience).
        readerStage.initModality(Modality.WINDOW_MODAL);
        readerStage.initStyle(StageStyle.UTILITY);
        readerStage.setFullScreen(false);
        readerStage.setMaximized(false);
        readerStage.fullScreenProperty().addListener((obs, oldV, newV) -> {
            if (Boolean.TRUE.equals(newV)) {
                readerStage.setFullScreen(false);
            }
        });
        double ownerW = navigator.getStage().getWidth();
        double ownerH = navigator.getStage().getHeight();
        if (ownerW > 50 && ownerH > 50) {
            readerStage.setWidth(Math.min(1100, ownerW - 40));
            readerStage.setHeight(Math.min(820, ownerH - 60));
        }

        int[] currentPage = {0};
        if (startPageIndex0 != null) {
            currentPage[0] = Math.min(Math.max(0, startPageIndex0), pageCount - 1);
        } else {
            try {
                ReadingProgressDao.getLastPage(borrowId, user.getId())
                        .ifPresent(p -> currentPage[0] = Math.min(Math.max(0, p), pageCount - 1));
            } catch (SQLException ignored) {
            }
        }

        // Resume point in this reader == the last page we persisted to reading_progress.
        // We initialize it to the current page (which itself may come from last saved progress).
        int[] bookmarkedPage = {currentPage[0]};

        int initialZoom = startZoomPercent != null ? startZoomPercent : 100;
        initialZoom = Math.min(400, Math.max(25, initialZoom));
        final int[] zoomPercent = {initialZoom};

        final long[] lastReadAccumWallMs = {System.currentTimeMillis()};

        Runnable flushReadSeconds = () -> {
            long now = System.currentTimeMillis();
            int secs = (int) Math.min(600, Math.max(0, (now - lastReadAccumWallMs[0]) / 1000));
            lastReadAccumWallMs[0] = now;
            if (secs <= 0) {
                return;
            }
            try {
                ReadingProgressDao.addReadSeconds(borrowId, user.getId(), bookId, bookmarkedPage[0], secs,
                        Instant.now().toString());
            } catch (SQLException ignored) {
            }
        };

        Runnable saveProgress = () -> {
            try {
                flushReadSeconds.run();
                // Persist the user's explicit resume point (the last page they clicked "Bookmark" on),
                // without overwriting it with whatever page the user might be on when closing.
                ReadingProgressDao.upsert(borrowId, user.getId(), bookId, bookmarkedPage[0], null, Instant.now().toString());
            } catch (SQLException ignored) {
            }
        };

        Runnable checkpoint = () -> {
            try {
                SessionService.saveReaderSession(user.getId(), borrowId, bookId, currentPage[0], zoomPercent[0]);
            } catch (Exception ignored) {
            }
        };
        checkpoint.run();

        WebView webView = new WebView();
        WebEngine webEngine = webView.getEngine();
        webEngine.setJavaScriptEnabled(true);
        webEngine.setCreatePopupHandler(features -> null);

        final boolean[] suppressNavGuard = {false};
        final boolean[] closingGuard = {false};
        final boolean[] highlightBridgeInjected = {false};
        final String[] lastPersistedHighlightKey = {null};

        Runnable pushPdfFragment = () -> {
            try {
                int p = Math.min(Math.max(1, currentPage[0] + 1), pageCount);
                int z = Math.min(400, Math.max(25, zoomPercent[0]));
                zoomPercent[0] = z;
                String raw = pdfHttpBaseRef;

                // Inject saved highlight data (texts + geometry) so the web shell can render them.
                // (Page-numbered callbacks use 1-based page numbers; DAO uses 0-based page indices.)
                try {
                    int pageIndex0 = currentPage[0];
                    List<ReadingHighlightDao.HighlightRow> rows = findHighlightRowsForPage(borrowId, user.getId(), pageIndex0);
                    List<String> highlightTexts = new ArrayList<>();
                    List<String> highlightRectsJsons = new ArrayList<>();
                    for (ReadingHighlightDao.HighlightRow r : rows) {
                        highlightTexts.add(r.highlightText());
                        String rects = r.highlightRectsJson();
                        // Keep both arrays strictly aligned by index so the web layer can
                        // treat rects[i] as corresponding to texts[i].
                        highlightRectsJsons.add((rects != null && !rects.isBlank()) ? rects : "[]");
                    }
                    String jsTexts = toJsStringArray(highlightTexts);
                    String jsRects = toJsStringArray(highlightRectsJsons);
                    webEngine.executeScript("setPendingHighlightsForPage(" + p + ", " + jsTexts + ", " + jsRects + ")");
                } catch (Exception ignored) {
                    // best-effort only; reader must never break
                }

                // PDF.js fallback: render the requested page+zoom directly.
                webEngine.executeScript("setPdfUrlAndPage('" + escapeForSingleQuotedJs(raw) + "', " + p + ", " + z + ")");
            } catch (Exception ex) {
                // WebKit may throw if document not ready
            }
        };

        Runnable loadHostThenPdf = () -> webEngine.load(hostLocation);

        Runnable refreshPdfInView = () -> {
            String loc = webEngine.getLocation();
            if (loc != null && loc.contains("pdf-reader/host.html")) {
                pushPdfFragment.run();
            } else {
                loadHostThenPdf.run();
            }
        };

        ChangeListener<Worker.State> loadListener = (obs, oldV, newV) -> {
            if (newV == Worker.State.SUCCEEDED) {
                // Inject bridge as soon as host.html is loaded.
                // Avoid relying on fragile location substring checks; if the bridge isn't injected,
                // highlights render visually but are never persisted to SQLite.
                try {
                    JSObject window = (JSObject) webEngine.executeScript("window");
                    window.setMember("javaBridge", new HighlightBridge(borrowId, user.getId()));
                    highlightBridgeInjected[0] = true;
                } catch (Exception ignored) {
                    // bridge injection is best-effort
                }
                pushPdfFragment.run();
            }
        };
        webEngine.getLoadWorker().stateProperty().addListener(loadListener);

        webEngine.locationProperty().addListener((obs, oldLoc, newLoc) -> {
            if (suppressNavGuard[0] || newLoc == null) {
                return;
            }
            if (newLoc.startsWith("about:")) {
                return;
            }
            if (isAllowedLocation(newLoc, hostLocation, pdfHttpBaseRef, pdfPath)) {
                return;
            }
            suppressNavGuard[0] = true;
            Platform.runLater(() -> {
                try {
                    String loc = webEngine.getLocation();
                    if (loc != null && loc.contains("pdf-reader/host.html")) {
                        // Keep current context; just re-render the current page.
                        pushPdfFragment.run();
                    } else {
                        loadHostThenPdf.run();
                    }
                } finally {
                    suppressNavGuard[0] = false;
                }
            });
        });

        Label pageLabel = new Label();
        Label bookmarkLabel = new Label();
        Runnable updatePageLabel = () -> {
            pageLabel.setText("Page " + (currentPage[0] + 1) + " / " + pageCount);
            bookmarkLabel.setText("Saved bookmark: Page " + (bookmarkedPage[0] + 1));
        };

        Spinner<Integer> zoomSpinner = new Spinner<>();
        zoomSpinner.setPrefWidth(100);
        zoomSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(25, 400, zoomPercent[0], 5));
        zoomSpinner.getValueFactory().setValue(zoomPercent[0]);
        PauseTransition zoomDebounce = new PauseTransition(Duration.millis(120));
        zoomDebounce.setOnFinished(ev -> refreshPdfInView.run());

        zoomSpinner.valueProperty().addListener((o, ov, nv) -> {
            if (nv == null) {
                return;
            }
            zoomPercent[0] = nv;
            checkpoint.run();
            zoomDebounce.playFromStart();
        });
        zoomSpinner.setEditable(true);

        Button prevBtn = new Button("Previous");
        Button nextBtn = new Button("Next");
        prevBtn.getStyleClass().add("secondary-button");
        nextBtn.getStyleClass().add("secondary-button");
        prevBtn.setOnAction(e -> {
            if (currentPage[0] > 0) {
                currentPage[0]--;
                checkpoint.run();
                refreshPdfInView.run();
                updatePageLabel.run();
            }
        });
        nextBtn.setOnAction(e -> {
            if (currentPage[0] < pageCount - 1) {
                currentPage[0]++;
                checkpoint.run();
                refreshPdfInView.run();
                updatePageLabel.run();
            }
        });

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("secondary-button");
        closeBtn.setOnAction(e -> {
            saveProgress.run();
            readerStage.close();
        });

        Button saveHighlightBtn = new Button("Save Highlight");
        saveHighlightBtn.getStyleClass().add("primary-button");
        saveHighlightBtn.setOnAction(e -> {
            try {
                Object result = webEngine.executeScript("saveSelectionAsHighlight()");
                if (!Boolean.TRUE.equals(result)) {
                    Alert a = new Alert(Alert.AlertType.WARNING);
                    a.setTitle("Save Highlight");
                    a.setHeaderText(null);
                    a.setContentText("No text selection found (or reader not ready yet).");
                    a.showAndWait();
                    return;
                }

                Object textObj = webEngine.executeScript("window.__lastSelectionText || ''");
                Object rectsObj = webEngine.executeScript("window.__lastSelectionRectsJson || '[]'");
                Object pageNumObj = webEngine.executeScript("window.__lastSelectionPageNumber || 0");

                String text = textObj == null ? "" : String.valueOf(textObj);
                String rectsJson = rectsObj == null ? "[]" : String.valueOf(rectsObj);
                rectsJson = rectsJson == null ? "[]" : rectsJson.trim();
                if (rectsJson.isEmpty()) rectsJson = "[]";

                int pageNumber = 0;
                if (pageNumObj instanceof Number n) {
                    pageNumber = n.intValue();
                } else if (pageNumObj != null) {
                    try {
                        pageNumber = Integer.parseInt(pageNumObj.toString());
                    } catch (Exception ignored) {}
                }
                if (pageNumber <= 0) {
                    // Fallback: use current UI page if the JS snapshot page number is missing.
                    pageNumber = currentPage[0] + 1;
                }

                String trimmedText = text.trim();
                if (trimmedText.isEmpty()) {
                    Alert a = new Alert(Alert.AlertType.WARNING);
                    a.setTitle("Save Highlight");
                    a.setHeaderText(null);
                    a.setContentText("No text selection found (or reader not ready yet).");
                    a.showAndWait();
                    return;
                }

                int pageIndex0 = Math.max(0, pageNumber - 1);

                // De-dup: repeated clicks shouldn't insert multiple identical rows.
                String dedupeKey = pageNumber + "|" + trimmedText + "|" + rectsJson;
                if (dedupeKey.equals(lastPersistedHighlightKey[0])) {
                    return;
                }

                ReadingHighlightDao.insert(
                        borrowId,
                        user.getId(),
                        pageIndex0,
                        trimmedText,
                        rectsJson,
                        Instant.now().toString()
                );
                lastPersistedHighlightKey[0] = dedupeKey;

            } catch (Exception ignored) {
                // reader shell not fully initialized yet
            }
        });

        Button bookmarkBtn = new Button("Bookmark");
        bookmarkBtn.getStyleClass().add("secondary-button");
        bookmarkBtn.setOnAction(e -> {
            // Persist the current page as the "saved bookmark" resume point.
            bookmarkedPage[0] = currentPage[0];
            saveProgress.run();
            updatePageLabel.run();
        });

        Button goToBookmarkBtn = new Button("Go to bookmark");
        goToBookmarkBtn.getStyleClass().add("secondary-button");
        goToBookmarkBtn.setOnAction(e -> {
            if (bookmarkedPage[0] == currentPage[0]) {
                return;
            }
            currentPage[0] = bookmarkedPage[0];
            checkpoint.run();
            refreshPdfInView.run();
            updatePageLabel.run();
        });

        final Timeline[] dueWatchRef = new Timeline[1];
        Runnable stopDueWatch = () -> {
            if (dueWatchRef[0] != null) {
                dueWatchRef[0].stop();
                dueWatchRef[0] = null;
            }
        };
        Consumer<String> closeReaderWithMessage = message -> {
            if (closingGuard[0]) {
                return;
            }
            closingGuard[0] = true;
            stopDueWatch.run();
            flushReadSeconds.run();
            checkpoint.run();
            saveProgress.run();
            readerStage.close();
            if (message != null && !message.isBlank()) {
                Alert.AlertType severity = message.contains("auto-returned") || message.contains("past its due")
                        ? Alert.AlertType.WARNING
                        : Alert.AlertType.INFORMATION;
                Alert a = new Alert(severity);
                a.setTitle("Reader closed");
                a.setHeaderText(null);
                a.setContentText(message);
                a.showAndWait();
            }
        };
        Runnable checkBorrowStillValid = () -> {
            if (closingGuard[0]) return;
            try {
                var opt = BorrowDao.findById(borrowId);
                if (opt.isEmpty()) {
                    Platform.runLater(() -> closeReaderWithMessage.accept(
                            "This borrow record is no longer in the system; the reader was closed."));
                    return;
                }
                Borrow b = opt.get();
                if (b.getBorrowerUserId() != user.getId() || b.getBookId() != bookId) {
                    Platform.runLater(() -> closeReaderWithMessage.accept(
                            "This loan no longer matches your account; the reader was closed."));
                    return;
                }
                if (b.getReturnedAt() != null && !b.getReturnedAt().isEmpty()) {
                    Platform.runLater(() -> closeReaderWithMessage.accept(
                            "This book was returned (or auto-returned). The reader was closed."));
                    return;
                }
                if (b.getDueAt() != null && !b.getDueAt().isEmpty()) {
                    try {
                        Instant due = Instant.parse(b.getDueAt());
                        Instant now = Instant.now();
                        if (!now.isBefore(due)) {
                            Platform.runLater(() -> {
                                flushReadSeconds.run();
                                try {
                                    BorrowService.processDueReturns();
                                } catch (SQLException ignored) {
                                }
                                String msg;
                                try {
                                    var after = BorrowDao.findById(borrowId);
                                    if (after.isPresent() && (after.get().getReturnedAt() == null
                                            || after.get().getReturnedAt().isEmpty())) {
                                        msg = "This loan is past its due time, but it could not be auto-closed yet. "
                                                + "Please return the book from My Borrowed Books or try again shortly.";
                                    } else {
                                        msg = "The loan period ended; the book was auto-returned and is no longer available to read.";
                                    }
                                } catch (SQLException ignored) {
                                    msg = "The loan period ended; the reader was closed.";
                                }
                                closeReaderWithMessage.accept(msg);
                            });
                            return;
                        }
                        long secToDue = java.time.Duration.between(now, due).getSeconds();
                        if (secToDue > 0 && secToDue <= 120 && dueWatchRef[0] != null) {
                            Platform.runLater(() -> {
                                if (dueWatchRef[0] != null) {
                                    dueWatchRef[0].setRate(secToDue <= 45 ? 5.0 : 3.0);
                                }
                            });
                        }
                    } catch (Exception ignored) {
                    }
                }
            } catch (SQLException ignored) {
            }
        };

        dueWatchRef[0] = new Timeline(new KeyFrame(Duration.seconds(20), ev -> checkBorrowStillValid.run()));
        dueWatchRef[0].setCycleCount(Timeline.INDEFINITE);
        dueWatchRef[0].play();

        Timeline readAccumTimer = new Timeline(new KeyFrame(Duration.seconds(15), ev -> flushReadSeconds.run()));
        readAccumTimer.setCycleCount(Timeline.INDEFINITE);
        readAccumTimer.play();

        Timeline checkpointTimer = new Timeline(new KeyFrame(Duration.seconds(10), ev -> checkpoint.run()));
        checkpointTimer.setCycleCount(Timeline.INDEFINITE);
        checkpointTimer.play();

        readerStage.focusedProperty().addListener((o, was, focused) -> {
            if (Boolean.TRUE.equals(focused)) {
                checkBorrowStillValid.run();
            }
        });

        readerStage.setOnCloseRequest(ev -> {
            if (dueWatchRef[0] != null) {
                dueWatchRef[0].stop();
            }
            readAccumTimer.stop();
            checkpointTimer.stop();
            checkpoint.run();
            saveProgress.run();
            // Reader is opened as a modal/popup over "My Borrowed Books", so restore that screen after a normal close.
            SessionService.save("MY_BORROWS", user.getId());
            if (pdfServerRef != null) {
                pdfServerRef.stop(0);
            }
            if (httpExecutorRef != null) {
                // Shutdown policy: graceful first, then forced if needed.
                httpExecutorRef.shutdown();
                try {
                    if (!httpExecutorRef.awaitTermination(2, TimeUnit.SECONDS)) {
                        httpExecutorRef.shutdownNow();
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    httpExecutorRef.shutdownNow();
                }
            }
        });

        Label hint = new Label("Select text with the mouse. Click 'Save Highlight' to persist it. "
                + "Use 'Bookmark' to save a resume point. Closing also saves your current page. "
                + "External links stay in the reader.");
        hint.setWrapText(true);
        hint.getStyleClass().add("login-hint");

        VBox toolbar = new VBox(6);
        HBox toolbarRow1 = new HBox(10,
                prevBtn, nextBtn, pageLabel,
                bookmarkLabel);
        toolbarRow1.setAlignment(Pos.CENTER_LEFT);

        HBox toolbarRow2 = new HBox(10,
                bookmarkBtn, goToBookmarkBtn,
                new Label("Zoom (%):"), zoomSpinner,
                saveHighlightBtn,
                closeBtn);
        toolbarRow2.setAlignment(Pos.CENTER_LEFT);

        toolbar.getChildren().addAll(toolbarRow1, toolbarRow2);
        toolbar.setPadding(new Insets(4, 0, 4, 0));

        VBox centerBox = new VBox(6, toolbar, webView);
        VBox.setVgrow(webView, Priority.ALWAYS);
        centerBox.setPadding(new Insets(12));

        BorderPane root = new BorderPane();
        Label header = new Label(title);
        header.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        VBox topBox = new VBox(6,
                header,
                new Label("Logged in as " + user.getFullName()),
                centerBox);
        topBox.setPadding(new Insets(10));
        root.setTop(topBox);
        root.setBottom(hint);
        BorderPane.setAlignment(hint, Pos.CENTER_LEFT);
        root.setPadding(new Insets(0, 10, 10, 10));
        root.getStyleClass().add("app-root");

        Scene sc = new Scene(root, 1100, 820);
        var css = PdfReaderScreen.class.getResource("/app.css");
        if (css != null) {
            sc.getStylesheets().add(css.toExternalForm());
        }
        readerStage.setScene(sc);

        sc.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.LEFT) {
                prevBtn.fire();
                ev.consume();
            } else if (ev.getCode() == KeyCode.RIGHT) {
                nextBtn.fire();
                ev.consume();
            }
        });

        updatePageLabel.run();
        readerStage.show();
        loadHostThenPdf.run();
    }

    private static List<String> findHighlightTextsForPage(long borrowId, long userId, int pageIndex0) {
        List<ReadingHighlightDao.HighlightRow> rows = findHighlightRowsForPage(borrowId, userId, pageIndex0);
        List<String> out = new ArrayList<>();
        for (var r : rows) {
            out.add(r.highlightText());
        }
        return out;
    }

    private static List<ReadingHighlightDao.HighlightRow> findHighlightRowsForPage(long borrowId, long userId, int pageIndex0) {
        try {
            List<ReadingHighlightDao.HighlightRow> rows = ReadingHighlightDao.findByBorrow(borrowId, userId);
            List<ReadingHighlightDao.HighlightRow> out = new ArrayList<>();
            for (var r : rows) {
                if (r.pageIndex() == pageIndex0) {
                    out.add(r);
                }
            }
            return out;
        } catch (SQLException ignored) {
            return List.of();
        }
    }

    private static String toJsStringArray(List<String> items) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        if (items != null) {
            boolean first = true;
            for (String s : items) {
                if (!first) sb.append(',');
                first = false;
                sb.append('\'').append(escapeForSingleQuotedJs(s)).append('\'');
            }
        }
        sb.append(']');
        return sb.toString();
    }

    private static boolean isAllowedLocation(String loc, String hostLocation, String pdfHttpBase, Path pdfPath) {
        try {
            // Always allow the base PDF file URL (fragments may vary by viewer).
            String pdfBaseFile = pdfPath.toUri().toString();
            if (loc != null && (loc.startsWith(pdfBaseFile) || (pdfHttpBase != null && loc.startsWith(pdfHttpBase)))) {
                return true;
            }
            if (loc.startsWith(hostLocation)) {
                return true;
            }
            String noFrag = loc.split("#")[0];
            URI u = URI.create(noFrag);
            if ("file".equalsIgnoreCase(u.getScheme())) {
                Path p = Paths.get(u).normalize();
                return p.equals(pdfPath.normalize());
            }

            // Block arbitrary external links; only allow localhost (our local HTTP PDF server).
            if ("http".equalsIgnoreCase(u.getScheme()) || "https".equalsIgnoreCase(u.getScheme())) {
                String host = u.getHost();
                if (host != null && (host.equalsIgnoreCase("localhost") || host.equalsIgnoreCase("127.0.0.1"))) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static String escapeForSingleQuotedJs(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\r", "").replace("\n", "\\n");
    }
}
