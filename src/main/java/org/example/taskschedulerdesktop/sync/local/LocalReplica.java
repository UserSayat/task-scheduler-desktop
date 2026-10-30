package org.example.taskschedulerdesktop.sync.local;

import org.example.taskschedulerdesktop.shared.database.Jdbc;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.example.taskschedulerdesktop.sync.protocol.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Локальная копия серверных данных целиком: очистка при смене пользователя и удаление проекта,
 * к которому пропал доступ. Вызывать внутри транзакции.
 */
public class LocalReplica {

    private static final Logger log = LoggerFactory.getLogger(LocalReplica.class);

    /** Таблицы с данными в порядке удаления (сначала те, что ссылаются на другие). */
    private static final List<String> DATA_TABLES = List.of(
            "task_tags", "task_observers", "task_assignees", "tasks",
            "project_members", "projects", "tags",
            "pending_notification_reads", "notifications",
            "field_versions", "sync_log", "sync_errors",
            "user_roles", "users");

    private final UnitOfWork uow;
    private final FieldVersionRepository fieldVersions;
    private final SyncLogRepository syncLog;
    private final SyncErrorRepository syncErrors;
    private final SyncStateRepository syncState;

    public LocalReplica(UnitOfWork uow) {
        this.uow = uow;
        this.fieldVersions = new FieldVersionRepository(uow);
        this.syncLog = new SyncLogRepository(uow);
        this.syncErrors = new SyncErrorRepository(uow);
        this.syncState = new SyncStateRepository(uow);
    }

    public boolean hasData() throws SQLException {
        try (Statement st = uow.connection().createStatement();
             ResultSet rs = st.executeQuery("SELECT EXISTS (SELECT 1 FROM users) OR EXISTS (SELECT 1 FROM sync_log)")) {
            rs.next();
            return rs.getBoolean(1);
        }
    }

    /**
     * Удалить все данные и курсор pull — при входе другого пользователя: его данные загрузит snapshot.
     * Идентификатор устройства сохраняется.
     */
    public void wipe() throws SQLException {
        try (Statement st = uow.connection().createStatement()) {
            for (String table : DATA_TABLES) {
                st.executeUpdate("DELETE FROM " + table);
            }
        }
        syncState.remove(SyncStateRepository.PULL_CURSOR);
        log.info("Local data wiped");
    }

    /**
     * Пользователь потерял доступ к проекту (REVOKE, API.md, 8.4): удалить проект, его задачи и связанные записи.
     * Неотправленные изменения этих сущностей переносятся в sync_errors с кодом FORBIDDEN.
     */
    public void removeProject(UUID projectId, Instant at) throws SQLException {
        List<UUID> taskIds = new ArrayList<>();
        try (PreparedStatement ps = uow.connection().prepareStatement("SELECT id FROM tasks WHERE project_id = ?")) {
            Jdbc.setUuid(ps, 1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    taskIds.add(Jdbc.getUuid(rs, "id"));
                }
            }
        }

        List<SyncLogRepository.Entry> lost = new ArrayList<>(syncLog.removeUnsynced(EntityType.PROJECT, List.of(projectId)));
        lost.addAll(syncLog.removeUnsynced(EntityType.TASK, taskIds));
        Map<UUID, SyncLogRepository.Entry> byEntity = new LinkedHashMap<>();
        lost.forEach(entry -> byEntity.putIfAbsent(entry.entityId(), entry));
        for (SyncLogRepository.Entry entry : byEntity.values()) {
            syncErrors.insert(entry.entityType(), entry.entityId(), entry.operation(), "FORBIDDEN",
                    "Нет доступа к проекту — изменение не отправлено на сервер", at);
        }

        fieldVersions.deleteForEntities(EntityType.TASK, taskIds);
        fieldVersions.deleteForEntities(EntityType.PROJECT, List.of(projectId));
        try (PreparedStatement ps = uow.connection().prepareStatement("DELETE FROM projects WHERE id = ?")) {
            Jdbc.setUuid(ps, 1, projectId);
            ps.executeUpdate(); // задачи, участники, исполнители и теги задач удалятся каскадом
        }
        log.info("Access to project {} revoked: removed with {} tasks, {} unsent changes", projectId, taskIds.size(), byEntity.size());
    }

    /**
     * Удалить сущность, созданную на этом устройстве, но не принятую сервером. Проект удаляется
     * вместе с задачами, тег — вместе со связями с задачами.
     *
     * @return проект удалённой задачи (чтобы обновить экраны) или {@code null}
     */
    public UUID removeLocalOnly(EntityType type, UUID id, Instant at) throws SQLException {
        UUID projectId = null;
        switch (type) {
            case PROJECT -> removeProject(id, at);
            case TASK -> {
                try (PreparedStatement ps = uow.connection().prepareStatement("SELECT project_id FROM tasks WHERE id = ?")) {
                    Jdbc.setUuid(ps, 1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        projectId = rs.next() ? Jdbc.getUuid(rs, "project_id") : null;
                    }
                }
                execute("DELETE FROM tasks WHERE id = ?", id);
            }
            case TAG -> {
                execute("DELETE FROM task_tags WHERE tag_id = ?", id);
                execute("DELETE FROM tags WHERE id = ?", id);
            }
            case USER -> { } // сотрудников клиент не создаёт
        }
        fieldVersions.deleteForEntities(type, List.of(id));
        log.info("Local-only {} {} removed", type, id);
        return projectId;
    }

    private void execute(String sql, UUID id) throws SQLException {
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            Jdbc.setUuid(ps, 1, id);
            ps.executeUpdate();
        }
    }
}
