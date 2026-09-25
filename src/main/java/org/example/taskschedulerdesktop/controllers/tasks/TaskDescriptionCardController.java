package org.example.taskschedulerdesktop.controllers.tasks;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.example.taskschedulerdesktop.controllers.Shutdownable;
import org.example.taskschedulerdesktop.dto.TaskView;
import org.example.taskschedulerdesktop.listeners.EventBus;
import org.example.taskschedulerdesktop.listeners.TaskChangedEvent;
import org.example.taskschedulerdesktop.navigation.ContextAware;

import java.util.Objects;
import java.util.function.Consumer;

public class TaskDescriptionCardController implements ContextAware, Shutdownable {

    @FXML private Label taskSequenceNumberLabel;
    @FXML private Label taskNameLabel;
    @FXML private Label taskTypeLabel;
    @FXML private Label taskDeadlineLabel;
    @FXML private Label executorInitialsLabel;
    @FXML private Label priorityLabel;

    private TaskView contextTask;

    @Override
    public void setContext(Object context) {
        if (context instanceof TaskView taskView) {
            this.contextTask = taskView;
            updateUI();
        }
    }

    private void updateUI() {
       taskNameLabel.setText(contextTask.getTaskName());
       taskTypeLabel.setText(contextTask.getType());
       taskDeadlineLabel.setText(contextTask.getDeadline().toString());
       executorInitialsLabel.setText(contextTask.getExecutor());
       priorityLabel.setText(contextTask.getPriority().getDisplayName());
    }

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

    @Override
    public void shutdown() {
    }
}
