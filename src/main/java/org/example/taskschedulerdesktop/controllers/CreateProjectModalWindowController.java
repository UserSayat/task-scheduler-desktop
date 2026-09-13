package org.example.taskschedulerdesktop.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.example.taskschedulerdesktop.models.Project;
import org.example.taskschedulerdesktop.navigation.NavigationManager;
import org.example.taskschedulerdesktop.service.project.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateProjectModalWindowController {

    private final Logger log = LoggerFactory.getLogger(CreateProjectModalWindowController.class);

    private final ProjectService projectService;

    @FXML private TextField projectNameTextField;

    @FXML private ChoiceBox<String> supervisorChoiceBox;
    private String selectedSupervisor;

    @FXML private Button createProjectButton;
    @FXML private Button cancelButton;

    public CreateProjectModalWindowController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @FXML
    public void initialize() {

        supervisorChoiceBox.getItems().addAll("Алексей Козлов", "Мария Волкова", "Елена Никитина",
                "Павел Сорокин", "Дмитрий Лебедев", "Ирина Фёдорова");

        supervisorChoiceBox.setOnAction(event -> {
            this.selectedSupervisor = supervisorChoiceBox.getValue();
        });

        createProjectButton.setOnAction(event -> {
            if (projectNameTextField.getText().isEmpty() || selectedSupervisor == null) {
                NavigationManager.showToast("Заполните пустые поля", "info");
            }

            projectService.save(new Project(null,
                    projectNameTextField.getText(),
                    selectedSupervisor,
                    false));

            Stage stage = (Stage) createProjectButton.getScene().getWindow();
            if (stage != null) {
                NavigationManager.closeDialog(stage);
                NavigationManager.showToast("Проект создан", "success");
            }

            log.debug("createProjectButton.getScene() = {}", createProjectButton.getScene());
            log.debug("createProjectButton.getScene().getWindow() = {}", createProjectButton.getScene().getWindow());
        });

        cancelButton.setOnAction(event -> {
            Stage stage = (Stage) cancelButton.getScene().getWindow();
            if (stage != null) {
                NavigationManager.closeDialog(stage);
            }
        });
    }
}
