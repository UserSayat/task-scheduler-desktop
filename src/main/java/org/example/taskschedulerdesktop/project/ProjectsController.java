package org.example.taskschedulerdesktop.project;

import javafx.concurrent.Service;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.TilePane;
import org.example.taskschedulerdesktop.shared.navigation.CachedPage;
import org.example.taskschedulerdesktop.shared.event.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

public class ProjectsController implements CachedPage {

    private static final Logger log = LoggerFactory.getLogger(ProjectsController.class);

    private final AsyncProjectService projectService;
    private final Service<List<Node>> projectsLoader;
    /** Раздел скрыт (см. {@link CachedPage}): карточки не слушают изменения задач. */
    private boolean paused;

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
            cards().forEach(ProjectCardController::shutdown); // старые карточки перестают слушать задачи
            projectsTilePane.getChildren().clear();
            List<Node> projects = projectsLoader.getValue();

            if (projects == null || projects.isEmpty()) {
                projectsTilePane.getChildren().add(new Label("У вас еще нет созданных проектов"));
            } else {
                projectsTilePane.getChildren().addAll(projects);
                if (!paused) {
                    cards().forEach(ProjectCardController::listen);
                }
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

    /** Контроллеры показанных карточек. */
    private List<ProjectCardController> cards() {
        return projectsTilePane.getChildren().stream()
                .map(node -> node.getProperties().get(ProjectCardController.class))
                .filter(ProjectCardController.class::isInstance)
                .map(ProjectCardController.class::cast)
                .toList();
    }

    @Override
    public void pause() {
        paused = true;
        EventBus.getInstance().unsubscribe(ProjectChangedEvent.class, projectChangedListener);
        cards().forEach(ProjectCardController::shutdown);
    }

    @Override
    public void resume(boolean dataChanged) {
        paused = false;
        EventBus.getInstance().unsubscribe(ProjectChangedEvent.class, projectChangedListener); // не подписаться дважды
        EventBus.getInstance().subscribe(ProjectChangedEvent.class, projectChangedListener);
        if (dataChanged) {
            refreshProjects(); // новые карточки подпишутся сами
        } else {
            cards().forEach(ProjectCardController::listen);
        }
    }

    @Override
    public void shutdown() {
        pause();
        if (projectsLoader != null) {
            projectsLoadingIndicator.visibleProperty().unbind();
            projectsLoader.setOnSucceeded(null);
            projectsLoader.setOnFailed(null);
        }
    }
}
