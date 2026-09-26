package org.example.taskschedulerdesktop.controllers.review;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.controllers.Shutdownable;
import org.example.taskschedulerdesktop.dto.projects.ProjectListCell;
import org.example.taskschedulerdesktop.listeners.EventBus;
import org.example.taskschedulerdesktop.listeners.TaskChangedEvent;
import org.example.taskschedulerdesktop.models.Project;
import org.example.taskschedulerdesktop.navigation.NavigationManager;
import org.example.taskschedulerdesktop.service.project.AsyncProjectService;
import org.example.taskschedulerdesktop.service.task.AsyncTaskService;
import org.example.taskschedulerdesktop.utils.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.Consumer;

public class DashboardController implements Shutdownable {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final AsyncProjectService asyncProjectService;
    private final AsyncTaskService asyncTaskService;

    @FXML private Label statAll;
    @FXML private Label statInProgress;
    @FXML private Label statReview;
    @FXML private Label statDone;

    @FXML private ListView<Project> projectListView;

    @FXML private VBox taskTableContainerVBox;

    private final Consumer<TaskChangedEvent> taskUpdateListener = event -> {
        refreshStat();
    };

    public DashboardController(AsyncProjectService asyncProjectService, AsyncTaskService asyncTaskService) {
        this.asyncProjectService = asyncProjectService;
        this.asyncTaskService = asyncTaskService;
    }

    @FXML
    public void initialize() {
        EventBus.getInstance().subscribe(TaskChangedEvent.class, taskUpdateListener);

        refreshStat();
        loadProjects();
        loadTaskTable();
    }

    private void loadProjects() {
        log.debug("loadProjects()");
        asyncProjectService.findAllProjects(
                projects -> {
                    ObservableList<Project> observableList = FXCollections.observableList(projects);
                    log.debug("observableList size: {}", observableList.size());
                    projectListView.setItems(observableList);
                    log.debug("projectListView size : {}", projectListView.getItems().size());
                    projectListView.setCellFactory(listView -> new ProjectListCell());
                },
                error -> {
                    log.error("Error loading projects", error);
                }
        );
    }

    private void loadTaskTable() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/example/taskschedulerdesktop/view/task_table.fxml")
            );

            loader.setControllerFactory(AppConfig.getInstance().getControllerFactory());

            Node taskTable = loader.load();
            taskTableContainerVBox.getChildren().add(taskTable);

            log.debug("Task table loaded successfully");
        } catch (IOException e) {
            log.error("Error loading task table", e);
        }
    }

    private void refreshStat() {
        asyncTaskService.findAllTasksView(
                tasks -> {
                    log.debug("Found {} tasks", tasks.size());
                    statAll.setText(String.valueOf(tasks.size()));
                },
                error -> {
                    NavigationManager.showToast("Ошибка загрузки статистики", "error");
                    log.error(error.getMessage());
                }
        );

        asyncTaskService.countTasksByStatus(TaskStatus.IN_PROGRESS,
                amountOfTasks -> {
                    log.debug("Found {} tasks, task status: {}", amountOfTasks, TaskStatus.IN_PROGRESS);
                    statInProgress.setText(String.valueOf(amountOfTasks));
                },
                error -> {
                    NavigationManager.showToast("Ошибка загрузки статистики", "error");
                    log.error("Amount of tasks loading error, task status: {}", TaskStatus.IN_PROGRESS);
                })
        ;

        asyncTaskService.countTasksByStatus(TaskStatus.UNDER_REVIEW,
                amountOfTasks -> {
                    log.debug("Found {} tasks, task status: {}", amountOfTasks, TaskStatus.UNDER_REVIEW);
                    statReview.setText(String.valueOf(amountOfTasks));
                },
                error -> {
                    NavigationManager.showToast("Ошибка загрузки статистики", "error");
                    log.error("Amount of tasks loading error, task status: {}", TaskStatus.UNDER_REVIEW);
                })
        ;

        asyncTaskService.countTasksByStatus(TaskStatus.COMPLETED,
                amountOfTasks -> {
                    log.debug("Found {} tasks, task status: {}", amountOfTasks, TaskStatus.COMPLETED);
                    statDone.setText(String.valueOf(amountOfTasks));
                },
                error -> {
                    NavigationManager.showToast("Ошибка загрузки статистики", "error");
                    log.error("Amount of tasks loading error, task status: {}", TaskStatus.COMPLETED);
                })
        ;
    }

    @Override
    public void shutdown() {
        EventBus.getInstance().unsubscribe(TaskChangedEvent.class, taskUpdateListener);
    }
}
