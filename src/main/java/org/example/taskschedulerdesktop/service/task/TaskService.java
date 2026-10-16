package org.example.taskschedulerdesktop.service.task;

import org.example.taskschedulerdesktop.dto.tasks.TaskView;
import org.example.taskschedulerdesktop.utils.TaskPriority;
import org.example.taskschedulerdesktop.utils.TaskStatus;

import java.util.List;
import java.util.UUID;

public interface TaskService {

    List<TaskView> findAllViews();

    List<TaskView> findViewsByProjectIdAndStatus(UUID projectId, TaskStatus status);

    TaskView findViewById(UUID id);

    List<TaskView> findUpcomingDeadlines(int maxDaysUntilDeadline);

    TaskView create(NewTask newTask);

    /**
     * @return задача после изменения
     */
    TaskView update(TaskEdit edit);

    void delete(UUID id);

    int countByProjectIdAndStatus(UUID projectId, TaskStatus status);

    int countByStatus(TaskStatus status);

    int countByProjectIdAndPriority(UUID projectId, TaskPriority priority);
}
