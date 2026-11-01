package org.example.taskschedulerdesktop.project;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.user.Initials;

/**
 * Строка проекта на «Обзоре»: название, руководитель, сколько задач выполнено и полоса прогресса.
 * <p>
 * Собирается в коде, а не из FXML: список создаёт ячейки и для пустых строк, и загрузка FXML на каждую
 * ячейку подвешивала переход на «Обзор» примерно на 0,2 с.
 */
public class ProjectListCell extends ListCell<ProjectView> {

    private final Label nameLabel = new Label();
    private final Label supervisorLabel = new Label();
    private final Label ratioLabel = new Label();
    private final Label percentLabel = new Label();
    private final ProgressBar progressBar = new ProgressBar();
    private final VBox root;

    public ProjectListCell() {
        nameLabel.getStyleClass().add("project-name-label");
        supervisorLabel.getStyleClass().add("section-mono");
        ratioLabel.setStyle("-fx-font-size:13px;-fx-text-fill:#9b9b95;");
        percentLabel.getStyleClass().add("percent-of-completion-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(8, nameLabel, supervisorLabel, spacer, ratioLabel, percentLabel);
        top.setAlignment(Pos.CENTER_LEFT);

        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setStyle("-fx-accent:#1a3a8f;");
        progressBar.getStyleClass().add("progress-thin");

        root = new VBox(6, top, progressBar);
        root.setStyle("-fx-padding: 0 0 14 0;");
    }

    @Override
    protected void updateItem(ProjectView project, boolean empty) {
        super.updateItem(project, empty);

        if (empty || project == null) {
            setGraphic(null);
        } else {
            nameLabel.setText(project.name());
            ProjectLock.apply(nameLabel, project.isPrivate());
            supervisorLabel.setText(Initials.of(project.ownerName()));
            ratioLabel.setText(project.doneTasks() + "/" + project.totalTasks() + " задач");
            percentLabel.setText(project.percentDone() + "%");
            progressBar.setProgress(project.percentDone() / 100.0);
            setGraphic(root);
        }
    }
}
