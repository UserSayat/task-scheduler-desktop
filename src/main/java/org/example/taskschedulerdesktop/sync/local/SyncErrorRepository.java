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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    /**
     * Отклонённое изменение одной сущности — для экрана «Отклонённые изменения».
     *
     * @param name       название задачи, проекта, тега или имя сотрудника; {@code null}, если её уже нет локально
     * @param operation  CREATE — сущность так и не появилась на сервере
     */
    public record Rejected(EntityType entityType, UUID entityId, Operation operation, String code, String message,
                           Instant at, String name) {}

    /** Нерешённые ошибки, по одной на сущность (последняя), новые сверху. */
    public List<Rejected> findUnresolved() throws SQLException {
        String sql = """
                SELECT e.entity_type, e.entity_id, e.operation, e.error_code, e.error_message, e.created_at,
                       CASE e.entity_type
                           WHEN 'TASK' THEN (SELECT title FROM tasks WHERE id = e.entity_id)
                           WHEN 'PROJECT' THEN (SELECT name FROM projects WHERE id = e.entity_id)
                           WHEN 'TAG' THEN (SELECT name FROM tags WHERE id = e.entity_id)
                           WHEN 'USER' THEN (SELECT name FROM users WHERE id = e.entity_id)
                       END AS entity_name,
                       EXISTS (SELECT 1 FROM sync_errors c WHERE c.entity_type = e.entity_type AND c.entity_id = e.entity_id
                                  AND c.resolved_at IS NULL AND c.operation = 'CREATE') AS was_create
                  FROM sync_errors e
                 WHERE e.resolved_at IS NULL
                   AND e.created_at = (SELECT MAX(l.created_at) FROM sync_errors l
                                        WHERE l.entity_type = e.entity_type AND l.entity_id = e.entity_id
                                          AND l.resolved_at IS NULL)
                 ORDER BY e.created_at DESC
                """;
        List<Rejected> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        try (PreparedStatement ps = uow.connection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                EntityType type = Jdbc.getEnum(rs, "entity_type", EntityType.class);
                UUID id = Jdbc.getUuid(rs, "entity_id");
                if (!seen.add(type + ":" + id)) {
                    continue;
                }
                rows.add(new Rejected(type, id,
                        rs.getBoolean("was_create") ? Operation.CREATE : Jdbc.getEnum(rs, "operation", Operation.class),
                        rs.getString("error_code"), rs.getString("error_message"),
                        Jdbc.getInstant(rs, "created_at"), rs.getString("entity_name")));
            }
        }
        return rows;
    }

    /** Отметить ошибки сущности решёнными. Её неотправленные изменения снова уйдут на сервер. */
    public void resolve(EntityType type, UUID entityId, Instant at) throws SQLException {
        try (PreparedStatement ps = uow.connection().prepareStatement(
                "UPDATE sync_errors SET resolved_at = ? WHERE entity_type = ? AND entity_id = ? AND resolved_at IS NULL")) {
            Jdbc.setInstant(ps, 1, at);
            ps.setString(2, type.name());
            Jdbc.setUuid(ps, 3, entityId);
            ps.executeUpdate();
        }
    }
}
