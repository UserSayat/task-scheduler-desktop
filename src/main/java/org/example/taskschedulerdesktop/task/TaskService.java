package org.example.taskschedulerdesktop.task;


import java.util.List;
import java.util.UUID;

public interface TaskService {

    List<TaskView> findAllViews();

    List<TaskView> findViewsByProjectIdAndStatus(UUID projectId, TaskStatus status);

    TaskView findViewById(UUID id);

    List<TaskView> findUpcomingDeadlines(int maxDaysUntilDeadline);

    List<TaskView> findTopOpenViews(UUID projectId, int limit);

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
