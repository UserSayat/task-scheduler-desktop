package org.example.taskschedulerdesktop.controllers.tasks;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import org.example.taskschedulerdesktop.controllers.Shutdownable;
import org.example.taskschedulerdesktop.dto.tasks.TaskView;
import org.example.taskschedulerdesktop.navigation.ContextAware;
import org.example.taskschedulerdesktop.utils.Initials;
import org.example.taskschedulerdesktop.utils.TagChips;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.example.taskschedulerdesktop.utils.DateFormatter.formatShort;

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

       taskNameLabel.setText(contextTask.title());
       TagChips.fill(tagsFlowPane, contextTask.tagNames());
       taskDeadlineLabel.setText(formatShort(contextTask.deadline()));
       executorInitialsLabel.setText(Initials.of(contextTask.responsibleName()));
       priorityLabel.setText(contextTask.priority().getDisplayName());
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

    @Override
    public void shutdown() {
    }
}
