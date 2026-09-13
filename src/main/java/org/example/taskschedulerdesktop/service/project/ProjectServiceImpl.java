package org.example.taskschedulerdesktop.service.project;

import org.example.taskschedulerdesktop.models.Project;
import org.example.taskschedulerdesktop.repository.project.ProjectRepository;
import org.example.taskschedulerdesktop.service.task.TaskService;
import org.example.taskschedulerdesktop.utils.TaskStatus;

import java.util.List;

public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final TaskService taskService;

    public ProjectServiceImpl(ProjectRepository projectRepository, TaskService taskService) {
        this.projectRepository = projectRepository;
        this.taskService = taskService;
    }

    @Override
    public List<Project> findAll() {
        return projectRepository.findAll();
    }

    @Override
    public void save(Project project) {
        projectRepository.save(project);
    }

    @Override
    public void update(Project project) {
        projectRepository.update(project);
    }

    @Override
    public void delete(long id) {
        projectRepository.delete(id);
    }

    @Override
    public Project findById(long id) {
        return projectRepository.findById(id);
    }

    @Override
    public int countTasksByProjectNameAndStatus(String projectName, TaskStatus status) {
        return taskService.countTasksByProjectNameAndStatus(projectName, status);
    }

    @Override
    public int countTasksByProjectName(String projectName) {
        return taskService.countTasksByProjectName(projectName);
    }
}
