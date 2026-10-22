package org.example.taskschedulerdesktop.sync.protocol;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Сообщения протокола синхронизации (API.md, 8.2–8.5).
 */
public final class SyncMessages {

    private SyncMessages() {}

    /** Одно поле: значение (строка, null или массив) и момент его изменения. */
    public record FieldChange(String name, JsonNode value, Instant timestamp) {}

    /** Изменение одной сущности, отправляемое в push. */
    public record Change(List<UUID> syncLogIds, EntityType entityType, UUID entityId,
                         Operation operation, List<FieldChange> fields) {}

    public record PushRequest(List<Change> changes) {}

    public record PushResponse(List<PushResult> results, Instant serverTime) {}

    public record PushResult(EntityType entityType, UUID entityId, String status,
                             List<FieldChange> staleFields, ErrorBody error) {

        public boolean applied() {
            return "APPLIED".equals(status);
        }
    }

    public record ErrorBody(String code, String message, Map<String, List<String>> fields) {}

    /** Сущность из pull или snapshot: текущее состояние всех полей. У REVOKE полей нет. */
    public record SyncedEntity(EntityType entityType, UUID entityId, Operation operation,
                               UUID updatedBy, Instant serverTimestamp, List<FieldChange> fields) {

        public List<FieldChange> fieldsOrEmpty() {
            return fields == null ? List.of() : fields;
        }
    }

    public record PullResponse(List<SyncedEntity> changes, String cursor, boolean hasMore, Instant serverTime) {}

    public record SnapshotResponse(List<SyncedEntity> changes, int page, boolean hasMore,
                                   String cursor, Instant serverTime) {}
}
