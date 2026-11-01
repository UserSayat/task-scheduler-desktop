package org.example.taskschedulerdesktop.task;

import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.shared.ui.Shutdownable;
import org.example.taskschedulerdesktop.task.TaskFilter.DeadlineRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Экран «Задачи»: поиск, фильтры и таблица. Статус и приоритет — кнопки, можно выбрать несколько
 * значений; проект, исполнитель и срок — выпадающие списки с галочками.
 */
public class TasksController implements Shutdownable {

    private static final Logger log = LoggerFactory.getLogger(TasksController.class);

    /** Приоритеты в фильтре — от важного к неважному, как при сортировке. */
    private static final List<TaskPriority> PRIORITIES =
            List.of(TaskPriority.CRITICAL, TaskPriority.HIGH, TaskPriority.MEDIUM, TaskPriority.LOW);

    @FXML private HBox topFilters;
    @FXML private TextField searchField;
    @FXML private Button resetFiltersButton;
    @FXML private Label taskCountLabel;
    @FXML private HBox statusFilters;
    @FXML private HBox priorityFilters;
    @FXML private VBox taskTableContainerVBox;

    private TaskTableController table;
    private TaskFilter filter = TaskFilter.ALL;

    private ChipGroup<TaskStatus> statusChips;
    private ChipGroup<TaskPriority> priorityChips;
    private FilterMenu<UUID> projectMenu;
    private FilterMenu<UUID> executorMenu;
    private FilterMenu<DeadlineRange> deadlineMenu;

    private final ListChangeListener<TaskView> tasksListener = change -> refreshOptions();

    @FXML
    public void initialize() {
        loadTaskTable();

        statusChips = new ChipGroup<>(statusFilters, List.of(TaskStatus.values()), TaskStatus::getDisplayName,
                values -> apply(filter.withStatuses(values)));
        priorityChips = new ChipGroup<>(priorityFilters, PRIORITIES, TaskPriority::getDisplayName,
                values -> apply(filter.withPriorities(values)));

        projectMenu = new FilterMenu<>("Проект", values -> apply(filter.withProjects(values)));
        executorMenu = new FilterMenu<>("Ответственный", values -> apply(filter.withExecutors(values)));
        deadlineMenu = new FilterMenu<>("Срок", values -> apply(filter.withDeadlines(values)));
        Map<DeadlineRange, String> ranges = new LinkedHashMap<>();
        for (DeadlineRange range : DeadlineRange.values()) {
            ranges.put(range, range.getDisplayName());
        }
        deadlineMenu.setOptions(ranges);
        // После поля поиска
        topFilters.getChildren().addAll(1, List.of(projectMenu.node(), executorMenu.node(), deadlineMenu.node()));

        searchField.textProperty().addListener((obs, old, query) -> apply(filter.withQuery(query)));
        resetFiltersButton.setOnAction(event -> reset());
        SVGPath cross = new SVGPath();
        cross.setContent("M1 1L8 8M8 1L1 8"); // крестик 9×9
        cross.getStyleClass().add("filter-reset-icon");
        resetFiltersButton.setGraphic(cross);

        if (table != null) {
            table.totalProperty().addListener((obs, old, value) -> showCount());
            table.shownProperty().addListener((obs, old, value) -> showCount());
            table.tasks().addListener(tasksListener);
            refreshOptions();
        }
        apply(TaskFilter.ALL);
    }

    /** Проекты и исполнители в выпадающих списках — из загруженных задач, по алфавиту. */
    private void refreshOptions() {
        List<TaskView> tasks = table.tasks();
        projectMenu.setOptions(sortedOptions(tasks, TaskView::projectId, TaskView::projectName));
        executorMenu.setOptions(executorOptions(tasks));
    }

    /** Исполнители по алфавиту, а первым — «Без исполнителя», если такие задачи есть. */
    static Map<UUID, String> executorOptions(Collection<TaskView> tasks) {
        Map<UUID, String> options = new LinkedHashMap<>();
        if (tasks.stream().anyMatch(task -> task.responsibleId() == null)) {
            options.put(TaskFilter.NO_EXECUTOR, "Без ответственного");
        }
        options.putAll(sortedOptions(tasks, TaskView::responsibleId, TaskView::responsibleName));
        return options;
    }

    static Map<UUID, String> sortedOptions(Collection<TaskView> tasks, Function<TaskView, UUID> id,
                                           Function<TaskView, String> name) {
        Map<UUID, String> unique = new LinkedHashMap<>();
        for (TaskView task : tasks) {
            if (id.apply(task) != null) {
                unique.putIfAbsent(id.apply(task), Objects.requireNonNullElse(name.apply(task), "Без имени"));
            }
        }
        List<Map.Entry<UUID, String>> entries = new ArrayList<>(unique.entrySet());
        entries.sort(Map.Entry.comparingByValue(TaskSorting.TEXT));
        Map<UUID, String> sorted = new LinkedHashMap<>();
        entries.forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return sorted;
    }

    private void reset() {
        searchField.clear();
        statusChips.clear();
        priorityChips.clear();
        projectMenu.clear();
        executorMenu.clear();
        deadlineMenu.clear();
    }

    private void apply(TaskFilter newFilter) {
        filter = newFilter;
        if (table != null) {
            table.setFilter(filter);
        }
        resetFiltersButton.setVisible(filter.isActive());
        resetFiltersButton.setManaged(filter.isActive());
        showCount();
    }

    private void showCount() {
        if (table == null) {
            return;
        }
        int total = table.totalProperty().get();
        int shown = table.shownProperty().get();
        String word = tasksWord(total);
        taskCountLabel.setText(shown == total ? total + " " + word : shown + " / " + total + " " + word);
    }

    /** 1 задача, 2 задачи, 5 задач. */
    static String tasksWord(int count) {
        int lastTwo = count % 100;
        if (lastTwo >= 11 && lastTwo <= 14) {
            return "задач";
        }
        return switch (count % 10) {
            case 1 -> "задача";
            case 2, 3, 4 -> "задачи";
            default -> "задач";
        };
    }

    /**
     * Кнопки «Все» и по одной на значение. Можно выбрать несколько значений; ничего не выбрано —
     * горит «Все». Нажатие на «Все» снимает остальные.
     */
    private static final class ChipGroup<T> {

        private final ToggleButton all = chip("Все");
        private final Map<T, ToggleButton> chips = new LinkedHashMap<>();
        private final Consumer<Set<T>> onChange;
        private boolean updating;

        ChipGroup(HBox box, List<T> values, Function<T, String> name, Consumer<Set<T>> onChange) {
            this.onChange = onChange;
            box.getChildren().add(all);
            for (T value : values) {
                ToggleButton button = chip(name.apply(value));
                chips.put(value, button);
                box.getChildren().add(button);
                button.selectedProperty().addListener((obs, was, now) -> changed());
            }
            all.setSelected(true);
            all.selectedProperty().addListener((obs, was, now) -> {
                if (updating) {
                    return;
                }
                if (now) {
                    clear();
                } else if (selected().isEmpty()) {
                    all.setSelected(true); // «Все» не выключается само по себе
                }
            });
        }

        void clear() {
            updating = true;
            chips.values().forEach(button -> button.setSelected(false));
            all.setSelected(true);
            updating = false;
            onChange.accept(Set.of());
        }

        private void changed() {
            if (updating) {
                return;
            }
            Set<T> values = selected();
            updating = true;
            all.setSelected(values.isEmpty());
            updating = false;
            onChange.accept(values);
        }

        private Set<T> selected() {
            Set<T> values = new LinkedHashSet<>();
            chips.forEach((value, button) -> {
                if (button.isSelected()) {
                    values.add(value);
                }
            });
            return values;
        }

        private static ToggleButton chip(String text) {
            ToggleButton button = new ToggleButton(text);
            button.getStyleClass().add("filter-chip");
            button.setFocusTraversable(false);
            return button;
        }
    }

    private void loadTaskTable() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/example/taskschedulerdesktop/view/task_table.fxml")
            );
            loader.setControllerFactory(AppConfig.getInstance().getControllerFactory());
            Node taskTable = loader.load();
            table = loader.getController();
            VBox.setVgrow(taskTable, Priority.ALWAYS);
            taskTableContainerVBox.getChildren().add(taskTable);
            log.debug("Task table loaded successfully");
        } catch (IOException e) {
            log.error("Error loading task table", e);
        }
    }

    @Override
    public void shutdown() {
        if (table != null) {
            table.tasks().removeListener(tasksListener);
            table.shutdown();
        }
    }
}
