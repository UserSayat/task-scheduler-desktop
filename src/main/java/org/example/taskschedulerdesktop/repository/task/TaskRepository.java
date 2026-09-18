package org.example.taskschedulerdesktop.repository.task;

import org.example.taskschedulerdesktop.dto.TaskView;
import org.example.taskschedulerdesktop.models.Task;
import org.example.taskschedulerdesktop.utils.TaskStatus;

import java.util.List;
import java.util.Optional;

public interface TaskRepository {

    void save(Task task);
    void update(Task task);
    void delete(long id);
    Optional<Task> findById(long id);
    List<TaskView> findAllViews();
    Optional<TaskView> findViewById(long id);
    List<TaskView> findViewsByStatus(TaskStatus status);
    List<Task> findUnsynced();
    void markAsSynced(long id);
    int countByProjectNameAndStatus(String projectName, TaskStatus status);
    int countByProjectName(String projectName);
}
