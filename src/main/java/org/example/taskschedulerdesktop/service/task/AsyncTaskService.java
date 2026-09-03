package org.example.taskschedulerdesktop.service.task;

import org.example.taskschedulerdesktop.models.Task;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class AsyncTaskService {

    private final TaskService delegate;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public AsyncTaskService(TaskService delegate) {
        this.delegate = delegate;
    }

    public void findAll(Consumer<List<Task>> onSuccess, Consumer<Throwable> onError) {
        executor.submit(() -> {
            try {
                List<Task> tasks = delegate.findAll();
                onSuccess.accept(tasks);
            } catch (Exception e) {
                onError.accept(e);
            }
        });
    }

    public void findInProgress(Consumer<List<Task>> onSuccess, Consumer<Throwable> onError) {
        executor.submit(() -> {
            try {
                List<Task> tasks = delegate.findByStatus("InProgress");
                onSuccess.accept(tasks);
            } catch (Exception e) {
                onError.accept(e);
            }
        });
    }

    public void findUnderReview(Consumer<List<Task>> onSuccess, Consumer<Throwable> onError) {
        executor.submit(() -> {
            try {
                List<Task> tasks = delegate.findByStatus("UnderReview");
                onSuccess.accept(tasks);
            } catch (Exception e) {
                onError.accept(e);
            }
        });
    }

    public void findCompletedTasks(Consumer<List<Task>> onSuccess, Consumer<Throwable> onError) {
        executor.submit(() -> {
            try {
                List<Task> tasks = delegate.findByStatus("Completed");
                onSuccess.accept(tasks);
            } catch (Exception e) {
                onError.accept(e);
            }
        });
    }

    public void findById(int id, Consumer<Task> onSuccess, Consumer<Throwable> onError) {
        executor.submit(() -> {
            try {
                Task task = delegate.findById(id);
                onSuccess.accept(task);
            } catch (Exception e) {
                onError.accept(e);
            }
        });
    }

    public void save(Task task, Runnable onSuccess, Consumer<Throwable> onError) {
        executor.submit(() -> {
            try {
                delegate.save(task);
                onSuccess.run();
            } catch (Exception e) {
                onError.accept(e);
            }
        });
    }

    public void update(Task task, Runnable onSuccess, Consumer<Throwable> onError) {
        executor.submit(() -> {
            try {
                delegate.update(task);
                onSuccess.run();
            } catch (Exception e) {
                onError.accept(e);
            }
        });
    }

    public void delete(int id, Runnable onSuccess, Consumer<Throwable> onError) {
        executor.submit(() -> {
            try {
                delegate.delete(id);
                onSuccess.run();
            } catch (Exception e) {
                onError.accept(e);
            }
        });
    }
}
