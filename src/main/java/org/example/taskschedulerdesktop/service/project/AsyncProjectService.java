package org.example.taskschedulerdesktop.service.project;

import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.scene.Node;
import org.example.taskschedulerdesktop.listeners.ChangeType;
import org.example.taskschedulerdesktop.listeners.EventBus;
import org.example.taskschedulerdesktop.listeners.ProjectChangedEvent;
import org.example.taskschedulerdesktop.models.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

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
                return new Task<>(){
                    @Override
                    protected List<Node> call() throws Exception {
                        log.debug("createProjectLoader()");

                        List<Project> projects = delegate.findAll();

                        if (projects.isEmpty()) {
                            return null;
                        }

                        log.debug("Project: {}, number of tasks = {}", projects.getFirst(), projects.getFirst().getNumberOfTasks());
                        return projectCardService.createCards(projects);
                    }
                };
            }
        };

        service.setExecutor(this.executor);
        return service;
    }

    public void saveProject(Project project, Runnable onSuccess, Consumer<Throwable> onError) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                delegate.save(project);
                log.debug("Project saved: {}", project.getName());
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            EventBus.getInstance().fire(new ProjectChangedEvent(project.getId(), ChangeType.DELETED));
            onSuccess.run();
        });
        task.setOnFailed(event -> onError.accept(task.getException()));

        executor.submit(task);
    }

    public void updateProject(Project project, Runnable onSuccess, Consumer<Throwable> onError) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                delegate.update(project);
                log.debug("Project updated: {}", project.getName());
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            EventBus.getInstance().fire(new ProjectChangedEvent(project.getId(), ChangeType.UPDATED));
            onSuccess.run();
        });
        task.setOnFailed(event -> onError.accept(task.getException()));

        executor.submit(task);
    }

    public void deleteProject(long projectId, Runnable onSuccess, Consumer<Throwable> onError) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                delegate.delete(projectId);
                log.debug("Project deleted id = {}", projectId);
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            //TODO В будущем при обновлении проекта обновлять только карточку проекта
            EventBus.getInstance().fire(new ProjectChangedEvent(projectId, ChangeType.DELETED));
            onSuccess.run();
        });
        task.setOnFailed(event -> onError.accept(task.getException()));

        executor.submit(task);
    }

    public void findProjectById(long projectId, Consumer<Project> onSuccess, Consumer<Throwable> onError) {
        Task<Project> task = new Task<>() {
            @Override
            protected Project call() throws Exception {
                Project project = delegate.findById(projectId);
                if (project == null) {
                    log.info("Project not found id = {}", projectId);
                }
                return project;            }
        };

        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> onError.accept(task.getException()));

        executor.submit(task);
    }

    public void findAllProjects(Consumer<List<Project>> onSuccess, Consumer<Throwable> onError) {
        Task<List<Project>> task = new Task<>() {
            @Override
            protected List<Project> call() throws Exception {
                List<Project> projects = delegate.findAll();
                log.debug("Found {} projects", projects.size());
                return projects;
            }
        };

        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> onError.accept(task.getException()));

        executor.submit(task);
    }
}
