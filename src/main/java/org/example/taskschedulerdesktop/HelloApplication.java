package org.example.taskschedulerdesktop;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class HelloApplication extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/org/example/taskschedulerdesktop/view/main.fxml")
        );
        Parent root = loader.load();

        Scene scene = new Scene(root, 900, 600);

        try {
            scene.getStylesheets().add(
                    getClass().getResource("/org/example/taskschedulerdesktop/styles/style.css").toExternalForm());
        } catch (Exception e) {
            System.out.println("CSS не найден");
        }



        primaryStage.setTitle("Корпоративный планировщик");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}