package org.example.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.example.app.Navigator;

/**
 * Welcome screen: choose Student/Staff, Author, or Librarian portal.
 */
public final class WelcomeScreen {

    private WelcomeScreen() {}

    public static Scene create(Navigator navigator) {
        Label title = new Label("E-Book Library System");
        title.getStyleClass().add("welcome-title");

        Button studentStaffBtn = new Button("Student / Staff Portal");
        studentStaffBtn.getStyleClass().add("portal-button");
        studentStaffBtn.setOnAction(e -> navigator.showStudentStaffPortal());

        Button authorBtn = new Button("Author Portal");
        authorBtn.getStyleClass().add("portal-button");
        authorBtn.setOnAction(e -> navigator.showAuthorPortal());

        Button librarianBtn = new Button("Librarian Portal");
        librarianBtn.getStyleClass().add("portal-button");
        librarianBtn.setOnAction(e -> navigator.showLibrarianPortal());

        VBox root = new VBox(20, title, studentStaffBtn, authorBtn, librarianBtn);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.getStyleClass().add("welcome-root");

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        java.net.URL cssResource = WelcomeScreen.class.getResource("/app.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }
        return scene;
    }
}
