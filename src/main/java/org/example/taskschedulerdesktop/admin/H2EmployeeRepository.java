package org.example.taskschedulerdesktop.admin;

import org.example.taskschedulerdesktop.shared.database.Jdbc;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class H2EmployeeRepository implements EmployeeRepository {

    private final UnitOfWork uow;

    public H2EmployeeRepository(UnitOfWork uow) {
        this.uow = uow;
    }

    @Override
    public List<Employee> findAll() throws SQLException {
        Map<UUID, List<RoleAssignment>> roles = findRoles();

        String sql = """
                SELECT id, name, email, position, timezone, status FROM users
                 WHERE status IN ('ACTIVE', 'BLOCKED') AND deleted_at IS NULL
                 ORDER BY name
                """;
        List<Employee> employees = new ArrayList<>();
        try (PreparedStatement ps = uow.connection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID id = Jdbc.getUuid(rs, "id");
                employees.add(new Employee(id, rs.getString("name"), rs.getString("email"),
                        rs.getString("position"), rs.getString("timezone"), rs.getString("status"),
                        roles.getOrDefault(id, List.of())));
            }
        }
        return employees;
    }

    @Override
    public boolean hasGlobalRole(UUID userId, String roleCode) throws SQLException {
        String sql = """
                SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id
                 WHERE ur.user_id = ? AND ur.scope_type = 'GLOBAL' AND r.code = ?
                """;
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            Jdbc.setUuid(ps, 1, userId);
            ps.setString(2, roleCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private Map<UUID, List<RoleAssignment>> findRoles() throws SQLException {
        String sql = "SELECT ur.user_id, r.code, ur.scope_type, ur.scope_id FROM user_roles ur JOIN roles r ON r.id = ur.role_id";
        Map<UUID, List<RoleAssignment>> roles = new HashMap<>();
        try (PreparedStatement ps = uow.connection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                roles.computeIfAbsent(Jdbc.getUuid(rs, "user_id"), id -> new ArrayList<>())
                        .add(new RoleAssignment(rs.getString("code"), rs.getString("scope_type"),
                                Jdbc.getUuid(rs, "scope_id")));
            }
        }
        return roles;
    }
}
