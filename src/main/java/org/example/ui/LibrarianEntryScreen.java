package org.example.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.example.app.Navigator;

/**
 * Librarian portal entry: placeholder for Login and Register (to be implemented).
 */
public final class LibrarianEntryScreen {

    private LibrarianEntryScreen() {}

    public static Scene create(Navigator navigator) {
        Label title = new Label("Librarian Portal");
        title.getStyleClass().add("screen-title");

        Button backBtn = new Button("Back to Welcome");
        backBtn.setOnAction(e -> navigator.showWelcome());

        VBox root = new VBox(20, title, backBtn);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        java.net.URL cssResource = LibrarianEntryScreen.class.getResource("/app.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }
        return scene;
    }
}
