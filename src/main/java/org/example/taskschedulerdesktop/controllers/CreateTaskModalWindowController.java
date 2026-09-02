package org.example.taskschedulerdesktop.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.example.taskschedulerdesktop.models.Task;
import org.example.taskschedulerdesktop.service.TaskService;

import java.time.LocalDate;

public class CreateTaskModalWindowController {

    private final TaskService taskService;

    @FXML
    private TextField taskNameTextField;

    @FXML
    private ChoiceBox<String> projectChoiceBox;
    private String selectedProject;

    @FXML
    private ChoiceBox<String>executorChoiceBox;
    private String selectedExecutor;

    @FXML
    private Label highPriorityLabel;
    @FXML
    private Label middlePriorityLabel;
    @FXML
    private Label lowPriorityLabel;

    @FXML
    private DatePicker deadlineDatePicker;
    private LocalDate selectedDate;

    @FXML
    private TextField tagsTextField;

    @FXML
    private Button createTaskButton;
    @FXML
    private Button cancelButton;
    @FXML
    private Button exitButton;

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

        deadlineDatePicker.setOnAction(event -> {
            selectedDate = deadlineDatePicker.getValue();
        });

        createTaskButton.setOnAction(event -> {
            if (selectedProject != null && selectedExecutor != null && selectedDate != null) {
                taskService.save(new Task(null,
                        taskNameTextField.getText(),
                        selectedProject,
                        selectedExecutor,
                        tagsTextField.getText(),
                        "for execution",
                        highPriorityLabel.getText(),
                        deadlineDatePicker.getAccessibleText(),
                        null,
                        false));
            }
        });
    }
}
