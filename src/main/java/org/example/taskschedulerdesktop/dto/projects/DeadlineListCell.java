package org.example.taskschedulerdesktop.dto.projects;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import org.example.taskschedulerdesktop.dto.tasks.TaskView;
import org.example.taskschedulerdesktop.navigation.Routes;
import org.example.taskschedulerdesktop.utils.DateFormatter;
import org.example.taskschedulerdesktop.utils.Initials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

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
            deadlineExecutorInitialsLabel.setText(Initials.of(taskView.responsibleName()));
            deadlineTitleLabel.setText(taskView.title());
            deadlineDateLabel.setText(DateFormatter.formatShortWithYear(taskView.deadline()));

            // Ячейки переиспользуются, поэтому класс прошлой задачи нужно снять
            deadlineDateLabel.getStyleClass().removeAll("deadline-overdue", "deadline-normal");
            deadlineDateLabel.getStyleClass().add(taskView.isOverdue() ? "deadline-overdue" : "deadline-normal");

            setGraphic(root);
        }
    }
}
