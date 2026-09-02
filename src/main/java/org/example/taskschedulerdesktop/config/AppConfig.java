package org.example.taskschedulerdesktop.config;

import javafx.stage.Stage;
import javafx.util.Callback;
import org.example.taskschedulerdesktop.controllers.CreateTaskModalWindowController;
import org.example.taskschedulerdesktop.controllers.ProjectExtendedPageController;
import org.example.taskschedulerdesktop.controllers.TaskController;
import org.example.taskschedulerdesktop.database.DatabaseConnection;
import org.example.taskschedulerdesktop.repository.H2TaskRepository;
import org.example.taskschedulerdesktop.repository.TaskRepository;
import org.example.taskschedulerdesktop.service.TaskServiceImpl;
import org.example.taskschedulerdesktop.service.TaskService;

public class AppConfig {

    private static AppConfig instance;

    private Stage primaryStage;

    private final DatabaseConnection databaseConnection;
    private final TaskRepository taskRepository;
    private final TaskService taskService;

    private final Callback<Class<?>, Object> controllerFactory;

    private AppConfig() {
        this.databaseConnection = DatabaseConnection.getInstance();
        this.taskRepository = new H2TaskRepository(databaseConnection);
        this.taskService = new TaskServiceImpl(taskRepository);

        this.controllerFactory = clazz -> {
            if (clazz == TaskController.class) {
                return new TaskController(taskService);
            }
            if (clazz == ProjectExtendedPageController.class) {
                return new ProjectExtendedPageController(taskService);
            }
            if (clazz == CreateTaskModalWindowController.class) {
                return new CreateTaskModalWindowController(taskService);
            }
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    public static AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public Callback<Class<?>, Object> getControllerFactory() {
        return controllerFactory;
    }

    public DatabaseConnection getDatabaseConnection() {
        return databaseConnection;
    }
}
