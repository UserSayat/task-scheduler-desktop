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
import java.util.UUID;

/**
 * Ошибки синхронизации (sync_errors): изменения, которые сервер отклонил (нет прав, не прошли валидацию)
 * или которые не дошли до сервера из-за потери доступа к проекту. Устаревшие поля (stale) сюда не попадают — D5.
 */
public class SyncErrorRepository {

    private final UnitOfWork uow;

    public SyncErrorRepository(UnitOfWork uow) {
        this.uow = uow;
    }

    public void insert(EntityType type, UUID entityId, Operation operation, String code, String message,
                       Instant at) throws SQLException {
        String sql = """
                INSERT INTO sync_errors (id, entity_type, entity_id, operation, error_code, error_message, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            Jdbc.setUuid(ps, 1, UuidCreator.getTimeOrderedEpoch());
            ps.setString(2, type.name());
            Jdbc.setUuid(ps, 3, entityId);
            ps.setString(4, operation.name());
            ps.setString(5, code);
            ps.setString(6, message);
            Jdbc.setInstant(ps, 7, at);
            ps.executeUpdate();
        }
    }

    public int countUnresolved() throws SQLException {
        try (PreparedStatement ps = uow.connection().prepareStatement("SELECT COUNT(*) FROM sync_errors WHERE resolved_at IS NULL");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
