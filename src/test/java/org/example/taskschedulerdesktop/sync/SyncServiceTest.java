package org.example.taskschedulerdesktop.sync;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.taskschedulerdesktop.access.H2PermissionRepository;
import org.example.taskschedulerdesktop.access.PermissionService;
import org.example.taskschedulerdesktop.config.DevDataSeeder;
import org.example.taskschedulerdesktop.project.H2ProjectRepository;
import org.example.taskschedulerdesktop.project.ProjectServiceImpl;
import org.example.taskschedulerdesktop.project.ProjectSyncAdapter;
import org.example.taskschedulerdesktop.project.ProjectView;
import org.example.taskschedulerdesktop.session.Session;
import org.example.taskschedulerdesktop.shared.api.ApiException;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.example.taskschedulerdesktop.sync.adapter.SyncEntityAdapter;
import org.example.taskschedulerdesktop.sync.local.ChangeTracker;
import org.example.taskschedulerdesktop.sync.local.FieldVersionRepository;
import org.example.taskschedulerdesktop.sync.local.LocalReplica;
import org.example.taskschedulerdesktop.sync.protocol.EntityType;
import org.example.taskschedulerdesktop.sync.protocol.Operation;
import org.example.taskschedulerdesktop.sync.protocol.SyncApi;
import org.example.taskschedulerdesktop.sync.protocol.SyncMessages.Change;
import org.example.taskschedulerdesktop.sync.protocol.SyncMessages.ErrorBody;
import org.example.taskschedulerdesktop.sync.protocol.SyncMessages.FieldChange;
import org.example.taskschedulerdesktop.sync.protocol.SyncMessages.PullResponse;
import org.example.taskschedulerdesktop.sync.protocol.SyncMessages.PushResponse;
import org.example.taskschedulerdesktop.sync.protocol.SyncMessages.PushResult;
import org.example.taskschedulerdesktop.sync.protocol.SyncMessages.SnapshotResponse;
import org.example.taskschedulerdesktop.sync.protocol.SyncMessages.SyncedEntity;
import org.example.taskschedulerdesktop.sync.protocol.SyncValues;
import org.example.taskschedulerdesktop.sync.pull.ChangeApplier;
import org.example.taskschedulerdesktop.tag.H2TagRepository;
import org.example.taskschedulerdesktop.tag.TagService;
import org.example.taskschedulerdesktop.tag.TagSyncAdapter;
import org.example.taskschedulerdesktop.task.H2TaskRepository;
import org.example.taskschedulerdesktop.task.NewTask;
import org.example.taskschedulerdesktop.task.TaskEdit;
import org.example.taskschedulerdesktop.task.TaskPriority;
import org.example.taskschedulerdesktop.task.TaskServiceImpl;
import org.example.taskschedulerdesktop.task.TaskSyncAdapter;
import org.example.taskschedulerdesktop.task.TaskView;
import org.example.taskschedulerdesktop.user.UserSyncAdapter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Синхронизация клиента на настоящей H2 со всеми миграциями и сервером-заглушкой:
 * сборка push из журнала, ответы сервера, pull по LWW, snapshot и потеря доступа к проекту.
 */
class SyncServiceTest {

    private static final UUID MARIA = UUID.fromString("00000000-0000-7000-8000-0000000000a2");
    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

    private String url;
    private UnitOfWork uow;
    private Session session;
    private FakeServer server;
    private SyncService sync;
    private ChangeApplier applier;
    private TaskServiceImpl taskService;
    private ProjectServiceImpl projectService;

    @BeforeEach
    void setUp() {
        url = "jdbc:h2:mem:sync_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:migration").load().migrate();
        uow = new UnitOfWork(() -> DriverManager.getConnection(url, "sa", ""));
        DevDataSeeder.seed(uow);

        session = new Session(UUID.randomUUID(), DevDataSeeder.DEV_ADMIN_ID);
        ChangeTracker tracker = new ChangeTracker(uow, session);
        H2TaskRepository taskRepository = new H2TaskRepository(uow);
        H2ProjectRepository projectRepository = new H2ProjectRepository(uow);
        taskService = new TaskServiceImpl(uow, taskRepository,
                new TagService(new H2TagRepository(uow), tracker, session,
                        new PermissionService(uow, new H2PermissionRepository(uow), session)), tracker, session,
                new H2PermissionRepository(uow));
        projectService = new ProjectServiceImpl(uow, projectRepository, tracker, session);

        UserSyncAdapter users = new UserSyncAdapter(uow);
        List<SyncEntityAdapter> adapters = List.of(users, new TagSyncAdapter(uow),
                new ProjectSyncAdapter(uow, projectRepository, users), new TaskSyncAdapter(uow, taskRepository, users));
        server = new FakeServer();
        sync = SyncService.create(uow, server, session, users, adapters);

        Map<EntityType, SyncEntityAdapter> byType = new HashMap<>();
        adapters.forEach(a -> byType.put(a.type(), a));
        applier = new ChangeApplier(uow, byType, new FieldVersionRepository(uow), new LocalReplica(uow), users, session);
    }

    // ===== push =====

    @Test
    void pushSendsLocalChangesAndMarksThemSynced() throws SQLException {
        TaskView task = createTask("Написать ТЗ");

        SyncStatus status = sync.synchronize();

        assertEquals(SyncStatus.State.SYNCED, status.state());
        assertEquals(0, status.pending());
        assertEquals(0, count("SELECT COUNT(*) FROM sync_log WHERE synced = FALSE"));

        List<Change> pushed = server.pushes.get(0);
        Change project = find(pushed, EntityType.PROJECT, task.projectId());
        assertEquals(Operation.CREATE, project.operation());
        assertEquals("OWNER", field(project, "members").value().get(0).get("role").asText());

        Change created = find(pushed, EntityType.TASK, task.id());
        assertEquals(Operation.CREATE, created.operation());
        assertEquals("Написать ТЗ", field(created, "title").value().asText());
        assertEquals(DevDataSeeder.DEV_ADMIN_ID.toString(), field(created, "creatorId").value().asText());
        assertEquals(MARIA.toString(), field(created, "assignees").value().get(0).get("userId").asText());
        assertFalse(created.syncLogIds().isEmpty());
        assertTrue(pushed.indexOf(project) < pushed.indexOf(created), "проект отправляется раньше задачи");
        assertEquals(1, server.snapshotCalls.size(), "курсора ещё не было — сначала полный snapshot");
    }

    @Test
    void pushSendsOnlyFieldsChangedSinceLastSync() {
        TaskView task = createTask("Задача");
        sync.synchronize();

        taskService.update(new TaskEdit(task.id(), "Новое название", task.description(), task.status(),
                task.priority(), task.deadline(), task.responsibleId(), task.tagNames()));
        sync.synchronize();

        Change update = find(server.pushes.get(1), EntityType.TASK, task.id());
        assertEquals(Operation.UPDATE, update.operation());
        assertEquals(List.of("title"), update.fields().stream().map(FieldChange::name).toList());
    }

    @Test
    void staleFieldsFromServerOverwriteLocalValue() throws SQLException {
        TaskView task = createTask("Локальное название");
        Instant serverTime = Instant.now().plusSeconds(60).truncatedTo(ChronoUnit.MICROS);
        server.onPush = change -> new PushResult(change.entityType(), change.entityId(), "APPLIED",
                change.entityType() == EntityType.TASK
                        ? List.of(new FieldChange("title", SyncValues.node("Название с сервера"), serverTime))
                        : List.of(),
                null);

        sync.synchronize();

        assertEquals("Название с сервера", taskService.findViewById(task.id()).title());
        assertEquals("server", text("SELECT device_id FROM field_versions WHERE entity_id = '" + task.id() + "' AND field_name = 'title'"));
        assertEquals(0, count("SELECT COUNT(*) FROM sync_errors"), "stale — не ошибка (D5)");
    }

    @Test
    void rejectedChangeIsRecordedAndNotSentAgain() throws SQLException {
        TaskView task = createTask("Задача");
        server.onPush = change -> change.entityType() == EntityType.TASK
                ? new PushResult(change.entityType(), change.entityId(), "REJECTED", null,
                new ErrorBody("FORBIDDEN", "Нет прав", null))
                : new PushResult(change.entityType(), change.entityId(), "APPLIED", List.of(), null);

        SyncStatus first = sync.synchronize();
        sync.synchronize();

        assertEquals(1, first.errors());
        assertEquals("FORBIDDEN", text("SELECT error_code FROM sync_errors WHERE entity_id = '" + task.id() + "'"));
        assertTrue(server.pushes.stream().skip(1).flatMap(List::stream)
                .noneMatch(change -> change.entityId().equals(task.id())), "отклонённое изменение не повторяется");
        assertFalse(taskService.findViewById(task.id()).synced(), "задача остаётся помеченной как неотправленная");
    }

    @Test
    void retryResendsRejectedChange() throws SQLException {
        TaskView task = createTask("Задача");
        server.onPush = change -> change.entityType() == EntityType.TASK
                ? new PushResult(change.entityType(), change.entityId(), "REJECTED", null, new ErrorBody("FORBIDDEN", "Нет прав", null))
                : new PushResult(change.entityType(), change.entityId(), "APPLIED", List.of(), null);
        sync.synchronize();
        assertEquals(1, sync.rejectedChanges().size());
        assertEquals("Задача", sync.rejectedChanges().get(0).name());
        assertEquals(Operation.CREATE, sync.rejectedChanges().get(0).operation());

        // Права выдали — повторяем
        server.onPush = change -> new PushResult(change.entityType(), change.entityId(), "APPLIED", List.of(), null);
        sync.retryRejected(EntityType.TASK, task.id());
        SyncStatus status = sync.synchronize();

        assertEquals(0, status.errors());
        assertEquals(0, status.pending());
        assertTrue(sync.rejectedChanges().isEmpty());
        assertTrue(taskService.findViewById(task.id()).synced());
    }

    @Test
    void discardingRejectedCreationRemovesLocalTask() throws SQLException {
        TaskView task = createTask("Задача");
        server.onPush = change -> change.entityType() == EntityType.TASK
                ? new PushResult(change.entityType(), change.entityId(), "REJECTED", null, new ErrorBody("FORBIDDEN", "Нет прав", null))
                : new PushResult(change.entityType(), change.entityId(), "APPLIED", List.of(), null);
        sync.synchronize();

        sync.discardRejected(EntityType.TASK, task.id(), true);

        assertEquals(0, count("SELECT COUNT(*) FROM tasks WHERE id = '" + task.id() + "'"));
        assertEquals(0, count("SELECT COUNT(*) FROM sync_log WHERE synced = FALSE"));
        assertEquals(0, sync.status().errors());
    }

    @Test
    void discardingRejectedEditRestoresServerValues() throws SQLException {
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        SyncedEntity serverProject = project(projectId, "Проект", MARIA, T0);
        SyncedEntity serverTask = task(taskId, projectId, "Название с сервера", MARIA, T0);
        applyFromServer(serverProject);
        applyFromServer(serverTask);
        server.projectSnapshots.put(projectId, List.of(serverProject, serverTask));

        TaskView local = taskService.findViewById(taskId);
        taskService.update(new TaskEdit(taskId, "Моя правка", local.description(), local.status(), local.priority(),
                local.deadline(), local.responsibleId(), local.tagNames()));
        server.onPush = change -> new PushResult(change.entityType(), change.entityId(), "REJECTED", null,
                new ErrorBody("FORBIDDEN", "Нет прав", null));
        sync.synchronize();
        assertEquals(Operation.UPDATE, sync.rejectedChanges().get(0).operation());

        sync.discardRejected(EntityType.TASK, taskId, false);

        assertEquals("Название с сервера", taskService.findViewById(taskId).title(), "серверное значение выиграло");
        assertEquals(0, count("SELECT COUNT(*) FROM sync_log WHERE synced = FALSE"));
        assertEquals(0, sync.status().errors());
        assertTrue(taskService.findViewById(taskId).synced());
    }

    // ===== pull =====

    @Test
    void pullCreatesEntitiesAndPlaceholdersForUnknownUsers() throws SQLException {
        UUID newUser = UUID.randomUUID();
        UUID formerEmployee = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        server.pulls.add(page(
                task(taskId, projectId, "Задача с сервера", formerEmployee, T0),
                project(projectId, "Проект с сервера", newUser, T0),
                user(newUser, "Новый Сотрудник", "new@taskhub.test", T0)));

        sync.synchronize();

        TaskView task = taskService.findViewById(taskId);
        assertEquals("Задача с сервера", task.title());
        assertEquals("Проект с сервера", task.projectName());
        assertTrue(task.synced());
        assertEquals("Новый Сотрудник", text("SELECT name FROM users WHERE id = '" + newUser + "'"));
        assertEquals("DELETED", text("SELECT status FROM users WHERE id = '" + formerEmployee + "'"),
                "автор задачи, которого нет в snapshot, получает заглушку");
        assertEquals("cursor-1", text("SELECT state_value FROM sync_state WHERE state_key = 'pull_cursor'"));
        assertTrue(server.snapshotCalls.contains(projectId), "новый проект догружается snapshot-ом проекта");
    }

    @Test
    void pullKeepsNewerLocalEditAndAppliesOtherFields() {
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        server.pulls.add(page(project(projectId, "Проект", MARIA, T0), task(taskId, projectId, "Старое", MARIA, T0)));
        sync.synchronize();

        TaskView task = taskService.findViewById(taskId);
        server.onPush = change -> null; // сервер недоступен для push — правка остаётся локальной
        taskService.update(new TaskEdit(taskId, "Моя правка", task.description(), task.status(),
                task.priority(), task.deadline(), task.responsibleId(), task.tagNames()));

        // С сервера приходит более старое название и более новый приоритет
        List<FieldChange> fields = new ArrayList<>(task(taskId, projectId, "Чужая старая правка", MARIA, T0.plusSeconds(10)).fields());
        fields.removeIf(f -> f.name().equals("priority"));
        fields.add(new FieldChange("priority", SyncValues.node("CRITICAL"), Instant.now().plusSeconds(30)));
        applyFromServer(new SyncedEntity(EntityType.TASK, taskId, Operation.UPDATE, MARIA, T0, fields));

        TaskView merged = taskService.findViewById(taskId);
        assertEquals("Моя правка", merged.title(), "локальная правка новее — она сохраняется");
        assertEquals(TaskPriority.CRITICAL, merged.priority(), "более новое поле с сервера применяется");
    }

    @Test
    void taskInUnknownProjectLoadsProjectSnapshot() {
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        server.pulls.add(page(task(taskId, projectId, "Задача", MARIA, T0)));
        server.projectSnapshots.put(projectId, List.of(project(projectId, "Проект", MARIA, T0), task(taskId, projectId, "Задача", MARIA, T0)));

        sync.synchronize();

        assertEquals("Проект", taskService.findViewById(taskId).projectName());
    }

    @Test
    void outdatedCursorFallsBackToFullSnapshot() {
        sync.synchronize();
        server.pulls.add(new ApiException(422, "VALIDATION_ERROR", "Курсор устарел", null));

        SyncStatus status = sync.synchronize();

        assertEquals(SyncStatus.State.SYNCED, status.state());
        assertEquals(2, server.snapshotCalls.stream().filter(id -> id == null).count());
    }

    @Test
    void fullSnapshotRemovesWhatServerNoLongerHas() throws SQLException {
        TaskView kept = createTask("Есть на сервере");
        sync.synchronize();
        TaskView gone = taskService.create(new NewTask("Удалена на сервере", kept.projectId(), MARIA,
                TaskPriority.LOW, null, List.of()));
        sync.synchronize();
        TaskView rejected = taskService.create(new NewTask("Отклонена сервером", kept.projectId(), MARIA,
                TaskPriority.LOW, null, List.of()));

        // На сервере задачу удалили, а журнал pull устарел — клиент загружает всё заново
        server.known.remove(gone.id());
        server.onPush = change -> change.entityId().equals(rejected.id())
                ? new PushResult(change.entityType(), change.entityId(), "REJECTED", null, new ErrorBody("FORBIDDEN", "Нет прав", null))
                : new PushResult(change.entityType(), change.entityId(), "APPLIED", List.of(), null);
        server.pulls.add(new ApiException(422, "VALIDATION_ERROR", "outdated cursor", null));
        sync.synchronize();

        assertEquals(1, count("SELECT COUNT(*) FROM tasks WHERE id = '" + kept.id() + "'"));
        assertEquals(0, count("SELECT COUNT(*) FROM tasks WHERE id = '" + gone.id() + "'"), "призрак удалён");
        assertEquals(1, count("SELECT COUNT(*) FROM tasks WHERE id = '" + rejected.id() + "'"), "отклонённую решает пользователь");
    }

    @Test
    void revokeRemovesProjectAndMovesUnsentChangesToErrors() throws SQLException {
        UUID projectId = UUID.randomUUID();
        server.pulls.add(page(project(projectId, "Чужой проект", MARIA, T0)));
        sync.synchronize();
        TaskView unsent = taskService.create(new NewTask("Не успела уйти", projectId, MARIA, TaskPriority.LOW, null, List.of()));

        applyFromServer(new SyncedEntity(EntityType.PROJECT, projectId, Operation.REVOKE, null, T0, List.of()));

        assertEquals(0, count("SELECT COUNT(*) FROM projects WHERE id = '" + projectId + "'"));
        assertEquals(0, count("SELECT COUNT(*) FROM tasks WHERE id = '" + unsent.id() + "'"));
        assertEquals(0, count("SELECT COUNT(*) FROM sync_log WHERE entity_id = '" + unsent.id() + "'"));
        assertEquals("FORBIDDEN", text("SELECT error_code FROM sync_errors WHERE entity_id = '" + unsent.id() + "'"));
    }

    // ===== Вспомогательное =====

    private TaskView createTask(String title) {
        ProjectView project = projectService.create("Проект", DevDataSeeder.DEV_ADMIN_ID);
        addEveryone(project.id()); // исполнитель — участник проекта
        return taskService.create(new NewTask(title, project.id(), MARIA, TaskPriority.HIGH, null, List.of()));
    }

    private void applyFromServer(SyncedEntity entity) {
        uow.inTransaction(() -> applier.apply(List.of(entity)));
    }

    private static Change find(List<Change> changes, EntityType type, UUID id) {
        return changes.stream().filter(c -> c.entityType() == type && c.entityId().equals(id)).findFirst().orElseThrow();
    }

    private static FieldChange field(Change change, String name) {
        return change.fields().stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow();
    }

    private static PullResponse page(SyncedEntity... entities) {
        return new PullResponse(List.of(entities), "cursor-1", false, Instant.now());
    }

    private static SyncedEntity user(UUID id, String name, String email, Instant at) {
        return new SyncedEntity(EntityType.USER, id, Operation.CREATE, null, at, List.of(
                f("name", name, at), f("email", email, at), f("position", null, at), f("avatarUrl", null, at),
                f("timezone", "Europe/Moscow", at), f("status", "ACTIVE", at), f("deletedAt", null, at),
                new FieldChange("roles", array(), at)));
    }

    private static SyncedEntity project(UUID id, String name, UUID owner, Instant at) {
        return new SyncedEntity(EntityType.PROJECT, id, Operation.CREATE, owner, at, List.of(
                f("name", name, at), f("description", null, at), f("ownerId", owner, at), f("status", "ACTIVE", at),
                f("visibility", "PUBLIC", at), f("startDate", null, at), f("deadline", null, at), f("deletedAt", null, at),
                new FieldChange("members", SyncValues.usersWithRoles(List.of(new SyncValues.UserWithRole(owner, "OWNER"))), at)));
    }

    private static SyncedEntity task(UUID id, UUID projectId, String title, UUID creator, Instant at) {
        return new SyncedEntity(EntityType.TASK, id, Operation.CREATE, creator, at, List.of(
                f("title", title, at), f("description", null, at), f("projectId", projectId, at), f("creatorId", creator, at),
                f("status", "NEW", at), f("priority", "MEDIUM", at), f("startDate", null, at), f("deadline", "2026-11-01", at),
                f("closedAt", null, at), f("deletedAt", null, at),
                new FieldChange("assignees", SyncValues.usersWithRoles(List.of(new SyncValues.UserWithRole(creator, "RESPONSIBLE"))), at),
                new FieldChange("observers", array(), at), new FieldChange("tags", array(), at)));
    }

    private static FieldChange f(String name, Object value, Instant at) {
        return new FieldChange(name, SyncValues.node(value), at);
    }

    private static JsonNode array() {
        return SyncValues.uuidArray(List.of());
    }

    /** Все сотрудники — участники проекта. */
    private void addEveryone(UUID projectId) {
        try (Connection c = DriverManager.getConnection(url, "sa", "");
             PreparedStatement ps = c.prepareStatement("INSERT INTO project_members (id, project_id, user_id, role) SELECT RANDOM_UUID(), p.id, u.id, 'MEMBER' FROM projects p CROSS JOIN users u WHERE p.id = ? AND NOT EXISTS (SELECT 1 FROM project_members m WHERE m.project_id = p.id AND m.user_id = u.id)")) {
            ps.setObject(1, projectId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private int count(String sql) throws SQLException {
        return Integer.parseInt(text(sql));
    }

    private String text(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(url, "sa", "");
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    /**
     * Сервер-заглушка: принимает все изменения (или как скажет {@link #onPush}), отдаёт заранее заданные
     * страницы pull и snapshot.
     */
    private static class FakeServer implements SyncApi {

        final List<List<Change>> pushes = new ArrayList<>();
        /** null в ответе — сервер недоступен. */
        Function<Change, PushResult> onPush = change ->
                new PushResult(change.entityType(), change.entityId(), "APPLIED", List.of(), null);
        final Deque<Object> pulls = new ArrayDeque<>();
        final Map<UUID, List<SyncedEntity>> projectSnapshots = new HashMap<>();
        final List<UUID> snapshotCalls = new ArrayList<>();
        /** Что есть на «сервере»: принятые изменения и заданные проекты. Полный snapshot отдаёт их id. */
        final Map<UUID, EntityType> known = new LinkedHashMap<>();

        @Override
        public PushResponse push(List<Change> changes) {
            List<PushResult> results = new ArrayList<>();
            for (Change change : changes) {
                PushResult result = onPush.apply(change);
                if (result == null) {
                    throw new org.example.taskschedulerdesktop.shared.api.OfflineException("offline", null);
                }
                results.add(result);
                if (result.applied()) {
                    known.put(change.entityId(), change.entityType());
                }
            }
            pushes.add(changes);
            return new PushResponse(results, Instant.now());
        }

        @Override
        public PullResponse pull(String cursor) {
            Object next = pulls.poll();
            if (next instanceof RuntimeException e) {
                throw e;
            }
            return next != null ? (PullResponse) next : new PullResponse(List.of(), cursor, false, Instant.now());
        }

        @Override
        public SnapshotResponse snapshot(UUID projectId, int page) {
            snapshotCalls.add(projectId);
            if (projectId != null) {
                return new SnapshotResponse(projectSnapshots.getOrDefault(projectId, List.of()), page, false,
                        "cursor-0", Instant.now());
            }
            // Полный snapshot: всё известное серверу. Пустые поля ничего не меняют локально,
            // но говорят клиенту, что сущность на сервере есть
            List<SyncedEntity> all = new ArrayList<>();
            projectSnapshots.values().forEach(entities -> entities.forEach(e -> known.putIfAbsent(e.entityId(), e.entityType())));
            known.forEach((id, type) -> all.add(new SyncedEntity(type, id, Operation.UPDATE, null, Instant.now(), List.of())));
            return new SnapshotResponse(all, page, false, "cursor-0", Instant.now());
        }
    }
}
