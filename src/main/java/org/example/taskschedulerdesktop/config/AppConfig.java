package org.example.taskschedulerdesktop.config;

import javafx.stage.Stage;
import javafx.util.Callback;
import org.example.taskschedulerdesktop.controllers.projects.*;
import org.example.taskschedulerdesktop.controllers.tasks.*;
import org.example.taskschedulerdesktop.database.DatabaseConnection;
import org.example.taskschedulerdesktop.repository.project.H2ProjectRepository;
import org.example.taskschedulerdesktop.repository.project.ProjectRepository;
import org.example.taskschedulerdesktop.repository.task.H2TaskRepository;
import org.example.taskschedulerdesktop.repository.task.TaskRepository;
import org.example.taskschedulerdesktop.service.project.AsyncProjectService;
import org.example.taskschedulerdesktop.service.project.ProjectCardService;
import org.example.taskschedulerdesktop.service.project.ProjectService;
import org.example.taskschedulerdesktop.service.project.ProjectServiceImpl;
import org.example.taskschedulerdesktop.service.task.AsyncTaskService;
import org.example.taskschedulerdesktop.service.task.TaskCardService;
import org.example.taskschedulerdesktop.service.task.TaskServiceImpl;
import org.example.taskschedulerdesktop.service.task.TaskService;

public class AppConfig {

    private static AppConfig instance;

    private Stage primaryStage;

    private final DatabaseConnection databaseConnection;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final TaskService taskService;
    private final TaskCardService taskCardService;
    private final AsyncTaskService asyncTaskService;
    private final ProjectService projectService;
    private final ProjectCardService projectCardService;
    private final AsyncProjectService asyncProjectService;

    private final Callback<Class<?>, Object> controllerFactory;

    private AppConfig() {
        this.databaseConnection = DatabaseConnection.getInstance();
        this.taskRepository = new H2TaskRepository(databaseConnection);
        this.projectRepository = new H2ProjectRepository(databaseConnection);
        this.taskService = new TaskServiceImpl(taskRepository);
        this.taskCardService = new TaskCardService();
        this.asyncTaskService = new AsyncTaskService(taskService, taskCardService);
        this.projectService = new ProjectServiceImpl(projectRepository, taskService);
        this.projectCardService = new ProjectCardService();
        this.asyncProjectService = new AsyncProjectService(projectService, projectCardService);



        this.controllerFactory = clazz -> {
            if (clazz == TaskTableController.class) {
                return new TaskTableController(asyncTaskService);
            }
            if (clazz == ProjectDetailsController.class) {
                return new ProjectDetailsController(asyncTaskService, asyncProjectService);
            }
            if (clazz == CreateTaskController.class) {
                return new CreateTaskController(asyncTaskService, asyncProjectService);
            }
            if (clazz == TaskDetailsController.class) {
                return new TaskDetailsController(asyncTaskService);
            }
            if (clazz == ProjectsController.class) {
                return new ProjectsController(asyncProjectService);
            }
            if (clazz == CreateProjectController.class) {
                return new CreateProjectController(asyncProjectService);
            }
            if (clazz == EditProjectController.class) {
                return new EditProjectController(asyncProjectService);
            }
            if (clazz == ProjectCardController.class) {
                return new ProjectCardController(asyncProjectService);
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

    public AsyncTaskService getAsyncTaskService() {
        return asyncTaskService;
    }

    public AsyncProjectService getAsyncProjectService() {
        return asyncProjectService;
    }
}
