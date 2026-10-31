package org.example.taskschedulerdesktop.project;

import javafx.concurrent.Service;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.access.AsyncPermissionService;
import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.shared.ui.DateFormatter;
import org.example.taskschedulerdesktop.shared.ui.Shutdownable;
import org.example.taskschedulerdesktop.shared.event.ChangeType;
import org.example.taskschedulerdesktop.shared.event.EventBus;
import org.example.taskschedulerdesktop.task.TaskChangedEvent;
import org.example.taskschedulerdesktop.shared.navigation.ContextAware;
import org.example.taskschedulerdesktop.shared.navigation.NavigationManager;
import org.example.taskschedulerdesktop.shared.navigation.Routes;
import org.example.taskschedulerdesktop.task.AsyncTaskService;
import org.example.taskschedulerdesktop.task.TaskPriority;
import org.example.taskschedulerdesktop.task.TaskStatus;
import org.example.taskschedulerdesktop.user.Initials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

public class ProjectDetailsController implements Shutdownable, ContextAware {

    private AsyncTaskService asyncTaskService;
    private AsyncProjectService asyncProjectService;
    private final AsyncPermissionService permissions;
    private static final Logger log = LoggerFactory.getLogger(ProjectDetailsController.class);

    @FXML private Label projectNameLabel;
    @FXML private Label projectSupervisorLabel;
    @FXML private Label numberOfTasksLabel;
    @FXML private Label completedTasksLabel;
    @FXML private Label remainingTasksLabel;
    @FXML private Label percentOfCompletionLabel;
    @FXML private ProgressBar progressBar;

    @FXML private Button editProjectButton;
    @FXML private Button archiveButton;
    @FXML private Label projectMetaLabel;
    @FXML private Label projectDescriptionLabel;
    @FXML private Button deleteProjectButton;
    @FXML private HBox confirmDeleteVBox;
    @FXML private Button confirmDeleteButton;
    @FXML private Button cancelDeleteButton;

    @FXML private VBox newTasksVBox;
    @FXML private VBox tasksInProgressVBox;
    @FXML private VBox tasksUnderReviewVBox;
    @FXML private VBox completedTasksVBox;
    @FXML private Label noTasksLabel;

    @FXML private ProgressIndicator newTasksLoadingIndicator;
    @FXML private ProgressIndicator tasksInProgressLoadingIndicator;
    @FXML private ProgressIndicator tasksUnderReviewLoadingIndicator;
    @FXML private ProgressIndicator completedTasksLoadingIndicator;

    private Service<List<Node>> newTasksLoader;
    private Service<List<Node>> inProgressLoader;
    private Service<List<Node>> underReviewLoader;
    private Service<List<Node>> completedLoader;

    @FXML private Label amountOfTasksInProgressLabel;
    @FXML private Label amountOfTasksUnderReviewLabel;
    @FXML private Label amountOfNewTasksLabel;
    @FXML private Label amountOfCompletedTasksLabel;

    @FXML private Label amountOfTasksInProgressLabelSidePanel;
    @FXML private Label amountOfTasksUnderReviewLabelSidePanel;
    @FXML private Label amountOfNewTasksLabelSidePanel;
    @FXML private Label amountOfCompletedTasksLabelSidePanel;

    private int amountOfTasksInProgress = 0;
    private int amountOfTasksUnderReview = 0;
    private int amountOfNewTasks = 0;
    private int amountOfCompletedTasks = 0;

    @FXML private Label amountOfCriticalPriorityTasksLabel;
    @FXML private Label amountOfHighPriorityTasksLabel;
    @FXML private Label amountOfMediumPriorityTasksLabel;
    @FXML private Label amountOfLowPriorityTasksLabel;

    @FXML private VBox membersVBox;


    private ProjectView context;

    private final Consumer<TaskChangedEvent> taskUpdateListener = event -> {
        log.debug("ProjectDetailsController: TaskChangedEvent received: projectId={}", event.getProjectId());

        if (context != null && event.getProjectId().equals(context.id())) {
            //TODO обновлять одну задачу, а не весь контейнер event.getTaskId();
            refreshAllContainers();

            loadTasksStatisticByStatus();
            loadTasksStatisticByPriority();
            loadMembers();

            asyncProjectService.findProjectById(
                    context.id(),
                    project -> {
                        this.context = project;
                        updateUI();
                    },
                    error -> log.error("Error updating project's data", error)
            );
        }
    };

    private final Consumer<ProjectChangedEvent> projectUpdateListener = event -> {
        log.debug("ProjectDetailsController: ProjectChangedEvent received: projectId={}", event.getProjectId());

        if (context == null || !event.getProjectId().equals(context.id())) {
            return;
        }

        if (event.getChangeType() == ChangeType.UPDATED) {
            loadMembers();
            loadInfo();
            loadRights(); // состав участников мог измениться — вместе с ним и права
            asyncProjectService.findProjectById(
                    context.id(),
                    project -> {
                        this.context = project;
                        updateUI();
                    },
                    error -> log.error("Error updating project's data", error)
            );

            return;
        }

        if (event.getChangeType() == ChangeType.DELETED) {
            log.debug("Project deleted, navigating back to projects list");
            NavigationManager.navigateTo(Routes.PROJECTS);
        }
    };

    public ProjectDetailsController(AsyncTaskService taskService, AsyncProjectService projectService,
                                    AsyncPermissionService permissions) {
        this.asyncTaskService = taskService;
        this.asyncProjectService = projectService;
        this.permissions = permissions;
    }

    private Service<List<Node>> setupLoaderService(TaskStatus status, VBox container, ProgressIndicator indicator) {

        if (context == null) {
            log.error("Context is null");
            throw new RuntimeException("Context is null");
        }

        Service<List<Node>> loaderService = asyncTaskService.createLoaderService(context.id(), status);

        indicator.visibleProperty().bind(loaderService.runningProperty());
        showSection(container, false); // до загрузки раздел скрыт, чтобы не мелькал пустым

        loaderService.setOnSucceeded(event -> {
            List<Node> taskCards = loaderService.getValue();
            container.getChildren().setAll(taskCards);
            // Раздел без задач не показываем вовсе
            showSection(container, !taskCards.isEmpty());
        });

        loaderService.setOnFailed(event -> {
            container.getChildren().setAll(new Label("Не удалось загрузить задачи"));
            showSection(container, true);
            Throwable error = loaderService.getException();
            if (error != null) error.printStackTrace();
        });

        return loaderService;
    }

    /** Показать или скрыть раздел статуса целиком (заголовок со счётчиком и список задач). */
    private void showSection(VBox container, boolean show) {
        Node section = container.getParent();
        section.setVisible(show);
        section.setManaged(show);
        // Состояние загрузчиков обновляется после этого обработчика — проверяем на следующем такте
        javafx.application.Platform.runLater(this::updateNoTasksLabel);
    }

    /** «В проекте пока нет задач» — когда все разделы загружены и пусты. */
    private void updateNoTasksLabel() {
        boolean anyShown = java.util.stream.Stream.of(newTasksVBox, tasksInProgressVBox, tasksUnderReviewVBox, completedTasksVBox)
                .anyMatch(list -> list.getParent().isManaged());
        boolean anyLoading = java.util.stream.Stream.of(newTasksLoader, inProgressLoader, underReviewLoader, completedLoader)
                .anyMatch(loader -> loader == null || loader.getState() != javafx.concurrent.Worker.State.SUCCEEDED
                        && loader.getState() != javafx.concurrent.Worker.State.FAILED);
        noTasksLabel.setVisible(!anyShown && !anyLoading);
        noTasksLabel.setManaged(!anyShown && !anyLoading);
    }

    public void refreshAllContainers() {
        newTasksLoader.restart();
        inProgressLoader.restart();
        underReviewLoader.restart();
        completedLoader.restart();
    }

    /**
     * Методы для принудительного обновления списка.
     * Его можно вызывать по нажатию отдельной кнопки "Обновить" или программно.
     */
    public void refreshNewTasks() { newTasksLoader.restart(); }
    public void refreshInProgressTasks() { inProgressLoader.restart(); }
    public void refreshUnderReviewTasks() { underReviewLoader.restart(); }
    public void refreshCompletedTasks() { completedLoader.restart(); }

    @FXML
    public void initialize() {
        EventBus.getInstance().subscribe(TaskChangedEvent.class, taskUpdateListener);
        EventBus.getInstance().subscribe(ProjectChangedEvent.class, projectUpdateListener);
    }

    @Override
    public void setContext(Object context) {
        log.debug("setContext: {}", context);
        log.debug("class = {}", context != null ? context.getClass().getName() : "null");

        if (context instanceof ProjectView projectCard) {
            this.context = projectCard;
            updateUI();

            newTasksLoader = setupLoaderService(TaskStatus.NEW, newTasksVBox, newTasksLoadingIndicator);
            inProgressLoader = setupLoaderService(TaskStatus.IN_PROGRESS, tasksInProgressVBox, tasksInProgressLoadingIndicator);
            underReviewLoader = setupLoaderService(TaskStatus.ON_REVIEW, tasksUnderReviewVBox, tasksUnderReviewLoadingIndicator);
            completedLoader = setupLoaderService(TaskStatus.DONE, completedTasksVBox, completedTasksLoadingIndicator);

            refreshAllContainers();

            asyncProjectService.findProjectById(
                    projectCard.id(),
                    project -> {
                        this.context = project;
                        updateUI();
                    },
                    error -> log.error("Project loading error", error)
            );

            editProjectButton.setOnAction(event -> {
                NavigationManager.openDialog(Routes.EDIT_PROJECT,
                        "РЕДАКТИРОВАНИЕ",
                        AppConfig.getInstance().getPrimaryStage(),
                        this.context);
            });

            deleteProjectButton.setOnAction(event -> {
                log.debug("Click on delete button");

                log.info("Open delete panel for project: {}", projectCard.id());
                deleteProjectButton.setVisible(false);
                deleteProjectButton.setManaged(false);

                confirmDeleteVBox.setVisible(true);
                confirmDeleteVBox.setManaged(true);
            });

            confirmDeleteButton.setOnAction(event -> {
                asyncProjectService.deleteProject(
                        projectCard.id(),
                        () -> {
                            NavigationManager.showToast("Проект удален", "info");
                        },
                        error -> NavigationManager.showToast("Не удалось удалить", "error")
                );
            });

            cancelDeleteButton.setOnAction(event -> {
                resetDeleteUI();
            });

            loadTasksStatisticByStatus();
            loadTasksStatisticByPriority();
            loadMembers();
            loadRights();
            loadInfo();

            archiveButton.setOnAction(event -> {
                boolean archive = !this.context.archived();
                asyncProjectService.setArchived(this.context.id(), archive,
                        updated -> NavigationManager.showToast(archive ? "Проект в архиве" : "Проект возвращён из архива", "success"),
                        error -> NavigationManager.showToast("Не удалось изменить проект", "error"));
            });
        } else {
            log.error("Context isn't an instance of Project");
        }
    }

    private void loadTasksStatisticByStatus() {

        if (context == null) {
            log.error("Context is null");
            throw new RuntimeException("Context is null");
        }

        asyncTaskService.countTasksByProjectIdAndStatus(context.id(), TaskStatus.IN_PROGRESS,
                amount -> {
                    this.amountOfTasksInProgress = amount;
                    amountOfTasksInProgressLabelSidePanel.setText(String.valueOf(amountOfTasksInProgress));
                    amountOfTasksInProgressLabel.setText(String.valueOf(amountOfTasksInProgress));
                },
                error -> {
                    log.error("Tasks loading error", error);
                    NavigationManager.showToast("Не удалось загрузить задачи!", "warn");
                }
        );

        asyncTaskService.countTasksByProjectIdAndStatus(context.id(), TaskStatus.ON_REVIEW,
                amount -> {
                    this.amountOfTasksUnderReview = amount;
                    amountOfTasksUnderReviewLabelSidePanel.setText(String.valueOf(amountOfTasksUnderReview));
                    amountOfTasksUnderReviewLabel.setText(String.valueOf(amountOfTasksUnderReview));
                },
                error -> {
                    log.error("Tasks loading error", error);
                    NavigationManager.showToast("Не удалось загрузить задачи!", "warn");
                }
        );

        asyncTaskService.countTasksByProjectIdAndStatus(context.id(), TaskStatus.NEW,
                amount -> {
                    amountOfNewTasks = amount;
                    amountOfNewTasksLabelSidePanel.setText(String.valueOf(amountOfNewTasks));
                    amountOfNewTasksLabel.setText(String.valueOf(amountOfNewTasks));
                },
                error -> {
                    log.error("Tasks loading error", error);
                    NavigationManager.showToast("Не удалось загрузить задачи!", "warn");
                }
        );

        asyncTaskService.countTasksByProjectIdAndStatus(context.id(), TaskStatus.DONE,
                amount -> {
                    amountOfCompletedTasks = amount;
                    amountOfCompletedTasksLabelSidePanel.setText(String.valueOf(amountOfCompletedTasks));
                    amountOfCompletedTasksLabel.setText(String.valueOf(amountOfCompletedTasks));
                },
                error -> {
                    log.error("Tasks loading error", error);
                    NavigationManager.showToast("Не удалось загрузить задачи!", "warn");
                }
        );
    }

    /** Менять и удалять проект могут администратор, руководитель и менеджеры проекта. */
    private void loadRights() {
        showManageButtons(false);
        permissions.projectRights(context.id(), rights -> showManageButtons(rights.canManage()));
    }

    private void showManageButtons(boolean visible) {
        editProjectButton.setVisible(visible);
        editProjectButton.setManaged(visible);
        archiveButton.setVisible(visible);
        archiveButton.setManaged(visible);
        if (!visible) {
            resetDeleteUI();
        }
        deleteProjectButton.setVisible(visible);
        deleteProjectButton.setManaged(visible);
    }

    /** Описание, сроки и видимость. */
    private void loadInfo() {
        asyncProjectService.findInfo(context.id(), info -> {
            boolean hasDescription = info.description() != null && !info.description().isBlank();
            projectDescriptionLabel.setText(hasDescription ? info.description() : "");
            projectDescriptionLabel.setVisible(hasDescription);
            projectDescriptionLabel.setManaged(hasDescription);

            StringBuilder meta = new StringBuilder(info.visibility() == Visibility.PUBLIC ? "Открытый" : "Закрытый");
            if (info.startDate() != null || info.deadline() != null) {
                meta.append(" · ")
                        .append(info.startDate() != null ? DateFormatter.formatShortWithYear(info.startDate()) : "…")
                        .append(" — ")
                        .append(info.deadline() != null ? DateFormatter.formatShortWithYear(info.deadline()) : "…");
            }
            projectMetaLabel.setText(meta.toString());
        }, error -> log.error("Project info loading error", error));
    }

    private void loadMembers() {
        asyncProjectService.findMembers(context.id(), this::showMembers,
                error -> log.error("Members loading error", error));
    }

    private void showMembers(List<ProjectMemberView> members) {
        membersVBox.getChildren().clear();
        for (ProjectMemberView member : members) {
            Label initials = new Label(Initials.of(member.name()));
            initials.getStyleClass().add("avatar-text");
            StackPane avatar = new StackPane(initials);
            avatar.getStyleClass().add("avatar-box");

            Label name = new Label(member.name());
            name.getStyleClass().add("member-name");
            Label details = new Label(member.role().getDisplayName() + " · " + tasksText(member.openTasks()));
            details.getStyleClass().add("member-role");

            HBox row = new HBox(10, avatar, new VBox(1, name, details));
            row.setAlignment(Pos.CENTER_LEFT);
            membersVBox.getChildren().add(row);
        }
    }

    /** «1 задача», «3 задачи», «5 задач». */
    static String tasksText(int count) {
        int lastTwo = count % 100;
        int last = count % 10;
        String word = (lastTwo >= 11 && lastTwo <= 14) ? "задач"
                : last == 1 ? "задача"
                : (last >= 2 && last <= 4) ? "задачи"
                : "задач";
        return count + " " + word;
    }

    private void loadTasksStatisticByPriority() {

        if (context == null) {
            log.error("Context is null");
            throw new RuntimeException("Context is null");
        }

        loadPriorityCount(TaskPriority.CRITICAL, amountOfCriticalPriorityTasksLabel);
        loadPriorityCount(TaskPriority.HIGH, amountOfHighPriorityTasksLabel);
        loadPriorityCount(TaskPriority.MEDIUM, amountOfMediumPriorityTasksLabel);
        loadPriorityCount(TaskPriority.LOW, amountOfLowPriorityTasksLabel);
    }

    private void loadPriorityCount(TaskPriority priority, Label label) {
        asyncTaskService.countTasksByProjectIdAndPriority(context.id(), priority,
                amount -> label.setText(String.valueOf(amount)),
                error -> {
                    log.error("Tasks loading error", error);
                    NavigationManager.showToast("Не удалось загрузить задачи!", "warn");
                }
        );
    }

    private void updateUI() {
        if (context == null) {
            log.error("Context not established");
            return;
        }

        log.debug("updateUI: name={}, tasks={}, completed={}, percent={}",
                context.name(),
                context.totalTasks(),
                context.doneTasks(),
                context.percentDone());

        this.projectNameLabel.setText(context.name() + (context.archived() ? " · в архиве" : ""));
        // Заголовок в шапке — название проекта (например, после переименования), если страница на экране
        if (NavigationManager.isCurrent(this) && !context.name().equals(NavigationManager.getCurrentTitle())) {
            NavigationManager.setCurrentTitle(context.name());
        }
        this.archiveButton.setText(context.archived() ? "Вернуть из архива" : "В архив");
        this.projectSupervisorLabel.setText(context.ownerName());
        this.numberOfTasksLabel.setText(String.valueOf(context.totalTasks()));
        this.completedTasksLabel.setText(String.valueOf(context.doneTasks()));
        this.remainingTasksLabel.setText(String.valueOf(context.remainingTasks()));
        this.percentOfCompletionLabel.setText(context.percentDone() + "%");
        this.progressBar.setProgress(context.percentDone() / 100.0);
        this.amountOfTasksInProgressLabelSidePanel.setText(String.valueOf(amountOfTasksInProgress));
        this.amountOfTasksUnderReviewLabelSidePanel.setText(String.valueOf(amountOfTasksUnderReview));
        this.amountOfNewTasksLabelSidePanel.setText(String.valueOf(amountOfNewTasks));
        this.amountOfCompletedTasksLabelSidePanel.setText(String.valueOf(amountOfCompletedTasks));

    }

    private void resetDeleteUI() {
        confirmDeleteVBox.setVisible(false);
        confirmDeleteVBox.setManaged(false);

        deleteProjectButton.setVisible(true);
        deleteProjectButton.setManaged(true);
    }

    @Override
    public void shutdown() {
        log.debug("ProjectDetailsController: shutdown()");
        EventBus.getInstance().unsubscribe(TaskChangedEvent.class, taskUpdateListener);
        EventBus.getInstance().unsubscribe(ProjectChangedEvent.class, projectUpdateListener);

        unregisterLoaderService(newTasksLoader);
        unregisterLoaderService(inProgressLoader);
        unregisterLoaderService(underReviewLoader);
        unregisterLoaderService(completedLoader);
    }

    private void unregisterLoaderService(Service<?> loader) {
        if (loader != null) {
            loader.setOnSucceeded(null);
            loader.setOnFailed(null);
        }
    }
}
