package org.example.taskschedulerdesktop.controllers.projects;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.models.ProjectCard;
import org.example.taskschedulerdesktop.navigation.ContextAware;
import org.example.taskschedulerdesktop.navigation.NavigationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProjectCardController implements ContextAware {

    private final AppConfig appConfig = AppConfig.getInstance();
    private static final Logger log = LoggerFactory.getLogger(ProjectCardController.class);

    @FXML
    private VBox rootVBox;

    @FXML
    private Label projectNameLabel;

    @FXML
    private Label projectSupervisorLabel;

    @FXML
    private Label percentOfCompletionLabel;

    @FXML
    private Label numberOfTasksLabel;

    @FXML
    private Label completedTasksLabel;

    @FXML
    private Label remainingTasksLabel;

    private ProjectCard context;

//    @FXML
//    private Label firstTaskDescriptionLabel;
//
//    @FXML
//    private Label firstTaskDeadlineLabel;
//
//    @FXML
//    private Label secondTaskDescriptionLabel;
//
//    @FXML
//    private Label secondTaskDeadlineLabel;
//
//    @FXML
//    private Label thirdTaskDescriptionLabel;
//
//    @FXML
//    private Label thirdTaskDeadlineLabel;

    @FXML
    public void initialize() {
        log.debug("ProjectCardController.initialize(), hashCode = {}", this.hashCode());
        log.debug("context = {}", context);
    }

    @Override
    public void setContext(Object context) {
        log.debug("ProjectCardController.setContext: {}, hashCode = {}", context, this.hashCode());
        log.debug("class = {}", context != null ? context.getClass().getName() : "null");

        if (context instanceof ProjectCard projectContext) {
            this.context = projectContext;

            rootVBox.setOnMouseClicked(event -> {
                log.debug("Click on the card, context = {}", context);
                NavigationManager
                        .navigateTo("/org/example/taskschedulerdesktop/view/project_extended_page.fxml", null, context);
            });
        } else {
            log.error("Context isn't an instance of Project");
        }
    }

    public Label getProjectNameLabel() {
        return projectNameLabel;
    }

    public void setProjectNameLabel(String projectName) {
        this.projectNameLabel.setText(projectName);
    }

    public Label getProjectSupervisorLabel() {
        return projectSupervisorLabel;
    }

    public void setProjectSupervisorLabel(String projectSupervisor) {
        this.projectSupervisorLabel.setText(projectSupervisor);
    }

    public Label getPercentOfCompletionLabel() {
        return percentOfCompletionLabel;
    }

    public void setPercentOfCompletionLabel(int percentOfCompletion) {
        this.percentOfCompletionLabel.setText(String.valueOf(percentOfCompletion) + "%");
    }

    public Label getNumberOfTasksLabel() {
        return numberOfTasksLabel;
    }

    public void setNumberOfTasksLabel(int numberOfTasks) {
        this.numberOfTasksLabel.setText(String.valueOf(numberOfTasks));
    }

    public Label getCompletedTasksLabel() {
        return completedTasksLabel;
    }

    public void setCompletedTasksLabel(int completedTasks) {
        this.completedTasksLabel.setText(String.valueOf(completedTasks));
    }

    public Label getRemainingTasksLabel() {
        return remainingTasksLabel;
    }

    public void setRemainingTasksLabel(int remainingTasks) {
        this.remainingTasksLabel.setText(String.valueOf(remainingTasks));
    }
}
