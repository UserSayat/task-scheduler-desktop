package org.example.taskschedulerdesktop.dto.projects;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import org.example.taskschedulerdesktop.dto.tasks.TaskView;
import org.example.taskschedulerdesktop.navigation.Routes;
import org.example.taskschedulerdesktop.utils.DateFormatter;
import org.example.taskschedulerdesktop.utils.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;

public class DeadlineListCell extends ListCell<TaskView> {

    private static final Logger log = LoggerFactory.getLogger(DeadlineListCell.class);

    @FXML private HBox root;
    @FXML private Label deadlineExecutorInitialsLabel;
    @FXML private Label deadlineTitleLabel;
    @FXML private Label deadlineDateLabel;

    public DeadlineListCell() {
        try {
            log.debug("creating deadline list cell");
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(Routes.DEADLINE_ROW)
            );
            loader.setController(this);
            loader.load();
            log.debug("deadline list cell loaded successfully");
        } catch (IOException e) {
            log.error("deadline list cell loading failed");
            throw new RuntimeException("Не удалось загрузить project_description_card.fxml", e);
        }
    }

    @Override
    protected void updateItem(TaskView taskView, boolean empty) {
        super.updateItem(taskView, empty);

        if (empty || taskView == null) {
            setGraphic(null);
        } else {
            deadlineExecutorInitialsLabel.setText(getInitials(taskView.getExecutor()));
            deadlineTitleLabel.setText(taskView.getTaskName());
            deadlineDateLabel.setText(DateFormatter.formatShortWithYear(taskView.getDeadline()));

            if (taskView.getDeadline() != null
                    && taskView.getDeadline().isBefore(LocalDate.now())
                    && taskView.getStatus() != TaskStatus.COMPLETED) {
                deadlineDateLabel.getStyleClass().add("deadline-overdue");
            } else {
                deadlineDateLabel.getStyleClass().add("deadline-normal");
            }

            setGraphic(root);
        }
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
}
