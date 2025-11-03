package com.test.video;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import java.net.URL;

public class TestCamera extends Application {

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("내 웹캠 띄우기 (Maven)");

        WebView webView = new WebView();
        WebEngine webEngine = webView.getEngine();
        webEngine.setJavaScriptEnabled(true);

        URL url = getClass().getResource("/camera.html");
        if (url != null) {
            webEngine.load(url.toExternalForm());
        } else {
            System.err.println("오류: /camera.html 파일을 찾을 수 없습니다.");
        }

        BorderPane root = new BorderPane(webView);
        Scene scene = new Scene(root, 640, 480);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}