package org.example.taskschedulerdesktop.service.task;

import org.example.taskschedulerdesktop.models.Task;
import org.example.taskschedulerdesktop.utils.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class TasksLoaderService {

    private static final Logger log = LoggerFactory.getLogger(TasksLoaderService.class);

    private final TaskService delegate;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public TasksLoaderService(TaskService delegate) {
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

    public void findNewTasks(Consumer<List<Task>> onSuccess, Consumer<Throwable> onError) {
        log.debug("findNewTasks: request to db...");
        executor.submit(() -> {
            try {
                List<Task> tasks = delegate.findByStatus(TaskStatus.NEW);
                log.debug("findNewTasks: {} tasks found", tasks.size());
                onSuccess.accept(tasks);
            } catch (Exception e) {
                log.error("findNewTasks: ", e);
                onError.accept(e);
            }
        });
    }

    public void findInProgress(Consumer<List<Task>> onSuccess, Consumer<Throwable> onError) {
        executor.submit(() -> {
            try {
                List<Task> tasks = delegate.findByStatus(TaskStatus.IN_PROGRESS);
                onSuccess.accept(tasks);
            } catch (Exception e) {
                onError.accept(e);
            }
        });
    }

    public void findUnderReview(Consumer<List<Task>> onSuccess, Consumer<Throwable> onError) {
        executor.submit(() -> {
            try {
                List<Task> tasks = delegate.findByStatus(TaskStatus.UNDER_REVIEW);
                onSuccess.accept(tasks);
            } catch (Exception e) {
                onError.accept(e);
            }
        });
    }

    public void findCompletedTasks(Consumer<List<Task>> onSuccess, Consumer<Throwable> onError) {
        executor.submit(() -> {
            try {
                List<Task> tasks = delegate.findByStatus(TaskStatus.COMPLETED);
                onSuccess.accept(tasks);
            } catch (Exception e) {
                onError.accept(e);
            }
        });
    }

    public void findById(long id, Consumer<Task> onSuccess, Consumer<Throwable> onError) {
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

    public void delete(long id, Runnable onSuccess, Consumer<Throwable> onError) {
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
