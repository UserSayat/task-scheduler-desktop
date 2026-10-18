package org.example.taskschedulerdesktop.user;

import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Вызовы {@link UserService} в фоне. Колбэки выполняются в UI-потоке.
 */
public class AsyncUserService {

    private static final Logger log = LoggerFactory.getLogger(AsyncUserService.class);

    private final UserService delegate;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    });

    public AsyncUserService(UserService delegate) {
        this.delegate = delegate;
    }

    public void findActiveUsers(Consumer<List<User>> onSuccess, Consumer<Throwable> onError) {
        Task<List<User>> task = new Task<>() {
            @Override
            protected List<User> call() {
                return delegate.findAllActive();
            }
        };

        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> {
            log.error("Users loading failed", task.getException());
            onError.accept(task.getException());
        });

        executor.submit(task);
    }
}
