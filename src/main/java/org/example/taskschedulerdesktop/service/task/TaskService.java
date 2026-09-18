package org.example.taskschedulerdesktop.service.task;

import org.example.taskschedulerdesktop.dto.TaskView;
import org.example.taskschedulerdesktop.models.Task;
import org.example.taskschedulerdesktop.utils.TaskStatus;

import java.util.List;

public interface TaskService {
    List<TaskView> findAllViews();
    List<TaskView> findViewByStatus(TaskStatus status);
    TaskView findViewById(long id);
    void save(Task task);
    void update(Task task);
    void delete(long id);
    int countTasksByProjectNameAndStatus(String projectName, TaskStatus status);
    int countTasksByProjectName(String projectName);
    String getProjectNameById(long id);
}

