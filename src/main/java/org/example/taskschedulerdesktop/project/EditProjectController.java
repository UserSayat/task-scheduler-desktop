package org.example.taskschedulerdesktop.project;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.example.taskschedulerdesktop.user.User;
import org.example.taskschedulerdesktop.shared.navigation.ContextAware;
import org.example.taskschedulerdesktop.shared.navigation.NavigationManager;
import org.example.taskschedulerdesktop.user.AsyncUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EditProjectController implements ContextAware {
    private final Logger log = LoggerFactory.getLogger(EditProjectController.class);

    private final AsyncProjectService projectService;
    private final AsyncUserService userService;

    @FXML private TextField projectNameTextField;

    @FXML private ComboBox<User> supervisorComboBox;

    @FXML private Button saveProjectButton;
    @FXML private Button cancelButton;

    private ProjectView context;

    public EditProjectController(AsyncProjectService projectService, AsyncUserService userService) {
        this.projectService = projectService;
        this.userService = userService;
    }

    @FXML
    public void initialize() {
        cancelButton.setOnAction(event -> {
            Stage stage = (Stage) cancelButton.getScene().getWindow();
            if (stage != null) {
                NavigationManager.closeDialog(stage);
            }
        });

        saveProjectButton.setOnAction(event -> {
            User owner = supervisorComboBox.getValue();
            if (context == null || projectNameTextField.getText().isBlank() || owner == null) {
                NavigationManager.showToast("Заполните пустые поля", "info");
                return;
            }

            projectService.updateProject(context.id(), projectNameTextField.getText(), owner.getId(),
                    updated -> {
                        Stage stage = (Stage) saveProjectButton.getScene().getWindow();
                        if (stage != null) {
                            NavigationManager.closeDialog(stage);
                            NavigationManager.showToast("Проект обновлен", "success");
                        }
                    },
                    error -> NavigationManager.showToast("Ошибка обновления проекта", "error"));
        });
    }

    @Override
    public void setContext(Object context) {
        if (!(context instanceof ProjectView project)) {
            log.error("Context isn't an instance of ProjectView: {}", context);
            return;
        }

        this.context = project;
        projectNameTextField.setText(project.name());

        userService.findActiveUsers(
                users -> {
                    supervisorComboBox.getItems().setAll(users);
                    users.stream()
                            .filter(user -> user.getId().equals(project.ownerId()))
                            .findFirst()
                            .ifPresent(supervisorComboBox::setValue);
                },
                error -> NavigationManager.showToast("Ошибка загрузки сотрудников", "error")
        );
    }
}
