package org.example.taskschedulerdesktop.persistence;

import org.example.taskschedulerdesktop.config.DevDataSeeder;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.example.taskschedulerdesktop.project.ProjectView;
import org.example.taskschedulerdesktop.task.TaskView;
import org.example.taskschedulerdesktop.shared.exception.DataAccessException;
import org.example.taskschedulerdesktop.project.H2ProjectRepository;
import org.example.taskschedulerdesktop.tag.H2TagRepository;
import org.example.taskschedulerdesktop.task.H2TaskRepository;
import org.example.taskschedulerdesktop.project.ProjectService;
import org.example.taskschedulerdesktop.project.ProjectServiceImpl;
import org.example.taskschedulerdesktop.tag.TagService;
import org.example.taskschedulerdesktop.task.NewTask;
import org.example.taskschedulerdesktop.task.TaskEdit;
import org.example.taskschedulerdesktop.task.TaskService;
import org.example.taskschedulerdesktop.task.TaskServiceImpl;
import org.example.taskschedulerdesktop.session.Session;
import org.example.taskschedulerdesktop.sync.ChangeTracker;
import org.example.taskschedulerdesktop.task.TaskPriority;
import org.example.taskschedulerdesktop.task.TaskStatus;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Слой данных клиента на настоящей H2 со всеми миграциями: запись сущностей,
 * журнал синхронизации (sync_log, field_versions) и транзакционность.
 */
class DataLayerTest {

    private static final UUID MARIA = UUID.fromString("00000000-0000-7000-8000-0000000000a2");

    private String url;
    private UnitOfWork uow;
    private TaskService taskService;
    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        url = "jdbc:h2:mem:test_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:migration").load().migrate();

        uow = new UnitOfWork(() -> DriverManager.getConnection(url, "sa", ""));
        DevDataSeeder.seed(uow);

        Session session = new Session(UUID.randomUUID(), DevDataSeeder.DEV_ADMIN_ID);
        ChangeTracker tracker = new ChangeTracker(uow, session);
        TagService tagService = new TagService(new H2TagRepository(uow), tracker, session);
        taskService = new TaskServiceImpl(uow, new H2TaskRepository(uow), tagService, tracker, session);
        projectService = new ProjectServiceImpl(uow, new H2ProjectRepository(uow), tracker, session);
    }

    @Test
    void createWritesEntityAndChangeLogInOneGo() throws SQLException {
        ProjectView project = projectService.create("Редизайн сайта", MARIA);
        TaskView task = taskService.create(new NewTask(
                "Написать ТЗ", project.id(), MARIA, TaskPriority.HIGH, LocalDate.now().plusDays(3), List.of("backend", "Backend", "срочно")));

        assertEquals("Написать ТЗ", task.title());
        assertEquals("Редизайн сайта", task.projectName());
        assertEquals("Мария Волкова", task.responsibleName());
        assertEquals(List.of("backend", "срочно"), task.tagNames(), "тег ищется без учёта регистра");
        assertFalse(task.synced(), "новая задача ещё не отправлена на сервер");

        assertEquals(1, syncLog("PROJECT", project.id(), "CREATE"));
        assertEquals(1, syncLog("TASK", task.id(), "CREATE"));
        assertEquals(2, count("SELECT COUNT(*) FROM sync_log WHERE entity_type = 'TAG' AND operation = 'CREATE'"));
        assertTrue(hasFieldVersion("TASK", task.id(), "title"));
        assertTrue(hasFieldVersion("TASK", task.id(), "assignees"));

        // Создатель проекта (демо-админ) не владелец — он должен остаться в проекте менеджером
        assertEquals(2, count("SELECT COUNT(*) FROM project_members WHERE project_id = '" + project.id() + "'"));

        ProjectView stats = projectService.findById(project.id());
        assertEquals(1, stats.totalTasks());
        assertEquals(0, stats.doneTasks());
    }

    @Test
    void updateLogsOnlyChangedFields() throws Exception {
        TaskView task = createTask();
        String titleVersionBefore = fieldVersion("TASK", task.id(), "title");
        Thread.sleep(5);

        TaskView done = taskService.update(edit(task, task.title(), TaskStatus.DONE, task.tagNames()));

        assertEquals(TaskStatus.DONE, done.status());
        assertEquals(1, syncLog("TASK", task.id(), "UPDATE"));
        assertEquals(titleVersionBefore, fieldVersion("TASK", task.id(), "title"), "название не менялось");
        assertTrue(fieldVersion("TASK", task.id(), "status").compareTo(titleVersionBefore) > 0);
        assertTrue(hasClosedAt(task.id()), "при переходе в DONE ставится closedAt");
        assertEquals(1, projectService.findById(task.projectId()).doneTasks());
    }

    @Test
    void updateWithoutChangesWritesNothing() throws SQLException {
        TaskView task = createTask();

        taskService.update(edit(task, task.title(), task.status(), List.of("срочно", "backend")));

        assertEquals(0, syncLog("TASK", task.id(), "UPDATE"), "порядок тегов не считается изменением");
    }

    @Test
    void failedWriteRollsBackEverything() throws SQLException {
        int logBefore = count("SELECT COUNT(*) FROM sync_log");
        int tagsBefore = count("SELECT COUNT(*) FROM tags");

        UUID missingProject = UUID.randomUUID();
        assertThrows(DataAccessException.class, () -> taskService.create(new NewTask(
                "Задача без проекта", missingProject, MARIA, TaskPriority.LOW, null, List.of("новый-тег"))));

        assertEquals(logBefore, count("SELECT COUNT(*) FROM sync_log"), "журнал откатился");
        assertEquals(tagsBefore, count("SELECT COUNT(*) FROM tags"), "созданный в той же транзакции тег откатился");
    }

    @Test
    void deleteIsSoftAndLogged() throws SQLException {
        TaskView task = createTask();

        taskService.delete(task.id());

        assertTrue(taskService.findAllViews().isEmpty());
        assertEquals(1, count("SELECT COUNT(*) FROM tasks WHERE deleted_at IS NOT NULL"));
        assertEquals(1, syncLog("TASK", task.id(), "DELETE"));
        assertTrue(hasFieldVersion("TASK", task.id(), "deletedAt"));
    }

    private TaskView createTask() {
        ProjectView project = projectService.create("Проект", DevDataSeeder.DEV_ADMIN_ID);
        return taskService.create(new NewTask(
                "Задача", project.id(), MARIA, TaskPriority.MEDIUM, LocalDate.now().plusDays(1), List.of("backend", "срочно")));
    }

    private TaskEdit edit(TaskView task, String title, TaskStatus status, List<String> tags) {
        return new TaskEdit(task.id(), title, task.description(), status, task.priority(),
                task.deadline(), task.responsibleId(), tags);
    }

    private int syncLog(String type, UUID id, String operation) throws SQLException {
        return count("SELECT COUNT(*) FROM sync_log WHERE entity_type = '" + type
                + "' AND entity_id = '" + id + "' AND operation = '" + operation + "' AND synced = FALSE");
    }

    private boolean hasFieldVersion(String type, UUID id, String field) throws SQLException {
        return fieldVersion(type, id, field) != null;
    }

    private String fieldVersion(String type, UUID id, String field) throws SQLException {
        String sql = "SELECT CAST(updated_at AS VARCHAR) FROM field_versions WHERE entity_type = ? AND entity_id = ? AND field_name = ?";
        try (Connection c = DriverManager.getConnection(url, "sa", ""); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, type);
            ps.setObject(2, id);
            ps.setString(3, field);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private boolean hasClosedAt(UUID taskId) throws SQLException {
        return count("SELECT COUNT(*) FROM tasks WHERE id = '" + taskId + "' AND closed_at IS NOT NULL") == 1;
    }

    private int count(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(url, "sa", "");
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
