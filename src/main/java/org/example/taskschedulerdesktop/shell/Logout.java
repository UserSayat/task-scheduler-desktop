package org.example.taskschedulerdesktop.shell;

import javafx.concurrent.Task;
import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.shared.ui.Dialogs;
import org.example.taskschedulerdesktop.sync.SyncStatus;

/**
 * Выход из аккаунта (кнопка «Выйти» на странице профиля): подтверждение, затем попытка отправить
 * изменения. Если что-то осталось неотправленным (нет связи), пользователь решает, выходить ли:
 * изменения сохранятся на этом компьютере и уйдут после следующего входа.
 */
public final class Logout {

    private static boolean running;

    private Logout() {}

    /** В демо-режиме выходить некуда. */
    public static boolean available() {
        return !AppConfig.getInstance().isDemoMode();
    }

    /** @param onCancelled выход не состоялся (передумали или не удалось синхронизироваться) */
    public static void start(Runnable onCancelled) {
        if (running) {
            return;
        }
        AppConfig appConfig = AppConfig.getInstance();
        if (!Dialogs.confirm(appConfig.getPrimaryStage(), "Выход", "Выйти из аккаунта?",
                "Чтобы продолжить работу, нужно будет снова войти.")) {
            onCancelled.run();
            return;
        }
        running = true;
        Task<SyncStatus> sync = new Task<>() {
            @Override
            protected SyncStatus call() {
                return appConfig.getSyncService().synchronize();
            }
        };
        sync.setOnSucceeded(event -> {
            SyncStatus status = sync.getValue();
            if (status.pending() > 0 && !confirmUnsent(appConfig, status.pending())) {
                running = false;
                onCancelled.run();
                return;
            }
            Task<Void> logout = new Task<>() {
                @Override
                protected Void call() {
                    appConfig.getAuthService().logout();
                    return null;
                }
            };
            logout.setOnSucceeded(e -> finish(appConfig));
            logout.setOnFailed(e -> finish(appConfig));
            startDaemon(logout, "logout");
        });
        sync.setOnFailed(event -> {
            running = false;
            onCancelled.run();
        });
        startDaemon(sync, "logout-sync");
    }

    private static void finish(AppConfig appConfig) {
        running = false;
        appConfig.getAppShell().showLogin(null);
    }

    private static boolean confirmUnsent(AppConfig appConfig, int pending) {
        return Dialogs.confirm(appConfig.getPrimaryStage(), "Выход", "Выйти из аккаунта?",
                "Не отправлено изменений: " + pending + " (нет связи с сервером).\n"
                        + "Они сохранятся на этом компьютере и будут отправлены после следующего входа.");
    }

    private static void startDaemon(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }
}
