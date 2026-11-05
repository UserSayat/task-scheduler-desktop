package org.example.taskschedulerdesktop.project;

import javafx.concurrent.Service;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.TilePane;
import org.example.taskschedulerdesktop.shared.ui.Shutdownable;
import org.example.taskschedulerdesktop.shared.event.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

public class ProjectsController implements Shutdownable {

    private static final Logger log = LoggerFactory.getLogger(ProjectsController.class);

    private final AsyncProjectService projectService;
    private final Service<List<Node>> projectsLoader;

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

        // Кнопка «Новый проект» — в заголовке страницы (MainController)

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
