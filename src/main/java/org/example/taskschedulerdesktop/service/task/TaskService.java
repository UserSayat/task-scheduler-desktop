package org.example.taskschedulerdesktop.service.task;

import org.example.taskschedulerdesktop.models.Task;
import org.example.taskschedulerdesktop.utils.TaskStatus;

import java.util.List;

public interface TaskService {
    List<Task> findAll();
    List<Task> findByStatus(TaskStatus status);
    Task findById(long id);
    void save(Task task);
    void update(Task task);
    void delete(long id);
    int countByProjectNameAndStatus(String projectName, TaskStatus status);
}
