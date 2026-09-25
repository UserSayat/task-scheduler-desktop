package org.example.taskschedulerdesktop.controllers.projects;

import javafx.concurrent.Service;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.TilePane;
import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.controllers.Shutdownable;
import org.example.taskschedulerdesktop.listeners.EventBus;
import org.example.taskschedulerdesktop.listeners.ProjectChangedEvent;
import org.example.taskschedulerdesktop.navigation.NavigationManager;
import org.example.taskschedulerdesktop.navigation.Routes;
import org.example.taskschedulerdesktop.service.project.AsyncProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

public class ProjectsController implements Shutdownable {

    private static final Logger log = LoggerFactory.getLogger(ProjectsController.class);

    private final AsyncProjectService projectService;
    private final Service<List<Node>> projectsLoader;

    @FXML private Button createProjectButton;

    @FXML private TilePane projectsTilePane;
    @FXML private ProgressIndicator projectsLoadingIndicator;

    private final Consumer<ProjectChangedEvent> projectChangedListener = event -> {
        log.debug("ProjectChangedEvent received");
        refreshProjects();
    };

    public ProjectsController(AsyncProjectService projectService) {
        this.projectService = projectService;
        projectsLoader = projectService.createProjectsLoader();

    }

    @FXML
    public void initialize() {
        EventBus.getInstance().subscribe(ProjectChangedEvent.class, projectChangedListener);

        projectsLoadingIndicator.visibleProperty().bind(projectsLoader.runningProperty());

        projectsLoader.setOnSucceeded(event -> {
            projectsTilePane.getChildren().clear();
            List<Node> projects = projectsLoader.getValue();

            if (projects == null || projects.isEmpty()) {
                projectsTilePane.getChildren().add(new Label("У вас еще нет созданных проектов"));
            } else {
                projectsTilePane.getChildren().addAll(projects);
            }
        });

        projectsLoader.setOnFailed(event -> {
            projectsTilePane.getChildren().clear();
            projectsTilePane.getChildren().add(new Label("Ошибка загрузки данных из базы"));

            Throwable error = projectsLoader.getException();
            if (error != null) error.printStackTrace();
        });

        //NavigationManager.setCurrentTitle("Проекты");

        createProjectButton.setOnAction(event -> {
            NavigationManager.openDialog(
                    Routes.CREATE_PROJECT,
                    "Новый проект",
                    AppConfig.getInstance().getPrimaryStage());
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

        EventBus.getInstance().unsubscribe(ProjectChangedEvent.class, projectChangedListener);
    }
}
