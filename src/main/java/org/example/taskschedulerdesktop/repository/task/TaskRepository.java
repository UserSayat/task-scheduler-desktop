package org.example.taskschedulerdesktop.repository.task;

import org.example.taskschedulerdesktop.models.Task;
import org.example.taskschedulerdesktop.utils.TaskStatus;

import java.util.List;

public interface TaskRepository {

    List<Task> findAll();
    void save(Task task);
    void update(Task task);
    void delete(long id);
    Task findById(long id);
    List<Task> findByStatus(TaskStatus status);
    List<Task> findUnsynced();
    void markAsSynced(long id);
    int countByProjectNameAndStatus(String projectName, TaskStatus status);
    int countByProjectName(String projectName);
}
