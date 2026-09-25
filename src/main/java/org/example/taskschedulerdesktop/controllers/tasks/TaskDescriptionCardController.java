package org.example.taskschedulerdesktop.controllers.tasks;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import org.example.taskschedulerdesktop.controllers.Shutdownable;
import org.example.taskschedulerdesktop.dto.TaskView;
import org.example.taskschedulerdesktop.navigation.ContextAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskDescriptionCardController implements ContextAware, Shutdownable {

    private static final Logger log = LoggerFactory.getLogger(TaskDescriptionCardController.class);

    @FXML private Label taskSequenceNumberLabel;
    @FXML private Label taskNameLabel;
    @FXML private FlowPane tagsFlowPane;
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
        if (contextTask == null) {
            log.error("Context task is null");
            return;
        }

       taskNameLabel.setText(contextTask.getTaskName());
       updateTags();
       taskDeadlineLabel.setText(contextTask.getDeadline().toString());
       executorInitialsLabel.setText(getInitials(contextTask.getExecutor()));
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

    private String getInitials(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Имя не может быть пустым");
        }

        String[] parts = fullName.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();

        int count = Math.min(parts.length, 2);
        for (int i = 0; i < count; i++) {
            initials.append(parts[i].charAt(0));
        }

        return initials.toString().toUpperCase();
    }

    private void updateTags() {
        tagsFlowPane.getChildren().clear();

        String type = contextTask.getType();
        if (type == null || type.isBlank()) {
            tagsFlowPane.setVisible(false);
            tagsFlowPane.setManaged(false);
            return;
        }

        String[] tags = type.split(",");
        boolean hasValidTags = false;

        for (String tag : tags) {
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) {
                Label tagLabel = new Label(trimmed);
                tagLabel.getStyleClass().add("tag-chip");
                tagsFlowPane.getChildren().add(tagLabel);
                hasValidTags = true;
            }
        }

        tagsFlowPane.setVisible(hasValidTags);
        tagsFlowPane.setManaged(hasValidTags);
    }

    @Override
    public void shutdown() {
    }
}
