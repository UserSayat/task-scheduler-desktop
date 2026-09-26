package org.example.taskschedulerdesktop.controllers.projects;

import javafx.concurrent.Service;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.controllers.Shutdownable;
import org.example.taskschedulerdesktop.listeners.ChangeType;
import org.example.taskschedulerdesktop.listeners.EventBus;
import org.example.taskschedulerdesktop.listeners.ProjectChangedEvent;
import org.example.taskschedulerdesktop.listeners.TaskChangedEvent;
import org.example.taskschedulerdesktop.models.Project;
import org.example.taskschedulerdesktop.navigation.ContextAware;
import org.example.taskschedulerdesktop.navigation.NavigationManager;
import org.example.taskschedulerdesktop.navigation.Routes;
import org.example.taskschedulerdesktop.service.project.AsyncProjectService;
import org.example.taskschedulerdesktop.service.task.AsyncTaskService;
import org.example.taskschedulerdesktop.utils.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

public class ProjectDetailsController implements Shutdownable, ContextAware {

    private AsyncTaskService asyncTaskService;
    private AsyncProjectService asyncProjectService;
    private static final Logger log = LoggerFactory.getLogger(ProjectDetailsController.class);

    @FXML private Label projectNameLabel;
    @FXML private Label projectSupervisorLabel;
    @FXML private Label numberOfTasksLabel;
    @FXML private Label completedTasksLabel;
    @FXML private Label remainingTasksLabel;
    @FXML private Label percentOfCompletionLabel;
    @FXML private ProgressBar progressBar;

    @FXML private Button editProjectButton;
    @FXML private Button deleteProjectButton;
    @FXML private HBox confirmDeleteVBox;
    @FXML private Button confirmDeleteButton;
    @FXML private Button cancelDeleteButton;

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
        log.debug("ProjectDetailsController: TaskChangedEvent received: projectId={}", event.getProjectId());

        if (context != null && event.getProjectId().equals(context.getId())) {
            //TODO обновлять одну задачу, а не весь контейнер
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

    private final Consumer<ProjectChangedEvent> projectUpdateListener = event -> {
        log.debug("ProjectDetailsController: ProjectChangedEvent received: projectId={}", event.getProjectId());

        if (context == null && !event.getProjectId().equals(context.getId())) {
            return;
        }

        if (event.getChangeType() == ChangeType.UPDATED) {
            asyncProjectService.findProjectById(
                    context.getId(),
                    project -> {
                        this.context = project;
                        updateUI();
                    },
                    error -> log.error("Error updating project's data", error)
            );
            return;
        }

        if (event.getChangeType() == ChangeType.DELETED) {
            log.debug("Project deleted, navigating back to projects list");
            NavigationManager.navigateTo(Routes.PROJECTS);
        }
    };

    public ProjectDetailsController(AsyncTaskService taskService, AsyncProjectService projectService) {
        this.asyncTaskService = taskService;
        this.asyncProjectService = projectService;
    }

    private Service<List<Node>> setupLoaderService(TaskStatus status, VBox container, ProgressIndicator indicator) {

        if (context == null) {
            log.error("Context is null");
        }

        Service<List<Node>> loaderService = asyncTaskService.createLoaderService(context.getId(), status);

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
        EventBus.getInstance().subscribe(ProjectChangedEvent.class, projectUpdateListener);
    }

    @Override
    public void setContext(Object context) {
        log.debug("setContext: {}", context);
        log.debug("class = {}", context != null ? context.getClass().getName() : "null");

        if (context instanceof Project projectCard) {
            this.context = projectCard;
            updateUI();

            newTasksLoader = setupLoaderService(TaskStatus.NEW, newTasksVBox, newTasksLoadingIndicator);
            inProgressLoader = setupLoaderService(TaskStatus.IN_PROGRESS, tasksInProgressVBox, tasksInProgressLoadingIndicator);
            underReviewLoader = setupLoaderService(TaskStatus.UNDER_REVIEW, tasksUnderReviewVBox, tasksUnderReviewLoadingIndicator);
            completedLoader = setupLoaderService(TaskStatus.COMPLETED, completedTasksVBox, completedTasksLoadingIndicator);

            refreshAllContainers();

            asyncProjectService.findProjectById(
                    projectCard.getId(),
                    project -> {
                        this.context = project;
                        updateUI();
                    },
                    error -> log.error("Ошибка загрузки проекта", error)
            );

            editProjectButton.setOnAction(event -> {
                NavigationManager.openDialog(Routes.EDIT_PROJECT,
                        "РЕДАКТИРОВАНИЕ",
                        AppConfig.getInstance().getPrimaryStage(),
                        context);
            });

            deleteProjectButton.setOnAction(event -> {
                log.debug("Click on delete button");

                log.info("Open delete panel for project: {}", projectCard.getId());
                deleteProjectButton.setVisible(false);
                deleteProjectButton.setManaged(false);

                confirmDeleteVBox.setVisible(true);
                confirmDeleteVBox.setManaged(true);
            });

            confirmDeleteButton.setOnAction(event -> {
                asyncProjectService.deleteProject(
                        projectCard.getId(),
                        () -> {
                            NavigationManager.showToast("Проект удален", "info");
                        },
                        error -> NavigationManager.showToast("Не удалось удалить", "error")
                );
            });

            cancelDeleteButton.setOnAction(event -> {
                resetDeleteUI();
            });
        } else {
            log.error("Context isn't an instance of Project");
        }
    }

    private void updateUI() {
        if (context == null) {
            log.error("Context not established");
            return;
        }

        log.debug("updateUI: name={}, tasks={}, completed={}, percent={}",
                context.getName(),
                context.getNumberOfTasks(),
                context.getCompletedTasks(),
                context.getPercentOfCompletion());

        this.projectNameLabel.setText(context.getName());
        this.projectSupervisorLabel.setText(context.getSupervisor());
        this.numberOfTasksLabel.setText(String.valueOf(context.getNumberOfTasks()));
        this.completedTasksLabel.setText(String.valueOf(context.getCompletedTasks()));
        this.remainingTasksLabel.setText(String.valueOf(context.getRemainingTasks()));
        this.percentOfCompletionLabel.setText(context.getPercentOfCompletion() + "%");
        this.progressBar.setProgress(context.getPercentOfCompletion() / 100.0);
    }

    private void resetDeleteUI() {
        confirmDeleteVBox.setVisible(false);
        confirmDeleteVBox.setManaged(false);

        deleteProjectButton.setVisible(true);
        deleteProjectButton.setManaged(true);
    }

    @Override
    public void shutdown() {
        log.debug("ProjectDetailsController: shutdown()");
        EventBus.getInstance().unsubscribe(TaskChangedEvent.class, taskUpdateListener);
        EventBus.getInstance().unsubscribe(ProjectChangedEvent.class, projectUpdateListener);

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
