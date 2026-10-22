package org.example.taskschedulerdesktop.sync.pull;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.taskschedulerdesktop.session.Session;
import org.example.taskschedulerdesktop.shared.database.Jdbc;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.example.taskschedulerdesktop.sync.adapter.KnownUsers;
import org.example.taskschedulerdesktop.sync.adapter.MissingReferenceException;
import org.example.taskschedulerdesktop.sync.adapter.SyncEntityAdapter;
import org.example.taskschedulerdesktop.sync.local.FieldVersionRepository.FieldVersion;
import org.example.taskschedulerdesktop.sync.local.FieldVersionRepository;
import org.example.taskschedulerdesktop.sync.local.LocalReplica;
import org.example.taskschedulerdesktop.sync.protocol.EntityType;
import org.example.taskschedulerdesktop.sync.protocol.Operation;
import org.example.taskschedulerdesktop.sync.protocol.SyncMessages.FieldChange;
import org.example.taskschedulerdesktop.sync.protocol.SyncMessages.SyncedEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Запись данных с сервера в локальную базу по правилу field-level LWW (API.md, 8.6).
 * <p>
 * Поле применяется, если его метка времени не старше локальной. Более новая локальная метка означает
 * правку, которая ещё не дошла до сервера, — она сохраняется и уйдёт со следующим push. При равных метках
 * побеждает сервер: его значение — уже итог разрешения конфликта (D8).
 * <p>
 * Вызывать внутри транзакции: страница pull применяется целиком или не применяется вовсе.
 */
public class ChangeApplier {

    private static final Logger log = LoggerFactory.getLogger(ChangeApplier.class);

    /** Порядок применения внутри страницы: сначала те, на кого ссылаются (API.md, 8.4). */
    private static final List<EntityType> ORDER = List.of(EntityType.USER, EntityType.TAG, EntityType.PROJECT, EntityType.TASK);

    private final UnitOfWork uow;
    private final Map<EntityType, SyncEntityAdapter> adapters;
    private final FieldVersionRepository fieldVersions;
    private final LocalReplica replica;
    private final KnownUsers knownUsers;
    private final Session session;

    public ChangeApplier(UnitOfWork uow, Map<EntityType, SyncEntityAdapter> adapters, FieldVersionRepository fieldVersions,
                         LocalReplica replica, KnownUsers knownUsers, Session session) {
        this.uow = uow;
        this.adapters = adapters;
        this.fieldVersions = fieldVersions;
        this.replica = replica;
        this.knownUsers = knownUsers;
        this.session = session;
    }

    /** Страница pull или snapshot. */
    public AppliedChanges apply(List<SyncedEntity> entities) throws SQLException {
        AppliedChanges result = new AppliedChanges();
        List<SyncedEntity> ordered = entities.stream()
                .sorted(Comparator.comparingInt(entity -> ORDER.indexOf(entity.entityType())))
                .toList();

        for (SyncedEntity entity : ordered) {
            if (entity.operation() == Operation.REVOKE) {
                if (entity.entityType() == EntityType.PROJECT) {
                    replica.removeProject(entity.entityId(), Instant.now());
                    result.projectRevoked(entity.entityId());
                }
                continue;
            }
            applyFields(entity.entityType(), entity.entityId(), entity.fieldsOrEmpty(), entity.updatedBy(), result);
        }
        return result;
    }

    /** Поля, проигравшие на сервере (staleFields в ответе push): записать серверные значения. */
    public AppliedChanges applyStale(EntityType type, UUID entityId, List<FieldChange> staleFields) throws SQLException {
        AppliedChanges result = new AppliedChanges();
        if (staleFields != null && !staleFields.isEmpty()) {
            applyFields(type, entityId, staleFields, null, result);
        }
        return result;
    }

    private void applyFields(EntityType type, UUID id, List<FieldChange> fields, UUID updatedBy,
                             AppliedChanges result) throws SQLException {
        SyncEntityAdapter adapter = adapters.get(type);
        boolean exists = adapter.exists(id);
        Map<String, FieldVersion> local = fieldVersions.find(type, id);

        Map<String, JsonNode> accepted = new LinkedHashMap<>();
        Map<String, Instant> times = new LinkedHashMap<>();
        for (FieldChange field : fields) {
            FieldVersion version = local.get(field.name());
            if (!exists || version == null || !field.timestamp().isBefore(version.updatedAt())) {
                accepted.put(field.name(), field.value());
                times.put(field.name(), field.timestamp());
            }
        }
        if (accepted.isEmpty()) {
            return;
        }

        Instant at = times.values().stream().max(Comparator.naturalOrder()).orElseThrow();
        try {
            adapter.apply(id, accepted, at);
        } catch (MissingReferenceException e) {
            log.debug("{} {} deferred: project {} is not loaded yet", type, id, e.projectId());
            result.projectMissing(e.projectId());
            return;
        } catch (IllegalArgumentException | NullPointerException e) {
            // Некорректные данные одной сущности не должны останавливать всю синхронизацию
            log.warn("Skipping {} {} from server: {}", type, id, e.toString());
            return;
        }

        UUID author = updatedBy != null ? updatedBy : session.getCurrentUserId();
        knownUsers.ensureExist(List.of(author));
        fieldVersions.upsert(type, id, times, author, FieldVersionRepository.SERVER_DEVICE);

        switch (type) {
            case USER -> result.userChanged();
            case TAG -> result.tagChanged();
            case PROJECT -> result.projectChanged(id, !exists);
            case TASK -> result.taskChanged(projectOfTask(id));
        }
    }

    private UUID projectOfTask(UUID taskId) throws SQLException {
        try (PreparedStatement ps = uow.connection().prepareStatement("SELECT project_id FROM tasks WHERE id = ?")) {
            Jdbc.setUuid(ps, 1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Jdbc.getUuid(rs, "project_id") : null;
            }
        }
    }
}
