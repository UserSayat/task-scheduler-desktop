package org.example.taskschedulerdesktop.controllers.tasks;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.example.taskschedulerdesktop.models.Project;
import org.example.taskschedulerdesktop.models.Task;
import org.example.taskschedulerdesktop.navigation.NavigationManager;
import org.example.taskschedulerdesktop.service.project.AsyncProjectService;
import org.example.taskschedulerdesktop.service.task.AsyncTaskService;
import org.example.taskschedulerdesktop.utils.ProjectStringConverter;
import org.example.taskschedulerdesktop.utils.TaskPriority;
import org.example.taskschedulerdesktop.utils.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

public class CreateTaskController {

    private static final Logger log = LoggerFactory.getLogger(CreateTaskController.class);

    private final AsyncTaskService taskService;
    private final AsyncProjectService projectService;

    @FXML private TextField taskNameTextField;

    @FXML private ComboBox<Project> projectComboBox;
    private Long selectedProject;

    @FXML private ComboBox<String> executorComboBox;
    private String selectedExecutor;

    @FXML private ToggleButton tHigh;
    @FXML private ToggleButton tMedium;
    @FXML private ToggleButton tLow;
    private TaskPriority currentPriority = TaskPriority.LOW;

    @FXML private DatePicker deadlineDatePicker;
    private LocalDate selectedDate;

    @FXML private TextField tagsTextField;

    @FXML private Button createTaskButton;
    @FXML private Button cancelButton;

    public CreateTaskController(AsyncTaskService taskService, AsyncProjectService projectService) {
        this.taskService = taskService;
        this.projectService = projectService;
    }

    @FXML
    public void initialize() {
        executorComboBox.getItems().addAll("Алексей Козлов", "Мария Волкова", "Елена Никитина",
                "Павел Сорокин", "Дмитрий Лебедев", "Ирина Фёдорова");

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

        projectComboBox.setOnAction(event -> {
            selectedProject = projectComboBox.getValue().getId();
        });

        executorComboBox.setOnAction(event -> {
            selectedExecutor = executorComboBox.getValue();
        });

        tHigh.setOnAction(event -> {
            this.currentPriority = TaskPriority.HIGH;
        });

        tMedium.setOnAction(event -> {
            this.currentPriority = TaskPriority.MIDDLE;
        });

        tLow.setOnAction(event -> {
            this.currentPriority = TaskPriority.LOW;
        });

        deadlineDatePicker.setOnAction(event -> {
            selectedDate = deadlineDatePicker.getValue();
        });

        createTaskButton.setOnAction(event -> {
            if (taskNameTextField.getText().isEmpty() ||
                    selectedProject == null ||
                    selectedExecutor == null ||
                    selectedDate == null) {
                NavigationManager.showToast("Заполните пустые поля", "info");
                return;
            }

            if (deadlineDatePicker.getValue().isBefore(LocalDate.now())) {
                NavigationManager.showToast("Дедлайн истек! Выберете корректную дату!", "info");
                return;
            }

            //TODO может быть стоит использовать AsyncTaskService
            taskService.createTask(new Task(null,
                    taskNameTextField.getText(),
                    selectedProject,
                    selectedExecutor,
                    tagsTextField.getText(),
                    TaskStatus.NEW,
                    currentPriority,
                    deadlineDatePicker.getValue(),
                    null,
                    false),
                    () -> {
                        Stage stage = (Stage) createTaskButton.getScene().getWindow();
                        if (stage != null) {
                            NavigationManager.closeDialog(stage);
                            NavigationManager.showToast("Задача сохранена", "success");
                        }
                    },
                    error -> NavigationManager.showToast("Ошибка создания задачи", "error")
            );

            log.debug("createTaskButton.getScene() = {}", createTaskButton.getScene());
            log.debug("createTaskButton.getScene().getWindow() = {}", createTaskButton.getScene().getWindow());
        });

        cancelButton.setOnAction(event -> {
            Stage stage = (Stage) cancelButton.getScene().getWindow();
            if (stage != null) {
                NavigationManager.closeDialog(stage);
            }
        });
    }
}
