package org.example.taskschedulerdesktop.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class ProjectExtendedPageController {

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
}
