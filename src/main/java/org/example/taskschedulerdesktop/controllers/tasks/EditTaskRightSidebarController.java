package org.example.taskschedulerdesktop.controllers.tasks;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.controllers.sidebar.RightSidebarController;
import org.example.taskschedulerdesktop.models.Task;
import org.example.taskschedulerdesktop.navigation.NavigationManager;
import org.example.taskschedulerdesktop.service.task.AsyncTaskService;
import org.example.taskschedulerdesktop.utils.TaskPriority;
import org.example.taskschedulerdesktop.utils.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EditTaskRightSidebarController implements RightSidebarController {

    private static final Logger log = LoggerFactory.getLogger(EditTaskRightSidebarController.class);

    private final AsyncTaskService taskService;

    @FXML private VBox sidebarRoot;
    @FXML private Button closeButton;
    @FXML private Label projectNameLabel;
    @FXML private Label taskNameLabel;
    @FXML private ChoiceBox<String> statusChoiceBox;
    @FXML private ChoiceBox<String> priorityChoiceBox;
    @FXML private ChoiceBox<String> executorChoiceBox;
    @FXML private DatePicker deadlineDatePicker;
    @FXML private TextField tagsTextField;
    @FXML private TextArea taskDescriptionTextArea;
    @FXML private Button saveTheTaskButton;
    @FXML private Button cancelButton;

    private Task contextTask;

    public EditTaskRightSidebarController(AsyncTaskService taskService) {
        this.taskService = taskService;
    }

    @FXML
    public void initialize() {
        log.info("initialize()");

        for (TaskStatus status : TaskStatus.values()) {
            statusChoiceBox.getItems().add(status.getDisplayName());
        }

        for (TaskPriority priority : TaskPriority.values()) {
            priorityChoiceBox.getItems().add(priority.getDisplayName());
        }

        closeButton.setOnAction(event -> {
            log.debug("Close right sidebar");
            NavigationManager.closeRightSidebar();
        });

        saveTheTaskButton.setOnAction(event -> {
            contextTask.setStatus(TaskStatus.fromString(statusChoiceBox.getValue()));
            contextTask.setPriority(TaskPriority.fromString(priorityChoiceBox.getValue()));
            if (deadlineDatePicker.getValue() != null) {
                contextTask.setDeadline(deadlineDatePicker.getValue());
            } else {
                NavigationManager.showToast("Выберите дату!", "info");
                return;
            }
            contextTask.setType(tagsTextField.getText());
            contextTask.setDescription(taskDescriptionTextArea.getText());

            taskService.updateTask(
                    contextTask,
                    () -> NavigationManager.showToast("Данные обновлены", "success"),
                    error -> {
                        NavigationManager.showToast("Возникла ошибка при обновлении данных", "error");
                    });
        });
    }

    @Override
    public void setContext(Object context) {
        if (!(context instanceof Task)) {
            throw new IllegalArgumentException();
        }

        this.contextTask = (Task) context;

        updateUI();
    }

    @Override
    public void updateUI() {

        if (contextTask == null) {
            return;
        }

        taskService.getProjectNameById(contextTask.getProjectId(),
                projectName -> projectNameLabel.setText(projectName),
                error -> log.error(error.getClass().getName()));
        taskNameLabel.setText(contextTask.getTaskName());
        statusChoiceBox.setValue(contextTask.getStatus().getDisplayName());
        priorityChoiceBox.setValue(contextTask.getPriority().getDisplayName());
        executorChoiceBox.setValue(contextTask.getExecutor());
        deadlineDatePicker.setValue(contextTask.getDeadline());
        tagsTextField.setText(contextTask.getType());
        taskDescriptionTextArea.setText(contextTask.getDescription());
    }

    public void loadExecutors() {

    }
}

