package org.example.taskschedulerdesktop.service;

import org.example.taskschedulerdesktop.dto.TaskForProjectExtendedPage;
import org.example.taskschedulerdesktop.repository.TaskRepository;

import java.util.List;

public class ProjectExtendedPageTaskService implements TaskService<TaskForProjectExtendedPage> {

    private final TaskRepository taskRepository;

    public ProjectExtendedPageTaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public List<TaskForProjectExtendedPage> findAll() {
        return taskRepository.findAll().stream()
                .map(task -> new TaskForProjectExtendedPage(task.getTaskName(),
                    task.getType(),
                    task.getDeadline(),
                    task.getExecutor(),
                    task.getStatus(),
                    task.getPriority(),
                    task.isSynced()))
                .toList();
    }
}
