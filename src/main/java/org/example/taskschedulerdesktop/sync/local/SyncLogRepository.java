package org.example.taskschedulerdesktop.sync.local;

import com.github.f4b6a3.uuid.UuidCreator;
import org.example.taskschedulerdesktop.shared.database.Jdbc;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.example.taskschedulerdesktop.sync.protocol.EntityType;
import org.example.taskschedulerdesktop.sync.protocol.Operation;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Локальный журнал изменений sync_log — очередь для push (схема синхронизации, 3.1).
 */
public class SyncLogRepository {

    /** Неотправленная запись журнала. */
    public record Entry(UUID id, EntityType entityType, UUID entityId, Operation operation, Instant timestamp) {}

    private final UnitOfWork uow;

    public SyncLogRepository(UnitOfWork uow) {
        this.uow = uow;
    }

    public void insert(EntityType type, UUID entityId, Operation operation, Instant at,
                       UUID userId, String deviceId) throws SQLException {
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

    /**
     * Неотправленные записи в порядке создания (API.md, 8.3). Сущности с нерешённой ошибкой синхронизации
     * пропускаются: сервер их уже отклонил (нет прав, валидация), и повтор ничего не изменит.
     */
    public List<Entry> findUnsynced(int limit) throws SQLException {
        String sql = """
                SELECT s.id, s.entity_type, s.entity_id, s.operation, s.timestamp
                  FROM sync_log s
                 WHERE s.synced = FALSE
                   AND NOT EXISTS (SELECT 1 FROM sync_errors e
                                    WHERE e.entity_type = s.entity_type AND e.entity_id = s.entity_id
                                      AND e.resolved_at IS NULL)
                 ORDER BY s.timestamp, s.id
                 LIMIT ?
                """;
        List<Entry> entries = new ArrayList<>();
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new Entry(
                            Jdbc.getUuid(rs, "id"),
                            Jdbc.getEnum(rs, "entity_type", EntityType.class),
                            Jdbc.getUuid(rs, "entity_id"),
                            Jdbc.getEnum(rs, "operation", Operation.class),
                            Jdbc.getInstant(rs, "timestamp")));
                }
            }
        }
        return entries;
    }

    public void markSynced(Collection<UUID> ids, Instant serverTime) throws SQLException {
        String sql = "UPDATE sync_log SET synced = TRUE, server_timestamp = ? WHERE id = ?";
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            for (UUID id : ids) {
                Jdbc.setInstant(ps, 1, serverTime);
                Jdbc.setUuid(ps, 2, id);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public int countUnsynced() throws SQLException {
        try (PreparedStatement ps = uow.connection().prepareStatement("SELECT COUNT(*) FROM sync_log WHERE synced = FALSE");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Неотправленные записи сущностей и их удаление из очереди — при потере доступа к проекту. */
    public List<Entry> removeUnsynced(EntityType type, Collection<UUID> entityIds) throws SQLException {
        List<Entry> removed = new ArrayList<>();
        String select = "SELECT id, operation, timestamp FROM sync_log WHERE synced = FALSE AND entity_type = ? AND entity_id = ?";
        String delete = "DELETE FROM sync_log WHERE entity_type = ? AND entity_id = ?";
        try (PreparedStatement sel = uow.connection().prepareStatement(select);
             PreparedStatement del = uow.connection().prepareStatement(delete)) {
            for (UUID id : entityIds) {
                sel.setString(1, type.name());
                Jdbc.setUuid(sel, 2, id);
                try (ResultSet rs = sel.executeQuery()) {
                    while (rs.next()) {
                        removed.add(new Entry(Jdbc.getUuid(rs, "id"), type, id,
                                Jdbc.getEnum(rs, "operation", Operation.class), Jdbc.getInstant(rs, "timestamp")));
                    }
                }
                del.setString(1, type.name());
                Jdbc.setUuid(del, 2, id);
                del.addBatch();
            }
            del.executeBatch();
        }
        return removed;
    }
}
