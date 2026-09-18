package org.example.taskschedulerdesktop.controllers.projects;

import javafx.concurrent.Service;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.controllers.Shutdownable;
import org.example.taskschedulerdesktop.listeners.TaskUpdateListener;
import org.example.taskschedulerdesktop.models.ProjectCard;
import org.example.taskschedulerdesktop.models.Task;
import org.example.taskschedulerdesktop.navigation.ContextAware;
import org.example.taskschedulerdesktop.service.task.AsyncTaskService;
import org.example.taskschedulerdesktop.utils.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

public class ProjectExtendedPageController implements Shutdownable, ContextAware {

    private AsyncTaskService asyncTaskService;
    private static final Logger log = LoggerFactory.getLogger(ProjectExtendedPageController.class);

    @FXML private Label projectNameLabel;
    @FXML private Label projectSupervisorLabel;
    @FXML private Label numberOfTasksLabel;
    @FXML private Label completedTasksLabel;
    @FXML private Label remainingTasksLabel;
    @FXML private Label percentOfCompletionLabel;

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

    private ProjectCard context;

    private final Consumer<Task> taskUpdateListener = changedTask -> {

        asyncTaskService.invalidateCache(changedTask.getStatus());

        switch (changedTask.getStatus()) {
            case NEW -> refreshNewTasks();
            case IN_PROGRESS -> refreshInProgressTasks();
            case UNDER_REVIEW -> refreshUnderReviewTasks();
            case COMPLETED -> refreshCompletedTasks();
        }
    };


    public ProjectExtendedPageController(AsyncTaskService taskService) {
        this.asyncTaskService = taskService;
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
        TaskUpdateListener.subscribe(taskUpdateListener);

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

        if (context instanceof ProjectCard projectCard) {
            this.context = projectCard;
            updateUI();
        } else {
            log.error("Context isn't an instance of ProjectCard");
        }
    }

    private void updateUI() {
        if (context == null) {
            log.error("Context not established");
            return;
        }

        this.projectNameLabel.setText(context.getProjectName());
        this.projectSupervisorLabel.setText(context.getProjectSupervisor());
        this.numberOfTasksLabel.setText(String.valueOf(context.getNumberOfTasks()));
        this.completedTasksLabel.setText(String.valueOf(context.getCompletedTasks()));
        this.remainingTasksLabel.setText(String.valueOf(context.getRemainingTasks()));
        this.percentOfCompletionLabel.setText(context.getPercentOfCompletion() + "%");
    }

    @Override
    public void shutdown() {
        TaskUpdateListener.unsubscribe(taskUpdateListener);

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
