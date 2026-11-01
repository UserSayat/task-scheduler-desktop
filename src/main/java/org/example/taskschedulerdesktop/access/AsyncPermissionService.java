package org.example.taskschedulerdesktop.access;

import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Проверки {@link PermissionService} в фоне для экранов. Колбэк выполняется в UI-потоке.
 * Если проверка не удалась, экран получает «прав нет» — кнопка просто не появится.
 */
public class AsyncPermissionService {

    private static final Logger log = LoggerFactory.getLogger(AsyncPermissionService.class);

    private final PermissionService delegate;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "permissions");
        thread.setDaemon(true);
        return thread;
    });

    public AsyncPermissionService(PermissionService delegate) {
        this.delegate = delegate;
    }

    public void globalRights(Consumer<PermissionService.GlobalRights> onResult) {
        run(delegate::globalRights, onResult, new PermissionService.GlobalRights(false, false));
    }

    public void projectRights(UUID projectId, Consumer<PermissionService.ProjectRights> onResult) {
        run(() -> delegate.projectRights(projectId), onResult, new PermissionService.ProjectRights(false, false));
    }

    public void taskRights(UUID taskId, Consumer<PermissionService.TaskRights> onResult) {
        run(() -> delegate.taskRights(taskId), onResult, PermissionService.TaskRights.NONE);
    }

    /** {@code null} в результате — любые проекты (администратор). */
    public void projectsForNewTasks(Consumer<Set<UUID>> onResult) {
        run(delegate::projectsForNewTasks, onResult, Set.of());
    }

    /** Кого можно назначить на задачи проекта; при ошибке — никого. */
    public void projectTeam(UUID projectId, Consumer<ProjectTeam> onResult) {
        run(() -> delegate.projectTeam(projectId), onResult, ProjectTeam.EMPTY);
    }

    private <T> void run(Callable<T> check, Consumer<T> onResult, T denied) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return check.call();
            }
        };
        task.setOnSucceeded(event -> onResult.accept(task.getValue()));
        task.setOnFailed(event -> {
            log.error("Permission check failed", task.getException());
            onResult.accept(denied);
        });
        executor.submit(task);
    }
}
