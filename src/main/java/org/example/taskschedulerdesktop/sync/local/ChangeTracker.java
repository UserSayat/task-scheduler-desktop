package org.example.taskschedulerdesktop.sync.local;

import org.example.taskschedulerdesktop.session.Session;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.example.taskschedulerdesktop.sync.protocol.EntityType;
import org.example.taskschedulerdesktop.sync.protocol.Operation;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final Session session;
    private final FieldVersionRepository fieldVersions;
    private final SyncLogRepository syncLog;
    private Runnable onChange = () -> {};

    public ChangeTracker(UnitOfWork uow, Session session) {
        this.session = session;
        this.fieldVersions = new FieldVersionRepository(uow);
        this.syncLog = new SyncLogRepository(uow);
    }

    /**
     * Что сделать после локального изменения — планировщик синхронизации отправит его через несколько секунд.
     */
    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
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

        Map<String, Instant> versions = new LinkedHashMap<>();
        fields.forEach(field -> versions.put(field, at));
        fieldVersions.upsert(type, entityId, versions, userId, deviceId);
        syncLog.insert(type, entityId, operation, at, userId, deviceId);
        onChange.run();
    }
}
