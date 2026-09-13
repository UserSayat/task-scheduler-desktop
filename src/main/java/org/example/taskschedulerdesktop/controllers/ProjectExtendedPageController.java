package org.example.taskschedulerdesktop.controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.service.task.TasksLoaderService;
import org.example.taskschedulerdesktop.service.task.TaskCardService;

public class ProjectExtendedPageController {

    private final TasksLoaderService tasksLoaderService;
    private final TaskCardService taskCardService;

    @FXML private Label projectNameLabel;
    @FXML private Label projectSupervisorLabel;
    @FXML private Label numberOfTasksLabel;
    @FXML private Label completedTasksLabel;
    @FXML private Label remainingTasksLabel;
    @FXML private Label percentOfCompletionLabel;

    @FXML private VBox newTasksVBox;
    @FXML private ProgressIndicator newTasksLoadingIndicator;
    @FXML private VBox tasksInProgressVBox;
    @FXML private ProgressIndicator tasksInProgressLoadingIndicator;
    @FXML private VBox tasksUnderReviewVBox;
    @FXML private ProgressIndicator tasksUnderReviewLoadingIndicator;
    @FXML private VBox completedTasksVBox;
    @FXML private ProgressIndicator completedTasksLoadingIndicator;

    public ProjectExtendedPageController(TasksLoaderService taskService, TaskCardService taskCardService) {
        this.tasksLoaderService = taskService;
        this.taskCardService = taskCardService;
    }

    public void initialize() {
        loadNewTasks();
        loadTasksInProgress();
        loadTasksUnderReview();
        loadCompletedTasks();
    }

    public void loadNewTasks() {
        newTasksLoadingIndicator.setVisible(true);
        newTasksVBox.getChildren().clear();

        tasksLoaderService.findNewTasks(
                tasks -> Platform.runLater(() -> {
                    if (tasks.isEmpty()) {
                        newTasksVBox.getChildren().add(new Label("Нет новых задач"));
                        newTasksLoadingIndicator.setVisible(false);
                    } else {
                        newTasksVBox.getChildren().addAll(
                                taskCardService.createCards(tasks)
                        );
                    }
                    newTasksLoadingIndicator.setVisible(false);
                }),
                error -> Platform.runLater(() -> {
                    newTasksVBox.getChildren().add(new Label("Ошибка"));
                    newTasksLoadingIndicator.setVisible(false);
                })
        );
    }

    public void loadTasksInProgress() {
        tasksInProgressLoadingIndicator.setVisible(true);
        tasksInProgressVBox.getChildren().clear();

        tasksLoaderService.findInProgress(
                tasks -> Platform.runLater(() -> {
                    if (tasks.isEmpty()) {
                        tasksInProgressVBox.getChildren().add(new Label("Нет задач в работе"));
                        tasksInProgressLoadingIndicator.setVisible(false);
                    } else {
                        tasksInProgressVBox.getChildren().addAll(
                                taskCardService.createCards(tasks)
                        );
                    }
                    tasksInProgressLoadingIndicator.setVisible(false);
                }),
                error -> {
                    tasksInProgressVBox.getChildren().add(new Label("Ошибка"));
                    tasksInProgressLoadingIndicator.setVisible(false);
                }
        );
    }

    public void loadTasksUnderReview() {
        tasksUnderReviewLoadingIndicator.setVisible(true);
        tasksUnderReviewVBox.getChildren().clear();

        tasksLoaderService.findUnderReview(
                tasks -> Platform.runLater(() -> {
                    if (tasks.isEmpty()) {
                        tasksUnderReviewVBox.getChildren().add(new Label("Нет задач на проверке"));
                        tasksUnderReviewLoadingIndicator.setVisible(false);
                    } else {
                        tasksUnderReviewVBox.getChildren().addAll(
                                taskCardService.createCards(tasks)
                        );
                    }
                    tasksUnderReviewLoadingIndicator.setVisible(false);
                }),
                error -> {
                    tasksUnderReviewVBox.getChildren().add(new Label("Ошибка"));
                    tasksUnderReviewLoadingIndicator.setVisible(false);
                }
        );
    }

    public void loadCompletedTasks() {
        completedTasksLoadingIndicator.setVisible(true);
        completedTasksVBox.getChildren().clear();

        tasksLoaderService.findCompletedTasks(
                tasks -> Platform.runLater(() -> {
                    if (tasks.isEmpty()) {
                        completedTasksVBox.getChildren().add(new Label("Нет завершенных задач"));
                        completedTasksLoadingIndicator.setVisible(false);
                    } else {
                        completedTasksVBox.getChildren().addAll(
                                taskCardService.createCards(tasks)
                        );
                    }
                    completedTasksLoadingIndicator.setVisible(false);
                }),
                error -> {
                    completedTasksVBox.getChildren().add(new Label("Ошибка"));
                    completedTasksLoadingIndicator.setVisible(false);
                }
        );
    }
}
