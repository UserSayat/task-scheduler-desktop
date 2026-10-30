package org.example.taskschedulerdesktop.task;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.access.AsyncPermissionService;
import org.example.taskschedulerdesktop.access.PermissionService;
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

    @FXML private Label executorAvatarLabel;
    @FXML private Label executorNameLabel;
    @FXML private ComboBox<User> executorComboBox;

    @FXML private Label deadlineLabel;
    @FXML private DatePicker deadlineDatePicker;

    @FXML private FlowPane tagsFlowPane;
    @FXML private TextField tagsTextField;

    @FXML private Label taskDescriptionLabel;
    //TODO добавить в режиме редактирования
    //@FXML private TextArea taskDescriptionTextArea;

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

        userService.findActiveUsers(
                users -> executorComboBox.getItems().setAll(users),
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
                    contextTask.description(),
                    TaskStatus.fromString(statusComboBox.getValue()),
                    TaskPriority.fromString(priorityComboBox.getValue()),
                    deadline,
                    executor != null ? executor.getId() : contextTask.responsibleId(),
                    Tags.parse(tagsTextField.getText()));

            taskService.updateTask(
                    edit,
                    updated -> {
                        contextTask = updated;
                        exitEditMode();
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
        taskDescriptionLabel.setText(contextTask.description());

        log.info("UI updated");
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
        executorComboBox.setValue(executorComboBox.getItems().stream()
                .filter(user -> user.getId().equals(contextTask.responsibleId()))
                .findFirst()
                .orElse(null));
        tagsTextField.setText(contextTask.tagsAsText());

        boolean statusOnly = !rights.canEdit();
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

        updateUI();
    }
}
