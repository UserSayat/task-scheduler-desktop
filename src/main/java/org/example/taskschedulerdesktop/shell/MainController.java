package org.example.taskschedulerdesktop.shell;

import org.example.taskschedulerdesktop.shared.ui.Dialogs;
import org.example.taskschedulerdesktop.shared.ui.Animations;
import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.notification.NotificationsChangedEvent;
import org.example.taskschedulerdesktop.notification.NotificationsPanel;
import org.example.taskschedulerdesktop.project.ProjectChangedEvent;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.project.ProjectView;
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
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import javafx.animation.Animation;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.layout.HBox;
import javafx.scene.control.Button;
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
    @FXML private Button employeesButton;
    @FXML private Button tagsButton;
    @FXML private Button settingsButton;
    @FXML private HBox userBox;

    private final Consumer<UsersChangedEvent> usersListener = event -> showCurrentUser();
    @FXML private Button notificationsButton;
    @FXML private Label unreadBadge;

    private NotificationsPanel notificationsPanel;

    /** −1 — счётчик ещё не загружен (первая загрузка не «подпрыгивает»). */
    private int lastUnreadCount = -1;

    private Animation syncBreathing;

    private final Consumer<NotificationsChangedEvent> notificationsListener = event -> showUnreadCount();

    @FXML private Button backButton;

    @FXML private Label pageTitleLabel;
    @FXML private Label pageDate;

    @FXML private Button syncStatusButton;
    @FXML private Button createTaskButton;

    @FXML private Label userInitialsLabel;
    @FXML private Label userNameLabel;
    @FXML private Label userPositionLabel;

    // Общая полоса сверху: в неё своя рамка окна встраивает кнопки окна
    @FXML private HBox appHeader;
    @FXML private StackPane headerLogoCell;
    @FXML private HBox logoBox;
    @FXML private HBox headerRight;

    @FXML private VBox sidebarProjectsList;
    private SidebarProjects sidebarProjects;

    private final MenuItem logoutItem = new MenuItem("Выйти");
    private boolean loggingOut;

    private final Consumer<PageTitleChangedEvent> titleUpdateListener = event -> {
        pageTitleLabel.setText(event.getPageTitle());
        updateBackButtonState();
        highlightCurrentSection();
    };

    /** Какой пункт меню подсвечивать на странице. Страница проекта относится к «Проектам». */
    private Map<String, Button> sectionButtons() {
        return Map.of(
                Routes.DASHBOARD, reviewButton,
                Routes.TASKS, tasksButton,
                Routes.PROJECTS, projectsButton,
                Routes.PROJECT_DETAILS, projectsButton,
                Routes.TEAM, teamButton,
                Routes.EMPLOYEES, employeesButton,
                Routes.TAGS, tagsButton);
    }

    private void highlightCurrentSection() {
        Button active = sectionButtons().get(NavigationManager.getCurrentPage());
        // Открыт проект из списка в меню — подсвечиваем его строку, а не пункт «Проекты»
        UUID openProject = Routes.PROJECT_DETAILS.equals(NavigationManager.getCurrentPage())
                && NavigationManager.getCurrentContext() instanceof ProjectView project ? project.id() : null;
        if (sidebarProjects != null && sidebarProjects.highlight(openProject)) {
            active = null;
        }
        // Настройки открываются шестерёнкой в верхней полосе — подсвечиваем её
        settingsButton.getStyleClass().remove("header-icon-active");
        if (Routes.SETTINGS.equals(NavigationManager.getCurrentPage())) {
            settingsButton.getStyleClass().add("header-icon-active");
        }
        for (Button button : List.of(reviewButton, tasksButton, projectsButton, teamButton,
                employeesButton, tagsButton)) {
            button.getStyleClass().remove("nav-btn-active");
            if (button == active) {
                button.getStyleClass().add("nav-btn-active");
            }
        }
    }

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
        employeesButton.setOnAction(event -> NavigationManager.navigateTo(Routes.EMPLOYEES, "Сотрудники"));
        tagsButton.setOnAction(event -> NavigationManager.navigateTo(Routes.TAGS, "Теги"));
        settingsButton.setOnAction(event -> NavigationManager.navigateTo(Routes.SETTINGS, "Настройки"));

        sidebarProjects = new SidebarProjects(sidebarProjectsList, appConfig.getAsyncProjectService(),
                this::openProjectFromSidebar);
        sidebarProjects.start();

        showCurrentUser();
        setUpProfileMenu();
        EventBus.getInstance().subscribe(UsersChangedEvent.class, usersListener);
        setUpSync();
        setUpNotifications();

        NavigationManager.navigateTo(Routes.DASHBOARD, "Обзор");
    }

    /** Проект из меню: как переключение раздела. Уже открытый проект не перезагружаем. */
    private void openProjectFromSidebar(ProjectView project) {
        if (Routes.PROJECT_DETAILS.equals(NavigationManager.getCurrentPage())
                && NavigationManager.getCurrentContext() instanceof ProjectView open
                && open.id().equals(project.id())) {
            return;
        }
        NavigationManager.navigateToSection(Routes.PROJECT_DETAILS, project.name(), project);
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
        // Пункт «Сотрудники» — только администратору; роль могли выдать или снять на сервере
        appConfig.getEmployeeAdminService().isAdmin(userId, admin -> {
            employeesButton.setVisible(admin);
            employeesButton.setManaged(admin);
            tagsButton.setVisible(admin);
            tagsButton.setManaged(admin);
            // В демо-режиме сервера нет — настройки не сохранить
            boolean settings = admin && !appConfig.isDemoMode();
            settingsButton.setVisible(settings);
            settingsButton.setManaged(settings);
        });

    }

    /** Нажатие на профиль в верхней полосе — меню «Профиль / Выйти». */
    private void setUpProfileMenu() {
        MenuItem profileItem = new MenuItem("Профиль");
        profileItem.setOnAction(event -> NavigationManager.openDialog(Routes.PROFILE, "Профиль", appConfig.getPrimaryStage()));
        logoutItem.setOnAction(event -> logout());
        ContextMenu menu = new ContextMenu(profileItem);
        if (!appConfig.isDemoMode()) {
            menu.getItems().addAll(new SeparatorMenuItem(), logoutItem); // в демо-режиме выходить некуда
        }
        menu.getStyleClass().add("profile-menu");
        userBox.setOnMouseClicked(event -> {
            event.consume(); // двойной щелчок по профилю не должен разворачивать окно
            if (menu.isShowing()) {
                menu.hide();
            } else {
                menu.show(userBox, javafx.geometry.Side.BOTTOM, 0, 6);
            }
        });
    }

    /** Верхняя полоса и её левая ячейка (над меню) — для своей рамки окна. */
    public HBox header() {
        return appHeader;
    }

    public StackPane headerLogoCell() {
        return headerLogoCell;
    }

    public HBox logoBox() {
        return logoBox;
    }

    public HBox headerRight() {
        return headerRight;
    }

    /**
     * Выход: сначала попытка отправить изменения. Если что-то осталось неотправленным (нет связи),
     * пользователь решает, выходить ли: изменения сохранятся на этом компьютере и уйдут после следующего входа.
     */
    private void logout() {
        if (loggingOut) {
            return;
        }
        loggingOut = true;
        logoutItem.setDisable(true);
        Task<SyncStatus> sync = new Task<>() {
            @Override
            protected SyncStatus call() {
                return appConfig.getSyncService().synchronize();
            }
        };
        sync.setOnSucceeded(event -> {
            SyncStatus status = sync.getValue();
            if (status.pending() > 0 && !confirmLogout(status.pending())) {
                loggingOut = false;
                logoutItem.setDisable(false);
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
        sync.setOnFailed(event -> {
            loggingOut = false;
            logoutItem.setDisable(false);
        });
        startDaemon(sync, "logout-sync");
    }

    private boolean confirmLogout(int pending) {
        return Dialogs.confirm(appConfig.getPrimaryStage(), "Выход", "Выйти из аккаунта?",
                "Не отправлено изменений: " + pending + " (нет связи с сервером).\n"
                        + "Они сохранятся на этом компьютере и будут отправлены после следующего входа.");
    }

    // ===== Уведомления =====

    private void setUpNotifications() {
        notificationsPanel = new NotificationsPanel(appConfig.getAsyncNotificationService(), appConfig.getAsyncTaskService());
        notificationsButton.setOnAction(event -> notificationsPanel.toggle(notificationsButton));
        EventBus.getInstance().subscribe(NotificationsChangedEvent.class, notificationsListener);
        showUnreadCount();
    }

    /** Счётчик на колокольчике; «99+», чтобы не растягивать шапку. */
    private void showUnreadCount() {
        appConfig.getAsyncNotificationService().unreadCount(count -> {
            if (count > lastUnreadCount && lastUnreadCount >= 0) {
                Animations.pulse(unreadBadge); // пришло новое уведомление
            }
            lastUnreadCount = count;
            unreadBadge.setText(count > 99 ? "99+" : String.valueOf(count));
            unreadBadge.setVisible(count > 0);
            unreadBadge.setManaged(count > 0);
            notificationsPanel.refresh();
        });
    }

    // ===== Синхронизация =====

    private void setUpSync() {
        if (appConfig.isDemoMode()) {
            syncStatusButton.setText("Демо-режим · без сервера");
            syncStatusButton.getStyleClass().add("sync-offline");
            syncStatusButton.setDisable(true);
            return;
        }
        // Есть отклонённые изменения — показать их; иначе синхронизировать сейчас
        syncStatusButton.setOnAction(event -> {
            if (appConfig.getSyncService().status().errors() > 0) {
                NavigationManager.openDialog(Routes.REJECTED_CHANGES, "Отклонённые изменения", appConfig.getPrimaryStage());
            } else {
                appConfig.getSyncScheduler().syncNow();
            }
        });
        appConfig.getSyncService().addStatusListener(syncStatusListener);
        appConfig.getSyncService().addChangeListener(serverChangesListener);
    }

    private void showSyncStatus(SyncStatus status) {
        // Пока идёт обмен с сервером, индикатор мягко «дышит»
        if (status.state() == SyncStatus.State.SYNCING && syncBreathing == null) {
            syncBreathing = Animations.breathe(syncStatusButton);
        } else if (status.state() != SyncStatus.State.SYNCING && syncBreathing != null) {
            syncBreathing.stop();
            syncBreathing = null;
            syncStatusButton.setOpacity(1);
        }
        String text;
        String style;
        String details = null;
        switch (status.state()) {
            case SYNCING -> {
                text = "Синхронизация…";
                style = "sync-pending";
            }
            case OFFLINE -> {
                text = "Офлайн" + (status.pending() > 0 ? " · не отправлено: " + status.pending() : "");
                style = "sync-offline";
                details = "Нет связи с сервером. Изменения сохраняются на компьютере и будут отправлены позже.";
            }
            case ERROR -> {
                text = "Ошибка синхронизации";
                style = "sync-error";
                details = status.message();
            }
            case SIGNED_OUT -> {
                text = "Требуется вход";
                style = "sync-error";
            }
            default -> {
                if (status.errors() > 0) {
                    text = "Отклонено сервером: " + status.errors();
                    style = "sync-error";
                    details = "Эти изменения сервер не принял (нет прав или некорректные данные). Нажмите, чтобы разобраться.";
                } else if (status.pending() > 0) {
                    text = "Не отправлено: " + status.pending();
                    style = "sync-pending";
                } else if (status.lastSyncedAt() != null) {
                    text = "Последняя синхронизация: " + TIME.format(status.lastSyncedAt());
                    style = "sync-ok";
                } else {
                    text = "Синхронизация";
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
        EventBus.getInstance().unsubscribe(NotificationsChangedEvent.class, notificationsListener);
        EventBus.getInstance().unsubscribe(UsersChangedEvent.class, usersListener);
        if (sidebarProjects != null) {
            sidebarProjects.stop();
        }
        appConfig.getSyncService().removeStatusListener(syncStatusListener);
        appConfig.getSyncService().removeChangeListener(serverChangesListener);
    }
}
