package org.example.taskschedulerdesktop.controllers.projects;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.example.taskschedulerdesktop.listeners.EventBus;
import org.example.taskschedulerdesktop.listeners.ProjectChangedEvent;
import org.example.taskschedulerdesktop.models.Project;
import org.example.taskschedulerdesktop.navigation.NavigationManager;
import org.example.taskschedulerdesktop.service.project.AsyncProjectService;
import org.example.taskschedulerdesktop.service.project.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateProjectController {

    private final Logger log = LoggerFactory.getLogger(CreateProjectController.class);

    private final AsyncProjectService projectService;

    @FXML private TextField projectNameTextField;

    @FXML private ComboBox<String> supervisorComboBox;
    private String selectedSupervisor;

    @FXML private Button createProjectButton;
    @FXML private Button cancelButton;

    public CreateProjectController(AsyncProjectService projectService) {
        this.projectService = projectService;
    }

    @FXML
    public void initialize() {

        supervisorComboBox.getItems().addAll("Алексей Козлов", "Мария Волкова", "Елена Никитина",
                "Павел Сорокин", "Дмитрий Лебедев", "Ирина Фёдорова");

        supervisorComboBox.setOnAction(event -> {
            this.selectedSupervisor = supervisorComboBox.getValue();
        });

        createProjectButton.setOnAction(event -> {
            if (projectNameTextField.getText().isEmpty() || selectedSupervisor == null) {
                NavigationManager.showToast("Заполните пустые поля", "info");
                return;
            }

            projectService.saveProject(new Project(
                    null, projectNameTextField.getText(), selectedSupervisor,
                    0, 0, 0, 0, false),
                    () -> {
                        Stage stage = (Stage) createProjectButton.getScene().getWindow();
                        if (stage != null) {
                            NavigationManager.closeDialog(stage);
                            NavigationManager.showToast("Проект создан", "success");
                        }
                    },
                    error -> {
                        NavigationManager.showToast("Ошибка создания проекта", "error");
                    });
        });

        cancelButton.setOnAction(event -> {
            Stage stage = (Stage) cancelButton.getScene().getWindow();
            if (stage != null) {
                NavigationManager.closeDialog(stage);
            }
        });
    }
}
