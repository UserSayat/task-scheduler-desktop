package org.example.taskschedulerdesktop.tag;

import org.example.taskschedulerdesktop.shared.database.Jdbc;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class H2TagRepository implements TagRepository {

    private final UnitOfWork uow;

    public H2TagRepository(UnitOfWork uow) {
        this.uow = uow;
    }

    @Override
    public Optional<Tag> findByNameIgnoreCase(String name) throws SQLException {
        String sql = "SELECT id, name, color FROM tags WHERE LOWER(name) = LOWER(?)";
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new Tag(Jdbc.getUuid(rs, "id"), rs.getString("name"), rs.getString("color")))
                        : Optional.empty();
            }
        }
    }

    @Override
    public void insert(Tag tag, Instant createdAt, UUID createdBy) throws SQLException {
        String sql = """
                INSERT INTO tags (id, name, color, created_at, updated_at, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            Jdbc.setUuid(ps, 1, tag.id());
            ps.setString(2, tag.name());
            ps.setString(3, tag.color());
            Jdbc.setInstant(ps, 4, createdAt);
            Jdbc.setInstant(ps, 5, createdAt);
            Jdbc.setUuid(ps, 6, createdBy);
            Jdbc.setUuid(ps, 7, createdBy);
            ps.executeUpdate();
        }
    }
}
