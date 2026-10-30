package org.example.taskschedulerdesktop.admin;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.session.Session;
import org.example.taskschedulerdesktop.shared.event.EventBus;
import org.example.taskschedulerdesktop.shared.navigation.NavigationManager;
import org.example.taskschedulerdesktop.shared.navigation.Routes;
import org.example.taskschedulerdesktop.shared.ui.Shutdownable;
import org.example.taskschedulerdesktop.user.UsersChangedEvent;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Экран «Сотрудники» для администратора: список, поиск, создание, изменение, блокировка,
 * сброс пароля и удаление.
 */
public class EmployeesController implements Shutdownable {

    private final EmployeeAdminService service;
    private final Session session;

    @FXML private TextField searchField;
    @FXML private Button addButton;

    @FXML private TableView<Employee> employeesTable;
    @FXML private TableColumn<Employee, String> nameColumn;
    @FXML private TableColumn<Employee, String> emailColumn;
    @FXML private TableColumn<Employee, String> positionColumn;
    @FXML private TableColumn<Employee, String> rolesColumn;
    @FXML private TableColumn<Employee, String> statusColumn;

    @FXML private Button editButton;
    @FXML private Button blockButton;
    @FXML private Button resetPasswordButton;
    @FXML private Button deleteButton;

    private final ObservableList<Employee> allEmployees = FXCollections.observableArrayList();
    private final FilteredList<Employee> employees = new FilteredList<>(allEmployees);

    private final Consumer<UsersChangedEvent> usersListener = event -> loadEmployees();

    public EmployeesController(EmployeeAdminService service, Session session) {
        this.service = service;
        this.session = session;
    }

    @FXML
    public void initialize() {
        setUpColumn(nameColumn, Employee::name);
        setUpColumn(emailColumn, Employee::email);
        setUpColumn(positionColumn, employee -> employee.position() != null ? employee.position() : "");
        setUpColumn(rolesColumn, Employee::rolesText);
        setUpColumn(statusColumn, Employee::statusText);
        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("employee-blocked");
                setText(empty ? null : item);
                if (!empty && getTableRow() != null && getTableRow().getItem() != null
                        && getTableRow().getItem().isBlocked()) {
                    getStyleClass().add("employee-blocked");
                }
            }
        });

        employeesTable.setItems(employees);
        employeesTable.setPlaceholder(new Label("Сотрудники появятся после синхронизации с сервером"));
        employeesTable.setRowFactory(table -> {
            TableRow<Employee> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openDialog(row.getItem());
                }
            });
            return row;
        });
        employeesTable.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> updateButtons(selected));

        searchField.textProperty().addListener((obs, old, query) -> applyFilter(query));

        addButton.setOnAction(event -> openDialog(null));
        editButton.setOnAction(event -> openDialog(selected()));
        blockButton.setOnAction(event -> toggleBlocked(selected()));
        resetPasswordButton.setOnAction(event -> resetPassword(selected()));
        deleteButton.setOnAction(event -> delete(selected()));

        EventBus.getInstance().subscribe(UsersChangedEvent.class, usersListener);
        updateButtons(null);
        loadEmployees();
    }

    private void loadEmployees() {
        Employee selected = selected();
        service.findEmployees(list -> {
            allEmployees.setAll(list);
            applyFilter(searchField.getText());
            // После обновления выделение остаётся на том же сотруднике
            if (selected != null) {
                list.stream().filter(e -> e.id().equals(selected.id())).findFirst()
                        .ifPresent(e -> employeesTable.getSelectionModel().select(e));
            }
        }, error -> NavigationManager.showToast(error, "error"));
    }

    private void applyFilter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        employees.setPredicate(employee -> q.isEmpty()
                || employee.name().toLowerCase(Locale.ROOT).contains(q)
                || employee.email().toLowerCase(Locale.ROOT).contains(q));
    }

    private void updateButtons(Employee selected) {
        boolean none = selected == null;
        // Себя нельзя заблокировать или удалить — иначе можно потерять доступ
        boolean self = !none && selected.id().equals(session.getCurrentUserId());
        editButton.setDisable(none);
        resetPasswordButton.setDisable(none);
        blockButton.setDisable(none || self);
        deleteButton.setDisable(none || self);
        blockButton.setText(!none && selected.isBlocked() ? "Разблокировать" : "Заблокировать");
    }

    // ===== Действия =====

    private void openDialog(Employee employee) {
        NavigationManager.openDialog(Routes.EMPLOYEE_DIALOG,
                employee == null ? "Новый сотрудник" : "Сотрудник",
                AppConfig.getInstance().getPrimaryStage(),
                employee);
    }

    private void toggleBlocked(Employee employee) {
        boolean block = !employee.isBlocked();
        String question = block
                ? "Заблокировать «" + employee.name() + "»? Сотрудник сразу потеряет доступ, все его сеансы завершатся."
                : "Разблокировать «" + employee.name() + "»?";
        if (!confirm(block ? "Блокировка" : "Разблокировка", question)) {
            return;
        }
        service.setBlocked(employee, block,
                () -> NavigationManager.showToast(block ? "Сотрудник заблокирован" : "Сотрудник разблокирован", "success"),
                error -> NavigationManager.showToast(error, "error"));
    }

    private void resetPassword(Employee employee) {
        if (!confirm("Сброс пароля", "Сбросить пароль «" + employee.name()
                + "»? Будет создан временный пароль, все сеансы сотрудника завершатся.")) {
            return;
        }
        String password = TemporaryPasswords.generate();
        service.resetPassword(employee, password,
                () -> PasswordNotice.show(window(), employee.name(), password),
                error -> NavigationManager.showToast(error, "error"));
    }

    private void delete(Employee employee) {
        if (!confirm("Удаление", "Удалить «" + employee.name()
                + "»? Сотрудник потеряет доступ. Его задачи и проекты останутся.")) {
            return;
        }
        service.delete(employee,
                () -> NavigationManager.showToast("Сотрудник удалён", "success"),
                error -> NavigationManager.showToast(error, "error"));
    }

    // ===== Вспомогательное =====

    private Employee selected() {
        return employeesTable.getSelectionModel().getSelectedItem();
    }

    private boolean confirm(String title, String text) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, text, ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.initOwner(window());
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private Window window() {
        return employeesTable.getScene() != null ? employeesTable.getScene().getWindow() : null;
    }

    private static void setUpColumn(TableColumn<Employee, String> column, Function<Employee, String> value) {
        column.setCellValueFactory(data -> new SimpleStringProperty(value.apply(data.getValue())));
    }

    @Override
    public void shutdown() {
        EventBus.getInstance().unsubscribe(UsersChangedEvent.class, usersListener);
    }
}
