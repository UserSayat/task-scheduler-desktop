package org.example.taskschedulerdesktop.controllers.projects;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.example.taskschedulerdesktop.models.Project;
import org.example.taskschedulerdesktop.navigation.ContextAware;
import org.example.taskschedulerdesktop.navigation.NavigationManager;
import org.example.taskschedulerdesktop.service.project.AsyncProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EditProjectController implements ContextAware {
    private final Logger log = LoggerFactory.getLogger(EditProjectController.class);

    private final AsyncProjectService projectService;

    @FXML private TextField projectNameTextField;

    @FXML private ComboBox<String> supervisorComboBox;
    private String selectedSupervisor;

    @FXML private Button saveProjectButton;
    @FXML private Button cancelButton;

    private Project context;

    public EditProjectController(AsyncProjectService projectService) {
        this.projectService = projectService;
    }

    @FXML
    public void initialize() {

        supervisorComboBox.getItems().addAll("Алексей Козлов", "Мария Волкова", "Елена Никитина",
                "Павел Сорокин", "Дмитрий Лебедев", "Ирина Фёдорова");

        supervisorComboBox.setOnAction(event -> {
            this.selectedSupervisor = supervisorComboBox.getValue();
        });

        cancelButton.setOnAction(event -> {
            Stage stage = (Stage) cancelButton.getScene().getWindow();
            if (stage != null) {
                NavigationManager.closeDialog(stage);
            }
        });
    }

    @Override
    public void setContext(Object context) {

        if (context instanceof Project contextProject) {
            this.context = contextProject;

            projectNameTextField.setText(contextProject.getName());
            supervisorComboBox.setValue(contextProject.getSupervisor());
            selectedSupervisor = contextProject.getSupervisor();

            saveProjectButton.setOnAction(event -> {
                if (projectNameTextField.getText().isEmpty() || selectedSupervisor == null) {
                    NavigationManager.showToast("Заполните пустые поля", "info");
                    return;
                }

                Project updated = new Project(
                        contextProject.getId(),
                        projectNameTextField.getText(),
                        selectedSupervisor,
                        contextProject.getNumberOfTasks(),
                        contextProject.getCompletedTasks(),
                        contextProject.getRemainingTasks(),
                        contextProject.getPercentOfCompletion(),
                        contextProject.isSynced()
                );

                projectService.updateProject(updated,
                        () -> {
                            Stage stage = (Stage) saveProjectButton.getScene().getWindow();
                            if (stage != null) {
                                NavigationManager.closeDialog(stage);
                                NavigationManager.showToast("Проект обновлен", "success");
                            }
                        },
                        error -> {
                            NavigationManager.showToast("Ошибка обновления проекта", "error");
                        });
            });
        } else {
            log.error("Context isn't an instance of Project: {}", context);
        }
    }
}
