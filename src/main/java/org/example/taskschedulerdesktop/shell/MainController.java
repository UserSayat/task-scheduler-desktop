package org.example.taskschedulerdesktop.shell;

import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.project.ProjectChangedEvent;
import org.example.taskschedulerdesktop.shared.event.ChangeType;
import org.example.taskschedulerdesktop.shared.event.EventBus;
import org.example.taskschedulerdesktop.shared.event.PageTitleChangedEvent;
import org.example.taskschedulerdesktop.shared.navigation.NavigationManager;
import org.example.taskschedulerdesktop.shared.navigation.Routes;
import org.example.taskschedulerdesktop.shared.ui.DateFormatter;
import org.example.taskschedulerdesktop.shared.ui.Shutdownable;
import org.example.taskschedulerdesktop.sync.SyncStatus;
import org.example.taskschedulerdesktop.sync.pull.AppliedChanges;
import org.example.taskschedulerdesktop.task.TaskChangedEvent;
import org.example.taskschedulerdesktop.user.Initials;
import org.example.taskschedulerdesktop.user.UsersChangedEvent;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;

public class MainController implements Shutdownable {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final List<String> SYNC_STYLES = List.of("sync-ok", "sync-pending", "sync-offline", "sync-error");

    private final AppConfig appConfig = AppConfig.getInstance();

    @FXML private StackPane globalStackPane;
    @FXML private StackPane contentArea;

    @FXML private Button reviewButton;
    @FXML private Button tasksButton;
    @FXML private Button projectsButton;
    @FXML private Button teamButton;

    @FXML private Button backButton;

    @FXML private Label pageTitleLabel;
    @FXML private Label pageDate;

    @FXML private Button syncStatusButton;
    @FXML private Button createTaskButton;

    @FXML private Label userInitialsLabel;
    @FXML private Label userNameLabel;
    @FXML private Label userPositionLabel;
    @FXML private Button logoutButton;

    private final Consumer<PageTitleChangedEvent> titleUpdateListener = event -> {
        pageTitleLabel.setText(event.getPageTitle());
        updateBackButtonState();
    };

    private final Consumer<SyncStatus> syncStatusListener = status -> Platform.runLater(() -> showSyncStatus(status));

    private final Consumer<AppliedChanges> serverChangesListener = changes -> Platform.runLater(() -> refreshScreens(changes));

    @FXML
    public void initialize() {

        NavigationManager.init(globalStackPane,
                contentArea,
                appConfig.getControllerFactory());

        EventBus.getInstance().subscribe(PageTitleChangedEvent.class, titleUpdateListener);

        backButton.setOnAction(event -> NavigationManager.goBack());
        updateBackButtonState(); // начальное состояние

        pageDate.setText(DateFormatter.formatFullWithDay(LocalDate.now()));

        createTaskButton.setOnAction(event -> NavigationManager.openDialog(
                Routes.CREATE_TASK,
                "Новая задача",
                AppConfig.getInstance().getPrimaryStage()));

        reviewButton.setOnAction(event -> NavigationManager.navigateTo(Routes.DASHBOARD, "Обзор"));
        tasksButton.setOnAction(event -> NavigationManager.navigateTo(Routes.TASKS, "Задачи"));
        projectsButton.setOnAction(event -> NavigationManager.navigateTo(Routes.PROJECTS, "Проекты"));
        teamButton.setOnAction(event -> NavigationManager.navigateTo(Routes.TEAM, "Команда"));

        showCurrentUser();
        setUpSync();

        NavigationManager.navigateTo(Routes.DASHBOARD, "Обзор");
    }

    public void updateBackButtonState() {
        boolean canGoBack = NavigationManager.canGoBack();
        backButton.setVisible(canGoBack);
        backButton.setManaged(canGoBack);
    }

    // ===== Пользователь =====

    private void showCurrentUser() {
        UUID userId = appConfig.getSession().getCurrentUserId();
        appConfig.getAsyncUserService().findUser(userId, user -> user.ifPresent(u -> {
            userNameLabel.setText(u.getName());
            userInitialsLabel.setText(Initials.of(u.getName()));
            userPositionLabel.setText(u.getPosition() != null ? u.getPosition() : "");
        }));

        if (appConfig.isDemoMode()) {
            logoutButton.setVisible(false);
            logoutButton.setManaged(false);
        } else {
            logoutButton.setOnAction(event -> logout());
        }
    }

    /**
     * Выход: сначала попытка отправить изменения. Если что-то осталось неотправленным (нет связи),
     * пользователь решает, выходить ли: изменения сохранятся на этом компьютере и уйдут после следующего входа.
     */
    private void logout() {
        logoutButton.setDisable(true);
        Task<SyncStatus> sync = new Task<>() {
            @Override
            protected SyncStatus call() {
                return appConfig.getSyncService().synchronize();
            }
        };
        sync.setOnSucceeded(event -> {
            SyncStatus status = sync.getValue();
            if (status.pending() > 0 && !confirmLogout(status.pending())) {
                logoutButton.setDisable(false);
                return;
            }
            Task<Void> logout = new Task<>() {
                @Override
                protected Void call() {
                    appConfig.getAuthService().logout();
                    return null;
                }
            };
            logout.setOnSucceeded(e -> appConfig.getAppShell().showLogin(null));
            logout.setOnFailed(e -> appConfig.getAppShell().showLogin(null));
            startDaemon(logout, "logout");
        });
        sync.setOnFailed(event -> logoutButton.setDisable(false));
        startDaemon(sync, "logout-sync");
    }

    private boolean confirmLogout(int pending) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Не отправлено изменений: " + pending + " (нет связи с сервером).\n"
                        + "Они сохранятся на этом компьютере и будут отправлены после следующего входа.",
                ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle("Выход");
        alert.setHeaderText("Выйти из аккаунта?");
        alert.initOwner(appConfig.getPrimaryStage());
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    // ===== Синхронизация =====

    private void setUpSync() {
        if (appConfig.isDemoMode()) {
            syncStatusButton.setText("Демо-режим · без сервера");
            syncStatusButton.getStyleClass().add("sync-offline");
            syncStatusButton.setDisable(true);
            return;
        }
        syncStatusButton.setOnAction(event -> appConfig.getSyncScheduler().syncNow());
        appConfig.getSyncService().addStatusListener(syncStatusListener);
        appConfig.getSyncService().addChangeListener(serverChangesListener);
    }

    private void showSyncStatus(SyncStatus status) {
        String text;
        String style;
        String details = null;
        switch (status.state()) {
            case SYNCING -> {
                text = "⟳ Синхронизация…";
                style = "sync-pending";
            }
            case OFFLINE -> {
                text = "● Офлайн" + (status.pending() > 0 ? " · не отправлено: " + status.pending() : "");
                style = "sync-offline";
                details = "Нет связи с сервером. Изменения сохраняются на компьютере и будут отправлены позже.";
            }
            case ERROR -> {
                text = "⚠ Ошибка синхронизации";
                style = "sync-error";
                details = status.message();
            }
            case SIGNED_OUT -> {
                text = "⚠ Требуется вход";
                style = "sync-error";
            }
            default -> {
                if (status.errors() > 0) {
                    text = "⚠ Отклонено сервером: " + status.errors();
                    style = "sync-error";
                    details = "Эти изменения сервер не принял (нет прав или некорректные данные).";
                } else if (status.pending() > 0) {
                    text = "● Не отправлено: " + status.pending();
                    style = "sync-pending";
                } else if (status.lastSyncedAt() != null) {
                    text = "● Синхронизировано · " + TIME.format(status.lastSyncedAt());
                    style = "sync-ok";
                } else {
                    text = "● Синхронизация";
                    style = "sync-pending";
                }
            }
        }
        syncStatusButton.setText(text);
        syncStatusButton.getStyleClass().removeAll(SYNC_STYLES);
        syncStatusButton.getStyleClass().add(style);
        syncStatusButton.setTooltip(new Tooltip(details != null ? details + "\nНажмите, чтобы синхронизировать сейчас."
                : "Нажмите, чтобы синхронизировать сейчас."));
    }

    /** Данные с сервера изменили локальную базу — открытые экраны перечитывают свои данные. */
    private void refreshScreens(AppliedChanges changes) {
        EventBus bus = EventBus.getInstance();
        changes.projects().forEach(id -> bus.fire(new ProjectChangedEvent(id, ChangeType.UPDATED)));
        changes.revokedProjects().forEach(id -> bus.fire(new ProjectChangedEvent(id, ChangeType.DELETED)));
        changes.taskProjects().forEach(id -> bus.fire(new TaskChangedEvent(id, null)));
        if (changes.usersChanged()) {
            showCurrentUser();
            bus.fire(new UsersChangedEvent());
        }
    }

    private static void startDaemon(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void shutdown() {
        EventBus.getInstance().unsubscribe(PageTitleChangedEvent.class, titleUpdateListener);
        appConfig.getSyncService().removeStatusListener(syncStatusListener);
        appConfig.getSyncService().removeChangeListener(serverChangesListener);
    }
}
