package org.example.taskschedulerdesktop.project;

import com.github.f4b6a3.uuid.UuidCreator;
import org.example.taskschedulerdesktop.shared.database.Jdbc;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class H2ProjectRepository implements ProjectRepository {

    /**
     * Проект + владелец + статистика по задачам + признак синхронизации.
     */
    private static final String VIEW_SELECT = """
            SELECT p.id, p.name, p.owner_id, u.name AS owner_name, p.status, p.visibility,
                   (SELECT COUNT(*) FROM tasks t
                     WHERE t.project_id = p.id AND t.deleted_at IS NULL) AS total_tasks,
                   (SELECT COUNT(*) FROM tasks t
                     WHERE t.project_id = p.id AND t.deleted_at IS NULL AND t.status = 'DONE') AS done_tasks,
                   EXISTS (SELECT 1 FROM sync_log s
                            WHERE s.entity_id = p.id AND s.synced = FALSE) AS has_unsynced
              FROM projects p
              JOIN users u ON u.id = p.owner_id
             WHERE p.deleted_at IS NULL
            """;

    private final UnitOfWork uow;

    public H2ProjectRepository(UnitOfWork uow) {
        this.uow = uow;
    }

    // ===== ЗАПИСЬ =====

    @Override
    public void insert(Project project) throws SQLException {
        String sql = """
                INSERT INTO projects (id, name, description, owner_id, status, visibility, start_date, deadline,
                                      created_at, updated_at, created_by, updated_by, deleted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            Jdbc.setUuid(ps, 1, project.getId());
            ps.setString(2, project.getName());
            ps.setString(3, project.getDescription());
            Jdbc.setUuid(ps, 4, project.getOwnerId());
            ps.setString(5, project.getStatus().name());
            ps.setString(6, project.getVisibility().name());
            Jdbc.setDate(ps, 7, project.getStartDate());
            Jdbc.setDate(ps, 8, project.getDeadline());
            Jdbc.setInstant(ps, 9, project.getCreatedAt());
            Jdbc.setInstant(ps, 10, project.getUpdatedAt());
            Jdbc.setUuid(ps, 11, project.getCreatedBy());
            Jdbc.setUuid(ps, 12, project.getUpdatedBy());
            Jdbc.setInstant(ps, 13, project.getDeletedAt());
            ps.executeUpdate();
        }
        replaceMembers(project);
    }

    @Override
    public void update(Project project) throws SQLException {
        String sql = """
                UPDATE projects SET name = ?, description = ?, owner_id = ?, status = ?, visibility = ?,
                                    start_date = ?, deadline = ?, updated_at = ?, updated_by = ?, deleted_at = ?
                 WHERE id = ?
                """;
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            ps.setString(1, project.getName());
            ps.setString(2, project.getDescription());
            Jdbc.setUuid(ps, 3, project.getOwnerId());
            ps.setString(4, project.getStatus().name());
            ps.setString(5, project.getVisibility().name());
            Jdbc.setDate(ps, 6, project.getStartDate());
            Jdbc.setDate(ps, 7, project.getDeadline());
            Jdbc.setInstant(ps, 8, project.getUpdatedAt());
            Jdbc.setUuid(ps, 9, project.getUpdatedBy());
            Jdbc.setInstant(ps, 10, project.getDeletedAt());
            Jdbc.setUuid(ps, 11, project.getId());
            ps.executeUpdate();
        }
        replaceMembers(project);
    }

    private void replaceMembers(Project project) throws SQLException {
        try (PreparedStatement ps = uow.connection().prepareStatement("DELETE FROM project_members WHERE project_id = ?")) {
            Jdbc.setUuid(ps, 1, project.getId());
            ps.executeUpdate();
        }

        String sql = """
                INSERT INTO project_members (id, project_id, user_id, role, joined_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            for (ProjectMember member : project.getMembers()) {
                Jdbc.setUuid(ps, 1, UuidCreator.getTimeOrderedEpoch());
                Jdbc.setUuid(ps, 2, project.getId());
                Jdbc.setUuid(ps, 3, member.userId());
                ps.setString(4, member.role().name());
                Jdbc.setInstant(ps, 5, member.joinedAt());
                Jdbc.setInstant(ps, 6, project.getUpdatedAt());
                Jdbc.setInstant(ps, 7, project.getUpdatedAt());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ===== ЧТЕНИЕ СУЩНОСТИ =====

    @Override
    public Optional<Project> findById(UUID id) throws SQLException {
        Project project;
        try (PreparedStatement ps = uow.connection().prepareStatement("SELECT * FROM projects WHERE id = ?")) {
            Jdbc.setUuid(ps, 1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                project = mapProject(rs);
            }
        }
        project.setMembers(findMembers(id));
        return Optional.of(project);
    }

    private List<ProjectMember> findMembers(UUID projectId) throws SQLException {
        List<ProjectMember> members = new ArrayList<>();
        String sql = "SELECT user_id, role, joined_at FROM project_members WHERE project_id = ? ORDER BY joined_at";
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            Jdbc.setUuid(ps, 1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    members.add(new ProjectMember(
                            Jdbc.getUuid(rs, "user_id"),
                            Jdbc.getEnum(rs, "role", MemberRole.class),
                            Jdbc.getInstant(rs, "joined_at")));
                }
            }
        }
        return members;
    }

    private Project mapProject(ResultSet rs) throws SQLException {
        Project project = new Project();
        project.setId(Jdbc.getUuid(rs, "id"));
        project.setName(rs.getString("name"));
        project.setDescription(rs.getString("description"));
        project.setOwnerId(Jdbc.getUuid(rs, "owner_id"));
        project.setStatus(Jdbc.getEnum(rs, "status", ProjectStatus.class));
        project.setVisibility(Jdbc.getEnum(rs, "visibility", Visibility.class));
        project.setStartDate(Jdbc.getDate(rs, "start_date"));
        project.setDeadline(Jdbc.getDate(rs, "deadline"));
        project.setCreatedAt(Jdbc.getInstant(rs, "created_at"));
        project.setUpdatedAt(Jdbc.getInstant(rs, "updated_at"));
        project.setCreatedBy(Jdbc.getUuid(rs, "created_by"));
        project.setUpdatedBy(Jdbc.getUuid(rs, "updated_by"));
        project.setDeletedAt(Jdbc.getInstant(rs, "deleted_at"));
        return project;
    }

    // ===== ЧТЕНИЕ ДЛЯ ЭКРАНОВ =====

    @Override
    public List<ProjectView> findAllViews() throws SQLException {
        List<ProjectView> views = new ArrayList<>();
        try (PreparedStatement ps = uow.connection().prepareStatement(VIEW_SELECT + " ORDER BY CASE p.status WHEN 'ARCHIVED' THEN 1 ELSE 0 END, p.name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                views.add(mapView(rs));
            }
        }
        return views;
    }

    @Override
    public Optional<ProjectView> findViewById(UUID id) throws SQLException {
        try (PreparedStatement ps = uow.connection().prepareStatement(VIEW_SELECT + " AND p.id = ?")) {
            Jdbc.setUuid(ps, 1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapView(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<ProjectMemberView> findMemberViews(UUID projectId) throws SQLException {
        String sql = """
                SELECT m.user_id, u.name, u.position, m.role,
                       (SELECT COUNT(*) FROM tasks t JOIN task_assignees a ON a.task_id = t.id
                         WHERE t.project_id = m.project_id AND a.user_id = m.user_id
                           AND t.deleted_at IS NULL AND t.status <> 'DONE') AS open_tasks
                  FROM project_members m
                  JOIN users u ON u.id = m.user_id
                 WHERE m.project_id = ?
                 ORDER BY CASE m.role WHEN 'OWNER' THEN 0 WHEN 'MANAGER' THEN 1 WHEN 'MEMBER' THEN 2 ELSE 3 END,
                          u.name
                """;
        List<ProjectMemberView> members = new ArrayList<>();
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            Jdbc.setUuid(ps, 1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    members.add(new ProjectMemberView(
                            Jdbc.getUuid(rs, "user_id"),
                            rs.getString("name"),
                            rs.getString("position"),
                            Jdbc.getEnum(rs, "role", MemberRole.class),
                            rs.getInt("open_tasks")));
                }
            }
        }
        return members;
    }

    private ProjectView mapView(ResultSet rs) throws SQLException {
        return new ProjectView(
                Jdbc.getUuid(rs, "id"),
                rs.getString("name"),
                Jdbc.getUuid(rs, "owner_id"),
                rs.getString("owner_name"),
                rs.getInt("total_tasks"),
                rs.getInt("done_tasks"),
                !rs.getBoolean("has_unsynced"),
                Jdbc.getEnum(rs, "status", ProjectStatus.class),
                Jdbc.getEnum(rs, "visibility", Visibility.class));
    }
}
