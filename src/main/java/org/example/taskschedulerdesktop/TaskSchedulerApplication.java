package org.example.taskschedulerdesktop;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.database.DatabaseConnection;
import org.example.taskschedulerdesktop.navigation.NavigationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URL;

public class TaskSchedulerApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(TaskSchedulerApplication.class);

    private static final String[] STYLES = {
            "/org/example/taskschedulerdesktop/styles/style.css",
            "/org/example/taskschedulerdesktop/styles/base.css",
            "/org/example/taskschedulerdesktop/styles/row_task_table.css",
            "/org/example/taskschedulerdesktop/styles/task_table_view.css"
    };

    @Override
    public void init() {
        loadFont("/org/example/taskschedulerdesktop/fonts/Inter_18pt-Regular.ttf");
        loadFont("/org/example/taskschedulerdesktop/fonts/Inter_18pt-Medium.ttf");
        loadFont("/org/example/taskschedulerdesktop/fonts/Inter_18pt-SemiBold.ttf");
        loadFont("/org/example/taskschedulerdesktop/fonts/Lora-Regular.ttf");
        loadFont("/org/example/taskschedulerdesktop/fonts/JetBrainsMono-Regular.ttf");
    }

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

        for (String cssPath : STYLES) {
            URL cssUrl = getClass().getResource(cssPath);
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
                log.debug("CSS: {}", cssPath);
            } else {
                log.error("Not found: {}", cssPath);
            }
        }

        primaryStage.setTitle("Корпоративный планировщик");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private void loadFont(String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is != null) {
                Font font = Font.loadFont(is, 12);
                if (font != null) {
                    log.info("Name: '{}'", font.getName());       // "DM Serif Display"
                    log.info("Family: '{}'", font.getFamily());   // "DM Serif Display"
                    log.info("Style: '{}'", font.getStyle());
                    log.debug("Font loaded: {} → '{}'", path, font.getFamily());
                } else {
                    log.warn("File read, but font not recognized: {}", path);
                }
            } else {
                log.error("File not found: {}", path);
            }
        } catch (Exception e) {
            log.error("Font loading error: {}", path, e);
        }
    }

    @Override
    public void stop() {
        NavigationManager.dispose();
        DatabaseConnection.getInstance().close();
    }
}