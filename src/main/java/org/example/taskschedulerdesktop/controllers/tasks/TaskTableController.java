package org.example.taskschedulerdesktop.controllers.tasks;

import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.example.taskschedulerdesktop.controllers.Shutdownable;
import org.example.taskschedulerdesktop.dto.tasks.TaskView;
import org.example.taskschedulerdesktop.listeners.EventBus;
import org.example.taskschedulerdesktop.listeners.TaskChangedEvent;
import org.example.taskschedulerdesktop.navigation.NavigationManager;
import org.example.taskschedulerdesktop.navigation.Routes;
import org.example.taskschedulerdesktop.service.task.AsyncTaskService;
import org.example.taskschedulerdesktop.utils.TaskPriority;
import org.example.taskschedulerdesktop.utils.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

// TODO: заменить getIndex() на передачу TaskView через cellValueFactory,
//       чтобы избежать IndexOutOfBoundsException при обновлении/фильтрации таблицы
public class TaskTableController implements Shutdownable {

    private final AsyncTaskService asyncTaskService;
    private static final Logger log = LoggerFactory.getLogger(TaskTableController.class);

    @FXML private TableView<TaskView> taskTable;

    //@FXML private TableColumn<TaskView, Long> idColumn;
    @FXML private TableColumn<TaskView, String> taskNameColumn;
    @FXML private TableColumn<TaskView, String> projectNameColumn;
    @FXML private TableColumn<TaskView, String> executorColumn;
    @FXML private TableColumn<TaskView, String> priorityColumn;
    @FXML private TableColumn<TaskView, String> statusColumn;
    @FXML private TableColumn<TaskView, String> deadlineColumn;

    private final Consumer<TaskChangedEvent> taskChangedEventListener = event -> {
        loadTasks();
    };

    public TaskTableController(AsyncTaskService asyncTaskService) {
        this.asyncTaskService = asyncTaskService;
    }

    @FXML
    public void initialize() {

//        idColumn.setCellValueFactory(
//                cellData -> new SimpleLongProperty(cellData.getValue().getId()).asObject()
//        );
        taskNameColumn.setCellValueFactory(
                cellData -> new SimpleStringProperty(cellData.getValue().getTaskName())
        );
        projectNameColumn.setCellValueFactory(
                cellData -> new SimpleStringProperty(cellData.getValue().getProjectName())
        );
        executorColumn.setCellValueFactory(
                cellData -> new SimpleStringProperty(
                        //TODO Использовать инициалы а не полное имя, и при нажатии переходить на страницу сотрудника
                        cellData.getValue().getExecutor()
                )
        );

        setupPriorityColumn();
        setupStatusColumn();
        setupDeadlineColumn();

        taskTable.setRowFactory(tv -> {
            TableRow<TaskView> row = new TableRow<>();

            row.setOnMouseClicked(event -> {
                if (row.isEmpty()) return;
                if (event.getClickCount() != 2) return;

                TaskView item = row.getItem();
                NavigationManager.openRightSidebar(
                        Routes.TASK_DETAILS,
                        item
                );
            });

            return row;
        });

        taskTable.setPlaceholder(new Label("Нет задач"));

        EventBus.getInstance().subscribe(TaskChangedEvent.class, taskChangedEventListener);

        loadTasks();
    }

    public void loadTasks() {
        //taskTable.setPlaceholder(new Label("Загрузка..."));
        asyncTaskService.findAllTasksView(
                taskViews -> {
                    log.debug("onSuccess: received {} tasks", taskViews.size());
                    taskTable.getItems().setAll(taskViews);
                    log.debug("Table has: {} tasks", taskTable.getItems().size());
                },
                error -> {
                    taskTable.getItems().clear();
                    taskTable.setPlaceholder(new Label("Ошибка загрузки: " + error.getMessage()));
                    log.error("Error loading tasks", error);
                }
        );
    }

    private void setupPriorityColumn() {
        priorityColumn.setCellValueFactory(
                cellData -> new SimpleStringProperty(
                        cellData.getValue().getPriority().getDisplayName()
                )
        );

        priorityColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    TaskView task = getTableView().getItems().get(getIndex());
                    Label label = new Label(item);
                    label.getStyleClass().add(getStyleClassForPriority(task.getPriority()));

                    setGraphic(label);
                }
            }
        });
    }

    private void setupStatusColumn() {
        statusColumn.setCellValueFactory(
                cellData -> new SimpleStringProperty(
                        cellData.getValue().getStatus().getDisplayName()
                )
        );

        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    TaskView task = getTableView().getItems().get(getIndex());
                    Label label = new Label(item);
                    label.getStyleClass().add(getStyleClassForStatus(task.getStatus()));
                    label.setStyle("-fx");

                    setGraphic(label);
                }
            }
        });
    }

    private void setupDeadlineColumn() {
        deadlineColumn.setCellValueFactory(
                cellData -> new SimpleStringProperty(
                        cellData.getValue().getDeadline() != null
                                ? cellData.getValue().getDeadline().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                                : ""
                )
        );

        deadlineColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null || item.isEmpty()) {
                    setText(null);
                    setGraphic(null);
                } else {
                    TaskView task = getTableView().getItems().get(getIndex());
                    Label label = new Label(item);

                    if (task.getDeadline() != null
                            && task.getDeadline().isBefore(LocalDate.now())
                            && task.getStatus() != TaskStatus.COMPLETED) {
                        label.getStyleClass().add("deadline-overdue");
                    } else {
                        label.getStyleClass().add("deadline-normal");
                    }

                    setGraphic(label);
                }
            }
        });
    }

    private String getStyleClassForPriority(TaskPriority priority) {
        return switch (priority) {
            case LOW -> "priority-low";
            case MIDDLE -> "priority-middle";
            case HIGH -> "priority-high";
        };
    }

    private String getStyleClassForStatus(TaskStatus status) {
        return switch (status) {
            case NEW -> "status-new";
            case IN_PROGRESS -> "status-in-progress";
            case UNDER_REVIEW -> "status-under-review";
            case COMPLETED -> "status-completed";
        };
    }

    @Override
    public void shutdown() {
        EventBus.getInstance().unsubscribe(TaskChangedEvent.class, taskChangedEventListener);
        log.debug("Unsubscribe from TaskChangedEvent");
    }
}
