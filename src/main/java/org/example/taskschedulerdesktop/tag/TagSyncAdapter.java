package org.example.taskschedulerdesktop.tag;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.taskschedulerdesktop.shared.database.Jdbc;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.example.taskschedulerdesktop.sync.adapter.SyncEntityAdapter;
import org.example.taskschedulerdesktop.sync.protocol.EntityType;
import org.example.taskschedulerdesktop.sync.protocol.SyncValues;
import org.example.taskschedulerdesktop.tag.Tag.Fields;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Синхронизация тегов (API.md, 8.2): имя, цвет и мягкое удаление.
 */
public class TagSyncAdapter implements SyncEntityAdapter {

    private final UnitOfWork uow;

    public TagSyncAdapter(UnitOfWork uow) {
        this.uow = uow;
    }

    @Override
    public EntityType type() {
        return EntityType.TAG;
    }

    @Override
    public boolean exists(UUID id) throws SQLException {
        return find(id).isPresent();
    }

    @Override
    public Optional<Map<String, JsonNode>> values(UUID id) throws SQLException {
        return find(id);
    }

    private Optional<Map<String, JsonNode>> find(UUID id) throws SQLException {
        try (PreparedStatement ps = uow.connection().prepareStatement("SELECT name, color, deleted_at FROM tags WHERE id = ?")) {
            Jdbc.setUuid(ps, 1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Map<String, JsonNode> fields = new LinkedHashMap<>();
                fields.put(Fields.NAME, SyncValues.node(rs.getString("name")));
                fields.put(Fields.COLOR, SyncValues.node(rs.getString("color")));
                fields.put(Fields.DELETED_AT, SyncValues.node(Jdbc.getInstant(rs, "deleted_at")));
                return Optional.of(fields);
            }
        }
    }

    @Override
    public void apply(UUID id, Map<String, JsonNode> fields, Instant at) throws SQLException {
        Map<String, JsonNode> current = find(id).orElse(null);
        Map<String, JsonNode> merged = new LinkedHashMap<>(current != null ? current : Map.of());
        merged.putAll(fields);

        String name = Objects.requireNonNull(SyncValues.text(merged.get(Fields.NAME)), "name");
        String color = SyncValues.text(merged.get(Fields.COLOR));
        Instant deletedAt = SyncValues.instant(merged.get(Fields.DELETED_AT));
        releaseName(name, id);

        String sql = current == null
                ? "INSERT INTO tags (name, color, deleted_at, updated_at, created_at, id) VALUES (?, ?, ?, ?, ?, ?)"
                : "UPDATE tags SET name = ?, color = ?, deleted_at = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, color != null ? color : Tag.DEFAULT_COLOR);
            Jdbc.setInstant(ps, 3, deletedAt);
            Jdbc.setInstant(ps, 4, at);
            if (current == null) {
                Jdbc.setInstant(ps, 5, at);
                Jdbc.setUuid(ps, 6, id);
            } else {
                Jdbc.setUuid(ps, 5, id);
            }
            ps.executeUpdate();
        }
    }

    /**
     * Имя тега уникально. Если локально его занимает другой тег (переименованный на сервере раньше,
     * чем до нас дошло его новое имя, или созданный офлайн дубликат), тот временно получает суффикс:
     * его настоящее имя придёт следующим pull.
     */
    private void releaseName(String name, UUID id) throws SQLException {
        String sql = "UPDATE tags SET name = CONCAT(LEFT(name, 91), '~', LEFT(CAST(id AS VARCHAR), 8)) WHERE name = ? AND id <> ?";
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            ps.setString(1, name);
            Jdbc.setUuid(ps, 2, id);
            ps.executeUpdate();
        }
    }
}
