package org.example.taskschedulerdesktop.service.project;

import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.scene.Node;
import org.example.taskschedulerdesktop.models.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
                        List<Project> projects = delegate.findAll();

                        return projectCardService.createCards(projects);
                    }
                };
            }
        };

        service.setExecutor(this.executor);
        return service;
    }

}
