package org.example.taskschedulerdesktop.user;

import org.example.taskschedulerdesktop.shared.database.Jdbc;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class H2UserRepository implements UserRepository {

    private static final String SELECT = "SELECT id, name, email, position, timezone, status FROM users";

    private final UnitOfWork uow;

    public H2UserRepository(UnitOfWork uow) {
        this.uow = uow;
    }

    @Override
    public List<User> findAllActive() throws SQLException {
        List<User> users = new ArrayList<>();
        String sql = SELECT + " WHERE status = 'ACTIVE' AND deleted_at IS NULL ORDER BY name";
        try (PreparedStatement ps = uow.connection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(map(rs));
            }
        }
        return users;
    }

    @Override
    public Optional<User> findById(UUID id) throws SQLException {
        try (PreparedStatement ps = uow.connection().prepareStatement(SELECT + " WHERE id = ?")) {
            Jdbc.setUuid(ps, 1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private User map(ResultSet rs) throws SQLException {
        return new User(
                Jdbc.getUuid(rs, "id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("position"),
                rs.getString("timezone"),
                rs.getString("status"));
    }
}
