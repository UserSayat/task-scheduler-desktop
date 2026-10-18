package org.example.taskschedulerdesktop.task;


import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Хранилище задач. Методы вызываются только внутри единицы работы (UnitOfWork).
 * Удалённые задачи (deleted_at) и задачи удалённых проектов в выборки не попадают.
 */
public interface TaskRepository {

    void insert(Task task) throws SQLException;

    /**
     * Сохраняет строку задачи и полностью заменяет её коллекции (исполнители, наблюдатели, теги).
     */
    void update(Task task) throws SQLException;

    Optional<Task> findById(UUID id) throws SQLException;

    List<TaskView> findAllViews() throws SQLException;

    Optional<TaskView> findViewById(UUID id) throws SQLException;

    List<TaskView> findViewsByProjectIdAndStatus(UUID projectId, TaskStatus status) throws SQLException;

    List<TaskView> findUpcomingDeadlines(int maxDaysUntilDeadline) throws SQLException;

    int countByProjectIdAndStatus(UUID projectId, TaskStatus status) throws SQLException;

    int countByStatus(TaskStatus status) throws SQLException;

    int countByProjectIdAndPriority(UUID projectId, TaskPriority priority) throws SQLException;
}
