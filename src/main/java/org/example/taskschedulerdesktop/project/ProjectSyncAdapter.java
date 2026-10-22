package org.example.taskschedulerdesktop.project;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.taskschedulerdesktop.project.Project.Fields;
import org.example.taskschedulerdesktop.shared.database.Jdbc;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.example.taskschedulerdesktop.sync.adapter.KnownUsers;
import org.example.taskschedulerdesktop.sync.adapter.SyncEntityAdapter;
import org.example.taskschedulerdesktop.sync.protocol.EntityType;
import org.example.taskschedulerdesktop.sync.protocol.SyncValues.UserWithRole;
import org.example.taskschedulerdesktop.sync.protocol.SyncValues;

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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Синхронизация проектов (API.md, 8.2): поля проекта и состав участников.
 */
public class ProjectSyncAdapter implements SyncEntityAdapter {

    private final UnitOfWork uow;
    private final ProjectRepository repository;
    private final KnownUsers knownUsers;

    public ProjectSyncAdapter(UnitOfWork uow, ProjectRepository repository, KnownUsers knownUsers) {
        this.uow = uow;
        this.repository = repository;
        this.knownUsers = knownUsers;
    }

    @Override
    public EntityType type() {
        return EntityType.PROJECT;
    }

    @Override
    public boolean exists(UUID id) throws SQLException {
        try (PreparedStatement ps = uow.connection().prepareStatement("SELECT 1 FROM projects WHERE id = ?")) {
            Jdbc.setUuid(ps, 1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public Optional<Map<String, JsonNode>> values(UUID id) throws SQLException {
        return repository.findById(id).map(ProjectSyncAdapter::toFields);
    }

    private static Map<String, JsonNode> toFields(Project project) {
        Map<String, JsonNode> fields = new LinkedHashMap<>();
        fields.put(Fields.NAME, SyncValues.node(project.getName()));
        fields.put(Fields.DESCRIPTION, SyncValues.node(project.getDescription()));
        fields.put(Fields.OWNER_ID, SyncValues.node(project.getOwnerId()));
        fields.put(Fields.STATUS, SyncValues.node(project.getStatus()));
        fields.put(Fields.VISIBILITY, SyncValues.node(project.getVisibility()));
        fields.put(Fields.START_DATE, SyncValues.node(project.getStartDate()));
        fields.put(Fields.DEADLINE, SyncValues.node(project.getDeadline()));
        fields.put(Fields.DELETED_AT, SyncValues.node(project.getDeletedAt()));
        fields.put(Fields.MEMBERS, SyncValues.usersWithRoles(project.getMembers().stream()
                .map(m -> new UserWithRole(m.userId(), m.role().name())).toList()));
        return fields;
    }

    @Override
    public void apply(UUID id, Map<String, JsonNode> fields, Instant at) throws SQLException {
        Project project = repository.findById(id).orElse(null);
        boolean isNew = project == null;
        if (isNew) {
            project = new Project();
            project.setId(id);
            project.setCreatedAt(at);
        }

        for (Map.Entry<String, JsonNode> field : fields.entrySet()) {
            JsonNode value = field.getValue();
            switch (field.getKey()) {
                case Fields.NAME -> project.setName(SyncValues.text(value));
                case Fields.DESCRIPTION -> project.setDescription(SyncValues.text(value));
                case Fields.OWNER_ID -> project.setOwnerId(SyncValues.uuid(value));
                case Fields.STATUS -> project.setStatus(SyncValues.enumValue(value, ProjectStatus.class));
                case Fields.VISIBILITY -> project.setVisibility(SyncValues.enumValue(value, Visibility.class));
                case Fields.START_DATE -> project.setStartDate(SyncValues.date(value));
                case Fields.DEADLINE -> project.setDeadline(SyncValues.date(value));
                case Fields.DELETED_AT -> project.setDeletedAt(SyncValues.instant(value));
                case Fields.MEMBERS -> project.setMembers(members(project.getMembers(), SyncValues.usersWithRoles(value), at));
                default -> { } // поле из более новой версии API — пропускаем
            }
        }

        Objects.requireNonNull(project.getName(), "name");
        Objects.requireNonNull(project.getOwnerId(), "ownerId");
        Objects.requireNonNull(project.getStatus(), "status");
        Objects.requireNonNull(project.getVisibility(), "visibility");

        List<UUID> users = new ArrayList<>();
        users.add(project.getOwnerId());
        project.getMembers().forEach(m -> users.add(m.userId()));
        knownUsers.ensureExist(users);

        project.setUpdatedAt(at);
        if (isNew) {
            project.setCreatedBy(project.getOwnerId());
            repository.insert(project);
        } else {
            repository.update(project);
        }
    }

    /** Новый состав участников; у тех, кто уже был в проекте, сохраняется дата вступления. */
    private static List<ProjectMember> members(List<ProjectMember> current, List<UserWithRole> incoming, Instant at) {
        Map<UUID, ProjectMember> byUser = current.stream()
                .collect(Collectors.toMap(ProjectMember::userId, Function.identity(), (a, b) -> a));
        return incoming.stream()
                .map(m -> new ProjectMember(m.userId(), MemberRole.valueOf(m.role()),
                        byUser.containsKey(m.userId()) ? byUser.get(m.userId()).joinedAt() : at))
                .toList();
    }
}
