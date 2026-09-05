package org.example.taskschedulerdesktop.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class TaskDescriptionCardController {

    @FXML
    private Label taskSequenceNumberLabel;

    @FXML
    private Label taskNameLabel;

    @FXML
    private Label taskTypeLabel;

    @FXML
    private Label taskDeadlineLabel;

    @FXML
    private Label executorInitialsLabel;

    @FXML
    private Label priorityLabel;

    public Label getTaskSequenceNumberLabel() {
        return taskSequenceNumberLabel;
    }

    public void setTaskSequenceNumberLabel(int taskSequenceNumber) {
        this.taskSequenceNumberLabel.setText(String.valueOf(taskSequenceNumber));
    }

    public Label getTaskNameLabel() {
        return taskNameLabel;
    }

    public void setTaskNameLabel(String taskName) {
        this.taskNameLabel.setText(taskName);
    }

    public Label getTaskTypeLabel() {
        return taskTypeLabel;
    }

    public void setTaskTypeLabel(String taskType) {
        this.taskTypeLabel.setText(taskType);
    }

    public Label getTaskDeadlineLabel() {
        return taskDeadlineLabel;
    }

    public void setTaskDeadlineLabel(String taskDeadline) {
        this.taskDeadlineLabel.setText(taskDeadline);
    }

    public Label getExecutorInitialsLabel() {
        return executorInitialsLabel;
    }

    public void setExecutorInitialsLabel(String executorInitials) {
        this.executorInitialsLabel.setText(executorInitials);
    }

    public Label getPriorityLabel() {
        return priorityLabel;
    }

    public void setPriorityLabel(String priority) {
        this.priorityLabel.setText(priority);
    }
}
