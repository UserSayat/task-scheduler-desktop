package org.example.taskschedulerdesktop.service.project;

import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.exeptions.NotFoundException;
import org.example.taskschedulerdesktop.listeners.EventBus;
import org.example.taskschedulerdesktop.listeners.ProjectChangedEvent;
import org.example.taskschedulerdesktop.listeners.TaskChangedEvent;
import org.example.taskschedulerdesktop.models.Project;
import org.example.taskschedulerdesktop.repository.project.ProjectRepository;
import org.example.taskschedulerdesktop.service.task.TaskService;
import org.example.taskschedulerdesktop.utils.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ProjectServiceImpl implements ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectServiceImpl.class);
    private final ProjectRepository projectRepository;
    private final TaskService taskService;

    public ProjectServiceImpl(ProjectRepository projectRepository, TaskService taskService) {
        log.debug("ProjectServiceImpl created");

        this.projectRepository = projectRepository;
        this.taskService = taskService;

        EventBus.getInstance().subscribe(TaskChangedEvent.class, event -> {

            log.debug("TaskChangedEvent received: projectId={}", event.getProjectId());

            try {
                Project project = projectRepository.findById(event.getProjectId())
                        .orElseThrow(() -> new NotFoundException(""));

                log.debug("Project found: {}", project.getName());
                update(project);
                log.debug("Project updated: {}", project.getName());

            } catch (Exception e) {
                log.error("Error updating project ", e);
            }
        });

        log.debug("Subscribe on TaskChangedEvent completed");
    }

    @Override
    public List<Project> findAll() {
        return projectRepository.findAll();
    }

    @Override
    public void save(Project project) {
        log.debug("save({})", project);

        projectRepository.save(project);
    }

    @Override
    public void update(Project project) {
        log.debug("update({})", project);

        int numberOfTasks = countTasksByProjectId(project.getId());
        int completedTasks = countTasksByProjectIdAndStatus(project.getId(), TaskStatus.COMPLETED);
        int remainingTasks = numberOfTasks - completedTasks;
        int percentOfCompletion = numberOfTasks > 0 ? (completedTasks * 100 / numberOfTasks) : 0;

        project.setNumberOfTasks(numberOfTasks);
        project.setCompletedTasks(completedTasks);
        project.setRemainingTasks(remainingTasks);
        project.setPercentOfCompletion(percentOfCompletion);

        log.debug("Project: {}, numberOfTasks = {}, completedTasks = {}",
                project.getName(), project.getNumberOfTasks(), project.getCompletedTasks());

        projectRepository.update(project);
    }

    @Override
    public void delete(long id) {
        projectRepository.delete(id);
    }

    @Override
    public Project findById(long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Project not found"));
    }

    @Override
    public int countTasksByProjectIdAndStatus(long projectId, TaskStatus status) {
        return taskService.countTasksByProjectIdAndStatus(projectId, status);
    }

    @Override
    public int countTasksByProjectId(long projectId) {
        return taskService.countTasksByProjectId(projectId);
    }
}
