package org.example.taskschedulerdesktop.sync.push;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.taskschedulerdesktop.session.Session;
import org.example.taskschedulerdesktop.sync.adapter.SyncEntityAdapter;
import org.example.taskschedulerdesktop.sync.local.FieldVersionRepository;
import org.example.taskschedulerdesktop.sync.local.SyncLogRepository;
import org.example.taskschedulerdesktop.sync.protocol.EntityType;
import org.example.taskschedulerdesktop.sync.protocol.Operation;
import org.example.taskschedulerdesktop.sync.protocol.SyncMessages.Change;
import org.example.taskschedulerdesktop.sync.protocol.SyncMessages.FieldChange;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Сборка пачки изменений для push из локального журнала (API.md, 8.3).
 * <p>
 * Записи sync_log одной сущности объединяются в одно изменение. Какие поля отправить, видно по field_versions:
 * поля, которые это устройство изменило не раньше самой старой неотправленной записи. Значения берутся
 * из самой сущности — в журнале их нет.
 */
public class OutgoingChanges {

    /** Сколько строк журнала читать за раз: изменений одной сущности может быть много. */
    private static final int ROWS_PER_CHANGE = 20;

    private record Key(EntityType type, UUID id) {}

    private final Map<EntityType, SyncEntityAdapter> adapters;
    private final SyncLogRepository syncLog;
    private final FieldVersionRepository fieldVersions;
    private final Session session;

    public OutgoingChanges(Map<EntityType, SyncEntityAdapter> adapters, SyncLogRepository syncLog,
                           FieldVersionRepository fieldVersions, Session session) {
        this.adapters = adapters;
        this.syncLog = syncLog;
        this.fieldVersions = fieldVersions;
        this.session = session;
    }

    /**
     * Следующая пачка — не больше {@code maxChanges} изменений в порядке создания. Записи, отправлять по которым
     * нечего (сущности уже нет или поля с тех пор перезаписал сервер), сразу помечаются отправленными.
     * Вызывать внутри транзакции.
     */
    public List<Change> nextBatch(int maxChanges) throws SQLException {
        Map<Key, List<SyncLogRepository.Entry>> groups = new LinkedHashMap<>();
        for (SyncLogRepository.Entry entry : syncLog.findUnsynced(maxChanges * ROWS_PER_CHANGE)) {
            Key key = new Key(entry.entityType(), entry.entityId());
            if (groups.containsKey(key) || groups.size() < maxChanges) {
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
            }
        }

        String deviceId = session.getDeviceId().toString();
        List<Change> changes = new ArrayList<>();
        List<UUID> nothingToSend = new ArrayList<>();

        for (Map.Entry<Key, List<SyncLogRepository.Entry>> group : groups.entrySet()) {
            Key key = group.getKey();
            List<SyncLogRepository.Entry> entries = group.getValue();
            List<UUID> logIds = entries.stream().map(SyncLogRepository.Entry::id).toList();

            Instant since = entries.stream().map(SyncLogRepository.Entry::timestamp)
                    .min(Comparator.naturalOrder()).orElseThrow();
            Map<String, Instant> changedFields = fieldVersions.findChangedByDevice(key.type(), key.id(), deviceId, since);
            Optional<Map<String, JsonNode>> values = adapters.get(key.type()).values(key.id());

            List<FieldChange> fields = new ArrayList<>();
            if (values.isPresent()) {
                changedFields.forEach((name, timestamp) -> {
                    if (values.get().containsKey(name)) {
                        fields.add(new FieldChange(name, values.get().get(name), timestamp));
                    }
                });
            }
            if (fields.isEmpty()) {
                nothingToSend.addAll(logIds);
                continue;
            }
            changes.add(new Change(logIds, key.type(), key.id(), operation(entries), fields));
        }

        if (!nothingToSend.isEmpty()) {
            syncLog.markSynced(nothingToSend, Instant.now());
        }
        return changes;
    }

    /** CREATE, если среди записей есть создание; иначе DELETE, если есть удаление; иначе UPDATE. */
    private static Operation operation(List<SyncLogRepository.Entry> entries) {
        Set<Operation> operations = entries.stream().map(SyncLogRepository.Entry::operation).collect(Collectors.toSet());
        if (operations.contains(Operation.CREATE)) {
            return Operation.CREATE;
        }
        return operations.contains(Operation.DELETE) ? Operation.DELETE : Operation.UPDATE;
    }
}
