package org.example.taskschedulerdesktop.task;


import java.util.List;
import java.util.UUID;

public interface TaskService {

    List<TaskView> findAllViews();

    List<TaskView> findViewsByProjectIdAndStatus(UUID projectId, TaskStatus status);

    TaskView findViewById(UUID id);

    List<TaskView> findUpcomingDeadlines(int maxDaysUntilDeadline);

    /** «Мои задачи»: незавершённые, где текущий пользователь ответственный или соисполнитель. */
    List<TaskView> findMyOpenViews();

    List<TaskView> findTopOpenViews(UUID projectId, int limit);

    TaskView create(NewTask newTask);

    /**
     * @return задача после изменения
     */
    TaskView update(TaskEdit edit);

    /**
     * «Взять в работу»: у задачи без исполнителей текущий пользователь становится ответственным;
     * статус меняется на «В работе», если задача была новой.
     */
    TaskView take(UUID id);

    /** «Присоединиться»: текущий пользователь становится соисполнителем. */
    TaskView join(UUID id);

    /**
     * «Отказаться»: текущий пользователь уходит из исполнителей. Если он был ответственным,
     * задача возвращается в «Новая».
     */
    TaskView leave(UUID id);

    /** Дата начала, соисполнители и наблюдатели. */
    TaskExtras findExtras(UUID id);

    void delete(UUID id);

    int countByProjectIdAndStatus(UUID projectId, TaskStatus status);

    int countByStatus(TaskStatus status);

    int countByProjectIdAndPriority(UUID projectId, TaskPriority priority);
}
