package org.example.taskschedulerdesktop.task;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.example.taskschedulerdesktop.project.ProjectView;
import org.example.taskschedulerdesktop.user.User;
import org.example.taskschedulerdesktop.shared.navigation.NavigationManager;
import org.example.taskschedulerdesktop.project.AsyncProjectService;
import org.example.taskschedulerdesktop.user.AsyncUserService;
import org.example.taskschedulerdesktop.project.ProjectStringConverter;
import org.example.taskschedulerdesktop.tag.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

public class CreateTaskController {

    private static final Logger log = LoggerFactory.getLogger(CreateTaskController.class);

    private final AsyncTaskService taskService;
    private final AsyncProjectService projectService;
    private final AsyncUserService userService;

    @FXML private TextField taskNameTextField;

    @FXML private ComboBox<ProjectView> projectComboBox;

    @FXML private ComboBox<User> executorComboBox;

    @FXML private ToggleButton tHigh;
    @FXML private ToggleButton tMedium;
    @FXML private ToggleButton tLow;
    private TaskPriority currentPriority = TaskPriority.LOW;

    @FXML private DatePicker deadlineDatePicker;

    @FXML private TextField tagsTextField;

    @FXML private Button createTaskButton;
    @FXML private Button cancelButton;

    public CreateTaskController(AsyncTaskService taskService, AsyncProjectService projectService,
                                AsyncUserService userService) {
        this.taskService = taskService;
        this.projectService = projectService;
        this.userService = userService;
    }

    @FXML
    public void initialize() {
        userService.findActiveUsers(
                users -> executorComboBox.getItems().setAll(users),
                error -> NavigationManager.showToast("Ошибка загрузки сотрудников", "error")
        );

        projectService.findAllProjects(
                projects -> {
                    projectComboBox.setConverter(new ProjectStringConverter());
                    projectComboBox.getItems().setAll(projects);
                },
                error -> {
                    log.error("Ошибка загрузки проектов", error);
                    NavigationManager.showToast("Ошибка загрузки проектов", "error");
                }
        );

        tHigh.setOnAction(event -> this.currentPriority = TaskPriority.HIGH);
        tMedium.setOnAction(event -> this.currentPriority = TaskPriority.MEDIUM);
        tLow.setOnAction(event -> this.currentPriority = TaskPriority.LOW);

        createTaskButton.setOnAction(event -> {
            ProjectView project = projectComboBox.getValue();
            User executor = executorComboBox.getValue();
            LocalDate deadline = deadlineDatePicker.getValue();

            if (taskNameTextField.getText().isBlank() || project == null || executor == null || deadline == null) {
                NavigationManager.showToast("Заполните пустые поля", "info");
                return;
            }

            if (deadline.isBefore(LocalDate.now())) {
                NavigationManager.showToast("Дедлайн истек! Выберете корректную дату!", "info");
                return;
            }

            NewTask newTask = new NewTask(
                    taskNameTextField.getText(),
                    project.id(),
                    executor.getId(),
                    currentPriority,
                    deadline,
                    Tags.parse(tagsTextField.getText()));

            taskService.createTask(newTask,
                    created -> {
                        Stage stage = (Stage) createTaskButton.getScene().getWindow();
                        if (stage != null) {
                            NavigationManager.closeDialog(stage);
                            NavigationManager.showToast("Задача сохранена", "success");
                        }
                    },
                    error -> NavigationManager.showToast("Ошибка создания задачи", "error")
            );
        });

        cancelButton.setOnAction(event -> {
            Stage stage = (Stage) cancelButton.getScene().getWindow();
            if (stage != null) {
                NavigationManager.closeDialog(stage);
            }
        });
    }
}
