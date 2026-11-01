package org.example.taskschedulerdesktop.task;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import org.example.taskschedulerdesktop.shared.event.EventBus;
import org.example.taskschedulerdesktop.shared.navigation.NavigationManager;
import org.example.taskschedulerdesktop.shared.navigation.Routes;
import org.example.taskschedulerdesktop.shared.ui.Clicks;
import org.example.taskschedulerdesktop.shared.ui.Shutdownable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Таблица задач: на экране «Задачи» и на «Обзоре». Сортировка — щелчком по заголовку колонки
 * (по возрастанию, по убыванию, исходный порядок), фильтр задаёт экран через {@link #setFilter}.
 */
public class TaskTableController implements Shutdownable {

    private static final Logger log = LoggerFactory.getLogger(TaskTableController.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final AsyncTaskService asyncTaskService;

    @FXML private TableView<TaskView> taskTable;

    @FXML private TableColumn<TaskView, String> taskNameColumn;
    @FXML private TableColumn<TaskView, String> projectNameColumn;
    @FXML private TableColumn<TaskView, String> executorColumn;
    @FXML private TableColumn<TaskView, TaskPriority> priorityColumn;
    @FXML private TableColumn<TaskView, TaskStatus> statusColumn;
    @FXML private TableColumn<TaskView, LocalDate> deadlineColumn;

    private final ObservableList<TaskView> allTasks = FXCollections.observableArrayList();
    private final ObservableList<TaskView> readOnlyTasks = FXCollections.unmodifiableObservableList(allTasks);
    private final FilteredList<TaskView> shownTasks = new FilteredList<>(allTasks, task -> true);
    private final ReadOnlyIntegerWrapper total = new ReadOnlyIntegerWrapper();
    private final ReadOnlyIntegerWrapper shown = new ReadOnlyIntegerWrapper();
    private final Label emptyPlaceholder = new Label("Нет задач");
    private final Label noMatchPlaceholder = new Label("Нет задач по выбранным условиям");

    private final Consumer<TaskChangedEvent> taskChangedEventListener = event -> loadTasks();

    /** Откуда брать задачи: по умолчанию все видимые; «Обзор» подставляет «Мои задачи». */
    private BiConsumer<Consumer<List<TaskView>>, Consumer<Throwable>> source;
    private int loadRequest;

    public TaskTableController(AsyncTaskService asyncTaskService) {
        this.asyncTaskService = asyncTaskService;
        this.source = asyncTaskService::findAllTasksView;
    }

    /** Показывать задачи из другого источника (сразу перезагружает таблицу). */
    public void setSource(BiConsumer<Consumer<List<TaskView>>, Consumer<Throwable>> source) {
        this.source = source;
        loadTasks();
    }

    /** Подсказка вместо пустой таблицы, когда задач нет совсем. */
    public void setEmptyText(String text) {
        emptyPlaceholder.setText(text);
    }

    /** Упорядочить по сроку: ближайший первым (как щелчок по заголовку «Дедлайн»). */
    public void sortByDeadline() {
        deadlineColumn.setSortType(TableColumn.SortType.ASCENDING);
        taskTable.getSortOrder().setAll(List.of(deadlineColumn));
    }

    @FXML
    public void initialize() {
        taskNameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().title()));
        taskNameColumn.setComparator(TaskSorting.TEXT);
        projectNameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().projectName()));
        projectNameColumn.setComparator(TaskSorting.TEXT);
        executorColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().responsibleName()));
        executorColumn.setComparator(TaskSorting.TEXT);

        priorityColumn.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().priority()));
        priorityColumn.setComparator(TaskSorting.PRIORITY);
        priorityColumn.setCellFactory(column -> badgeCell(priority -> new Badge(
                priority.getDisplayName(), "priority-" + priority.styleName())));

        statusColumn.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().status()));
        statusColumn.setComparator(TaskSorting.STATUS);
        statusColumn.setCellFactory(column -> badgeCell(status -> new Badge(
                status.getDisplayName(), styleClassForStatus(status))));

        deadlineColumn.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().deadline()));
        deadlineColumn.setComparator(TaskSorting.DEADLINE);
        deadlineColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate deadline, boolean empty) {
                super.updateItem(deadline, empty);
                TaskView task = getTableRow() != null ? getTableRow().getItem() : null;
                if (empty || deadline == null || task == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label label = new Label(deadline.format(DATE));
                label.getStyleClass().add(task.isOverdue() ? "deadline-overdue" : "deadline-normal");
                setGraphic(label);
            }
        });

        // Сортировка по заголовкам: таблица задаёт порядок, отфильтрованный список его соблюдает
        SortedList<TaskView> sorted = new SortedList<>(shownTasks);
        sorted.comparatorProperty().bind(taskTable.comparatorProperty());
        taskTable.setItems(sorted);

        total.bind(Bindings.size(allTasks));
        shown.bind(Bindings.size(shownTasks));
        bindPlaceholder();

        taskTable.setRowFactory(tv -> {
            TableRow<TaskView> row = new TableRow<>();
            // Задача открывается одним щелчком
            Clicks.onClick(row, () -> {
                if (!row.isEmpty()) {
                    NavigationManager.openRightSidebar(Routes.TASK_DETAILS, row.getItem());
                }
            });
            return row;
        });

        EventBus.getInstance().subscribe(TaskChangedEvent.class, taskChangedEventListener);

        loadTasks();
    }

    /** Показывать только подходящие задачи; фильтр сохраняется при обновлении данных. */
    public void setFilter(Predicate<TaskView> filter) {
        shownTasks.setPredicate(filter);
    }

    /** Все загруженные задачи, без фильтра. */
    public ObservableList<TaskView> tasks() {
        return readOnlyTasks;
    }

    /** Всего задач. */
    public ReadOnlyIntegerProperty totalProperty() {
        return total.getReadOnlyProperty();
    }

    /** Задач после фильтра. */
    public ReadOnlyIntegerProperty shownProperty() {
        return shown.getReadOnlyProperty();
    }

    public void loadTasks() {
        // Ответы приходят асинхронно; более старый запрос (например, до смены источника) не должен затереть новый
        int request = ++loadRequest;
        source.accept(
                taskViews -> {
                    if (request != loadRequest) {
                        return;
                    }
                    log.debug("onSuccess: received {} tasks", taskViews.size());
                    bindPlaceholder();
                    allTasks.setAll(taskViews);
                },
                error -> {
                    if (request != loadRequest) {
                        return;
                    }
                    allTasks.clear();
                    taskTable.placeholderProperty().unbind();
                    taskTable.setPlaceholder(new Label("Ошибка загрузки: " + error.getMessage()));
                    log.error("Error loading tasks", error);
                }
        );
    }

    /** «Нет задач» или «Нет задач по выбранным условиям», если всё скрыл фильтр. */
    private void bindPlaceholder() {
        taskTable.placeholderProperty().bind(Bindings.when(total.isEqualTo(0))
                .then((Node) emptyPlaceholder).otherwise(noMatchPlaceholder));
    }

    private record Badge(String text, String styleClass) {}

    /** Ячейка-плашка (приоритет, статус); сортируется по значению, а не по тексту. */
    private static <T> TableCell<TaskView, T> badgeCell(Function<T, Badge> badge) {
        return new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Badge b = badge.apply(item);
                Label label = new Label(b.text());
                label.getStyleClass().add(b.styleClass());
                setGraphic(label);
            }
        };
    }

    private static String styleClassForStatus(TaskStatus status) {
        return switch (status) {
            case NEW -> "status-new";
            case IN_PROGRESS -> "status-in-progress";
            case ON_REVIEW -> "status-under-review";
            case DONE -> "status-completed";
        };
    }

    @Override
    public void shutdown() {
        EventBus.getInstance().unsubscribe(TaskChangedEvent.class, taskChangedEventListener);
        log.debug("Unsubscribe from TaskChangedEvent");
    }
}
