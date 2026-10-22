package org.example.taskschedulerdesktop.task;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.taskschedulerdesktop.shared.database.Jdbc;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.example.taskschedulerdesktop.sync.adapter.KnownUsers;
import org.example.taskschedulerdesktop.sync.adapter.MissingReferenceException;
import org.example.taskschedulerdesktop.sync.adapter.SyncEntityAdapter;
import org.example.taskschedulerdesktop.sync.protocol.EntityType;
import org.example.taskschedulerdesktop.sync.protocol.SyncValues.UserWithRole;
import org.example.taskschedulerdesktop.sync.protocol.SyncValues;
import org.example.taskschedulerdesktop.task.Task.Fields;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Синхронизация задач (API.md, 8.2): поля задачи, исполнители, наблюдатели и теги.
 */
public class TaskSyncAdapter implements SyncEntityAdapter {

    private final UnitOfWork uow;
    private final TaskRepository repository;
    private final KnownUsers knownUsers;

    public TaskSyncAdapter(UnitOfWork uow, TaskRepository repository, KnownUsers knownUsers) {
        this.uow = uow;
        this.repository = repository;
        this.knownUsers = knownUsers;
    }

    @Override
    public EntityType type() {
        return EntityType.TASK;
    }

    @Override
    public boolean exists(UUID id) throws SQLException {
        return rowExists("SELECT 1 FROM tasks WHERE id = ?", id);
    }

    @Override
    public Optional<Map<String, JsonNode>> values(UUID id) throws SQLException {
        return repository.findById(id).map(TaskSyncAdapter::toFields);
    }

    private static Map<String, JsonNode> toFields(Task task) {
        Map<String, JsonNode> fields = new LinkedHashMap<>();
        fields.put(Fields.TITLE, SyncValues.node(task.getTitle()));
        fields.put(Fields.DESCRIPTION, SyncValues.node(task.getDescription()));
        fields.put(Fields.PROJECT_ID, SyncValues.node(task.getProjectId()));
        fields.put(Fields.CREATOR_ID, SyncValues.node(task.getCreatorId()));
        fields.put(Fields.STATUS, SyncValues.node(task.getStatus()));
        fields.put(Fields.PRIORITY, SyncValues.node(task.getPriority()));
        fields.put(Fields.START_DATE, SyncValues.node(task.getStartDate()));
        fields.put(Fields.DEADLINE, SyncValues.node(task.getDeadline()));
        fields.put(Fields.CLOSED_AT, SyncValues.node(task.getClosedAt()));
        fields.put(Fields.DELETED_AT, SyncValues.node(task.getDeletedAt()));
        fields.put(Fields.ASSIGNEES, SyncValues.usersWithRoles(task.getAssignees().stream()
                .map(a -> new UserWithRole(a.userId(), a.role().name())).toList()));
        fields.put(Fields.OBSERVERS, SyncValues.uuidArray(task.getObserverIds()));
        fields.put(Fields.TAGS, SyncValues.uuidArray(task.getTagIds()));
        return fields;
    }

    @Override
    public void apply(UUID id, Map<String, JsonNode> fields, Instant at) throws SQLException {
        Task task = repository.findById(id).orElse(null);
        boolean isNew = task == null;
        if (isNew) {
            task = new Task();
            task.setId(id);
            task.setCreatedAt(at);
        }

        for (Map.Entry<String, JsonNode> field : fields.entrySet()) {
            JsonNode value = field.getValue();
            switch (field.getKey()) {
                case Fields.TITLE -> task.setTitle(SyncValues.text(value));
                case Fields.DESCRIPTION -> task.setDescription(SyncValues.text(value));
                case Fields.PROJECT_ID -> task.setProjectId(SyncValues.uuid(value));
                case Fields.CREATOR_ID -> task.setCreatorId(SyncValues.uuid(value));
                case Fields.STATUS -> task.setStatus(SyncValues.enumValue(value, TaskStatus.class));
                case Fields.PRIORITY -> task.setPriority(SyncValues.enumValue(value, TaskPriority.class));
                case Fields.START_DATE -> task.setStartDate(SyncValues.date(value));
                case Fields.DEADLINE -> task.setDeadline(SyncValues.date(value));
                case Fields.CLOSED_AT -> task.setClosedAt(SyncValues.instant(value));
                case Fields.DELETED_AT -> task.setDeletedAt(SyncValues.instant(value));
                case Fields.ASSIGNEES -> task.setAssignees(SyncValues.usersWithRoles(value).stream()
                        .map(a -> new TaskAssignee(a.userId(), AssigneeRole.valueOf(a.role()))).toList());
                case Fields.OBSERVERS -> task.setObserverIds(SyncValues.uuidList(value));
                case Fields.TAGS -> task.setTagIds(SyncValues.uuidList(value));
                default -> { } // поле из более новой версии API — пропускаем
            }
        }

        Objects.requireNonNull(task.getTitle(), "title");
        Objects.requireNonNull(task.getProjectId(), "projectId");
        Objects.requireNonNull(task.getCreatorId(), "creatorId");
        Objects.requireNonNull(task.getStatus(), "status");
        Objects.requireNonNull(task.getPriority(), "priority");

        if (!rowExists("SELECT 1 FROM projects WHERE id = ?", task.getProjectId())) {
            throw new MissingReferenceException(task.getProjectId());
        }
        List<UUID> users = new ArrayList<>();
        users.add(task.getCreatorId());
        task.getAssignees().forEach(a -> users.add(a.userId()));
        users.addAll(task.getObserverIds());
        knownUsers.ensureExist(users);
        task.setTagIds(existingTags(task.getTagIds()));

        task.setUpdatedAt(at);
        if (isNew) {
            task.setCreatedBy(task.getCreatorId());
            repository.insert(task);
        } else {
            repository.update(task);
        }
    }

    /** Теги, которых нет локально, пропускаются: они придут следующим pull вместе со ссылкой. */
    private List<UUID> existingTags(List<UUID> tagIds) throws SQLException {
        List<UUID> existing = new ArrayList<>();
        for (UUID tagId : tagIds) {
            if (rowExists("SELECT 1 FROM tags WHERE id = ?", tagId)) {
                existing.add(tagId);
            }
        }
        return existing;
    }

    private boolean rowExists(String sql, UUID id) throws SQLException {
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            Jdbc.setUuid(ps, 1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
