package org.example.taskschedulerdesktop.sync.local;

import com.github.f4b6a3.uuid.UuidCreator;
import org.example.taskschedulerdesktop.shared.database.Jdbc;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.example.taskschedulerdesktop.sync.protocol.EntityType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Таблица field_versions: когда, кем и с какого устройства последний раз менялось каждое поле
 * (field-level LWW, API.md, 8.6). Значения полей здесь не хранятся.
 */
public class FieldVersionRepository {

    /** Устройство для значений, пришедших с сервера: в pull deviceId полей не передаётся. */
    public static final String SERVER_DEVICE = "server";

    public record FieldVersion(String field, Instant updatedAt, String deviceId) {}

    private final UnitOfWork uow;

    public FieldVersionRepository(UnitOfWork uow) {
        this.uow = uow;
    }

    public Map<String, FieldVersion> find(EntityType type, UUID entityId) throws SQLException {
        Map<String, FieldVersion> versions = new LinkedHashMap<>();
        String sql = "SELECT field_name, updated_at, device_id FROM field_versions WHERE entity_type = ? AND entity_id = ?";
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            ps.setString(1, type.name());
            Jdbc.setUuid(ps, 2, entityId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String field = rs.getString("field_name");
                    versions.put(field, new FieldVersion(field, Jdbc.getInstant(rs, "updated_at"), rs.getString("device_id")));
                }
            }
        }
        return versions;
    }

    /**
     * Поля, которые это устройство изменило начиная с {@code since}, с метками времени — их отправляет push.
     * Метки берутся из базы, а не из памяти, чтобы на сервер ушла ровно та точность, что хранится локально.
     */
    public Map<String, Instant> findChangedByDevice(EntityType type, UUID entityId, String deviceId,
                                                    Instant since) throws SQLException {
        Map<String, Instant> fields = new LinkedHashMap<>();
        String sql = """
                SELECT field_name, updated_at FROM field_versions
                 WHERE entity_type = ? AND entity_id = ? AND device_id = ? AND updated_at >= ?
                 ORDER BY field_name
                """;
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            ps.setString(1, type.name());
            Jdbc.setUuid(ps, 2, entityId);
            ps.setString(3, deviceId);
            Jdbc.setInstant(ps, 4, since);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    fields.put(rs.getString("field_name"), Jdbc.getInstant(rs, "updated_at"));
                }
            }
        }
        return fields;
    }

    public void upsert(EntityType type, UUID entityId, Map<String, Instant> fields,
                       UUID userId, String deviceId) throws SQLException {
        String sql = """
                MERGE INTO field_versions fv
                USING (VALUES (CAST(? AS VARCHAR), CAST(? AS UUID), CAST(? AS VARCHAR))) AS src(entity_type, entity_id, field_name)
                ON fv.entity_type = src.entity_type AND fv.entity_id = src.entity_id AND fv.field_name = src.field_name
                WHEN MATCHED THEN UPDATE SET updated_at = ?, updated_by = ?, device_id = ?
                WHEN NOT MATCHED THEN INSERT (id, created_at, entity_type, entity_id, field_name, updated_at, updated_by, device_id)
                    VALUES (?, ?, src.entity_type, src.entity_id, src.field_name, ?, ?, ?)
                """;
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            for (Map.Entry<String, Instant> field : fields.entrySet()) {
                Instant at = field.getValue();
                ps.setString(1, type.name());
                Jdbc.setUuid(ps, 2, entityId);
                ps.setString(3, field.getKey());
                Jdbc.setInstant(ps, 4, at);
                Jdbc.setUuid(ps, 5, userId);
                ps.setString(6, deviceId);
                Jdbc.setUuid(ps, 7, UuidCreator.getTimeOrderedEpoch());
                Jdbc.setInstant(ps, 8, at);
                Jdbc.setInstant(ps, 9, at);
                Jdbc.setUuid(ps, 10, userId);
                ps.setString(11, deviceId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public void deleteForEntities(EntityType type, Collection<UUID> entityIds) throws SQLException {
        String sql = "DELETE FROM field_versions WHERE entity_type = ? AND entity_id = ?";
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            for (UUID id : entityIds) {
                ps.setString(1, type.name());
                Jdbc.setUuid(ps, 2, id);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
