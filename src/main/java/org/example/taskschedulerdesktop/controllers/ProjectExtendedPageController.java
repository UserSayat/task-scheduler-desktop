package org.example.taskschedulerdesktop.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.dto.TaskForProjectExtendedPage;
import org.example.taskschedulerdesktop.navigation.NavigationManager;
import org.example.taskschedulerdesktop.service.TaskService;

import java.io.IOException;
import java.util.List;

public class ProjectExtendedPageController {

    private final TaskService<TaskForProjectExtendedPage> taskService;

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

    public ProjectExtendedPageController(TaskService<TaskForProjectExtendedPage> taskService) {
        this.taskService = taskService;
    }

    public Label getProjectNameLabel() {
        return projectNameLabel;
    }

    public void setProjectNameLabel(Label projectNameLabel) {
        this.projectNameLabel = projectNameLabel;
    }

    public Label getProjectSupervisorLabel() {
        return projectSupervisorLabel;
    }

    public void setProjectSupervisorLabel(Label projectSupervisorLabel) {
        this.projectSupervisorLabel = projectSupervisorLabel;
    }

    public Label getNumberOfTasksLabel() {
        return numberOfTasksLabel;
    }

    public void setNumberOfTasksLabel(Label numberOfTasksLabel) {
        this.numberOfTasksLabel = numberOfTasksLabel;
    }

    public Label getCompletedTasksLabel() {
        return completedTasksLabel;
    }

    public void setCompletedTasksLabel(Label completedTasksLabel) {
        this.completedTasksLabel = completedTasksLabel;
    }

    public Label getRemainingTasksLabel() {
        return remainingTasksLabel;
    }

    public void setRemainingTasksLabel(Label remainingTasksLabel) {
        this.remainingTasksLabel = remainingTasksLabel;
    }

    public Label getPercentOfCompletionLabel() {
        return percentOfCompletionLabel;
    }

    public void setPercentOfCompletionLabel(Label percentOfCompletionLabel) {
        this.percentOfCompletionLabel = percentOfCompletionLabel;
    }

    public VBox getTasksInProgressVBox() {
        return tasksInProgressVBox;
    }

    public void setTasksInProgressVBox(VBox tasksInProgressVBox) {
        this.tasksInProgressVBox = tasksInProgressVBox;
    }

    public VBox getTasksUnderReviewVBox() {
        return tasksUnderReviewVBox;
    }

    public void setTasksUnderReviewVBox(VBox tasksUnderReviewVBox) {
        this.tasksUnderReviewVBox = tasksUnderReviewVBox;
    }

    public VBox getCompletedTasksVBox() {
        return completedTasksVBox;
    }

    public void setCompletedTasksVBox(VBox completedTasksVBox) {
        this.completedTasksVBox = completedTasksVBox;
    }

    public void initialize() {
        loadTasksInProgress();
    }

    public void loadTasksInProgress() {
        List<TaskForProjectExtendedPage> tasksInProgress = taskService.findAll(); //findInProgress()
                //= List.of(new TaskDescriptionCard(1, "Оптимизация загрузки приложений", "frontend", "06 авг", "АК", "Средний"));

        try {
            for (TaskForProjectExtendedPage task : tasksInProgress) {
                int sequenceNumber = 1;
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/org/example/taskschedulerdesktop/view/task_description_card.fxml")
                );

                HBox taskDescriptionCard = loader.load();
                TaskDescriptionCardController taskDescriptionCardController = loader.getController();


                taskDescriptionCardController.setTaskSequenceNumberLabel(new Label(String.valueOf(sequenceNumber)));
                taskDescriptionCardController.setTaskNameLabel(new Label(task.getTaskName()));
                taskDescriptionCardController.setTaskTypeLabel(new Label(task.getType()));
                taskDescriptionCardController.setTaskDeadlineLabel(new Label(task.getDeadline()));
                taskDescriptionCardController.setExecutorInitialsLabel(new Label(task.getExecutor()));
                taskDescriptionCardController.setPriorityLabel(new Label(task.getPriority()));

                taskDescriptionCard.setOnMouseClicked(event -> {
                    if (event.getButton() == MouseButton.PRIMARY) {
                        NavigationManager.openRightSidebar("/org/example/taskschedulerdesktop/view/task_right_sidebar.fxml", task);
                        event.consume();
                    }
                });

                tasksInProgressVBox.getChildren().add(taskDescriptionCard);
                sequenceNumber++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadTasksUnderReview() {
        List<TaskForProjectExtendedPage> tasksUnderReview = taskService.findAll(); //findUnderReview()
        //List.of(new TaskDescriptionCard(1, "Утвердить макеты главной страницы", "Дизайн", "03 авг", "АК", "Высокий"));

        try {
            for (TaskForProjectExtendedPage task : tasksUnderReview) {
                int sequenceNumber = 1;
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/org/example/taskschedulerdesktop/view/task_description_card.fxml")
                );

                HBox taskDescriptionCard = loader.load();
                TaskDescriptionCardController taskDescriptionCardController = loader.getController();

                taskDescriptionCardController.setTaskSequenceNumberLabel(new Label(String.valueOf(sequenceNumber++)));
                taskDescriptionCardController.setTaskNameLabel(new Label(task.getTaskName()));
                taskDescriptionCardController.setTaskTypeLabel(new Label(task.getType()));
                taskDescriptionCardController.setTaskDeadlineLabel(new Label(task.getDeadline()));
                taskDescriptionCardController.setExecutorInitialsLabel(new Label(task.getExecutor()));
                taskDescriptionCardController.setPriorityLabel(new Label(task.getPriority()));

                taskDescriptionCard.setOnMouseClicked(event -> {
                    if (event.getButton() == MouseButton.PRIMARY) {
                        NavigationManager.openRightSidebar("/org/example/taskschedulerdesktop/view/task_right_sidebar.fxml", task);
                        event.consume();
                    }
                });

                tasksUnderReviewVBox.getChildren().add(taskDescriptionCard);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadCompletedTasks() {
        List<TaskForProjectExtendedPage> completedTasks = taskService.findAll();
        try {
            for (TaskForProjectExtendedPage task : completedTasks) {
                int sequenceNumber = 1;
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/org/example/taskschedulerdesktop/view/task_description_card.fxml")
                );

                HBox taskDescriptionCard = loader.load();
                TaskDescriptionCardController taskDescriptionCardController = loader.getController();

                taskDescriptionCardController.setTaskSequenceNumberLabel(new Label(String.valueOf(sequenceNumber++)));
                taskDescriptionCardController.setTaskNameLabel(new Label(task.getTaskName()));
                taskDescriptionCardController.setTaskTypeLabel(new Label(task.getType()));
                taskDescriptionCardController.setTaskDeadlineLabel(new Label(task.getDeadline()));
                taskDescriptionCardController.setExecutorInitialsLabel(new Label(task.getExecutor()));
                taskDescriptionCardController.setPriorityLabel(new Label(task.getPriority()));

                taskDescriptionCard.setOnMouseClicked(event -> {
                    if (event.getButton() == MouseButton.PRIMARY) {
                        NavigationManager.openRightSidebar("/org/example/taskschedulerdesktop/view/task_right_sidebar.fxml", task);
                        event.consume();
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
