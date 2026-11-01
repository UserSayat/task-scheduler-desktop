package org.example.taskschedulerdesktop.task;

import javafx.concurrent.Service;
import javafx.scene.Node;
import org.example.taskschedulerdesktop.shared.event.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Вызовы {@link TaskService} в фоне. Колбэки выполняются в UI-потоке.
 */
public class AsyncTaskService {

    private static final Logger log = LoggerFactory.getLogger(AsyncTaskService.class);

    private final TaskService delegate;
    private final TaskCardService taskCardService;

    private final ExecutorService executor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    });

    public AsyncTaskService(TaskService delegate, TaskCardService taskCardService) {
        this.delegate = delegate;
        this.taskCardService = taskCardService;
    }

    public Service<List<Node>> createLoaderService(UUID projectId, TaskStatus status) {
        Service<List<Node>> service = new Service<>() {
            @Override
            protected javafx.concurrent.Task<List<Node>> createTask() {
                return new javafx.concurrent.Task<>() {
                    @Override
                    protected List<Node> call() {
                        List<TaskView> tasks = delegate.findViewsByProjectIdAndStatus(projectId, status);
                        return taskCardService.createCardsForProjectExtendedPage(tasks);
                    }
                };
            }
        };

        service.setExecutor(this.executor);
        return service;
    }

    public void createTask(NewTask newTask, Consumer<TaskView> onSuccess, Consumer<Throwable> onError) {
        submit(() -> delegate.create(newTask), created -> {
            EventBus.getInstance().fire(new TaskChangedEvent(created.projectId(), created.title()));
            onSuccess.accept(created);
        }, onError);
    }

    public void updateTask(TaskEdit edit, Consumer<TaskView> onSuccess, Consumer<Throwable> onError) {
        submit(() -> delegate.update(edit), updated -> {
            EventBus.getInstance().fire(new TaskChangedEvent(updated.projectId(), updated.title()));
            onSuccess.accept(updated);
        }, onError);
    }

    public void takeTask(UUID id, Consumer<TaskView> onSuccess, Consumer<Throwable> onError) {
        changeAssignment(() -> delegate.take(id), onSuccess, onError);
    }

    public void joinTask(UUID id, Consumer<TaskView> onSuccess, Consumer<Throwable> onError) {
        changeAssignment(() -> delegate.join(id), onSuccess, onError);
    }

    public void leaveTask(UUID id, Consumer<TaskView> onSuccess, Consumer<Throwable> onError) {
        changeAssignment(() -> delegate.leave(id), onSuccess, onError);
    }

    private void changeAssignment(Callable<TaskView> action,
                                  Consumer<TaskView> onSuccess, Consumer<Throwable> onError) {
        submit(action, updated -> {
            EventBus.getInstance().fire(new TaskChangedEvent(updated.projectId(), updated.title()));
            onSuccess.accept(updated);
        }, onError);
    }

    public void deleteTask(TaskView taskToDelete, Runnable onSuccess, Consumer<Throwable> onError) {
        submit(() -> {
            delegate.delete(taskToDelete.id());
            return null;
        }, ignored -> {
            EventBus.getInstance().fire(new TaskChangedEvent(taskToDelete.projectId(), taskToDelete.title()));
            onSuccess.run();
        }, onError);
    }

    public void findAllTasksView(Consumer<List<TaskView>> onSuccess, Consumer<Throwable> onError) {
        submit(delegate::findAllViews, onSuccess, onError);
    }

    public void findMyOpenTasks(Consumer<List<TaskView>> onSuccess, Consumer<Throwable> onError) {
        submit(delegate::findMyOpenViews, onSuccess, onError);
    }

    public void findUpcomingDeadlines(int maxDaysUntilDeadline, Consumer<List<TaskView>> onSuccess, Consumer<Throwable> onError) {
        submit(() -> delegate.findUpcomingDeadlines(maxDaysUntilDeadline), onSuccess, onError);
    }

    public void findTaskView(UUID taskId, Consumer<TaskView> onSuccess, Consumer<Throwable> onError) {
        submit(() -> delegate.findViewById(taskId), onSuccess, onError);
    }

    public void findTaskExtras(UUID taskId, Consumer<TaskExtras> onSuccess, Consumer<Throwable> onError) {
        submit(() -> delegate.findExtras(taskId), onSuccess, onError);
    }

    public void findTopOpenTasks(UUID projectId, int limit, Consumer<List<TaskView>> onSuccess, Consumer<Throwable> onError) {
        submit(() -> delegate.findTopOpenViews(projectId, limit), onSuccess, onError);
    }

    public void countTasksByProjectIdAndStatus(UUID projectId, TaskStatus status, Consumer<Integer> onSuccess, Consumer<Throwable> onError) {
        submit(() -> delegate.countByProjectIdAndStatus(projectId, status), onSuccess, onError);
    }

    public void countTasksByStatus(TaskStatus status, Consumer<Integer> onSuccess, Consumer<Throwable> onError) {
        submit(() -> delegate.countByStatus(status), onSuccess, onError);
    }

    public void countTasksByProjectIdAndPriority(UUID projectId, TaskPriority priority, Consumer<Integer> onSuccess, Consumer<Throwable> onError) {
        submit(() -> delegate.countByProjectIdAndPriority(projectId, priority), onSuccess, onError);
    }

    private <T> void submit(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        javafx.concurrent.Task<T> task = new javafx.concurrent.Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };

        task.setOnSucceeded(event -> {
            if (onSuccess != null) onSuccess.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            log.error("Background task failed", task.getException());
            if (onError != null) onError.accept(task.getException());
        });

        executor.submit(task);
    }
}
