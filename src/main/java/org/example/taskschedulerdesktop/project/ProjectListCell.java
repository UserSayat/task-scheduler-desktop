package org.example.taskschedulerdesktop.project;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

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
    private final HBox root;
    private static final double PROGRESS_WIDTH = 160;

    public ProjectListCell() {
        nameLabel.getStyleClass().add("project-name-label");
        supervisorLabel.getStyleClass().add("section-mono");
        // При нехватке места сокращается имя руководителя, а не название проекта и счётчики
        nameLabel.setMinWidth(Region.USE_PREF_SIZE);
        ratioLabel.setMinWidth(Region.USE_PREF_SIZE);
        percentLabel.setMinWidth(Region.USE_PREF_SIZE);
        ratioLabel.setStyle("-fx-font-size:16px;-fx-text-fill:#6b6b65;");
        percentLabel.getStyleClass().add("percent-of-completion-label");
        // Процент фиксированной ширины, справа: полоски во всех строках стоят ровно друг под другом
        percentLabel.setMinWidth(44);
        percentLabel.setPrefWidth(44);
        percentLabel.setAlignment(Pos.CENTER_RIGHT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Полоска прогресса — в той же строке, короткая
        progressBar.setMinWidth(PROGRESS_WIDTH);
        progressBar.setPrefWidth(PROGRESS_WIDTH);
        progressBar.setMaxWidth(PROGRESS_WIDTH);
        progressBar.setStyle("-fx-accent:#1a3a8f;");
        progressBar.getStyleClass().add("progress-thin");

        root = new HBox(12, nameLabel, supervisorLabel, spacer, ratioLabel, progressBar, percentLabel);
        root.setAlignment(Pos.CENTER_LEFT);
        root.getStyleClass().add("dashboard-item"); // отдельная карточка, как задачи на странице проекта
        setPrefWidth(0); // по ширине списка, без горизонтальной прокрутки
    }

    @Override
    protected void updateItem(ProjectView project, boolean empty) {
        super.updateItem(project, empty);

        if (empty || project == null) {
            setGraphic(null);
        } else {
            nameLabel.setText(project.name());
            ProjectLock.apply(nameLabel, project.isPrivate());
            supervisorLabel.setText(project.ownerName() != null ? project.ownerName() : "");
            ratioLabel.setText(project.doneTasks() + "/" + project.totalTasks() + " задач");
            percentLabel.setText(project.percentDone() + "%");
            progressBar.setProgress(project.percentDone() / 100.0);
            setGraphic(root);
        }
    }
}
