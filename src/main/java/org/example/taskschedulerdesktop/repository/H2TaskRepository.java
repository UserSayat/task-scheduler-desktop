package org.example.taskschedulerdesktop.repository;

import org.example.taskschedulerdesktop.database.DatabaseConnection;
import org.example.taskschedulerdesktop.models.Task;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class H2TaskRepository implements TaskRepository {

    private final DatabaseConnection db;

    public H2TaskRepository(DatabaseConnection db) {
        this.db = db;
        createTableIfNotExists();
    }

    // ===== СОЗДАНИЕ ТАБЛИЦЫ =====

    private void createTableIfNotExists() {
        String sql = """
            CREATE TABLE IF NOT EXISTS tasks (
                id INT PRIMARY KEY AUTO_INCREMENT,
                taskName VARCHAR(255) NOT NULL,
                projectName VARCHAR(255) NOT NULL,
                executor VARCHAR(255) NOT NULL,
                type VARCHAR(255) NOT NULL,
                status VARCHAR(50),
                priority VARCHAR(50),
                deadline VARCHAR(50),
                description TEXT,
                synced BOOLEAN DEFAULT FALSE
            )
        """;
        //id, taskName, projectName, executor, type, status, priority, deadline, description, synced
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ===== CRUD МЕТОДЫ =====

    @Override
    public List<Task> findAll() {
        String sql = """
            SELECT id, taskName, projectName, executor, type, status, priority,
             deadline, description, synced FROM tasks ORDER BY deadline
        """;
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
    public void save(Task task) {
        String sql = """
            INSERT INTO tasks (taskName, projectName, executor, type, status,
             priority, deadline, description, synced) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, task.getTaskName());
            pstmt.setString(2, task.getProjectName());
            pstmt.setString(3, task.getExecutor());
            pstmt.setString(4, task.getType());
            pstmt.setString(5, task.getStatus());
            pstmt.setString(6, task.getPriority());
            pstmt.setString(7, task.getDeadline());
            pstmt.setString(8, task.getDescription());
            pstmt.setBoolean(9, task.isSynced());
            pstmt.executeUpdate();

            // Получаем сгенерированный ID
            ResultSet generatedKeys = pstmt.getGeneratedKeys();
            if (generatedKeys.next()) {
                task.setId(generatedKeys.getInt(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void update(Task task) {
        String sql = """
            UPDATE tasks SET taskName = ?, projectName = ?, executor = ?, type = ?, status = ?,
             priority = ?, deadline = ?, description = ?, synced = ? WHERE id = ?
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, task.getTaskName());
            pstmt.setString(2, task.getProjectName());
            pstmt.setString(3, task.getExecutor());
            pstmt.setString(4, task.getType());
            pstmt.setString(5, task.getStatus());
            pstmt.setString(6, task.getPriority());
            pstmt.setString(7, task.getDeadline());
            pstmt.setString(8, task.getDescription());
            pstmt.setBoolean(9, task.isSynced());
            pstmt.setLong(10, task.getId());
            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void delete(long id) {
        String sql = "DELETE FROM tasks WHERE id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, id);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Task findById(long id) {
        String sql = """
            SELECT id, taskName, projectName, executor, type, status, priority,
             deadline, description, synced FROM tasks WHERE id = ?
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapRowToTask(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // ===== МЕТОДЫ ДЛЯ СИНХРОНИЗАЦИИ =====

    @Override
    public List<Task> findUnsynced() {
        String sql = "SELECT id, taskName, projectName, executor, type, status, priority, deadline, description, synced FROM tasks WHERE synced = FALSE";
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
        return new Task(rs.getInt("id"),
                rs.getString("taskName"),
                rs.getString("projectName"),
                rs.getString("executor"),
                rs.getString("type"),
                rs.getString("status"),
                rs.getString("priority"),
                rs.getString("deadline"),
                rs.getString("description"),
                rs.getBoolean("synced"));
    }
}