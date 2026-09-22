package org.example.taskschedulerdesktop.controllers.projects;

import javafx.concurrent.Service;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.controllers.Shutdownable;
import org.example.taskschedulerdesktop.dto.TaskView;
import org.example.taskschedulerdesktop.listeners.EventBus;
import org.example.taskschedulerdesktop.listeners.TaskChangedEvent;
import org.example.taskschedulerdesktop.models.Project;
import org.example.taskschedulerdesktop.navigation.ContextAware;
import org.example.taskschedulerdesktop.service.project.AsyncProjectService;
import org.example.taskschedulerdesktop.service.task.AsyncTaskService;
import org.example.taskschedulerdesktop.utils.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

public class ProjectExtendedPageController implements Shutdownable, ContextAware {

    private AsyncTaskService asyncTaskService;
    private AsyncProjectService asyncProjectService;
    private static final Logger log = LoggerFactory.getLogger(ProjectExtendedPageController.class);

    @FXML private Label projectNameLabel;
    @FXML private Label projectSupervisorLabel;
    @FXML private Label numberOfTasksLabel;
    @FXML private Label completedTasksLabel;
    @FXML private Label remainingTasksLabel;
    @FXML private Label percentOfCompletionLabel;
    @FXML private ProgressBar progressBar;

    @FXML private VBox newTasksVBox;
    @FXML private VBox tasksInProgressVBox;
    @FXML private VBox tasksUnderReviewVBox;
    @FXML private VBox completedTasksVBox;

    @FXML private ProgressIndicator newTasksLoadingIndicator;
    @FXML private ProgressIndicator tasksInProgressLoadingIndicator;
    @FXML private ProgressIndicator tasksUnderReviewLoadingIndicator;
    @FXML private ProgressIndicator completedTasksLoadingIndicator;

    private Service<List<Node>> newTasksLoader;
    private Service<List<Node>> inProgressLoader;
    private Service<List<Node>> underReviewLoader;
    private Service<List<Node>> completedLoader;

    private Project context;

    private final Consumer<TaskChangedEvent> taskUpdateListener = event -> {
        if (context != null && event.getProjectId().equals(context.getId())) {
            refreshAllContainers();

            asyncProjectService.findProjectById(
                    context.getId(),
                    project -> {
                        this.context = project;
                        updateUI();
                    },
                    error -> log.error("Error updating project's data", error)
            );
        }
    };


    public ProjectExtendedPageController(AsyncTaskService taskService, AsyncProjectService projectService) {
        this.asyncTaskService = taskService;
        this.asyncProjectService = projectService;
    }

    private Service<List<Node>> setupLoaderService(TaskStatus status, VBox container, ProgressIndicator indicator) {

        Service<List<Node>> loaderService = asyncTaskService.createLoaderService(status);

        indicator.visibleProperty().bind(loaderService.runningProperty());

        loaderService.setOnSucceeded(event -> {
            container.getChildren().clear();
            List<Node> taskCards = loaderService.getValue();
            if (taskCards.isEmpty()) {
                container.getChildren().add(new Label("Нет задач"));
            } else {
                container.getChildren().addAll(taskCards);
            }
        });

        loaderService.setOnFailed(event -> {
            container.getChildren().clear();
            container.getChildren().add(new Label("Ошибка БД"));
            Throwable error = loaderService.getException();
            if (error != null) error.printStackTrace();
        });

        return loaderService;
    }

    public void refreshAllContainers() {
        newTasksLoader.restart();
        inProgressLoader.restart();
        underReviewLoader.restart();
        completedLoader.restart();
    }

    /**
     * Методы для принудительного обновления списка.
     * Его можно вызывать по нажатию отдельной кнопки "Обновить" или программно.
     */
    public void refreshNewTasks() { newTasksLoader.restart(); }
    public void refreshInProgressTasks() { inProgressLoader.restart(); }
    public void refreshUnderReviewTasks() { underReviewLoader.restart(); }
    public void refreshCompletedTasks() { completedLoader.restart(); }

    @FXML
    public void initialize() {
        EventBus.getInstance().subscribe(TaskChangedEvent.class, taskUpdateListener);

        newTasksLoader = setupLoaderService(TaskStatus.NEW, newTasksVBox, newTasksLoadingIndicator);
        inProgressLoader = setupLoaderService(TaskStatus.IN_PROGRESS, tasksInProgressVBox, tasksInProgressLoadingIndicator);
        underReviewLoader = setupLoaderService(TaskStatus.UNDER_REVIEW, tasksUnderReviewVBox, tasksUnderReviewLoadingIndicator);
        completedLoader = setupLoaderService(TaskStatus.COMPLETED, completedTasksVBox, completedTasksLoadingIndicator);

        refreshAllContainers();
    }

    @Override
    public void setContext(Object context) {
        log.debug("setContext: {}", context);
        log.debug("class = {}", context != null ? context.getClass().getName() : "null");

        if (context instanceof Project projectCard) {
            this.context = projectCard;

            asyncProjectService.findProjectById(
                    projectCard.getId(),
                    project -> {
                        this.context = project;
                        updateUI();
                    },
                    error -> log.error("Ошибка загрузки проекта", error)
            );
        } else {
            log.error("Context isn't an instance of Project");
        }
    }

    private void updateUI() {
        if (context == null) {
            log.error("Context not established");
            return;
        }

        this.projectNameLabel.setText(context.getName());
        this.projectSupervisorLabel.setText(context.getSupervisor());
        this.numberOfTasksLabel.setText(String.valueOf(context.getNumberOfTasks()));
        this.completedTasksLabel.setText(String.valueOf(context.getCompletedTasks()));
        this.remainingTasksLabel.setText(String.valueOf(context.getRemainingTasks()));
        this.percentOfCompletionLabel.setText(context.getPercentOfCompletion() + "%");
        this.progressBar.setProgress(context.getPercentOfCompletion() / 100.0);
    }

    @Override
    public void shutdown() {
        EventBus.getInstance().unsubscribe(TaskChangedEvent.class, taskUpdateListener);

        unregisterLoaderService(newTasksLoader);
        unregisterLoaderService(inProgressLoader);
        unregisterLoaderService(underReviewLoader);
        unregisterLoaderService(completedLoader);
    }

    private void unregisterLoaderService(Service<?> loader) {
        if (loader != null) {
            loader.setOnSucceeded(null);
            loader.setOnFailed(null);
        }
    }
}
