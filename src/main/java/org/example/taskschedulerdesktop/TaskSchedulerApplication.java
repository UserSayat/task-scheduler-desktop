package org.example.taskschedulerdesktop;

import javafx.application.Application;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.shared.database.DbConfig;
import org.example.taskschedulerdesktop.shared.database.DatabaseConnection;
import org.example.taskschedulerdesktop.shared.navigation.NavigationManager;
import org.example.taskschedulerdesktop.shell.AppShell;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

public class TaskSchedulerApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(TaskSchedulerApplication.class);

    private AppShell shell;

    @Override
    public void init() {
        loadFont("/org/example/taskschedulerdesktop/fonts/Inter_18pt-Regular.ttf");
        loadFont("/org/example/taskschedulerdesktop/fonts/Inter_18pt-Medium.ttf");
        loadFont("/org/example/taskschedulerdesktop/fonts/Inter_18pt-SemiBold.ttf");
        loadFont("/org/example/taskschedulerdesktop/fonts/Lora-Regular.ttf");
        loadFont("/org/example/taskschedulerdesktop/fonts/JetBrainsMono-Regular.ttf");
    }

    @Override
    public void start(Stage primaryStage) {
        Flyway.configure()
                .dataSource(DbConfig.getDbUrl(), "sa", "")
                .locations("classpath:migration")
                .load()
                .migrate();

        // Инициализация пула соединений
        DatabaseConnection.getInstance();

        AppConfig config = AppConfig.getInstance();
        config.setPrimaryStage(primaryStage);

        shell = new AppShell(primaryStage, config);
        config.setAppShell(shell);
        shell.start();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private void loadFont(String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                log.error("File not found: {}", path);
                return;
            }
            Font font = Font.loadFont(is, 12);
            if (font != null) {
                log.debug("Font loaded: {} → '{}'", path, font.getFamily());
            } else {
                log.warn("File read, but font not recognized: {}", path);
            }
        } catch (Exception e) {
            log.error("Font loading error: {}", path, e);
        }
    }

    @Override
    public void stop() {
        if (shell != null) {
            shell.stop();
        }
        NavigationManager.dispose();
        DatabaseConnection.getInstance().close();
    }
}
