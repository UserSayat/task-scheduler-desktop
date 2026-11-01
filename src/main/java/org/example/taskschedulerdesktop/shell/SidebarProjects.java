package org.example.taskschedulerdesktop.shell;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.project.AsyncProjectService;
import org.example.taskschedulerdesktop.project.ProjectChangedEvent;
import org.example.taskschedulerdesktop.project.ProjectLock;
import org.example.taskschedulerdesktop.project.ProjectView;
import org.example.taskschedulerdesktop.shared.event.EventBus;
import org.example.taskschedulerdesktop.task.TaskChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Collator;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Список проектов в боковом меню: название и сколько задач осталось.
 * Архивные проекты не показываются. Список обновляется при изменении проектов и задач
 * (в том числе пришедших с сервера), открытый проект подсвечивается.
 */
public class SidebarProjects {

    private static final Logger log = LoggerFactory.getLogger(SidebarProjects.class);

    private final VBox list;
    private final AsyncProjectService projects;
    private final Consumer<ProjectView> onOpen;
    private final Map<UUID, Button> rows = new LinkedHashMap<>();
    private UUID highlighted;
    private boolean reloadQueued;

    private final Consumer<ProjectChangedEvent> projectListener = event -> reloadLater();
    private final Consumer<TaskChangedEvent> taskListener = event -> reloadLater();

    /**
     * @param list   куда класть строки проектов
     * @param onOpen что делать по нажатию на проект
     */
    public SidebarProjects(VBox list, AsyncProjectService projects, Consumer<ProjectView> onOpen) {
        this.list = list;
        this.projects = projects;
        this.onOpen = onOpen;
    }

    public void start() {
        EventBus.getInstance().subscribe(ProjectChangedEvent.class, projectListener);
        EventBus.getInstance().subscribe(TaskChangedEvent.class, taskListener);
        reload();
    }

    public void stop() {
        EventBus.getInstance().unsubscribe(ProjectChangedEvent.class, projectListener);
        EventBus.getInstance().unsubscribe(TaskChangedEvent.class, taskListener);
    }

    /** Подсветить проект (null — ни один). Возвращает true, если такой проект есть в списке. */
    public boolean highlight(UUID projectId) {
        highlighted = projectId;
        rows.forEach((id, row) -> {
            row.getStyleClass().remove("nav-btn-active");
            if (id.equals(projectId)) {
                row.getStyleClass().add("nav-btn-active");
            }
        });
        return projectId != null && rows.containsKey(projectId);
    }

    /** Событий может прийти сразу много (синхронизация) — перечитываем список один раз. */
    private void reloadLater() {
        Platform.runLater(() -> {
            if (reloadQueued) {
                return;
            }
            reloadQueued = true;
            Platform.runLater(() -> {
                reloadQueued = false;
                reload();
            });
        });
    }

    private void reload() {
        projects.findAllProjects(this::show, error -> log.error("Sidebar: projects not loaded", error));
    }

    private void show(List<ProjectView> all) {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("ru"));
        List<ProjectView> active = all.stream()
                .filter(project -> !project.archived())
                .sorted(Comparator.comparing(ProjectView::name, collator))
                .toList();

        rows.clear();
        list.getChildren().clear();
        if (active.isEmpty()) {
            Label empty = new Label("Проектов пока нет");
            empty.getStyleClass().add("sidebar-projects-empty");
            list.getChildren().add(empty);
            return;
        }
        for (ProjectView project : active) {
            Button row = row(project);
            rows.put(project.id(), row);
            list.getChildren().add(row);
        }
        highlight(highlighted);
    }

    private Button row(ProjectView project) {
        Label name = new Label(project.name());
        name.getStyleClass().add("sidebar-project-name");
        ProjectLock.apply(name, project.isPrivate());
        name.setMinWidth(0);
        HBox.setHgrow(name, Priority.ALWAYS);
        name.setMaxWidth(Double.MAX_VALUE);

        HBox content = new HBox(10, name);
        content.setAlignment(Pos.CENTER_LEFT);
        if (project.remainingTasks() > 0) {
            Label count = new Label(String.valueOf(project.remainingTasks()));
            count.getStyleClass().add("sidebar-project-count");
            content.getChildren().add(count);
        }

        Button row = new Button();
        row.setGraphic(content);
        row.setMaxWidth(Double.MAX_VALUE);
        row.getStyleClass().addAll("nav-btn", "sidebar-project");
        row.setMnemonicParsing(false);
        content.prefWidthProperty().bind(row.widthProperty().subtract(40));
        row.setTooltip(new Tooltip(project.name() + (project.remainingTasks() > 0
                ? " — задач осталось: " + project.remainingTasks() : "")));
        row.setOnAction(event -> onOpen.accept(project));
        return row;
    }
}
