package org.example.taskschedulerdesktop.service.task;

import javafx.concurrent.Service;
import javafx.scene.Node;
import org.example.taskschedulerdesktop.dto.tasks.TaskView;
import org.example.taskschedulerdesktop.listeners.EventBus;
import org.example.taskschedulerdesktop.listeners.TaskChangedEvent;
import org.example.taskschedulerdesktop.models.Task;
import org.example.taskschedulerdesktop.utils.TaskPriority;
import org.example.taskschedulerdesktop.utils.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

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

    public Service<List<Node>> createLoaderService(long projectId, TaskStatus status) {
        Service<List<Node>> service = new Service<>() {
            @Override
            protected javafx.concurrent.Task<List<Node>> createTask() {
                return new javafx.concurrent.Task<>() {
                    @Override
                    protected List<Node> call() throws Exception {
                        //TODO добавить в качестве параметра поиска название проекта
                        // чтобы не выводились задачи со всех проектов, а только с текущего

                        List<TaskView> tasks = delegate.findViewByProjectIdAndStatus(projectId, status);
                        return taskCardService.createCardsForProjectExtendedPage(tasks);
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
            EventBus.getInstance().fire(new TaskChangedEvent(newTask.getProjectId(), newTask.getTaskName()));
            onSuccess.run();
        });
        if (onError != null) task.setOnFailed(event -> onError.accept(task.getException()));

        executor.submit(task);
    }

    public void findAllTasksView(Consumer<List<TaskView>> onSuccess, Consumer<Throwable> onError) {
        javafx.concurrent.Task<List<TaskView>> task = new javafx.concurrent.Task<>() {
            @Override
            protected List<TaskView> call() throws Exception {
                return delegate.findAllViews();
            }
        };

        if (onSuccess != null) {
            task.setOnSucceeded(event -> {
                log.debug("onSucceeded returned: {} tasks", task.getValue().size());
                onSuccess.accept(task.getValue());
            });
        }

        if (onError != null) {
            task.setOnFailed(event -> {
                log.error("onFailed: {}", task.getException().getMessage());
                onError.accept(task.getException());
            });
        }

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
            EventBus.getInstance().fire(new TaskChangedEvent(taskToUpdate.getProjectId(), taskToUpdate.getTaskName()));
            onSuccess.run();
        });
        if (onError != null) task.setOnFailed(e -> onError.accept(task.getException()));

        executor.submit(task);
    }

    /**
     * Фоновое удаление задачи из БД.
     */
    public void deleteTask(TaskView taskToDelete, Runnable onSuccess, Consumer<Throwable> onError) {
        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() throws Exception {
                delegate.delete(taskToDelete.getId());
                return null;
            }
        };

        if (onSuccess != null) task.setOnSucceeded(event -> {
            EventBus.getInstance().fire(new TaskChangedEvent(taskToDelete.getProjectId(), taskToDelete.getTaskName()));
            onSuccess.run();
        });
        if (onError != null) task.setOnFailed(e -> onError.accept(task.getException()));

        executor.submit(task);
    }

    public void countTasksByProjectIdAndStatus(long projectId, TaskStatus status, Consumer<Integer> onSuccess, Consumer<Throwable> onError) {
        javafx.concurrent.Task<Integer> task = new javafx.concurrent.Task<>() {
            @Override
            protected Integer call() throws Exception {
                return delegate.countTasksByProjectIdAndStatus(projectId, status);
            }
        };

        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> onError.accept(task.getException()));

        executor.submit(task);
    }

    public void countTasksByStatus(TaskStatus status, Consumer<Integer> onSuccess, Consumer<Throwable> onError) {
        javafx.concurrent.Task<Integer> task = new javafx.concurrent.Task() {
            @Override
            protected Integer call() throws Exception {
                return delegate.countTasksByStatus(status);
            }
        };

        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> onError.accept(task.getException()));

        executor.submit(task);
    }

    public void countTasksByProjectIdAndPriority(long projectId, TaskPriority priority, Consumer<Integer> onSuccess, Consumer<Throwable> onError) {
        javafx.concurrent.Task<Integer> task = new javafx.concurrent.Task<>() {
            @Override
            protected Integer call() throws Exception {
                return delegate.countTasksByProjectIdAndPriority(projectId, priority);
            }
        };

        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> onError.accept(task.getException()));

        executor.submit(task);
    }

    public void getProjectNameById(long id, Consumer<String> onSuccess, Consumer<Throwable> onError) {
        javafx.concurrent.Task<String> task = new javafx.concurrent.Task<>() {
            @Override
            protected String call() throws Exception {
                return delegate.getProjectNameById(id);
            }
        };

        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> onError.accept(task.getException()));

        executor.submit(task);
    }
}
