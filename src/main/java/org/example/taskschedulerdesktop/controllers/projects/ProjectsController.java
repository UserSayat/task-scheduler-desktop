package org.example.taskschedulerdesktop.controllers.projects;

import javafx.concurrent.Service;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.FlowPane;
import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.controllers.Shutdownable;
import org.example.taskschedulerdesktop.navigation.NavigationManager;
import org.example.taskschedulerdesktop.navigation.Routes;
import org.example.taskschedulerdesktop.service.project.AsyncProjectService;

import java.util.List;

public class ProjectsController implements Shutdownable {

    private final AsyncProjectService projectService;
    private final Service<List<Node>> projectsLoader;

    @FXML private Button createProjectButton;

    @FXML private FlowPane projectsFlowPane;
    @FXML private ProgressIndicator projectsLoadingIndicator;

    public ProjectsController(AsyncProjectService projectService) {
        this.projectService = projectService;
        projectsLoader = projectService.createProjectsLoader();

    }

    @FXML
    public void initialize() {
        projectsLoadingIndicator.visibleProperty().bind(projectsLoader.runningProperty());

        projectsLoader.setOnSucceeded(event -> {
            projectsFlowPane.getChildren().clear();
            List<Node> projects = projectsLoader.getValue();

            if (projects.isEmpty()) {
                projectsFlowPane.getChildren().add(new Label("У вас еще нет созданных проектов"));
            } else {
                projectsFlowPane.getChildren().addAll(projects);
            }
        });

        projectsLoader.setOnFailed(event -> {
            projectsFlowPane.getChildren().clear();
            projectsFlowPane.getChildren().add(new Label("Ошибка загрузки данных из базы"));

            Throwable error = projectsLoader.getException();
            if (error != null) error.printStackTrace();
        });

        //NavigationManager.setCurrentTitle("Проекты");

        createProjectButton.setOnAction(event -> {
            NavigationManager.openDialog(
                    Routes.CREATE_PROJECT,
                    "Новый проект",
                    AppConfig.getInstance().getPrimaryStage());
            projectService.invalidateCache();
        });

        refreshProjects();
    }

    public void refreshProjects() {
        projectsLoader.restart();
    }

    @Override
    public void shutdown() {
        if (projectsLoader != null) {
            projectsLoadingIndicator.visibleProperty().unbind();
            projectsLoader.setOnSucceeded(null);
            projectsLoader.setOnFailed(null);
        }
    }
}
