package org.example.taskschedulerdesktop.repository.task;

import com.github.f4b6a3.uuid.UuidCreator;
import org.example.taskschedulerdesktop.database.Jdbc;
import org.example.taskschedulerdesktop.database.UnitOfWork;
import org.example.taskschedulerdesktop.dto.tasks.TaskView;
import org.example.taskschedulerdesktop.models.Task;
import org.example.taskschedulerdesktop.models.TaskAssignee;
import org.example.taskschedulerdesktop.utils.AssigneeRole;
import org.example.taskschedulerdesktop.utils.TaskPriority;
import org.example.taskschedulerdesktop.utils.TaskStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class H2TaskRepository implements TaskRepository {

    /**
     * Общая часть выборок для экранов: задача + проект + ответственный + теги + признак синхронизации.
     */
    private static final String VIEW_SELECT = """
            SELECT t.id, t.title, t.description, t.project_id, p.name AS project_name,
                   t.status, t.priority, t.deadline,
                   u.id AS responsible_id, u.name AS responsible_name,
                   (SELECT LISTAGG(tg.name, ',') WITHIN GROUP (ORDER BY tg.name)
                      FROM task_tags tt JOIN tags tg ON tg.id = tt.tag_id
                     WHERE tt.task_id = t.id AND tg.deleted_at IS NULL) AS tag_names,
                   EXISTS (SELECT 1 FROM sync_log s
                            WHERE s.entity_id = t.id AND s.synced = FALSE) AS has_unsynced
              FROM tasks t
              JOIN projects p ON p.id = t.project_id
              LEFT JOIN task_assignees a ON a.task_id = t.id AND a.role = 'RESPONSIBLE'
              LEFT JOIN users u ON u.id = a.user_id
             WHERE t.deleted_at IS NULL AND p.deleted_at IS NULL
            """;

    private static final String ACTIVE_TASKS = """
            FROM tasks t JOIN projects p ON p.id = t.project_id
            WHERE t.deleted_at IS NULL AND p.deleted_at IS NULL
            """;

    private final UnitOfWork uow;

    public H2TaskRepository(UnitOfWork uow) {
        this.uow = uow;
    }

    // ===== ЗАПИСЬ =====

    @Override
    public void insert(Task task) throws SQLException {
        String sql = """
                INSERT INTO tasks (id, title, description, creator_id, project_id, status, priority,
                                   start_date, deadline, closed_at, created_at, updated_at,
                                   created_by, updated_by, deleted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            Jdbc.setUuid(ps, 1, task.getId());
            ps.setString(2, task.getTitle());
            ps.setString(3, task.getDescription());
            Jdbc.setUuid(ps, 4, task.getCreatorId());
            Jdbc.setUuid(ps, 5, task.getProjectId());
            ps.setString(6, task.getStatus().name());
            ps.setString(7, task.getPriority().name());
            Jdbc.setDate(ps, 8, task.getStartDate());
            Jdbc.setDate(ps, 9, task.getDeadline());
            Jdbc.setInstant(ps, 10, task.getClosedAt());
            Jdbc.setInstant(ps, 11, task.getCreatedAt());
            Jdbc.setInstant(ps, 12, task.getUpdatedAt());
            Jdbc.setUuid(ps, 13, task.getCreatedBy());
            Jdbc.setUuid(ps, 14, task.getUpdatedBy());
            Jdbc.setInstant(ps, 15, task.getDeletedAt());
            ps.executeUpdate();
        }
        replaceCollections(task);
    }

    @Override
    public void update(Task task) throws SQLException {
        String sql = """
                UPDATE tasks SET title = ?, description = ?, project_id = ?, status = ?, priority = ?,
                                 start_date = ?, deadline = ?, closed_at = ?, updated_at = ?,
                                 updated_by = ?, deleted_at = ?
                 WHERE id = ?
                """;
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            ps.setString(1, task.getTitle());
            ps.setString(2, task.getDescription());
            Jdbc.setUuid(ps, 3, task.getProjectId());
            ps.setString(4, task.getStatus().name());
            ps.setString(5, task.getPriority().name());
            Jdbc.setDate(ps, 6, task.getStartDate());
            Jdbc.setDate(ps, 7, task.getDeadline());
            Jdbc.setInstant(ps, 8, task.getClosedAt());
            Jdbc.setInstant(ps, 9, task.getUpdatedAt());
            Jdbc.setUuid(ps, 10, task.getUpdatedBy());
            Jdbc.setInstant(ps, 11, task.getDeletedAt());
            Jdbc.setUuid(ps, 12, task.getId());
            ps.executeUpdate();
        }
        replaceCollections(task);
    }

    private void replaceCollections(Task task) throws SQLException {
        Connection c = uow.connection();
        Instant now = task.getUpdatedAt();

        deleteByTaskId("task_assignees", task.getId());
        String assigneeSql = """
                INSERT INTO task_assignees (id, task_id, user_id, role, assigned_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = c.prepareStatement(assigneeSql)) {
            for (TaskAssignee assignee : task.getAssignees()) {
                Jdbc.setUuid(ps, 1, UuidCreator.getTimeOrderedEpoch());
                Jdbc.setUuid(ps, 2, task.getId());
                Jdbc.setUuid(ps, 3, assignee.userId());
                ps.setString(4, assignee.role().name());
                Jdbc.setInstant(ps, 5, now);
                Jdbc.setInstant(ps, 6, now);
                Jdbc.setInstant(ps, 7, now);
                ps.addBatch();
            }
            ps.executeBatch();
        }

        deleteByTaskId("task_observers", task.getId());
        String observerSql = """
                INSERT INTO task_observers (id, task_id, user_id, added_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = c.prepareStatement(observerSql)) {
            for (UUID userId : task.getObserverIds()) {
                Jdbc.setUuid(ps, 1, UuidCreator.getTimeOrderedEpoch());
                Jdbc.setUuid(ps, 2, task.getId());
                Jdbc.setUuid(ps, 3, userId);
                Jdbc.setInstant(ps, 4, now);
                Jdbc.setInstant(ps, 5, now);
                Jdbc.setInstant(ps, 6, now);
                ps.addBatch();
            }
            ps.executeBatch();
        }

        deleteByTaskId("task_tags", task.getId());
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO task_tags (task_id, tag_id) VALUES (?, ?)")) {
            for (UUID tagId : task.getTagIds()) {
                Jdbc.setUuid(ps, 1, task.getId());
                Jdbc.setUuid(ps, 2, tagId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void deleteByTaskId(String table, UUID taskId) throws SQLException {
        try (PreparedStatement ps = uow.connection().prepareStatement("DELETE FROM " + table + " WHERE task_id = ?")) {
            Jdbc.setUuid(ps, 1, taskId);
            ps.executeUpdate();
        }
    }

    // ===== ЧТЕНИЕ СУЩНОСТИ =====

    @Override
    public Optional<Task> findById(UUID id) throws SQLException {
        Task task;
        try (PreparedStatement ps = uow.connection().prepareStatement("SELECT * FROM tasks WHERE id = ?")) {
            Jdbc.setUuid(ps, 1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                task = mapTask(rs);
            }
        }

        task.setAssignees(findAssignees(id));
        task.setObserverIds(findIds("SELECT user_id FROM task_observers WHERE task_id = ?", id));
        task.setTagIds(findIds("SELECT tag_id FROM task_tags WHERE task_id = ?", id));
        return Optional.of(task);
    }

    private List<TaskAssignee> findAssignees(UUID taskId) throws SQLException {
        List<TaskAssignee> assignees = new ArrayList<>();
        String sql = "SELECT user_id, role FROM task_assignees WHERE task_id = ? ORDER BY assigned_at";
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            Jdbc.setUuid(ps, 1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    assignees.add(new TaskAssignee(Jdbc.getUuid(rs, "user_id"),
                            Jdbc.getEnum(rs, "role", AssigneeRole.class)));
                }
            }
        }
        return assignees;
    }

    private List<UUID> findIds(String sql, UUID taskId) throws SQLException {
        List<UUID> ids = new ArrayList<>();
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            Jdbc.setUuid(ps, 1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getObject(1, UUID.class));
                }
            }
        }
        return ids;
    }

    private Task mapTask(ResultSet rs) throws SQLException {
        Task task = new Task();
        task.setId(Jdbc.getUuid(rs, "id"));
        task.setTitle(rs.getString("title"));
        task.setDescription(rs.getString("description"));
        task.setCreatorId(Jdbc.getUuid(rs, "creator_id"));
        task.setProjectId(Jdbc.getUuid(rs, "project_id"));
        task.setStatus(Jdbc.getEnum(rs, "status", TaskStatus.class));
        task.setPriority(Jdbc.getEnum(rs, "priority", TaskPriority.class));
        task.setStartDate(Jdbc.getDate(rs, "start_date"));
        task.setDeadline(Jdbc.getDate(rs, "deadline"));
        task.setClosedAt(Jdbc.getInstant(rs, "closed_at"));
        task.setCreatedAt(Jdbc.getInstant(rs, "created_at"));
        task.setUpdatedAt(Jdbc.getInstant(rs, "updated_at"));
        task.setCreatedBy(Jdbc.getUuid(rs, "created_by"));
        task.setUpdatedBy(Jdbc.getUuid(rs, "updated_by"));
        task.setDeletedAt(Jdbc.getInstant(rs, "deleted_at"));
        return task;
    }

    // ===== ЧТЕНИЕ ДЛЯ ЭКРАНОВ =====

    @Override
    public List<TaskView> findAllViews() throws SQLException {
        return queryViews(VIEW_SELECT + " ORDER BY t.deadline NULLS LAST, t.created_at");
    }

    @Override
    public Optional<TaskView> findViewById(UUID id) throws SQLException {
        return queryViews(VIEW_SELECT + " AND t.id = ?", id).stream().findFirst();
    }

    @Override
    public List<TaskView> findViewsByProjectIdAndStatus(UUID projectId, TaskStatus status) throws SQLException {
        return queryViews(VIEW_SELECT + " AND t.project_id = ? AND t.status = ? ORDER BY t.deadline NULLS LAST, t.created_at",
                projectId, status.name());
    }

    @Override
    public List<TaskView> findUpcomingDeadlines(int maxDaysUntilDeadline) throws SQLException {
        return queryViews(VIEW_SELECT + """
                 AND t.deadline IS NOT NULL
                 AND t.status <> 'DONE'
                 AND t.deadline < DATEADD('DAY', ?, CURRENT_DATE)
                 ORDER BY t.deadline
                """, maxDaysUntilDeadline);
    }

    private List<TaskView> queryViews(String sql, Object... params) throws SQLException {
        List<TaskView> views = new ArrayList<>();
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    views.add(mapView(rs));
                }
            }
        }
        return views;
    }

    private TaskView mapView(ResultSet rs) throws SQLException {
        String tagNames = rs.getString("tag_names");
        return new TaskView(
                Jdbc.getUuid(rs, "id"),
                rs.getString("title"),
                rs.getString("description"),
                Jdbc.getUuid(rs, "project_id"),
                rs.getString("project_name"),
                Jdbc.getUuid(rs, "responsible_id"),
                rs.getString("responsible_name"),
                Jdbc.getEnum(rs, "status", TaskStatus.class),
                Jdbc.getEnum(rs, "priority", TaskPriority.class),
                Jdbc.getDate(rs, "deadline"),
                tagNames == null ? List.of() : Arrays.asList(tagNames.split(",")),
                !rs.getBoolean("has_unsynced"));
    }

    // ===== СТАТИСТИКА =====

    @Override
    public int countByProjectIdAndStatus(UUID projectId, TaskStatus status) throws SQLException {
        return count("SELECT COUNT(*) " + ACTIVE_TASKS + " AND t.project_id = ? AND t.status = ?",
                projectId, status.name());
    }

    @Override
    public int countByStatus(TaskStatus status) throws SQLException {
        return count("SELECT COUNT(*) " + ACTIVE_TASKS + " AND t.status = ?", status.name());
    }

    @Override
    public int countByProjectIdAndPriority(UUID projectId, TaskPriority priority) throws SQLException {
        return count("SELECT COUNT(*) " + ACTIVE_TASKS + " AND t.project_id = ? AND t.priority = ?",
                projectId, priority.name());
    }

    private int count(String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
