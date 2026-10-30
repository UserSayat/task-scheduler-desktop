package org.example.taskschedulerdesktop.shell;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.taskschedulerdesktop.shared.api.OfflineException;
import org.example.taskschedulerdesktop.shared.navigation.NavigationManager;
import org.example.taskschedulerdesktop.sync.SyncScheduler;
import org.example.taskschedulerdesktop.sync.SyncService;
import org.example.taskschedulerdesktop.sync.local.SyncErrorRepository.Rejected;
import org.example.taskschedulerdesktop.sync.protocol.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Изменения, которые сервер не принял (нет прав, некорректные данные, потерян доступ к проекту).
 * Для каждого: «Повторить» — отправить ещё раз, «Отменить мою правку» — вернуть данные с сервера.
 */
public class RejectedChangesController {

    private static final Logger log = LoggerFactory.getLogger(RejectedChangesController.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault());

    private final SyncService syncService;
    private final SyncScheduler scheduler;

    @FXML private VBox rowsVBox;
    @FXML private Button closeButton;

    public RejectedChangesController(SyncService syncService, SyncScheduler scheduler) {
        this.syncService = syncService;
        this.scheduler = scheduler;
    }

    @FXML
    public void initialize() {
        closeButton.setOnAction(event -> ((Stage) closeButton.getScene().getWindow()).close());
        load();
    }

    private void load() {
        run(syncService::rejectedChanges, this::show);
    }

    private void show(List<Rejected> rows) {
        rowsVBox.getChildren().clear();
        if (rows.isEmpty()) {
            Label empty = new Label("Все изменения приняты сервером");
            empty.getStyleClass().add("notifications-empty");
            rowsVBox.getChildren().add(empty);
            return;
        }
        rows.forEach(row -> rowsVBox.getChildren().add(row(row)));
    }

    private VBox row(Rejected rejected) {
        Label title = new Label(kind(rejected) + (rejected.name() != null ? " «" + rejected.name() + "»" : ""));
        title.getStyleClass().add("notification-title-unread");
        title.setWrapText(true);
        Label reason = new Label(reason(rejected));
        reason.getStyleClass().add("notification-body");
        reason.setWrapText(true);
        Label time = new Label(rejected.at() != null ? TIME.format(rejected.at()) : "");
        time.getStyleClass().add("notification-time");

        boolean created = rejected.operation() == Operation.CREATE;
        Button retry = new Button("Повторить");
        retry.getStyleClass().add("btn-secondary");
        retry.setOnAction(event -> run(() -> {
            syncService.retryRejected(rejected.entityType(), rejected.entityId());
            return null;
        }, ignored -> {
            scheduler.syncNow();
            NavigationManager.showToast("Изменение будет отправлено ещё раз", "info");
            load();
        }));

        Button discard = new Button(created ? "Удалить" : "Отменить мою правку");
        discard.getStyleClass().add("btn-ghost-danger");
        discard.setOnAction(event -> {
            if (!confirm(created
                    ? "Удалить с этого компьютера? На сервере этой записи нет."
                    : "Вернуть данные с сервера? Ваши неотправленные правки этой записи пропадут.")) {
                return;
            }
            run(() -> {
                syncService.discardRejected(rejected.entityType(), rejected.entityId(), created);
                return null;
            }, ignored -> {
                NavigationManager.showToast(created ? "Удалено" : "Данные возвращены с сервера", "success");
                load();
            });
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, time, spacer, retry, discard);
        actions.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(4, title, reason, actions);
        box.getStyleClass().add("rejected-row");
        return box;
    }

    private static String kind(Rejected rejected) {
        boolean created = rejected.operation() == Operation.CREATE;
        return switch (rejected.entityType()) {
            case TASK -> created ? "Новая задача" : "Задача";
            case PROJECT -> created ? "Новый проект" : "Проект";
            case TAG -> created ? "Новый тег" : "Тег";
            case USER -> "Профиль";
        };
    }

    private static String reason(Rejected rejected) {
        String code = switch (rejected.code()) {
            case "FORBIDDEN" -> "Нет прав на это изменение.";
            case "VALIDATION_ERROR" -> "Сервер счёл данные некорректными.";
            case "NOT_FOUND" -> "Запись удалена на сервере.";
            case "CONFLICT" -> "Такое имя уже занято.";
            default -> "Сервер не принял изменение.";
        };
        return rejected.message() != null && !rejected.message().isBlank() ? code + " " + rejected.message() : code;
    }

    private boolean confirm(String text) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, text, ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText(null);
        alert.initOwner(rowsVBox.getScene().getWindow());
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
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
            if (error instanceof OfflineException) {
                NavigationManager.showToast("Нет связи с сервером. Вернуть данные можно только онлайн", "info");
            } else {
                log.error("Rejected change action failed", error);
                NavigationManager.showToast("Не удалось выполнить действие", "error");
            }
        });
        Thread thread = new Thread(task, "rejected-changes");
        thread.setDaemon(true);
        thread.start();
    }
}
