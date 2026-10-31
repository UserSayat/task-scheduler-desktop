package org.example.taskschedulerdesktop.task;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.example.taskschedulerdesktop.access.AsyncPermissionService;
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
import java.util.List;

public class CreateTaskController {

    private static final Logger log = LoggerFactory.getLogger(CreateTaskController.class);

    private final AsyncTaskService taskService;
    private final AsyncProjectService projectService;
    private final AsyncUserService userService;
    private final AsyncPermissionService permissions;

    @FXML private TextField taskNameTextField;

    @FXML private ComboBox<ProjectView> projectComboBox;

    @FXML private ComboBox<User> executorComboBox;

    @FXML private ToggleGroup priorityGroup;
    @FXML private ToggleButton tCritical;
    @FXML private ToggleButton tHigh;
    @FXML private ToggleButton tMedium;
    @FXML private ToggleButton tLow;

    @FXML private DatePicker deadlineDatePicker;

    @FXML private TextField tagsTextField;

    /** Все активные сотрудники; в списке исполнителей — только те, кто видит выбранный проект. */
    private List<User> allUsers = List.of();

    @FXML private Button createTaskButton;
    @FXML private Button cancelButton;

    public CreateTaskController(AsyncTaskService taskService, AsyncProjectService projectService,
                                AsyncUserService userService, AsyncPermissionService permissions) {
        this.taskService = taskService;
        this.projectService = projectService;
        this.userService = userService;
        this.permissions = permissions;
    }

    @FXML
    public void initialize() {
        executorComboBox.setPromptText("Сначала выберите проект");
        userService.findActiveUsers(
                users -> {
                    allUsers = users;
                    refreshExecutors();
                },
                error -> NavigationManager.showToast("Ошибка загрузки сотрудников", "error")
        );
        projectComboBox.valueProperty().addListener((obs, oldProject, newProject) -> refreshExecutors());

        // Задачи создают администратор и участники проекта, кроме наблюдателей (API.md, 8.7)
        projectService.findAllProjects(
                projects -> permissions.projectsForNewTasks(allowed -> {
                    List<ProjectView> available = projects.stream()
                            .filter(project -> !project.archived())
                            .filter(project -> allowed == null || allowed.contains(project.id()))
                            .toList();
                    projectComboBox.setConverter(new ProjectStringConverter());
                    projectComboBox.getItems().setAll(available);
                    if (available.isEmpty()) {
                        createTaskButton.setDisable(true);
                        projectComboBox.setPromptText("Нет проектов, где вы можете создавать задачи");
                    }
                }),
                error -> {
                    log.error("Ошибка загрузки проектов", error);
                    NavigationManager.showToast("Ошибка загрузки проектов", "error");
                }
        );

        tCritical.setUserData(TaskPriority.CRITICAL);
        tHigh.setUserData(TaskPriority.HIGH);
        tMedium.setUserData(TaskPriority.MEDIUM);
        tLow.setUserData(TaskPriority.LOW);
        // Приоритет выбран всегда: повторное нажатие на выбранную кнопку не снимает выбор.
        priorityGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null) {
                oldToggle.setSelected(true);
            }
        });

        createTaskButton.setOnAction(event -> {
            ProjectView project = projectComboBox.getValue();
            User executor = executorComboBox.getValue();
            LocalDate deadline = deadlineDatePicker.getValue();

            if (taskNameTextField.getText().isBlank() || project == null || executor == null || deadline == null) {
                NavigationManager.showToast("Заполните пустые поля", "info");
                return;
            }

            if (deadline.isBefore(LocalDate.now())) {
                NavigationManager.showToast("Дедлайн истек! Выберите корректную дату!", "info");
                return;
            }

            NewTask newTask = new NewTask(
                    taskNameTextField.getText(),
                    project.id(),
                    executor.getId(),
                    (TaskPriority) priorityGroup.getSelectedToggle().getUserData(),
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
                    error -> NavigationManager.showToast(error instanceof IllegalArgumentException
                            ? error.getMessage() : "Ошибка создания задачи", "error")
            );
        });

        cancelButton.setOnAction(event -> {
            Stage stage = (Stage) cancelButton.getScene().getWindow();
            if (stage != null) {
                NavigationManager.closeDialog(stage);
            }
        });
    }

    /**
     * Исполнителем закрытого проекта может быть только его участник или администратор (API.md, 8.7):
     * список зависит от выбранного проекта.
     */
    private void refreshExecutors() {
        ProjectView project = projectComboBox.getValue();
        if (project == null) {
            executorComboBox.getItems().clear();
            return;
        }
        permissions.usersWhoCanView(project.id(), viewers -> {
            if (project != projectComboBox.getValue()) {
                return; // пока шёл запрос, выбрали другой проект
            }
            User selected = executorComboBox.getValue();
            List<User> available = allUsers.stream().filter(user -> viewers.contains(user.getId())).toList();
            executorComboBox.getItems().setAll(available);
            executorComboBox.setPromptText(available.isEmpty() ? "Нет сотрудников с доступом к проекту" : "Назначить");
            if (selected != null && available.stream().anyMatch(user -> user.getId().equals(selected.getId()))) {
                executorComboBox.setValue(selected);
            }
        });
    }
}
