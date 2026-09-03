package org.example.taskschedulerdesktop.controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.service.task.AsyncTaskService;
import org.example.taskschedulerdesktop.service.task.TaskCardService;

public class ProjectExtendedPageController {

    private final AsyncTaskService asyncTaskService;
    private final TaskCardService taskCardService;

    @FXML
    private Label projectNameLabel;

    @FXML
    private Label projectSupervisorLabel;

    @FXML
    private Label numberOfTasksLabel;

    @FXML
    private Label completedTasksLabel;

    @FXML
    private Label remainingTasksLabel;

    @FXML
    private Label percentOfCompletionLabel;

    @FXML
    private VBox tasksInProgressVBox;

    @FXML
    private VBox tasksUnderReviewVBox;

    @FXML
    private VBox completedTasksVBox;

    @FXML
    private ProgressIndicator loadingIndicator;

    private int loadedCount = 0;
    private final int TOTAL_LOADERS = 3;

    public ProjectExtendedPageController(AsyncTaskService taskService, TaskCardService taskCardService) {
        this.asyncTaskService = taskService;
        this.taskCardService = taskCardService;
    }

    public void initialize() {

        loadTasksInProgress();
        loadTasksUnderReview();
        loadCompletedTasks();
    }

    public void loadTasksInProgress() {
        loadingIndicator.setVisible(true);
        tasksInProgressVBox.getChildren().clear();

        asyncTaskService.findInProgress(
                tasks -> Platform.runLater(() -> {
                    checkAllLoaded();

                    if (tasks.isEmpty()) {
                        tasksInProgressVBox.getChildren().add(new Label("Нет задач в работе"));
                        return;
                    }

                    tasksInProgressVBox.getChildren().addAll(
                            taskCardService.createCards(tasks)
                    );
                }),
                error -> {
                    checkAllLoaded();
                    tasksInProgressVBox.getChildren().add(new Label("Ошибка"));
                }
        );
    }

    public void loadTasksUnderReview() {
        loadingIndicator.setVisible(true);
        tasksUnderReviewVBox.getChildren().clear();

        asyncTaskService.findAll(
                tasks -> Platform.runLater(() -> {
                    checkAllLoaded();

                    if (tasks.isEmpty()) {
                        tasksUnderReviewVBox.getChildren().add(new Label("Нет задач на проверке"));
                        return;
                    }

                    tasksUnderReviewVBox.getChildren().addAll(
                            taskCardService.createCards(tasks)
                    );
                }),
                error -> {
                    checkAllLoaded();
                    tasksUnderReviewVBox.getChildren().add(new Label("Ошибка"));
                }
        );
    }

    public void loadCompletedTasks() {
        loadingIndicator.setVisible(true);
        completedTasksVBox.getChildren().clear();

        asyncTaskService.findCompletedTasks(
                tasks -> Platform.runLater(() -> {
                    checkAllLoaded();

                    if (tasks.isEmpty()) {
                        completedTasksVBox.getChildren().add(new Label("Нет завершенных задач"));
                        return;
                    }

                    completedTasksVBox.getChildren().addAll(
                            taskCardService.createCards(tasks)
                    );
                }),
                error -> {
                    checkAllLoaded();
                    completedTasksVBox.getChildren().add(new Label("Ошибка"));
                }
        );
    }

    private void checkAllLoaded() {
        loadedCount++;
        if (loadedCount == TOTAL_LOADERS) {
            loadingIndicator.setVisible(false);
        }
    }
}
