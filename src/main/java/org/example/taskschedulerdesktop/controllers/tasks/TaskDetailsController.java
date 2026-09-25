package org.example.taskschedulerdesktop.controllers.tasks;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.controllers.sidebar.RightSidebar;
import org.example.taskschedulerdesktop.dto.TaskView;
import org.example.taskschedulerdesktop.listeners.EventBus;
import org.example.taskschedulerdesktop.listeners.TaskChangedEvent;
import org.example.taskschedulerdesktop.models.Task;
import org.example.taskschedulerdesktop.navigation.NavigationManager;
import org.example.taskschedulerdesktop.service.task.AsyncTaskService;
import org.example.taskschedulerdesktop.utils.TaskPriority;
import org.example.taskschedulerdesktop.utils.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskDetailsController implements RightSidebar {

    private static final Logger log = LoggerFactory.getLogger(TaskDetailsController.class);
    private final AsyncTaskService taskService;

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
    @FXML private ComboBox<String> executorComboBox;

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

    public TaskDetailsController(AsyncTaskService taskService) {
        this.taskService = taskService;
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

        executorComboBox.getItems().addAll("Алексей Козлов", "Мария Волкова", "Елена Никитина",
                "Павел Сорокин", "Дмитрий Лебедев", "Ирина Фёдорова");

        closeButton.setOnAction(event -> {
            log.debug("Close right sidebar");
            NavigationManager.closeRightSidebar();
        });

        acceptButton.setOnAction(event -> {
            log.debug("Click on button accept");

            //taskService.assignExecutor();


        });

        editButton.setOnAction(event -> {
            log.debug("Click on edit button");

            if (contextTask == null) {
                log.warn("contextTask = null");
                return;
            }

            enterEditMode();
//            log.info("Open edit sidebar for task: {}", contextTask.getTaskName());
//            NavigationManager.openRightSidebar(Routes.EDIT_TASK_RIGHT_SIDEBAR, contextTask);
        });

        saveEditedTaskButton.setOnAction(event -> {
            contextTask.setTaskName(taskNameLabel.getText());
            contextTask.setStatus(TaskStatus.fromString(statusComboBox.getValue()));
            contextTask.setPriority(TaskPriority.fromString(priorityComboBox.getValue()));
            contextTask.setExecutor(executorComboBox.getValue());
            if (deadlineDatePicker.getValue() != null) {
                contextTask.setDeadline(deadlineDatePicker.getValue());
            } else {
                NavigationManager.showToast("Выберите дату!", "info");
                return;
            }
            contextTask.setType(tagsTextField.getText());
            //contextTask.setDescription(taskDescriptionTextArea.getText());

            taskService.updateTask(
                    toTask(contextTask),
                    () -> {
                        exitEditMode();
                        NavigationManager.showToast("Данные обновлены", "success");
                    },
                    error -> {
                        exitEditMode();
                        NavigationManager.showToast("Возникла ошибка при обновлении данных", "error");
                    });
        });

        cancelEditButton.setOnAction(event -> {
            exitEditMode();
        });

        deleteButton.setOnAction(event -> {
            log.debug("Click on delete button");

            log.info("Open delete panel for task: {}", contextTask.getTaskName());
            deleteButton.setVisible(false);
            deleteButton.setManaged(false);

            confirmDeletePanelVBox.setVisible(true);
            confirmDeletePanelVBox.setManaged(true);

        });

        confirmDeleteButton.setOnAction(event -> {
            taskService.deleteTask(
                    contextTask,
                    () -> {
                        EventBus.getInstance().fire(new TaskChangedEvent(contextTask.getProjectId(), contextTask.getTaskName()));
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
        log.info("context saved: {}", contextTask.getTaskName());
        exitEditMode();
        updateUI();
    }

    @Override
    public void updateUI() {
        log.debug("updateUI()");

        if (contextTask == null) {
            log.warn("contextTask = null, skip");
            return;
        }

        log.debug("projectName: {}", contextTask.getProjectId());
        log.debug("taskName: {}", contextTask.getTaskName());
        log.debug("status: {}", contextTask.getStatus());
        log.debug("priority: {}", contextTask.getPriority().getDisplayName());
        projectNameLabel.setText(contextTask.getProjectName());
        taskNameLabel.setText(contextTask.getTaskName());
        statusLabel.setText(contextTask.getStatus().getDisplayName());
        priorityLabel.setText(contextTask.getPriority().getDisplayName());
        executorAvatarLabel.setText("AK");
        executorNameLabel.setText(contextTask.getExecutor());
        deadlineLabel.setText(contextTask.getDeadline().toString());
        updateTags();
        taskDescriptionLabel.setText(contextTask.getDescription());

        log.info("UI updated");
    }

    private void updateTags() {
        tagsFlowPane.getChildren().clear();

        String type = contextTask.getType();
        if (type == null || type.isBlank()) {
            tagsFlowPane.setVisible(false);
            tagsFlowPane.setManaged(false);
            return;
        }

        String[] tags = type.split(",");
        boolean hasValidTags = false;

        for (String tag : tags) {
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) {
                Label tagLabel = new Label(trimmed);
                tagLabel.getStyleClass().add("tag-chip");
                tagsFlowPane.getChildren().add(tagLabel);
                hasValidTags = true;
            }
        }

        tagsFlowPane.setVisible(hasValidTags);
        tagsFlowPane.setManaged(hasValidTags);
    }

    private void resetDeleteUI() {
        confirmDeletePanelVBox.setVisible(false);
        confirmDeletePanelVBox.setManaged(false);

        deleteButton.setVisible(true);
        deleteButton.setManaged(true);
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

        newTaskNameLabel.setText(contextTask.getTaskName());
        statusComboBox.setValue(contextTask.getStatus().getDisplayName());
        priorityComboBox.setValue(contextTask.getPriority().getDisplayName());
        deadlineDatePicker.setValue(contextTask.getDeadline());
        executorComboBox.setValue(contextTask.getExecutor());
        tagsTextField.setText(contextTask.getType());
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

    private Task toTask(TaskView taskView) {
        return new Task(taskView.getId(),
                taskView.getTaskName(),
                taskView.getProjectId(),
                taskView.getExecutor(),
                taskView.getType(),
                taskView.getStatus(),
                taskView.getPriority(),
                taskView.getDeadline(),
                taskView.getDescription(),
                taskView.isSynced());
    }
}
