package org.example.taskschedulerdesktop.team;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.user.Initials;

import static org.example.taskschedulerdesktop.shared.ui.DateFormatter.formatShort;

public class EmployeeCardController {

    @FXML private Label avatarLabel;
    @FXML private Label employeeNameLabel;
    @FXML private Label employeePositionLabel;
    @FXML private Label openTasksLabel;
    @FXML private Label doneTasksLabel;
    @FXML private VBox topTasksVBox;

    public void setMember(TeamMember member) {
        avatarLabel.setText(Initials.of(member.name()));
        employeeNameLabel.setText(member.name());
        employeePositionLabel.setText(member.position() != null && !member.position().isBlank()
                ? member.position() : "Должность не указана");
        openTasksLabel.setText(String.valueOf(member.openTasks()));
        doneTasksLabel.setText(String.valueOf(member.doneTasks()));

        topTasksVBox.getChildren().clear();
        if (member.topTasks().isEmpty()) {
            Label empty = new Label("Нет открытых задач");
            empty.getStyleClass().add("team-task-empty");
            topTasksVBox.getChildren().add(empty);
            return;
        }
        for (MemberTask task : member.topTasks()) {
            topTasksVBox.getChildren().add(taskRow(task));
        }
    }

    /** Строка задачи: точка цвета приоритета, название и срок. */
    private HBox taskRow(MemberTask task) {
        Region dot = new Region();
        dot.getStyleClass().addAll("team-task-dot", "team-task-dot-" + task.priority().styleName());

        Label title = new Label(task.title());
        title.getStyleClass().add("team-task-title");
        title.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(title, Priority.ALWAYS);

        HBox row = new HBox(6, dot, title);
        row.setAlignment(Pos.CENTER_LEFT);
        if (task.deadline() != null) {
            Label deadline = new Label(formatShort(task.deadline()));
            deadline.getStyleClass().add("team-task-deadline");
            row.getChildren().add(deadline);
        }
        return row;
    }
}
