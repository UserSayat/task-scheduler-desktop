package org.example.taskschedulerdesktop.project;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.shared.ui.Shutdownable;
import org.example.taskschedulerdesktop.shared.event.EventBus;
import org.example.taskschedulerdesktop.task.TaskChangedEvent;
import org.example.taskschedulerdesktop.shared.navigation.ContextAware;
import org.example.taskschedulerdesktop.shared.navigation.NavigationManager;
import org.example.taskschedulerdesktop.shared.navigation.Routes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Consumer;

public class ProjectCardController implements ContextAware, Shutdownable {

    private final AppConfig appConfig = AppConfig.getInstance();
    private static final Logger log = LoggerFactory.getLogger(ProjectCardController.class);
    private final AsyncProjectService projectService;

    @FXML private VBox rootVBox;

    @FXML private Label projectNameLabel;

    @FXML private Label projectSupervisorLabel;

    @FXML private Label percentOfCompletionLabel;

    @FXML private ProgressBar taskCompletionProgressBar;

    @FXML private Label numberOfTasksLabel;

    @FXML private Label completedTasksLabel;

    @FXML private Label remainingTasksLabel;

    private ProjectView context;
    private Consumer<TaskChangedEvent> taskChangedListener;



    public ProjectCardController(AsyncProjectService projectService) {
        this.projectService = projectService;
    }

    @FXML
    public void initialize() {
        log.debug("ProjectCardController.initialize(), hashCode = {}", this.hashCode());
        log.debug("context = {}", context);
    }

    @Override
    public void setContext(Object context) {
        log.debug("ProjectCardController.setContext: {}, hashCode = {}", context, this.hashCode());
        log.debug("class = {}", context != null ? context.getClass().getName() : "null");

        if (context instanceof ProjectView projectContext) {
            this.context = projectContext;

            updateUI(projectContext);

            taskChangedListener = event -> {
                log.debug("TaskChangedEvent received: projectId={}", event.getProjectId());

                if (Objects.equals(event.getProjectId(), projectContext.id())) {
                    refreshCard();
                } else {
                    log.debug("The event is not suitable");
                }
            };

            EventBus.getInstance().subscribe(TaskChangedEvent.class, taskChangedListener);

            rootVBox.setOnMouseClicked(event -> {
                log.debug("Click on the card, context = {}", context);
                NavigationManager
                        .navigateTo(Routes.PROJECT_DETAILS, null, context);
            });
        } else {
            log.error("Context isn't an instance of Project");
        }
    }

    private void refreshCard() {
        log.debug("refreshCard(), context = {}", context);

        if (context == null) {
            log.error("context is null, unable to update the card");
            return;
        }

        log.debug("context.id() = {}", context.id());

        projectService.findProjectById(
                context.id(),
                this::updateUI,
                error -> log.error("Error updating card")
        );
    }

    private void updateUI(ProjectView project) {
        projectNameLabel.setText(project.name());
        projectSupervisorLabel.setText("Руководитель: " + project.ownerName());
        numberOfTasksLabel.setText(String.valueOf(project.totalTasks()));
        completedTasksLabel.setText(String.valueOf(project.doneTasks()));
        remainingTasksLabel.setText(String.valueOf(project.remainingTasks()));
        percentOfCompletionLabel.setText(project.percentDone() + "%");
        taskCompletionProgressBar.setProgress(project.percentDone() / 100.0);
    }

    @Override
    public void shutdown() {
        if (taskChangedListener != null) {
            EventBus.getInstance().unsubscribe(TaskChangedEvent.class, taskChangedListener);
            log.debug("Unsubscribed from TaskChangedEvent");
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
        this.projectSupervisorLabel.setText("Руководитель:" + projectSupervisor);
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
