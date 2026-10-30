package org.example.taskschedulerdesktop.notification;

import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Вызовы {@link NotificationService} в фоне. Колбэки выполняются в UI-потоке.
 */
public class AsyncNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AsyncNotificationService.class);

    private final NotificationService delegate;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "notifications");
        thread.setDaemon(true);
        return thread;
    });

    public AsyncNotificationService(NotificationService delegate) {
        this.delegate = delegate;
    }

    public void findAll(Consumer<List<Notification>> onSuccess) {
        run(delegate::findAll, onSuccess);
    }

    public void unreadCount(Consumer<Integer> onSuccess) {
        run(delegate::unreadCount, onSuccess);
    }

    public void markRead(UUID id, Runnable onDone) {
        run(() -> {
            delegate.markRead(id);
            return null;
        }, ignored -> onDone.run());
    }

    public void markAllRead(Runnable onDone) {
        run(() -> {
            delegate.markAllRead();
            return null;
        }, ignored -> onDone.run());
    }

    private <T> void run(Callable<T> work, Consumer<T> onSuccess) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> log.error("Notifications failed", task.getException()));
        executor.submit(task);
    }
}
