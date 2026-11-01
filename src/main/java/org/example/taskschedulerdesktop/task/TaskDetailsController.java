package org.example.taskschedulerdesktop.task;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.access.AsyncPermissionService;
import org.example.taskschedulerdesktop.access.PermissionService;
import org.example.taskschedulerdesktop.access.ProjectTeam;
import org.example.taskschedulerdesktop.shared.ui.RightSidebar;
import org.example.taskschedulerdesktop.user.User;
import org.example.taskschedulerdesktop.shared.navigation.NavigationManager;
import org.example.taskschedulerdesktop.user.AsyncUserService;
import org.example.taskschedulerdesktop.user.Initials;
import org.example.taskschedulerdesktop.tag.TagChips;
import org.example.taskschedulerdesktop.tag.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.example.taskschedulerdesktop.shared.ui.DateFormatter.formatFull;

public class TaskDetailsController implements RightSidebar {

    private static final Logger log = LoggerFactory.getLogger(TaskDetailsController.class);
    private final AsyncTaskService taskService;
    private final AsyncUserService userService;
    private final AsyncPermissionService permissions;

    @FXML private Label projectNameLabel;
    @FXML private Button closeButton;

    @FXML private Label taskNameLabel;
    @FXML private TextField newTaskNameLabel;

    @FXML private Label statusLabel;
    @FXML private ComboBox<String> statusComboBox;

    @FXML private Label priorityLabel;
    @FXML private ComboBox<String> priorityComboBox;

    @FXML private HBox assigneeView;
    @FXML private Label executorAvatarLabel;
    @FXML private Label executorNameLabel;
    @FXML private ComboBox<User> executorComboBox;

    @FXML private Label deadlineLabel;
    @FXML private DatePicker deadlineDatePicker;

    @FXML private FlowPane tagsFlowPane;
    @FXML private TextField tagsTextField;

    @FXML private Label taskDescriptionLabel;
    @FXML private VBox descriptionBox;
    @FXML private TextArea descriptionTextArea;

    @FXML private Label coExecutorsLabel;
    @FXML private MenuButton coExecutorsMenu;
    @FXML private Label observersLabel;
    @FXML private MenuButton observersMenu;
    @FXML private Label startDateLabel;
    @FXML private DatePicker startDatePicker;

    private UserPicker coExecutorsPicker;
    private UserPicker observersPicker;

    /** Дата начала, соисполнители и наблюдатели открытой задачи; до загрузки — null. */
    private TaskExtras extras;

    /** Имена всех сотрудников, в том числе заблокированных, — для подписей. */
    private Map<UUID, String> userNames = Map.of();
    /** Все активные сотрудники. */
    private List<User> allUsers = List.of();
    /** Кто видит проект задачи; null — ещё не загружено. */
    private ProjectTeam projectTeam;

    @FXML private HBox viewActionsHBox;
    @FXML private Button acceptButton;

    @FXML private Button editButton;
    @FXML private HBox editActionsHBox;
    @FXML private Button saveEditedTaskButton;
    @FXML private Button cancelEditButton;

    @FXML private Button deleteButton;
    @FXML private VBox confirmDeletePanelVBox;
    @FXML private Button confirmDeleteButton;
    @FXML private Button cancelDeleteButton;

    private TaskView contextTask;
    private boolean editMode = false;

    /** Права на открытую задачу; до проверки — никаких. */
    private PermissionService.TaskRights rights = new PermissionService.TaskRights(false, false);

    public TaskDetailsController(AsyncTaskService taskService, AsyncUserService userService,
                                 AsyncPermissionService permissions) {
        this.taskService = taskService;
        this.userService = userService;
        this.permissions = permissions;
    }

    @FXML
    public void initialize() {
        log.info("initialize()");

        for (TaskStatus status : TaskStatus.values()) {
            statusComboBox.getItems().add(status.getDisplayName());
        }

        for (TaskPriority priority : TaskPriority.values()) {
            priorityComboBox.getItems().add(priority.getDisplayName());
        }

        coExecutorsPicker = new UserPicker(coExecutorsMenu, "Нет соисполнителей");
        observersPicker = new UserPicker(observersMenu, "Нет наблюдателей");

        userService.findActiveUsers(
                users -> {
                    allUsers = users;
                    executorComboBox.getItems().setAll(users);
                    userNames = users.stream().collect(Collectors.toMap(User::getId, User::getName));
                    showExtras();
                },
                error -> NavigationManager.showToast("Ошибка загрузки сотрудников", "error")
        );

        closeButton.setOnAction(event -> {
            log.debug("Close right sidebar");
            NavigationManager.closeRightSidebar();
        });

        acceptButton.setOnAction(event -> {
            if (contextTask == null) {
                return;
            }
            TaskEdit edit = new TaskEdit(contextTask.id(), contextTask.title(), contextTask.description(),
                    TaskStatus.IN_PROGRESS, contextTask.priority(), contextTask.deadline(),
                    contextTask.responsibleId(), contextTask.tagNames());
            taskService.updateTask(edit,
                    updated -> {
                        contextTask = updated;
                        updateUI();
                        applyRights(rights);
                        NavigationManager.showToast("Задача в работе", "success");
                    },
                    error -> NavigationManager.showToast("Не удалось взять задачу в работу", "error"));
        });

        editButton.setOnAction(event -> {
            log.debug("Click on edit button");

            if (contextTask == null) {
                log.warn("contextTask = null");
                return;
            }

            enterEditMode();
//            log.info("Open edit sidebar for task: {}", contextTask.title());
//            NavigationManager.openRightSidebar(Routes.EDIT_TASK_RIGHT_SIDEBAR, contextTask);
        });

        saveEditedTaskButton.setOnAction(event -> {
            LocalDate deadline = deadlineDatePicker.getValue();
            LocalDate startDate = startDatePicker.getValue();
            if (startDate != null && deadline != null && deadline.isBefore(startDate)) {
                NavigationManager.showToast("Дедлайн раньше даты начала", "info");
                return;
            }
            // Исполнитель меняет только статус — срок он не выбирает
            if (deadline == null && rights.canEdit()) {
                NavigationManager.showToast("Выберите дату!", "info");
                return;
            }
            // Прошедший дедлайн можно оставить как есть, но нельзя поставить заново
            if (deadline.isBefore(LocalDate.now()) && !deadline.equals(contextTask.deadline())) {
                NavigationManager.showToast("Дедлайн истек! Выберете корректную дату!", "info");
                return;
            }
            if (newTaskNameLabel.getText().isBlank()) {
                NavigationManager.showToast("Введите название задачи", "info");
                return;
            }

            User executor = executorComboBox.getValue();
            TaskEdit edit = new TaskEdit(
                    contextTask.id(),
                    newTaskNameLabel.getText(),
                    blankToNull(descriptionTextArea.getText()),
                    TaskStatus.fromString(statusComboBox.getValue()),
                    TaskPriority.fromString(priorityComboBox.getValue()),
                    deadline,
                    executor != null ? executor.getId() : contextTask.responsibleId(),
                    Tags.parse(tagsTextField.getText()),
                    extras == null ? null
                            : new TaskExtras(startDate, coExecutorsPicker.selected(), observersPicker.selected()));

            taskService.updateTask(
                    edit,
                    updated -> {
                        contextTask = updated;
                        exitEditMode();
                        loadExtras();
                        NavigationManager.showToast("Данные обновлены", "success");
                    },
                    error -> {
                        exitEditMode();
                        NavigationManager.showToast(error instanceof IllegalArgumentException
                                ? error.getMessage() : "Возникла ошибка при обновлении данных", "error");
                    });
        });

        cancelEditButton.setOnAction(event -> {
            exitEditMode();
        });

        deleteButton.setOnAction(event -> {
            log.debug("Click on delete button");

            log.info("Open delete panel for task: {}", contextTask.title());
            deleteButton.setVisible(false);
            deleteButton.setManaged(false);

            confirmDeletePanelVBox.setVisible(true);
            confirmDeletePanelVBox.setManaged(true);

        });

        confirmDeleteButton.setOnAction(event -> {
            taskService.deleteTask(
                    contextTask,
                    () -> {
                        NavigationManager.closeRightSidebar();
                        NavigationManager.showToast("Задача удалена!", "info");
                    },
                    error -> NavigationManager.showToast("Не удалось удалить", "error")
            );
        });

        cancelDeleteButton.setOnAction(event -> {
            resetDeleteUI();
        });
    }

    @Override
    public void setContext(Object context) {
        log.info("setContext()");
        log.debug("context = {}", context);

        if (!(context instanceof TaskView)) {
            log.error("Expected TaskView, received: {}", context.getClass().getSimpleName());
            throw new IllegalArgumentException();
        }

        this.contextTask = (TaskView) context;
        log.info("context saved: {}", contextTask.title());
        exitEditMode();
        updateUI();
        loadRights();
        loadExtras();
        loadProjectTeam();
    }

    private void loadProjectTeam() {
        projectTeam = null;
        TaskView task = contextTask;
        permissions.projectTeam(task.projectId(), team -> {
            if (task == contextTask) {
                projectTeam = team;
            }
        });
    }

    /**
     * Кого можно выбрать исполнителем и соисполнителем: участников проекта, кроме наблюдателей (API.md, 8.7),
     * и тех, кто уже исполняет задачу, — чтобы их можно было оставить или снять.
     */
    private List<User> assigneeCandidates() {
        Set<UUID> current = new HashSet<>();
        if (contextTask.responsibleId() != null) {
            current.add(contextTask.responsibleId());
        }
        if (extras != null) {
            current.addAll(extras.coExecutorIds());
        }
        return candidates(projectTeam == null ? null : projectTeam.assignable(), current);
    }

    /** Наблюдателем — любого участника проекта, администратора и тех, кто уже наблюдает. */
    private List<User> observerCandidates() {
        return candidates(projectTeam == null ? null : projectTeam.observers(),
                extras != null ? extras.observerIds() : List.of());
    }

    private List<User> candidates(Set<UUID> team, Collection<UUID> current) {
        if (team == null) {
            return allUsers; // состав ещё загружается; лишнего не пропустит проверка при сохранении
        }
        Set<UUID> allowed = new HashSet<>(team);
        allowed.addAll(current);
        return allUsers.stream().filter(user -> allowed.contains(user.getId())).toList();
    }

    @Override
    public void updateUI() {
        log.debug("updateUI()");

        if (contextTask == null) {
            log.warn("contextTask = null, skip");
            return;
        }

        log.debug("projectName: {}", contextTask.projectId());
        log.debug("taskName: {}", contextTask.title());
        log.debug("status: {}", contextTask.status());
        log.debug("priority: {}", contextTask.priority().getDisplayName());
        projectNameLabel.setText(contextTask.projectName());
        taskNameLabel.setText(contextTask.title());
        statusLabel.setText(contextTask.status().getDisplayName());
        PriorityBadge.apply(priorityLabel, contextTask.priority());
        executorAvatarLabel.setText(Initials.of(contextTask.responsibleName()));
        executorNameLabel.setText(contextTask.responsibleName() != null ? contextTask.responsibleName() : "Не назначен");
        deadlineLabel.setText(formatFull(contextTask.deadline()));
        updateTags();
        boolean hasDescription = contextTask.description() != null && !contextTask.description().isBlank();
        taskDescriptionLabel.setText(hasDescription ? contextTask.description() : "Описания нет");

        log.info("UI updated");
    }

    private void loadExtras() {
        taskService.findTaskExtras(contextTask.id(), loaded -> {
            extras = loaded;
            showExtras();
        }, error -> log.error("Task extras loading error", error));
    }

    private void showExtras() {
        if (extras == null) {
            return;
        }
        coExecutorsLabel.setText(names(extras.coExecutorIds(), "Нет"));
        observersLabel.setText(names(extras.observerIds(), "Нет"));
        startDateLabel.setText(extras.startDate() != null ? formatFull(extras.startDate()) : "—");
    }

    private String names(List<UUID> ids, String empty) {
        if (ids.isEmpty()) {
            return empty;
        }
        return ids.stream().map(id -> userNames.getOrDefault(id, "Неактивный сотрудник")).collect(Collectors.joining(", "));
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    /** Поля «Описание», «Начало», «Соисполнители», «Наблюдатели»: просмотр или правка. */
    private void showEditExtras(boolean editing) {
        show(assigneeView, !editing);
        show(descriptionBox, !editing);
        show(descriptionTextArea, editing);
        show(startDateLabel, !editing);
        show(startDatePicker, editing);
        show(coExecutorsLabel, !editing);
        show(coExecutorsMenu, editing);
        show(observersLabel, !editing);
        show(observersMenu, editing);
    }

    private void updateTags() {
        TagChips.fill(tagsFlowPane, contextTask.tagNames());
    }

    /**
     * «Изменить» видят те, кто может менять задачу целиком или только её статус (исполнители),
     * «Удалить» — только первые (API.md, 8.7).
     */
    private void loadRights() {
        applyRights(new PermissionService.TaskRights(false, false));
        permissions.taskRights(contextTask.id(), this::applyRights);
    }

    private void applyRights(PermissionService.TaskRights taskRights) {
        this.rights = taskRights;
        editButton.setVisible(taskRights.canChangeStatus());
        editButton.setManaged(taskRights.canChangeStatus());
        boolean canTake = taskRights.canChangeStatus() && contextTask.status() == TaskStatus.NEW;
        acceptButton.setVisible(canTake);
        acceptButton.setManaged(canTake);
        if (!taskRights.canEdit()) {
            resetDeleteUI();
        }
        deleteButton.setVisible(taskRights.canEdit());
        deleteButton.setManaged(taskRights.canEdit());
    }

    private void resetDeleteUI() {
        confirmDeletePanelVBox.setVisible(false);
        confirmDeletePanelVBox.setManaged(false);

        deleteButton.setVisible(rights.canEdit());
        deleteButton.setManaged(rights.canEdit());
    }

    private void enterEditMode() {
        editMode = true;

        taskNameLabel.setVisible(false);
        taskNameLabel.setManaged(false);
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
        priorityLabel.setVisible(false);
        priorityLabel.setManaged(false);
        deadlineLabel.setVisible(false);
        deadlineLabel.setManaged(false);
        executorAvatarLabel.setVisible(false);
        executorAvatarLabel.setManaged(false);
        executorNameLabel.setVisible(false);
        executorNameLabel.setManaged(false);
        tagsFlowPane.setVisible(false);
        tagsFlowPane.setManaged(false);
        viewActionsHBox.setVisible(false);
        viewActionsHBox.setManaged(false);

        newTaskNameLabel.setVisible(true);
        newTaskNameLabel.setManaged(true);
        statusComboBox.setVisible(true);
        statusComboBox.setManaged(true);
        priorityComboBox.setVisible(true);
        priorityComboBox.setManaged(true);
        deadlineDatePicker.setVisible(true);
        deadlineDatePicker.setManaged(true);
        executorComboBox.setVisible(true);
        executorComboBox.setManaged(true);
        tagsTextField.setVisible(true);
        tagsTextField.setManaged(true);
        editActionsHBox.setVisible(true);
        editActionsHBox.setManaged(true);

        newTaskNameLabel.setText(contextTask.title());
        statusComboBox.setValue(contextTask.status().getDisplayName());
        priorityComboBox.setValue(contextTask.priority().getDisplayName());
        deadlineDatePicker.setValue(contextTask.deadline());
        executorComboBox.getItems().setAll(assigneeCandidates());
        executorComboBox.setValue(executorComboBox.getItems().stream()
                .filter(user -> user.getId().equals(contextTask.responsibleId()))
                .findFirst()
                .orElse(null));
        tagsTextField.setText(contextTask.tagsAsText());
        descriptionTextArea.setText(contextTask.description());
        showEditExtras(true);
        if (extras != null) {
            startDatePicker.setValue(extras.startDate());
            coExecutorsPicker.show(executorComboBox.getItems(), Set.copyOf(extras.coExecutorIds()), userNames);
            observersPicker.show(observerCandidates(), Set.copyOf(extras.observerIds()), userNames);
        }

        boolean statusOnly = !rights.canEdit();
        descriptionTextArea.setDisable(statusOnly);
        startDatePicker.setDisable(statusOnly || extras == null);
        coExecutorsMenu.setDisable(statusOnly || extras == null);
        observersMenu.setDisable(statusOnly || extras == null);
        newTaskNameLabel.setDisable(statusOnly);
        priorityComboBox.setDisable(statusOnly);
        deadlineDatePicker.setDisable(statusOnly);
        executorComboBox.setDisable(statusOnly);
        tagsTextField.setDisable(statusOnly);
    }

    private void exitEditMode() {
        editMode = false;

        taskNameLabel.setVisible(true);
        taskNameLabel.setManaged(true);
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
        priorityLabel.setVisible(true);
        priorityLabel.setManaged(true);
        deadlineLabel.setVisible(true);
        deadlineLabel.setManaged(true);
        executorAvatarLabel.setVisible(true);
        executorAvatarLabel.setManaged(true);
        executorNameLabel.setVisible(true);
        executorNameLabel.setManaged(true);
        tagsFlowPane.setVisible(true);
        tagsFlowPane.setManaged(true);
        viewActionsHBox.setVisible(true);
        viewActionsHBox.setManaged(true);

        newTaskNameLabel.setVisible(false);
        newTaskNameLabel.setManaged(false);
        statusComboBox.setVisible(false);
        statusComboBox.setManaged(false);
        priorityComboBox.setVisible(false);
        priorityComboBox.setManaged(false);
        deadlineDatePicker.setVisible(false);
        deadlineDatePicker.setManaged(false);
        executorComboBox.setVisible(false);
        executorComboBox.setManaged(false);
        tagsTextField.setVisible(false);
        tagsTextField.setManaged(false);
        editActionsHBox.setVisible(false);
        editActionsHBox.setManaged(false);
        showEditExtras(false);

        updateUI();
    }
}
