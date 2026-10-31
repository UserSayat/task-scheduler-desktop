package org.example.taskschedulerdesktop.project;

import org.example.taskschedulerdesktop.shared.ui.Clicks;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.shared.ui.Animations;
import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.shared.event.EventBus;
import org.example.taskschedulerdesktop.shared.navigation.ContextAware;
import org.example.taskschedulerdesktop.shared.navigation.NavigationManager;
import org.example.taskschedulerdesktop.shared.navigation.Routes;
import org.example.taskschedulerdesktop.shared.ui.DateFormatter;
import org.example.taskschedulerdesktop.shared.ui.Shutdownable;
import org.example.taskschedulerdesktop.task.AsyncTaskService;
import org.example.taskschedulerdesktop.task.TaskChangedEvent;
import org.example.taskschedulerdesktop.task.TaskView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Consumer;

public class ProjectCardController implements ContextAware, Shutdownable {

    private final AppConfig appConfig = AppConfig.getInstance();
    private static final Logger log = LoggerFactory.getLogger(ProjectCardController.class);
    private final AsyncProjectService projectService;
    private final AsyncTaskService taskService;

    /** Сколько открытых задач показывать в карточке. */
    private static final int TOP_TASKS = 3;

    @FXML private VBox rootVBox;

    @FXML private Label projectNameLabel;

    @FXML private Label projectSupervisorLabel;

    @FXML private Label percentOfCompletionLabel;

    @FXML private ProgressBar taskCompletionProgressBar;

    @FXML private Label numberOfTasksLabel;

    @FXML private Label completedTasksLabel;

    @FXML private Label remainingTasksLabel;

    @FXML private VBox topTasksVBox;

    private ProjectView context;
    private Consumer<TaskChangedEvent> taskChangedListener;



    public ProjectCardController(AsyncProjectService projectService, AsyncTaskService taskService) {
        this.projectService = projectService;
        this.taskService = taskService;
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

            Animations.hoverShadow(rootVBox);
            Clicks.onClick(rootVBox, () -> {
                log.debug("Click on the card, context = {}", context);
                NavigationManager.navigateTo(Routes.PROJECT_DETAILS, this.context.name(), this.context);
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
        projectNameLabel.setText(project.name() + (project.archived() ? " · в архиве" : ""));
        rootVBox.setOpacity(project.archived() ? 0.6 : 1.0);
        projectSupervisorLabel.setText("Руководитель: " + project.ownerName());
        numberOfTasksLabel.setText(String.valueOf(project.totalTasks()));
        completedTasksLabel.setText(String.valueOf(project.doneTasks()));
        remainingTasksLabel.setText(String.valueOf(project.remainingTasks()));
        percentOfCompletionLabel.setText(project.percentDone() + "%");
        taskCompletionProgressBar.setProgress(project.percentDone() / 100.0);
        loadTopTasks(project);
    }

    /** Самые важные открытые задачи проекта: точка цвета приоритета, название, срок. */
    private void loadTopTasks(ProjectView project) {
        taskService.findTopOpenTasks(project.id(), TOP_TASKS, tasks -> {
            topTasksVBox.getChildren().clear();
            if (tasks.isEmpty()) {
                Label empty = new Label("Открытых задач нет");
                empty.getStyleClass().add("team-task-empty");
                topTasksVBox.getChildren().add(empty);
                return;
            }
            for (TaskView task : tasks) {
                Region dot = new Region();
                dot.getStyleClass().addAll("team-task-dot", "team-task-dot-" + task.priority().styleName());
                Label title = new Label(task.title());
                title.getStyleClass().add("project-card-task");
                title.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(title, Priority.ALWAYS);
                HBox row = new HBox(6, dot, title);
                row.setAlignment(Pos.CENTER_LEFT);
                if (task.deadline() != null) {
                    Label deadline = new Label(DateFormatter.formatShort(task.deadline()));
                    deadline.getStyleClass().add("section-mono");
                    row.getChildren().add(deadline);
                }
                topTasksVBox.getChildren().add(row);
            }
        }, error -> log.error("Top tasks loading error", error));
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
