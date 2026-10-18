package org.example.taskschedulerdesktop.project;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.example.taskschedulerdesktop.user.User;
import org.example.taskschedulerdesktop.shared.navigation.NavigationManager;
import org.example.taskschedulerdesktop.user.AsyncUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateProjectController {

    private final Logger log = LoggerFactory.getLogger(CreateProjectController.class);

    private final AsyncProjectService projectService;
    private final AsyncUserService userService;

    @FXML private TextField projectNameTextField;

    @FXML private ComboBox<User> supervisorComboBox;

    @FXML private Button createProjectButton;
    @FXML private Button cancelButton;

    public CreateProjectController(AsyncProjectService projectService, AsyncUserService userService) {
        this.projectService = projectService;
        this.userService = userService;
    }

    @FXML
    public void initialize() {
        userService.findActiveUsers(
                users -> supervisorComboBox.getItems().setAll(users),
                error -> NavigationManager.showToast("Ошибка загрузки сотрудников", "error")
        );

        createProjectButton.setOnAction(event -> {
            User owner = supervisorComboBox.getValue();
            if (projectNameTextField.getText().isBlank() || owner == null) {
                NavigationManager.showToast("Заполните пустые поля", "info");
                return;
            }

            projectService.saveProject(projectNameTextField.getText(), owner.getId(),
                    created -> {
                        Stage stage = (Stage) createProjectButton.getScene().getWindow();
                        if (stage != null) {
                            NavigationManager.closeDialog(stage);
                            NavigationManager.showToast("Проект создан", "success");
                        }
                    },
                    error -> NavigationManager.showToast("Ошибка создания проекта", "error"));
        });

        cancelButton.setOnAction(event -> {
            Stage stage = (Stage) cancelButton.getScene().getWindow();
            if (stage != null) {
                NavigationManager.closeDialog(stage);
            }
        });
    }
}
