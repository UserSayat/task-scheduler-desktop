package org.example.taskschedulerdesktop.sync;

import com.github.f4b6a3.uuid.UuidCreator;
import org.example.taskschedulerdesktop.database.Jdbc;
import org.example.taskschedulerdesktop.database.UnitOfWork;
import org.example.taskschedulerdesktop.session.Session;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Очередь изменений для синхронизации (схема синхронизации, 3.1).
 * <p>
 * Каждое локальное изменение сущности фиксируется здесь в той же транзакции:
 * <ul>
 *   <li>{@code field_versions} — для каждого изменённого поля: когда, кем и с какого устройства (field-level LWW);</li>
 *   <li>{@code sync_log} — запись с {@code synced = FALSE}, её потом заберёт push.</li>
 * </ul>
 * Значения полей здесь не хранятся: при push они берутся из самой сущности.
 * Вызывать только внутри {@link UnitOfWork#inTransaction}.
 */
public class ChangeTracker {

    private static final String DELETED_AT = "deletedAt";

    private final UnitOfWork uow;
    private final Session session;

    public ChangeTracker(UnitOfWork uow, Session session) {
        this.uow = uow;
        this.session = session;
    }

    public void created(EntityType type, UUID entityId, Collection<String> fields, Instant at) throws SQLException {
        record(type, entityId, Operation.CREATE, fields, at);
    }

    /**
     * Изменение отдельных полей. Если поля не менялись — ничего не пишется.
     */
    public void updated(EntityType type, UUID entityId, Collection<String> fields, Instant at) throws SQLException {
        if (fields.isEmpty()) {
            return;
        }
        record(type, entityId, Operation.UPDATE, fields, at);
    }

    /**
     * Мягкое удаление: в протоколе это изменение поля deletedAt.
     */
    public void deleted(EntityType type, UUID entityId, Instant at) throws SQLException {
        record(type, entityId, Operation.DELETE, List.of(DELETED_AT), at);
    }

    private void record(EntityType type, UUID entityId, Operation operation,
                        Collection<String> fields, Instant at) throws SQLException {
        UUID userId = session.getCurrentUserId();
        String deviceId = session.getDeviceId().toString();

        upsertFieldVersions(type, entityId, fields, at, userId, deviceId);
        insertSyncLog(type, entityId, operation, at, userId, deviceId);
    }

    private void upsertFieldVersions(EntityType type, UUID entityId, Collection<String> fields,
                                     Instant at, UUID userId, String deviceId) throws SQLException {
        String sql = """
                MERGE INTO field_versions fv
                USING (VALUES (CAST(? AS VARCHAR), CAST(? AS UUID), CAST(? AS VARCHAR))) AS src(entity_type, entity_id, field_name)
                ON fv.entity_type = src.entity_type AND fv.entity_id = src.entity_id AND fv.field_name = src.field_name
                WHEN MATCHED THEN UPDATE SET updated_at = ?, updated_by = ?, device_id = ?
                WHEN NOT MATCHED THEN INSERT (id, created_at, entity_type, entity_id, field_name, updated_at, updated_by, device_id)
                    VALUES (?, ?, src.entity_type, src.entity_id, src.field_name, ?, ?, ?)
                """;
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            for (String field : fields) {
                ps.setString(1, type.name());
                Jdbc.setUuid(ps, 2, entityId);
                ps.setString(3, field);
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

    private void insertSyncLog(EntityType type, UUID entityId, Operation operation,
                               Instant at, UUID userId, String deviceId) throws SQLException {
        String sql = """
                INSERT INTO sync_log (id, created_at, entity_type, entity_id, operation, user_id, device_id, timestamp, synced)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, FALSE)
                """;
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            Jdbc.setUuid(ps, 1, UuidCreator.getTimeOrderedEpoch());
            Jdbc.setInstant(ps, 2, at);
            ps.setString(3, type.name());
            Jdbc.setUuid(ps, 4, entityId);
            ps.setString(5, operation.name());
            Jdbc.setUuid(ps, 6, userId);
            ps.setString(7, deviceId);
            Jdbc.setInstant(ps, 8, at);
            ps.executeUpdate();
        }
    }
}
