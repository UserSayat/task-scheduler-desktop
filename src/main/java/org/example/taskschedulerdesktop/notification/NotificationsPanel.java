package org.example.taskschedulerdesktop.notification;

import org.example.taskschedulerdesktop.shared.ui.Clicks;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import org.example.taskschedulerdesktop.shared.event.EventBus;
import org.example.taskschedulerdesktop.shared.navigation.NavigationManager;
import org.example.taskschedulerdesktop.shared.navigation.Routes;
import org.example.taskschedulerdesktop.task.AsyncTaskService;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Список уведомлений под колокольчиком. Нажатие на уведомление отмечает его прочитанным
 * и открывает задачу, если она есть на этом компьютере.
 */
public class NotificationsPanel {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault());
    private static final double WIDTH = 380;

    private final AsyncNotificationService notifications;
    private final AsyncTaskService tasks;

    private final Popup popup = new Popup();
    private final VBox list = new VBox();
    private final Button readAllButton = new Button("Прочитать все");

    public NotificationsPanel(AsyncNotificationService notifications, AsyncTaskService tasks) {
        this.notifications = notifications;
        this.tasks = tasks;

        Label header = new Label("Уведомления");
        header.getStyleClass().add("notifications-header");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        readAllButton.getStyleClass().add("notifications-link");
        readAllButton.setOnAction(event -> notifications.markAllRead(this::changed));
        HBox top = new HBox(8, header, spacer, readAllButton);
        top.setAlignment(Pos.CENTER_LEFT);
        top.getStyleClass().add("notifications-top");

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setMaxHeight(440);
        scroll.getStyleClass().add("scroll-pane-clean");

        VBox root = new VBox(top, scroll);
        root.setPrefWidth(WIDTH);
        root.getStyleClass().add("notifications-panel");
        popup.getContent().add(root);
        popup.setAutoHide(true);
    }

    /** Показать под кнопкой или скрыть, если уже открыт. */
    public void toggle(Node anchor) {
        if (popup.isShowing()) {
            popup.hide();
            return;
        }
        // Стили панели берутся из сцены главного окна
        ((Region) popup.getContent().get(0)).getStylesheets().setAll(anchor.getScene().getStylesheets());
        refresh();
        Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        popup.show(anchor, bounds.getMaxX() - WIDTH, bounds.getMaxY() + 4);
    }

    /** Перечитать список, если панель открыта. */
    public void refresh() {
        notifications.findAll(this::show);
    }

    private void show(List<Notification> items) {
        list.getChildren().clear();
        readAllButton.setDisable(items.stream().allMatch(Notification::read));
        if (items.isEmpty()) {
            Label empty = new Label("Уведомлений пока нет");
            empty.getStyleClass().add("notifications-empty");
            list.getChildren().add(empty);
            return;
        }
        items.forEach(item -> list.getChildren().add(row(item)));
    }

    private Node row(Notification item) {
        Label title = new Label(item.title());
        title.getStyleClass().add(item.read() ? "notification-title" : "notification-title-unread");
        Label body = new Label(item.body());
        body.setWrapText(true);
        body.getStyleClass().add("notification-body");
        Label time = new Label(item.createdAt() != null ? TIME.format(item.createdAt()) : "");
        time.getStyleClass().add("notification-time");

        Region dot = new Region();
        dot.getStyleClass().add(item.read() ? "notification-dot-read" : "notification-dot");
        VBox text = new VBox(2, title, body, time);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(10, dot, text);
        row.getStyleClass().add("notification-row");
        Clicks.onClick(row, () -> open(item));
        return row;
    }

    private void open(Notification item) {
        if (!item.read()) {
            notifications.markRead(item.id(), this::changed);
        }
        if (!item.isAboutTask()) {
            return;
        }
        popup.hide();
        tasks.findTaskView(item.entityId(),
                task -> NavigationManager.openRightSidebar(Routes.TASK_DETAILS, task),
                error -> NavigationManager.showToast("Задача удалена или недоступна", "info"));
    }

    private void changed() {
        EventBus.getInstance().fire(new NotificationsChangedEvent());
        if (popup.isShowing()) {
            refresh();
        }
    }
}
