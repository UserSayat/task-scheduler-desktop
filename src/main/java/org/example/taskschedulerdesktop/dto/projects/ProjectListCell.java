package org.example.taskschedulerdesktop.dto.projects;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.models.Project;
import org.example.taskschedulerdesktop.navigation.Routes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ProjectListCell extends ListCell<Project> {

    private static final Logger log = LoggerFactory.getLogger(ProjectListCell.class);

    @FXML private VBox root;
    @FXML private Label projectNameLabel;
    @FXML private Label supervisorInitialsLabel;
    @FXML private Label ratioOfCompletedToAllLabel;
    @FXML private Label percentOfCompletionLabel;
    @FXML private ProgressBar progressBar;

    public ProjectListCell() {
        try {
            log.debug("creating cell");
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(Routes.PROJECT_DESCRIPTION_CARD)
            );
            loader.setController(this);
            loader.load();
            log.debug("loaded successfully");
        } catch (IOException e) {
            log.error("loading failed");
            throw new RuntimeException("Не удалось загрузить project_description_card.fxml", e);
        }
    }

    @Override
    protected void updateItem(Project project, boolean empty) {
        super.updateItem(project, empty);

        if (empty || project == null) {
            setGraphic(null);
        } else {
            projectNameLabel.setText(project.getName());
            supervisorInitialsLabel.setText(project.getSupervisor());
            ratioOfCompletedToAllLabel.setText(project.getCompletedTasks() + "/" + project.getNumberOfTasks() + " задач");
            percentOfCompletionLabel.setText(project.getPercentOfCompletion() + "%");
            progressBar.setProgress(project.getPercentOfCompletion() / 100.0);
            setGraphic(root);
        }
    }
}
