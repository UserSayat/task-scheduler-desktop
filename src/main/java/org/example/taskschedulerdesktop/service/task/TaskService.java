package org.example.taskschedulerdesktop.service.task;

import org.example.taskschedulerdesktop.dto.tasks.TaskView;
import org.example.taskschedulerdesktop.models.Task;
import org.example.taskschedulerdesktop.utils.TaskPriority;
import org.example.taskschedulerdesktop.utils.TaskStatus;

import java.util.List;

public interface TaskService {
    List<TaskView> findAllViews();
    List<TaskView> findViewByProjectIdAndStatus(long projectId, TaskStatus status);
    TaskView findViewById(long id);
    void save(Task task);
    void update(Task task);
    void delete(long id);
    int countTasksByProjectIdAndStatus(long projectId, TaskStatus status);
    int countTasksByStatus(TaskStatus status);
    int countTasksByProjectIdAndPriority(long projectId, TaskPriority priority);
    int countTasksByProjectId(long projectId);
    String getProjectNameById(long id);
}

