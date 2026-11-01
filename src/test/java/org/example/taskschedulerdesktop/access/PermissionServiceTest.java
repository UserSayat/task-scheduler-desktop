package org.example.taskschedulerdesktop.access;

import org.example.taskschedulerdesktop.config.DevDataSeeder;
import org.example.taskschedulerdesktop.project.H2ProjectRepository;
import org.example.taskschedulerdesktop.project.MemberRole;
import org.example.taskschedulerdesktop.project.ProjectServiceImpl;
import org.example.taskschedulerdesktop.project.ProjectView;
import org.example.taskschedulerdesktop.session.Session;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.example.taskschedulerdesktop.sync.local.ChangeTracker;
import org.example.taskschedulerdesktop.tag.H2TagRepository;
import org.example.taskschedulerdesktop.tag.TagService;
import org.example.taskschedulerdesktop.task.H2TaskRepository;
import org.example.taskschedulerdesktop.task.NewTask;
import org.example.taskschedulerdesktop.task.TaskEdit;
import org.example.taskschedulerdesktop.task.TaskExtras;
import org.example.taskschedulerdesktop.task.TaskPriority;
import org.example.taskschedulerdesktop.task.TaskServiceImpl;
import org.example.taskschedulerdesktop.task.TaskStatus;
import org.example.taskschedulerdesktop.task.TaskView;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Права в интерфейсе совпадают с правилами сервера (API.md, 8.7).
 * Проект Марии (руководитель проектов): Елена — участник, Павел — наблюдатель, Дмитрий — не в проекте.
 */
class PermissionServiceTest {

    private static final UUID ADMIN = DevDataSeeder.DEV_ADMIN_ID;
    private static final UUID MARIA = UUID.fromString("00000000-0000-7000-8000-0000000000a2");
    private static final UUID ELENA = UUID.fromString("00000000-0000-7000-8000-0000000000a3");
    private static final UUID PAVEL = UUID.fromString("00000000-0000-7000-8000-0000000000a4");
    private static final UUID DMITRY = UUID.fromString("00000000-0000-7000-8000-0000000000a5");

    private Session session;
    private PermissionService permissions;
    private TaskServiceImpl tasks;
    private ProjectServiceImpl projects;
    private ProjectView project;
    private TaskView mariasTask;

    @BeforeEach
    void setUp() {
        String url = "jdbc:h2:mem:perm_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:migration").load().migrate();
        UnitOfWork uow = new UnitOfWork(() -> DriverManager.getConnection(url, "sa", ""));
        DevDataSeeder.seed(uow);

        session = new Session(UUID.randomUUID(), MARIA);
        permissions = new PermissionService(uow, new H2PermissionRepository(uow), session);
        ChangeTracker tracker = new ChangeTracker(uow, session);
        tasks = new TaskServiceImpl(uow, new H2TaskRepository(uow),
                new TagService(new H2TagRepository(uow), tracker, session, permissions), tracker, session,
                new H2PermissionRepository(uow));
        projects = new ProjectServiceImpl(uow, new H2ProjectRepository(uow), tracker, session);

        project = projects.create("Проект Марии", MARIA);
        Map<UUID, MemberRole> members = new LinkedHashMap<>();
        members.put(ELENA, MemberRole.MEMBER);
        members.put(PAVEL, MemberRole.OBSERVER);
        projects.update(project.id(), project.name(), MARIA, members);
        mariasTask = tasks.create(new NewTask("Задача Марии", project.id(), ELENA, TaskPriority.MEDIUM, null, List.of()));
    }

    @Test
    void onlyAdminAndProjectManagerCreateProjects() {
        assertTrue(as(ADMIN).globalRights().canCreateProjects());
        assertTrue(as(ADMIN).globalRights().admin());
        assertTrue(as(MARIA).globalRights().canCreateProjects());
        assertFalse(as(MARIA).globalRights().admin());
        assertFalse(as(ELENA).globalRights().canCreateProjects());
    }

    @Test
    void projectRightsFollowMemberRole() {
        assertEquals(new PermissionService.ProjectRights(true, true), as(MARIA).projectRights(project.id()), "руководитель");
        assertEquals(new PermissionService.ProjectRights(true, true), as(ADMIN).projectRights(project.id()), "администратор вне проекта");
        assertEquals(new PermissionService.ProjectRights(false, true), as(ELENA).projectRights(project.id()), "участник");
        assertEquals(new PermissionService.ProjectRights(false, false), as(PAVEL).projectRights(project.id()), "наблюдатель");
        assertEquals(new PermissionService.ProjectRights(false, false), as(DMITRY).projectRights(project.id()), "не в проекте");

        assertTrue(as(ELENA).projectsForNewTasks().contains(project.id()));
        assertFalse(as(PAVEL).projectsForNewTasks().contains(project.id()));
        assertNull(as(ADMIN).projectsForNewTasks(), "администратору доступны все проекты");
    }

    @Test
    void assigneeChangesOnlyStatusAuthorEditsOwnTask() {
        assertEquals(new PermissionService.TaskRights(true, true, false, true, false, false), as(MARIA).taskRights(mariasTask.id()), "автор может присоединиться");
        assertEquals(new PermissionService.TaskRights(false, true, false, false, true, true), as(ELENA).taskRights(mariasTask.id()), "исполнитель");
        assertEquals(PermissionService.TaskRights.NONE, as(PAVEL).taskRights(mariasTask.id()));

        TaskView elenasTask = tasks.create(new NewTask("Задача Елены", project.id(), ELENA, TaskPriority.LOW, null, List.of()));
        assertTrue(permissions.taskRights(elenasTask.id()).canEdit(), "автор может менять свою задачу");
    }

    @Test
    void onlyAdminCreatesNewTags() {
        as(ADMIN);
        tasks.create(new NewTask("С новым тегом", project.id(), ELENA, TaskPriority.LOW, null, List.of("backend")));

        as(ELENA);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> tasks.create(
                new NewTask("С чужим тегом", project.id(), ELENA, TaskPriority.LOW, null, List.of("frontend"))));
        assertTrue(refused.getMessage().contains("frontend"));
        // Существующий тег (без учёта регистра) использовать можно
        tasks.create(new NewTask("С существующим тегом", project.id(), ELENA, TaskPriority.LOW, null, List.of("Backend")));
    }

    @Test
    void assigneesAndObserversMustBeProjectMembers() {
        // Исполнители — участники, кроме наблюдателей; наблюдатели — любые участники и администратор
        ProjectTeam team = permissions.projectTeam(project.id());
        assertEquals(Set.of(MARIA, ELENA), team.assignable());
        assertEquals(Set.of(MARIA, ELENA, PAVEL, ADMIN), team.observers());

        IllegalArgumentException outsider = assertThrows(IllegalArgumentException.class, () -> tasks.create(
                new NewTask("Постороннему", project.id(), DMITRY, TaskPriority.LOW, null, List.of())));
        assertTrue(outsider.getMessage().contains("не участвует в проекте"));
        assertThrows(IllegalArgumentException.class, () -> tasks.create(
                new NewTask("Администратору", project.id(), ADMIN, TaskPriority.LOW, null, List.of())),
                "администратор вне проекта");
        IllegalArgumentException observer = assertThrows(IllegalArgumentException.class, () -> tasks.create(
                new NewTask("Наблюдателю", project.id(), PAVEL, TaskPriority.LOW, null, List.of())));
        assertTrue(observer.getMessage().contains("наблюдатель в проекте"));

        assertThrows(IllegalArgumentException.class, () -> tasks.update(new TaskEdit(mariasTask.id(), mariasTask.title(),
                null, null, null, null, ELENA, List.of(), new TaskExtras(null, List.of(), List.of(DMITRY)))),
                "наблюдатель не из проекта");
        assertThrows(IllegalArgumentException.class, () -> tasks.update(new TaskEdit(mariasTask.id(), mariasTask.title(),
                null, null, null, null, ELENA, List.of(), new TaskExtras(null, List.of(PAVEL), List.of()))),
                "соисполнитель с ролью наблюдателя");
        tasks.update(new TaskEdit(mariasTask.id(), mariasTask.title(), null, null, null, null, ELENA, List.of(),
                new TaskExtras(null, List.of(MARIA), List.of(PAVEL, ADMIN))));
    }

    @Test
    void assigneeRemovedFromProjectStaysAndDoesNotBlockEditing() {
        Map<UUID, MemberRole> withoutElena = new LinkedHashMap<>();
        withoutElena.put(PAVEL, MemberRole.OBSERVER);
        projects.update(project.id(), project.name(), MARIA, withoutElena);

        // Елена уже не в проекте, но осталась исполнителем — добавить наблюдателя можно
        tasks.update(new TaskEdit(mariasTask.id(), mariasTask.title(), null, null, null, null, ELENA, List.of(),
                new TaskExtras(null, List.of(), List.of(PAVEL))));
    }

    @Test
    void myTasksAreOpenTasksWhereIAmResponsibleOrCoExecutor() {
        // Мария — соисполнитель задачи, где ответственная Елена
        tasks.update(new TaskEdit(mariasTask.id(), mariasTask.title(), null, null, null, null, ELENA, List.of(),
                new TaskExtras(null, List.of(MARIA), List.of(PAVEL))));

        as(ELENA);
        assertEquals(List.of(mariasTask.id()), tasks.findMyOpenViews().stream().map(TaskView::id).toList(), "ответственная");
        as(MARIA);
        assertEquals(List.of(mariasTask.id()), tasks.findMyOpenViews().stream().map(TaskView::id).toList(), "соисполнитель");
        as(PAVEL);
        assertTrue(tasks.findMyOpenViews().isEmpty(), "наблюдатель — не исполнитель");

        as(ELENA);
        tasks.update(new TaskEdit(mariasTask.id(), mariasTask.title(), null, TaskStatus.DONE, null, null, ELENA, List.of()));
        assertTrue(tasks.findMyOpenViews().isEmpty(), "завершённые не показываются");
    }

    private PermissionService as(UUID userId) {
        session.signIn(userId);
        return permissions;
    }

    @Test
    void takeJoinAndLeaveTaskWithoutExecutor() {
        as(MARIA);
        TaskView free = tasks.create(new NewTask("Свободная", project.id(), null, TaskPriority.LOW, null, List.of()));
        assertEquals(new PermissionService.TaskRights(false, false, true, false, false, false), as(ELENA).taskRights(free.id()));
        assertFalse(as(PAVEL).taskRights(free.id()).canTake(), "наблюдатель не берёт задачи");

        as(ELENA);
        TaskView taken = tasks.take(free.id());
        assertEquals(ELENA, taken.responsibleId());
        assertEquals(TaskStatus.IN_PROGRESS, taken.status());
        assertEquals(java.time.LocalDate.now(), tasks.findExtras(free.id()).startDate(), "дата начала — день, когда взяли в работу");
        assertTrue(taken.creatorName() != null && taken.createdAt() != null, "в задаче виден автор и дата создания");

        assertTrue(as(MARIA).taskRights(free.id()).canJoin());
        tasks.join(free.id());
        assertEquals(List.of(MARIA), tasks.findExtras(free.id()).coExecutorIds());

        // Ответственный отказывается: задача снова новая, соисполнитель остаётся
        as(ELENA);
        TaskView left = tasks.leave(free.id());
        assertNull(left.responsibleId());
        assertEquals(TaskStatus.NEW, left.status());
        assertEquals(List.of(MARIA), tasks.findExtras(free.id()).coExecutorIds());
    }
}
