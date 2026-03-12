package com.library;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Stage;

import java.io.*;
import java.nio.file.*;

import com.library.ui.LoginMenu;

public class Main extends Application {
    private static final String APP_NAME = "Library Management System";

    @Override
    public void start(Stage stage) throws Exception
    {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
        Parent root = loader.load();

        LoginMenu controller = loader.getController();
        controller.setPrimaryStage(stage);


        //Setup
        stage.setTitle(APP_NAME);
        stage.setScene(new Scene(root, 600, 400));
        stage.setResizable(true);
        stage.show();

        }
    public static void main(String[] args) {
        launch(args);
    }
}