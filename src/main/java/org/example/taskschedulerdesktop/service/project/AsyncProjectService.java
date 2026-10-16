package org.example.taskschedulerdesktop.service.project;

import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.scene.Node;
import org.example.taskschedulerdesktop.dto.projects.ProjectView;
import org.example.taskschedulerdesktop.listeners.ChangeType;
import org.example.taskschedulerdesktop.listeners.EventBus;
import org.example.taskschedulerdesktop.listeners.ProjectChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Вызовы {@link ProjectService} в фоне. Колбэки выполняются в UI-потоке.
 */
public class AsyncProjectService {

    private static final Logger log = LoggerFactory.getLogger(AsyncProjectService.class);

    private final ProjectService delegate;
    private final ProjectCardService projectCardService;

    private final ExecutorService executor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    });

    public AsyncProjectService(ProjectService delegate, ProjectCardService projectCardService) {
        this.delegate = delegate;
        this.projectCardService = projectCardService;
    }

    public Service<List<Node>> createProjectsLoader() {
        Service<List<Node>> service = new Service<>() {
            @Override
            protected Task<List<Node>> createTask() {
                return new Task<>() {
                    @Override
                    protected List<Node> call() {
                        List<ProjectView> projects = delegate.findAll();
                        return projects.isEmpty() ? null : projectCardService.createCards(projects);
                    }
                };
            }
        };

        service.setExecutor(this.executor);
        return service;
    }

    public void saveProject(String name, UUID ownerId, Consumer<ProjectView> onSuccess, Consumer<Throwable> onError) {
        submit(() -> delegate.create(name, ownerId), created -> {
            EventBus.getInstance().fire(new ProjectChangedEvent(created.id(), ChangeType.CREATED));
            onSuccess.accept(created);
        }, onError);
    }

    public void updateProject(UUID projectId, String name, UUID ownerId, Consumer<ProjectView> onSuccess, Consumer<Throwable> onError) {
        submit(() -> delegate.update(projectId, name, ownerId), updated -> {
            EventBus.getInstance().fire(new ProjectChangedEvent(projectId, ChangeType.UPDATED));
            onSuccess.accept(updated);
        }, onError);
    }

    public void deleteProject(UUID projectId, Runnable onSuccess, Consumer<Throwable> onError) {
        submit(() -> {
            delegate.delete(projectId);
            return null;
        }, ignored -> {
            EventBus.getInstance().fire(new ProjectChangedEvent(projectId, ChangeType.DELETED));
            onSuccess.run();
        }, onError);
    }

    public void findProjectById(UUID projectId, Consumer<ProjectView> onSuccess, Consumer<Throwable> onError) {
        submit(() -> delegate.findById(projectId), onSuccess, onError);
    }

    public void findAllProjects(Consumer<List<ProjectView>> onSuccess, Consumer<Throwable> onError) {
        submit(delegate::findAll, onSuccess, onError);
    }

    private <T> void submit(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
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
