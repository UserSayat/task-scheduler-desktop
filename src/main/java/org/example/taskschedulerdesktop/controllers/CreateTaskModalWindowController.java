package org.example.taskschedulerdesktop.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.example.taskschedulerdesktop.models.Task;
import org.example.taskschedulerdesktop.navigation.NavigationManager;
import org.example.taskschedulerdesktop.service.task.TaskService;
import org.example.taskschedulerdesktop.utils.TaskPriority;
import org.example.taskschedulerdesktop.utils.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

public class CreateTaskModalWindowController {

    private static final Logger log = LoggerFactory.getLogger(CreateTaskModalWindowController.class);

    private final TaskService taskService;

    @FXML private TextField taskNameTextField;

    @FXML private ChoiceBox<String> projectChoiceBox;
    private String selectedProject;

    @FXML private ChoiceBox<String>executorChoiceBox;
    private String selectedExecutor;

    @FXML private Button highPriorityButton;
    @FXML private Button middlePriorityButton;
    @FXML private Button lowPriorityButton;
    private TaskPriority currentPriority = TaskPriority.LOW;

    @FXML private DatePicker deadlineDatePicker;
    private LocalDate selectedDate;

    @FXML private TextField tagsTextField;

    @FXML private Button createTaskButton;
    @FXML private Button cancelButton;

    public CreateTaskModalWindowController(TaskService taskService) {
        this.taskService = taskService;
    }

    @FXML
    public void initialize() {

        //TODO Сделать загрузку данных с бд
        //TODO Добавлять объекты Task, а не строки (нужен javafx.util.StringConverter<Task>)
        projectChoiceBox.getItems().addAll("Редизайн портала", "Миграция CRM",
                "Мобильное приложение", "Отчетность Q3");

        executorChoiceBox.getItems().addAll("Алексей Козлов", "Мария Волкова", "Елена Никитина",
                "Павел Сорокин", "Дмитрий Лебедев", "Ирина Фёдорова");

        projectChoiceBox.setOnAction(event -> {
            selectedProject = projectChoiceBox.getValue();
        });

        executorChoiceBox.setOnAction(event -> {
            selectedExecutor = executorChoiceBox.getValue();
        });

        highPriorityButton.setOnAction(event -> {
            this.currentPriority = TaskPriority.HIGH;
        });

        middlePriorityButton.setOnAction(event -> {
            this.currentPriority = TaskPriority.MIDDLE;
        });

        lowPriorityButton.setOnAction(event -> {
            this.currentPriority = TaskPriority.LOW;
        });

        deadlineDatePicker.setOnAction(event -> {
            selectedDate = deadlineDatePicker.getValue();
        });

        createTaskButton.setOnAction(event -> {
            if (taskNameTextField.getText().isEmpty() ||
                    selectedProject != null ||
                    selectedExecutor != null ||
                    selectedDate != null) {
                NavigationManager.showToast("Заполните пустые поля", "info");
            }

            //TODO может быть стоит использовать AsyncTaskService
            taskService.save(new Task(null,
                    taskNameTextField.getText(),
                    selectedProject,
                    selectedExecutor,
                    tagsTextField.getText(),
                    TaskStatus.NEW,
                    currentPriority,
                    deadlineDatePicker.getValue(),
                    null,
                    false));

            Stage stage = (Stage) createTaskButton.getScene().getWindow();
            if (stage != null) {
                NavigationManager.closeDialog(stage);
                NavigationManager.showToast("Задача сохранена", "success");
            }

            log.debug("createTaskButton.getScene() = {}", createTaskButton.getScene());
            log.debug("createTaskButton.getScene().getWindow() = {}", createTaskButton.getScene().getWindow());

            //TODO После создания и удаления задач окна долго не закрываются исправить
        });

        cancelButton.setOnAction(event -> {
            Stage stage = (Stage) cancelButton.getScene().getWindow();
            if (stage != null) {
                NavigationManager.closeDialog(stage);
            }
        });
    }
}
