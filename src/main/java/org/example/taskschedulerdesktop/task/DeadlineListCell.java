package org.example.taskschedulerdesktop.task;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.shared.ui.DateFormatter;
import org.example.taskschedulerdesktop.user.Initials;

/**
 * Строка ближайшего срока на «Обзоре»: исполнитель, задача и дата.
 * Собирается в коде, а не из FXML — так «Обзор» открывается без задержки (см. {@code ProjectListCell}).
 */
public class DeadlineListCell extends ListCell<TaskView> {

    private final Label initialsLabel = new Label();
    private final Label titleLabel = new Label();
    private final Label dateLabel = new Label();
    private final HBox root;

    public DeadlineListCell() {
        initialsLabel.getStyleClass().add("avatar-text");
        StackPane avatar = new StackPane(initialsLabel);
        avatar.getStyleClass().add("avatar-box");

        titleLabel.getStyleClass().add("deadline-title");
        titleLabel.setWrapText(true);
        dateLabel.getStyleClass().add("deadline-date");
        VBox text = new VBox(2, titleLabel, dateLabel);
        HBox.setHgrow(text, Priority.ALWAYS);

        root = new HBox(10, avatar, text);
        root.setAlignment(Pos.CENTER_LEFT);
        root.getStyleClass().add("deadline-row");
    }

    @Override
    protected void updateItem(TaskView taskView, boolean empty) {
        super.updateItem(taskView, empty);

        if (empty || taskView == null) {
            setGraphic(null);
        } else {
            initialsLabel.setText(Initials.of(taskView.responsibleName()));
            titleLabel.setText(taskView.title());
            dateLabel.setText(DateFormatter.formatShortWithYear(taskView.deadline()));

            // Ячейки переиспользуются, поэтому класс прошлой задачи нужно снять
            dateLabel.getStyleClass().removeAll("deadline-overdue", "deadline-normal");
            dateLabel.getStyleClass().add(taskView.isOverdue() ? "deadline-overdue" : "deadline-normal");

            setGraphic(root);
        }
    }
}
