package org.example.taskschedulerdesktop.tag;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import org.example.taskschedulerdesktop.shared.navigation.NavigationManager;
import org.example.taskschedulerdesktop.tag.TagAdminService.TagRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Экран «Теги» для администратора: создать, переименовать, сменить цвет, удалить.
 */
public class TagsController {

    private static final Logger log = LoggerFactory.getLogger(TagsController.class);

    private final TagAdminService service;

    @FXML private TableView<TagRow> tagsTable;
    @FXML private TableColumn<TagRow, String> colorColumn;
    @FXML private TableColumn<TagRow, String> nameColumn;
    @FXML private TableColumn<TagRow, Number> tasksColumn;

    @FXML private Label formTitle;
    @FXML private TextField nameField;
    @FXML private ColorPicker colorPicker;
    @FXML private Button saveButton;
    @FXML private Button newButton;
    @FXML private Button deleteButton;

    public TagsController(TagAdminService service) {
        this.service = service;
    }

    @FXML
    public void initialize() {
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name()));
        tasksColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().tasks()));
        colorColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().color()));
        colorColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String color, boolean empty) {
                super.updateItem(color, empty);
                if (empty || color == null) {
                    setGraphic(null);
                    return;
                }
                Region swatch = new Region();
                swatch.getStyleClass().add("tag-swatch");
                swatch.setStyle("-fx-background-color: " + color + ";");
                setGraphic(swatch);
            }
        });
        tagsTable.setPlaceholder(new Label("Тегов пока нет"));
        tagsTable.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> showForm(selected));

        newButton.setOnAction(event -> tagsTable.getSelectionModel().clearSelection());
        saveButton.setOnAction(event -> save());
        deleteButton.setOnAction(event -> delete());

        showForm(null);
        load();
    }

    private void load() {
        run(service::findAll, rows -> tagsTable.getItems().setAll(rows));
    }

    private void showForm(TagRow tag) {
        boolean editing = tag != null;
        formTitle.setText(editing ? "Тег «" + tag.name() + "»" : "Новый тег");
        nameField.setText(editing ? tag.name() : "");
        colorPicker.setValue(Color.web(editing && tag.color() != null ? tag.color() : Tag.DEFAULT_COLOR));
        saveButton.setText(editing ? "Сохранить" : "Добавить тег");
        deleteButton.setVisible(editing);
        deleteButton.setManaged(editing);
    }

    private void save() {
        TagRow selected = tagsTable.getSelectionModel().getSelectedItem();
        String name = nameField.getText();
        String color = hex(colorPicker.getValue());
        run(() -> {
            if (selected == null) {
                service.create(name, color);
            } else {
                service.update(selected.id(), name, color);
            }
            return null;
        }, ignored -> {
            NavigationManager.showToast(selected == null ? "Тег добавлен" : "Тег сохранён", "success");
            tagsTable.getSelectionModel().clearSelection();
            load();
        });
    }

    private void delete() {
        TagRow selected = tagsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Удалить тег «" + selected.name() + "»? Он пропадёт у " + selected.tasks()
                        + " задач. Имя тега останется занятым.",
                ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText(null);
        alert.setTitle("Удаление тега");
        alert.initOwner(tagsTable.getScene().getWindow());
        if (alert.showAndWait().filter(ButtonType.OK::equals).isEmpty()) {
            return;
        }
        run(() -> {
            service.delete(selected.id());
            return null;
        }, ignored -> {
            NavigationManager.showToast("Тег удалён", "success");
            load();
        });
    }

    private static String hex(Color color) {
        return String.format(Locale.ROOT, "#%02X%02X%02X",
                Math.round(color.getRed() * 255), Math.round(color.getGreen() * 255), Math.round(color.getBlue() * 255));
    }

    private <T> void run(Callable<T> work, Consumer<T> onSuccess) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            if (error instanceof IllegalArgumentException) {
                NavigationManager.showToast(error.getMessage(), "info");
            } else {
                log.error("Tag action failed", error);
                NavigationManager.showToast("Не удалось выполнить действие", "error");
            }
        });
        Thread thread = new Thread(task, "tags");
        thread.setDaemon(true);
        thread.start();
    }
}
