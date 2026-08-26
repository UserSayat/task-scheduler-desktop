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

    public void setTaskSequenceNumberLabel(Label taskSequenceNumberLabel) {
        this.taskSequenceNumberLabel = taskSequenceNumberLabel;
    }

    public Label getTaskNameLabel() {
        return taskNameLabel;
    }

    public void setTaskNameLabel(Label taskNameLabel) {
        this.taskNameLabel = taskNameLabel;
    }

    public Label getTaskTypeLabel() {
        return taskTypeLabel;
    }

    public void setTaskTypeLabel(Label taskTypeLabel) {
        this.taskTypeLabel = taskTypeLabel;
    }

    public Label getTaskDeadlineLabel() {
        return taskDeadlineLabel;
    }

    public void setTaskDeadlineLabel(Label taskDeadlineLabel) {
        this.taskDeadlineLabel = taskDeadlineLabel;
    }

    public Label getExecutorInitialsLabel() {
        return executorInitialsLabel;
    }

    public void setExecutorInitialsLabel(Label executorInitialsLabel) {
        this.executorInitialsLabel = executorInitialsLabel;
    }

    public Label getPriorityLabel() {
        return priorityLabel;
    }

    public void setPriorityLabel(Label priorityLabel) {
        this.priorityLabel = priorityLabel;
    }
}
