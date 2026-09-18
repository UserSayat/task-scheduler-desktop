package org.example.taskschedulerdesktop.service.task;

import javafx.concurrent.Service;
import javafx.scene.Node;
import org.example.taskschedulerdesktop.models.Task;
import org.example.taskschedulerdesktop.utils.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class AsyncTaskService {

    private static final Logger log = LoggerFactory.getLogger(AsyncTaskService.class);

    private final TaskService delegate;
    private final TaskCardService taskCardService;

    private final Map<TaskStatus, List<Node>> taskCache = new ConcurrentHashMap<>();
    private final Map<TaskStatus, Boolean> dirtyFlags = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    });

    public AsyncTaskService(TaskService delegate, TaskCardService taskCardService) {
        this.delegate = delegate;
        this.taskCardService = taskCardService;
    }

    public void invalidateCache(TaskStatus status) {
        dirtyFlags.put(status, true);
    }

    public void invalidateAllCache() {
        for (TaskStatus status : TaskStatus.values()) {
            dirtyFlags.put(status, true);
        }
    }

    public Service<List<Node>> createLoaderService(TaskStatus status) {
        Service<List<Node>> service = new Service<>() {
            @Override
            protected javafx.concurrent.Task<List<Node>> createTask() {
                return new javafx.concurrent.Task<>() {
                    @Override
                    protected List<Node> call() throws Exception {
                        //TODO добавить в качестве параметра поиска название проекта
                        // чтобы не выводились задачи со всех проектов, а только с текущего
                        boolean isDirty = dirtyFlags.getOrDefault(status, true);

                        List<Node> cachedCards = taskCache.computeIfAbsent(status, k -> new java.util.concurrent.CopyOnWriteArrayList<>());

                        if (!isDirty && !cachedCards.isEmpty()) {
                            return cachedCards;
                        }

                        List<Task> tasks = delegate.findByStatus(status);
                        List<Node> newCards = taskCardService.createCards(tasks);

                        cachedCards.clear();
                        cachedCards.addAll(newCards);
                        dirtyFlags.put(status, false);

                        return cachedCards;
                    }
                };
            }
        };

        service.setExecutor(this.executor);
        return service;
    }

    /**
     * Фоновое создание новой задачи в БД.
     * @param newTask Объект новой задачи
     * @param onSuccess Действие в случае успеха (выполняется в UI-потоке)
     * @param onError Действие при ошибке БД (выполняется в UI-потоке)
     */
    public void createTask(Task newTask, Runnable onSuccess, Consumer<Throwable> onError) {
        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() throws Exception {
                delegate.save(newTask);
                return null;
            }
        };

        if (onSuccess != null) task.setOnSucceeded(event -> {
            invalidateCache(newTask.getStatus());
            onSuccess.run();
        });
        if (onError != null) task.setOnFailed(event -> onError.accept(task.getException()));

        executor.submit(task);
    }

    /**
     * Фоновое обновление параметров или статуса задачи в БД.
     */
    public void updateTask(Task taskToUpdate, Runnable onSuccess, Consumer<Throwable> onError) {
        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() throws Exception {
                delegate.update(taskToUpdate);
                return null;
            }
        };

        if (onSuccess != null) task.setOnSucceeded(event -> {
            invalidateCache(taskToUpdate.getStatus());
            onSuccess.run();
        });
        if (onError != null) task.setOnFailed(e -> onError.accept(task.getException()));

        executor.submit(task);
    }

    /**
     * Фоновое удаление задачи из БД.
     */
    public void deleteTask(Task taskToDelete, Runnable onSuccess, Consumer<Throwable> onError) {
        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() throws Exception {
                delegate.delete(taskToDelete.getId());
                return null;
            }
        };

        if (onSuccess != null) task.setOnSucceeded(event -> {
            invalidateCache(taskToDelete.getStatus());
            onSuccess.run();
        });
        if (onError != null) task.setOnFailed(e -> onError.accept(task.getException()));

        executor.submit(task);
    }
}
