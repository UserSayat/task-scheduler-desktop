package org.example.taskschedulerdesktop.repository.task;

import org.example.taskschedulerdesktop.database.DatabaseConnection;
import org.example.taskschedulerdesktop.dto.tasks.TaskView;
import org.example.taskschedulerdesktop.models.Task;
import org.example.taskschedulerdesktop.utils.TaskPriority;
import org.example.taskschedulerdesktop.utils.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class H2TaskRepository implements TaskRepository {

    private final DatabaseConnection db;
    private static final Logger log = LoggerFactory.getLogger(H2TaskRepository.class);

    public H2TaskRepository(DatabaseConnection db) {
        this.db = db;
        createTableIfNotExists();
        createIndexes();
    }

    // ===== СОЗДАНИЕ ТАБЛИЦЫ =====

    private void createTableIfNotExists() {
        log.debug("createTableIfNotExists()");

        String sql = """
            CREATE TABLE IF NOT EXISTS tasks (
                id INT PRIMARY KEY AUTO_INCREMENT,
                name VARCHAR(255) NOT NULL,
                project_id INT NOT NULL,
                executor VARCHAR(255) NOT NULL,
                type VARCHAR(255) NOT NULL,
                status VARCHAR(50),
                priority VARCHAR(50),
                deadline VARCHAR(50),
                description TEXT,
                synced BOOLEAN DEFAULT FALSE
            )
        """;

        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            log.error("Error creating the task table");
        }
    }

    // ===== CRUD МЕТОДЫ =====

    @Override
    public void save(Task task) {
        log.debug("save({})", task);

        String sql = """
            INSERT INTO tasks (name, project_id, executor, type, status,
             priority, deadline, description, synced) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, task.getTaskName());
            pstmt.setLong(2, task.getProjectId());
            pstmt.setString(3, task.getExecutor());
            pstmt.setString(4, task.getType());
            pstmt.setString(5, task.getStatus().name());
            pstmt.setString(6, task.getPriority().name());
            pstmt.setObject(7, task.getDeadline(), Types.DATE);
            pstmt.setString(8, task.getDescription());
            pstmt.setBoolean(9, task.isSynced());
            pstmt.executeUpdate();

            ResultSet generatedKeys = pstmt.getGeneratedKeys();
            if (generatedKeys.next()) {
                task.setId(generatedKeys.getLong(1));
            }

            log.debug("Task saved: id = {}", task.getId());
        } catch (SQLException e) {
            log.error("Error saving task {}", task);
        }
    }

    @Override
    public void update(Task task) {
        log.debug("update({})", task);

        String sql = """
            UPDATE tasks SET name = ?, project_id = ?, executor = ?, type = ?, status = ?,
             priority = ?, deadline = ?, description = ?, synced = ? WHERE id = ?
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, task.getTaskName());
            pstmt.setLong(2, task.getProjectId());
            pstmt.setString(3, task.getExecutor());
            pstmt.setString(4, task.getType());
            pstmt.setString(5, task.getStatus().name());
            pstmt.setString(6, task.getPriority().name());
            pstmt.setString(7, task.getDeadline().toString());
            pstmt.setString(8, task.getDescription());
            pstmt.setBoolean(9, task.isSynced());
            pstmt.setLong(10, task.getId());
            pstmt.executeUpdate();

            log.debug("Task updated: id = {}", task.getId());

        } catch (SQLException e) {
            log.error("Error updating task: id = {}", task.getId());
        }
    }

    @Override
    public void delete(long id) {
        log.debug("delete({})", id);

        String sql = "DELETE FROM tasks WHERE id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, id);
            pstmt.executeUpdate();

            log.debug("Task deleted: id = {}", id);
        } catch (SQLException e) {
            log.error("Error deleting task: id = {}", id);
        }
    }

    @Override
    public Optional<Task> findById(long id) {
        log.debug("findById({})", id);

        String sql = """
            SELECT t.id, t.name, t.project_id, t.executor, t.type, t.status, t.priority,
             t.deadline, t.description, t.synced FROM tasks t WHERE t.id = ?
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                log.debug("Task found: name = {}", rs.getString("name"));
                return Optional.of(mapRowToTask(rs));
            }
            log.debug("Task not found");
            return Optional.empty();

        } catch (SQLException e) {
            log.error("Error finding task: id = {}", id);
            return Optional.empty();
        }
    }

    @Override
    public List<TaskView> findAllViews() {
        log.debug("findAllViews()");
        String sql = """
            SELECT t.id, t.name, t.project_id, p.name AS project_name, t.executor, t.type, t.status, t.priority,
             t.deadline, t.description, t.synced FROM tasks t LEFT JOIN projects p ON t.project_id = p.id ORDER BY t.deadline
        """;
        List<TaskView> views = new ArrayList<>();

        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                views.add(mapRowToTaskView(rs));
            }

            log.debug("Found {} views", views.size());
        } catch (SQLException e) {
            log.error("Error loading tasks");
        }
        return views;
    }

    @Override
    public Optional<TaskView> findViewById(long id) {
        log.debug("findViewById({})", id);
        String sql = """
            SELECT t.id, t.name, t.project_id, p.name AS project_name, t.executor, t.type, t.status, t.priority,
             t.deadline, t.description, t.synced FROM tasks t LEFT JOIN projects p ON t.project_id = p.id WHERE t.id = ?
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                log.debug("View found: name = {}", rs.getString("name"));
                return Optional.of(mapRowToTaskView(rs));
            }
            log.debug("View not found");
            return Optional.empty();

        } catch (SQLException e) {
            log.error("Error finding view: id = {}", id);
            return Optional.empty();
        }
    }

    @Override
    public List<TaskView> findViewsByProjectIdAndStatus(long projectId, TaskStatus status) {
        log.debug("findViewsByProjectIdAndStatus({}, {})", projectId, status);

        String sql = """
            SELECT t.id, t.name, t.project_id, p.name AS project_name, t.executor, t.type, t.status, t.priority,
             t.deadline, t.description, t.synced FROM tasks t LEFT JOIN projects p ON t.project_id = p.id
             WHERE t.project_id = ? AND t.status = ? ORDER BY t.deadline
        """;

        List<TaskView> views = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, projectId);
            pstmt.setString(2, status.name());
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                views.add(mapRowToTaskView(rs));
            }
        } catch (SQLException e) {
            log.error("Error finding views by project id and status: project id = {},  status = {}", projectId, status, e);
        }

        return views;
    }

    public int countByProjectIdAndStatus(long projectId, TaskStatus status) {
        log.debug("countByProjectNameAndStatus({}, {})", projectId, status);

        String sql = "SELECT COUNT(1) FROM tasks WHERE project_id = ? AND status = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, projectId);
            stmt.setString(2, status.name());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            log.error("Error finding views by {} and {}", projectId, status);
        }
        return 0;
    }

    @Override
    public int countByStatus(TaskStatus status) {
        String sql = "SELECT COUNT(1) FROM tasks WHERE status = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status.name());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            log.error("Error finding views by status {}", status, e);
        }
        return 0;
    }

    @Override
    public int countByProjectId(long projectId) {
        log.debug("countByProjectName({})", projectId);

        String sql = "SELECT COUNT(1) FROM tasks WHERE project_id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, projectId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    log.debug("countTasksByProjectName: {}", rs.getInt(1));
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            log.error("Error finding views by {}", projectId);
        }
        return 0;
    }

    // ===== МЕТОДЫ ДЛЯ СИНХРОНИЗАЦИИ =====

    @Override
    public List<Task> findUnsynced() {
        String sql = "SELECT id, name, project_id, executor, type, status, priority, deadline, description, synced FROM tasks WHERE synced = FALSE";
        List<Task> tasks = new ArrayList<>();

        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                tasks.add(mapRowToTask(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return tasks;
    }

    @Override
    public void markAsSynced(long id) {
        String sql = "UPDATE tasks SET synced = TRUE WHERE id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, id);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====

    private Task mapRowToTask(ResultSet rs) throws SQLException {
        Date deadline = rs.getDate("deadline");

        if (deadline == null) {
            throw new IllegalArgumentException("deadline is null");
        }

        return new Task(rs.getLong("id"),
                rs.getString("name"),
                rs.getLong("project_id"),
                rs.getString("executor"),
                rs.getString("type"),
                TaskStatus.valueOf(rs.getString("status")),
                TaskPriority.valueOf(rs.getString("priority")),
                deadline.toLocalDate(),
                rs.getString("description"),
                rs.getBoolean("synced"));
    }

    private TaskView mapRowToTaskView(ResultSet rs) throws SQLException {
        Date deadline = rs.getDate("deadline");

        if (deadline == null) {
            throw new IllegalArgumentException("deadline is null");
        }

        return new TaskView(rs.getLong("id"),
        rs.getString("name"),
        rs.getLong("project_id"),
        rs.getString("project_name"), // JOIN
        rs.getString("executor"),
        rs.getString("type"),
        TaskStatus.valueOf(rs.getString("status")),
        TaskPriority.valueOf(rs.getString("priority")),
        deadline.toLocalDate(),
        rs.getString("description"),
        rs.getBoolean("synced"));
    }

    //TODO заменить по необходимости в оставшихся местах Task на TaskView

    private void createIndexes() {
        log.debug("createIndexes()");
        String sql = "CREATE INDEX IF NOT EXISTS idx_task_status ON tasks(status)";
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            log.error("Error creating indexes");
        }
    }
}



