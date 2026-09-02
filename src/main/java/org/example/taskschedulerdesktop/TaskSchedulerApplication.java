package org.example.taskschedulerdesktop;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.database.DatabaseConnection;

public class TaskSchedulerApplication extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {

        AppConfig.getInstance().setPrimaryStage(primaryStage);

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/org/example/taskschedulerdesktop/view/main.fxml")
        );
        Parent root = loader.load();

        Scene scene = new Scene(root, 1280, 720);

        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);

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

    @Override
    public void stop() {
        DatabaseConnection.getInstance().close();
    }
}

//Сначала создай локальную бд потом продолжай работу с контроллерами
//Ты остановился на TaskExtendedPageController т.к. нет данных
//Добавить синхронизацию с бд