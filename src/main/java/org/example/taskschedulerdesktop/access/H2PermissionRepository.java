package org.example.taskschedulerdesktop.access;

import org.example.taskschedulerdesktop.shared.database.Jdbc;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class H2PermissionRepository implements PermissionRepository {

    private final UnitOfWork uow;

    public H2PermissionRepository(UnitOfWork uow) {
        this.uow = uow;
    }

    @Override
    public Map<UUID, String> projectMembers(UUID projectId) throws SQLException {
        String sql = """
                SELECT m.user_id, m.role FROM project_members m JOIN users u ON u.id = m.user_id
                 WHERE m.project_id = ? AND u.deleted_at IS NULL
                """;
        Map<UUID, String> roles = new HashMap<>();
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            Jdbc.setUuid(ps, 1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    roles.put(Jdbc.getUuid(rs, "user_id"), rs.getString("role"));
                }
            }
        }
        return roles;
    }

    @Override
    public Set<UUID> admins() throws SQLException {
        String sql = """
                SELECT DISTINCT u.id FROM users u
                  JOIN user_roles ur ON ur.user_id = u.id
                  JOIN roles r ON r.id = ur.role_id
                 WHERE u.deleted_at IS NULL AND ur.scope_type = 'GLOBAL' AND r.code = 'ADMIN'
                """;
        Set<UUID> ids = new HashSet<>();
        try (PreparedStatement ps = uow.connection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add(Jdbc.getUuid(rs, "id"));
            }
        }
        return ids;
    }

    @Override
    public Optional<String> userName(UUID userId) throws SQLException {
        try (PreparedStatement ps = uow.connection().prepareStatement("SELECT name FROM users WHERE id = ?")) {
            Jdbc.setUuid(ps, 1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
            }
        }
    }

    @Override
    public Set<String> globalRoles(UUID userId) throws SQLException {
        String sql = """
                SELECT r.code FROM user_roles ur JOIN roles r ON r.id = ur.role_id
                 WHERE ur.user_id = ? AND ur.scope_type = 'GLOBAL'
                """;
        Set<String> roles = new HashSet<>();
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            Jdbc.setUuid(ps, 1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    roles.add(rs.getString(1));
                }
            }
        }
        return roles;
    }

    @Override
    public Optional<String> memberRole(UUID projectId, UUID userId) throws SQLException {
        String sql = "SELECT role FROM project_members WHERE project_id = ? AND user_id = ?";
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            Jdbc.setUuid(ps, 1, projectId);
            Jdbc.setUuid(ps, 2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        }
    }

    @Override
    public Set<UUID> projectsWithRole(UUID userId, Collection<String> roles) throws SQLException {
        String placeholders = String.join(", ", roles.stream().map(r -> "?").toList());
        String sql = "SELECT project_id FROM project_members WHERE user_id = ? AND role IN (" + placeholders + ")";
        Set<UUID> projects = new HashSet<>();
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            Jdbc.setUuid(ps, 1, userId);
            int index = 2;
            for (String role : roles) {
                ps.setString(index++, role);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    projects.add(Jdbc.getUuid(rs, "project_id"));
                }
            }
        }
        return projects;
    }

    @Override
    public Optional<TaskFacts> taskFacts(UUID taskId) throws SQLException {
        try (PreparedStatement ps = uow.connection().prepareStatement("SELECT project_id, creator_id FROM tasks WHERE id = ?")) {
            Jdbc.setUuid(ps, 1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new TaskFacts(Jdbc.getUuid(rs, "project_id"), Jdbc.getUuid(rs, "creator_id")))
                        : Optional.empty();
            }
        }
    }

    @Override
    public boolean isAssignee(UUID taskId, UUID userId) throws SQLException {
        try (PreparedStatement ps = uow.connection().prepareStatement(
                "SELECT 1 FROM task_assignees WHERE task_id = ? AND user_id = ?")) {
            Jdbc.setUuid(ps, 1, taskId);
            Jdbc.setUuid(ps, 2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public boolean isResponsible(UUID taskId, UUID userId) throws SQLException {
        try (PreparedStatement ps = uow.connection().prepareStatement(
                "SELECT 1 FROM task_assignees WHERE task_id = ? AND user_id = ? AND role = 'RESPONSIBLE'")) {
            Jdbc.setUuid(ps, 1, taskId);
            Jdbc.setUuid(ps, 2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public boolean hasAssignees(UUID taskId) throws SQLException {
        try (PreparedStatement ps = uow.connection().prepareStatement(
                "SELECT 1 FROM task_assignees WHERE task_id = ?")) {
            Jdbc.setUuid(ps, 1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
