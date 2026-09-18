package org.example.taskschedulerdesktop.controllers.projects;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.controllers.Shutdownable;
import org.example.taskschedulerdesktop.listeners.EventBus;
import org.example.taskschedulerdesktop.listeners.TaskChangedEvent;
import org.example.taskschedulerdesktop.models.Project;
import org.example.taskschedulerdesktop.navigation.ContextAware;
import org.example.taskschedulerdesktop.navigation.NavigationManager;
import org.example.taskschedulerdesktop.service.project.AsyncProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Consumer;

public class ProjectCardController implements ContextAware, Shutdownable {

    private final AppConfig appConfig = AppConfig.getInstance();
    private static final Logger log = LoggerFactory.getLogger(ProjectCardController.class);
    private final AsyncProjectService projectService;

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

    private Project context;
    private Consumer<TaskChangedEvent> taskChangedListener;

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


    public ProjectCardController(AsyncProjectService projectService) {
        this.projectService = projectService;
    }

    @FXML
    public void initialize() {
        log.debug("ProjectCardController.initialize(), hashCode = {}", this.hashCode());
        log.debug("context = {}", context);

        taskChangedListener = event -> {
            log.debug("TaskChangedEvent received: projectId={}", event.getProjectId());
            log.debug("context = {}", context);
            log.debug("context.getId() = {}", context != null ? context.getId() : "null");

            if (context != null && Objects.equals(event.getProjectId(), context.getId())) {
                log.debug("The event is coming up, updating the card");
                log.debug("The task has changed, updating the project card");
                refreshCard();
            } else {
                log.error("The event is not suitable");
            }
        };

        EventBus.getInstance().subscribe(TaskChangedEvent.class, taskChangedListener);
    }

    @Override
    public void setContext(Object context) {
        log.debug("ProjectCardController.setContext: {}, hashCode = {}", context, this.hashCode());
        log.debug("class = {}", context != null ? context.getClass().getName() : "null");

        if (context instanceof Project projectContext) {
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

    private void refreshCard() {
        log.debug("refreshCard() called");
        log.debug("context = {}", context);

        if (context == null) {
            log.error("context is null, unable to update the card");
        }

        log.debug("context.getId() = {}", context.getId());

        projectService.findProjectById(
                context.getId(),
                this::updateUI,
                error -> log.error("Error updating card")
        );
    }

    private void updateUI(Project project) {
        projectNameLabel.setText(project.getName());
        projectSupervisorLabel.setText(project.getSupervisor());
        numberOfTasksLabel.setText(String.valueOf(project.getNumberOfTasks()));
        completedTasksLabel.setText(String.valueOf(project.getCompletedTasks()));
        remainingTasksLabel.setText(String.valueOf(project.getRemainingTasks()));
        percentOfCompletionLabel.setText(project.getPercentOfCompletion() + "%");
    }

    @Override
    public void shutdown() {
        EventBus.getInstance().unsubscribe(TaskChangedEvent.class, taskChangedListener);
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
