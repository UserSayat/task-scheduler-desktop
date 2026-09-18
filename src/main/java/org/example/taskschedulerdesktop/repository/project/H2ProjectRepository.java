package org.example.taskschedulerdesktop.repository.project;

import org.example.taskschedulerdesktop.database.DatabaseConnection;
import org.example.taskschedulerdesktop.models.Project;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class H2ProjectRepository implements ProjectRepository {

    private final DatabaseConnection db;

    public H2ProjectRepository(DatabaseConnection db) {
        this.db = db;
        createTableIfNotExists();
        createIndexes();
    }

    // ===== СОЗДАНИЕ ТАБЛИЦЫ =====

    private void createTableIfNotExists() {
        String sql = """
            CREATE TABLE IF NOT EXISTS projects (
                id INT PRIMARY KEY AUTO_INCREMENT,
                name VARCHAR(255) NOT NULL,
                supervisor VARCHAR(255) NOT NULL,
                numberOfTasks INT,
                completedTasks INT,
                remainingTasks INT,
                percentOfCompletion INT,
                synced BOOLEAN DEFAULT FALSE
            )
        """;

        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ===== CRUD МЕТОДЫ =====

    @Override
    public List<Project> findAll() {
        String sql = """
            SELECT id, name, supervisor, numberOfTasks, completedTasks,
            remainingTasks, percentOfCompletion, synced FROM projects
        """;

        List<Project> projects = new ArrayList<>();

        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                projects.add(mapRowToProject(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return projects;
    }

    @Override
    public void save(Project project) {
        String sql = """
            INSERT INTO projects (name, supervisor, numberOfTasks, completedTasks,
            remainingTasks, percentOfCompletion, synced) VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, project.getName());
            pstmt.setString(2, project.getSupervisor());
            pstmt.setInt(3, project.getNumberOfTasks());
            pstmt.setInt(4, project.getCompletedTasks());
            pstmt.setInt(5, project.getRemainingTasks());
            pstmt.setInt(6, project.getPercentOfCompletion());
            pstmt.setBoolean(7, project.isSynced());
            pstmt.executeUpdate();

            // Получаем сгенерированный ID
            ResultSet generatedKeys = pstmt.getGeneratedKeys();
            if (generatedKeys.next()) {
                project.setId(generatedKeys.getLong(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void update(Project project) {
        String sql = """
            UPDATE projects SET name = ?, supervisor = ?, numberOfTasks = ?, completedTasks = ?,
            remainingTasks = ?, percentOfCompletion = ?, synced = ? WHERE id = ?
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, project.getName());
            pstmt.setString(2, project.getSupervisor());
            pstmt.setInt(3, project.getNumberOfTasks());
            pstmt.setInt(4, project.getCompletedTasks());
            pstmt.setInt(5, project.getRemainingTasks());
            pstmt.setInt(6, project.getPercentOfCompletion());
            pstmt.setBoolean(7, project.isSynced());
            pstmt.setLong(8, project.getId());
            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void delete(long id) {
        String sql = "DELETE FROM projects WHERE id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, id);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Optional<Project> findById(long id) {
        String sql = """
            SELECT id, name, supervisor, numberOfTasks, completedTasks,
            remainingTasks, percentOfCompletion, synced FROM projects WHERE id = ?
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return Optional.ofNullable(mapRowToProject(rs));
            } else {
                return Optional.empty();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // ===== МЕТОДЫ ДЛЯ СИНХРОНИЗАЦИИ =====

    @Override
    public List<Project> findUnsynced() {
        String sql = """
            SELECT id, name, supervisor, numberOfTasks, completedTasks,
            remainingTasks, percentOfCompletion, synced FROM projects WHERE synced = FALSE
        """;
        List<Project> projects = new ArrayList<>();

        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                projects.add(mapRowToProject(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return projects;
    }

    @Override
    public void markAsSynced(long id) {
        String sql = "UPDATE projects SET synced = TRUE WHERE id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, id);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====

    private Project mapRowToProject(ResultSet rs) throws SQLException {

        return new Project(rs.getLong("id"),
                rs.getString("name"),
                rs.getString("supervisor"),
                rs.getInt("numberOfTasks"),
                rs.getInt("completedTasks"),
                rs.getInt("remainingTasks"),
                rs.getInt("percentOfCompletion"),
                rs.getBoolean("synced"));
    }

    private void createIndexes() {
        String sql = "CREATE INDEX IF NOT EXISTS idx_project_name ON projects(name)";
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
