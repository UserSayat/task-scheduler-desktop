package org.example.taskschedulerdesktop.sync;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.f4b6a3.uuid.UuidCreator;
import org.example.taskschedulerdesktop.admin.Employee;
import org.example.taskschedulerdesktop.admin.EmployeeAdminApi;
import org.example.taskschedulerdesktop.admin.EmployeeAdminService;
import org.example.taskschedulerdesktop.admin.EmployeeForm;
import org.example.taskschedulerdesktop.admin.H2EmployeeRepository;
import org.example.taskschedulerdesktop.admin.RoleAssignment;
import org.example.taskschedulerdesktop.auth.AuthService;
import org.example.taskschedulerdesktop.auth.AuthUser;
import org.example.taskschedulerdesktop.auth.TokenStore;
import org.example.taskschedulerdesktop.project.H2ProjectRepository;
import org.example.taskschedulerdesktop.project.MemberRole;
import org.example.taskschedulerdesktop.project.ProjectMemberView;
import org.example.taskschedulerdesktop.project.ProjectServiceImpl;
import org.example.taskschedulerdesktop.project.ProjectSyncAdapter;
import org.example.taskschedulerdesktop.project.ProjectView;
import org.example.taskschedulerdesktop.session.Session;
import org.example.taskschedulerdesktop.shared.api.ApiClient;
import org.example.taskschedulerdesktop.shared.api.ApiException;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.example.taskschedulerdesktop.sync.local.ChangeTracker;
import org.example.taskschedulerdesktop.sync.protocol.HttpSyncApi;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.net.URI;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Клиент против настоящего сервера: несколько «устройств» со своими H2 синхронизируются через API.
 * <p>
 * Запускается вручную на чистом сервере:
 * <pre>
 * mvn test -Dtest=ServerIntegrationTest -Dtaskhub.e2e.server=http://localhost:8080 \
 *          -Dtaskhub.e2e.email=admin@... -Dtaskhub.e2e.password=...
 * </pre>
 */
@EnabledIfSystemProperty(named = "taskhub.e2e.server", matches = ".+")
class ServerIntegrationTest {

    private static final String SERVER = System.getProperty("taskhub.e2e.server");
    private static final String ADMIN_EMAIL = System.getProperty("taskhub.e2e.email");
    private static final String ADMIN_PASSWORD = System.getProperty("taskhub.e2e.password");

    @Test
    void devicesExchangeChangesThroughServer() {
        Device laptop = new Device();
        Device desktop = new Device();

        AuthUser admin = laptop.auth.login(ADMIN_EMAIL, ADMIN_PASSWORD);
        laptop.sync.initialLoad();

        // Ноутбук создаёт проект и задачу и отправляет их
        ProjectView project = laptop.projects.create("E2E проект " + UUID.randomUUID().toString().substring(0, 8), admin.id());
        TaskView task = laptop.tasks.create(new NewTask("Подготовить релиз", project.id(), admin.id(),
                TaskPriority.MEDIUM, null, List.of()));
        SyncStatus pushed = laptop.sync.synchronize();
        assertEquals(SyncStatus.State.SYNCED, pushed.state(), String.valueOf(pushed.message()));
        assertEquals(0, pushed.pending());
        assertEquals(0, pushed.errors());

        // Второе устройство того же пользователя получает их через snapshot
        desktop.auth.login(ADMIN_EMAIL, ADMIN_PASSWORD);
        desktop.sync.initialLoad();
        assertEquals("Подготовить релиз", desktop.tasks.findViewById(task.id()).title());

        // Правки разных полей одной задачи на двух устройствах сливаются (field-level LWW)
        rename(desktop, task.id(), "Подготовить релиз 2.0");
        changePriority(laptop, task.id(), TaskPriority.CRITICAL);
        assertEquals(SyncStatus.State.SYNCED, desktop.sync.synchronize().state());
        assertEquals(SyncStatus.State.SYNCED, laptop.sync.synchronize().state());
        assertEquals(SyncStatus.State.SYNCED, desktop.sync.synchronize().state());
        for (Device device : List.of(laptop, desktop)) {
            TaskView merged = device.tasks.findViewById(task.id());
            assertEquals("Подготовить релиз 2.0", merged.title());
            assertEquals(TaskPriority.CRITICAL, merged.priority());
            assertTrue(merged.synced());
        }

        // Новый сотрудник становится владельцем проекта и получает его вместе со старыми задачами
        String employeePassword = "Temp-" + UUID.randomUUID();
        UUID employeeId = createEmployee(laptop.api, employeePassword);
        assertEquals(SyncStatus.State.SYNCED, laptop.sync.synchronize().state(), "сотрудник пришёл через pull");
        laptop.projects.update(project.id(), project.name(), employeeId);
        assertEquals(SyncStatus.State.SYNCED, laptop.sync.synchronize().state());

        Device employeeDevice = new Device();
        employeeDevice.auth.login(employeeEmail, employeePassword);
        employeeDevice.sync.initialLoad();
        assertEquals("Подготовить релиз 2.0", employeeDevice.tasks.findViewById(task.id()).title());

        // Новая задача приходит сотруднику обычным pull
        TaskView second = laptop.tasks.create(new NewTask("Проверить сборку", project.id(), employeeId,
                TaskPriority.HIGH, null, List.of()));
        laptop.sync.synchronize();
        employeeDevice.sync.synchronize();
        assertEquals("Проверить сборку", employeeDevice.tasks.findViewById(second.id()).title());

        // Тег может создать только администратор: сервер отклоняет тег и задачу, которая на него ссылается
        employeeDevice.tasks.create(new NewTask("Задача с новым тегом", project.id(), employeeId,
                TaskPriority.LOW, null, List.of("e2e-" + UUID.randomUUID().toString().substring(0, 6))));
        SyncStatus rejected = employeeDevice.sync.synchronize();
        assertEquals(SyncStatus.State.SYNCED, rejected.state());
        assertEquals(2, rejected.errors(), "тег (FORBIDDEN) и задача с тегом (VALIDATION_ERROR)");

        laptop.auth.logout();
        desktop.auth.logout();
        employeeDevice.auth.logout();
    }

    @Test
    void adminManagesEmployeesAndProjectMembers() {
        Device adminDevice = new Device();
        AuthUser admin = adminDevice.auth.login(ADMIN_EMAIL, ADMIN_PASSWORD);
        adminDevice.sync.initialLoad();
        EmployeeAdminApi adminApi = new EmployeeAdminApi(adminDevice.api);
        ProjectView project = adminDevice.projects.create("Проект команды " + UUID.randomUUID().toString().substring(0, 8), admin.id());
        assertEquals(SyncStatus.State.SYNCED, adminDevice.sync.synchronize().state());

        // Создание: тот же JSON, что отправляет диалог «Сотрудник»
        UUID id = UuidCreator.getTimeOrderedEpoch();
        String email = "staff." + id.toString().substring(24) + "@taskhub.test";
        String password = "Temp-" + UUID.randomUUID().toString().substring(0, 8);
        adminApi.create(new EmployeeAdminApi.CreateRequest(id, "Анна Смирнова", email, null, "Europe/Moscow",
                password, List.of(RoleAssignment.global(RoleAssignment.PROJECT_MANAGER))));
        adminDevice.sync.synchronize();
        Employee created = employee(adminDevice, id);
        assertTrue(created.hasGlobalRole(RoleAssignment.PROJECT_MANAGER), "роль пришла через pull");

        // Роль в проекте (её выдают не на этом экране) должна пережить смену глобальных ролей
        RoleAssignment projectRole = new RoleAssignment(RoleAssignment.PROJECT_MANAGER, "PROJECT", project.id());
        adminApi.setRoles(id, List.of(RoleAssignment.global(RoleAssignment.PROJECT_MANAGER), projectRole));
        adminDevice.sync.synchronize();
        Employee withProjectRole = employee(adminDevice, id);
        EmployeeForm form = new EmployeeForm("Анна Смирнова-Иванова", email, "Аналитик", "Asia/Omsk", true, false);
        adminApi.update(id, new EmployeeAdminApi.UpdateRequest(form.name(), email, form.position(), form.timezone()));
        adminApi.setRoles(id, EmployeeAdminService.newRoles(withProjectRole, form));
        adminDevice.sync.synchronize();
        Employee updated = employee(adminDevice, id);
        assertEquals("Анна Смирнова-Иванова", updated.name());
        assertEquals("Asia/Omsk", updated.timezone());
        assertTrue(updated.hasGlobalRole(RoleAssignment.ADMIN));
        assertFalse(updated.hasGlobalRole(RoleAssignment.PROJECT_MANAGER));
        assertTrue(updated.roles().contains(projectRole), "проектная роль сохранилась");

        // Участник приватного проекта получает его на своё устройство
        adminDevice.projects.update(project.id(), project.name(), admin.id(), Map.of(id, MemberRole.MEMBER));
        assertEquals(SyncStatus.State.SYNCED, adminDevice.sync.synchronize().state());
        Device staffDevice = new Device();
        staffDevice.auth.login(email, password);
        staffDevice.sync.initialLoad();
        List<ProjectMemberView> members = staffDevice.projects.findMembers(project.id());
        assertEquals(List.of(admin.id(), id), members.stream().map(ProjectMemberView::userId).toList());
        assertEquals(MemberRole.MEMBER, members.get(1).role());

        // Блокировка действует сразу, даже с неистёкшим access-токеном (API.md, 6.4).
        // Разблокировка и сброс пароля возвращают доступ.
        adminApi.setStatus(id, Employee.BLOCKED);
        assertEquals(SyncStatus.State.SIGNED_OUT, staffDevice.sync.synchronize().state());
        ApiException blocked = assertThrows(ApiException.class, () -> new Device().auth.login(email, password));
        assertTrue(blocked.is("USER_BLOCKED"), blocked.code());
        adminApi.setStatus(id, Employee.ACTIVE);
        String newPassword = "Reset-" + UUID.randomUUID().toString().substring(0, 8);
        adminApi.resetPassword(id, newPassword);
        assertThrows(ApiException.class, () -> new Device().auth.login(email, password), "старый пароль больше не подходит");
        new Device().auth.login(email, newPassword);

        // Удалённый сотрудник пропадает с экрана администратора
        adminApi.delete(id);
        adminDevice.sync.synchronize();
        assertTrue(employees(adminDevice).stream().noneMatch(e -> e.id().equals(id)));

        adminDevice.auth.logout();
    }

    private static Employee employee(Device device, UUID id) {
        return employees(device).stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
    }

    private static List<Employee> employees(Device device) {
        H2EmployeeRepository repository = new H2EmployeeRepository(device.uow);
        return device.uow.read(repository::findAll);
    }

    private String employeeEmail;

    private UUID createEmployee(ApiClient api, String password) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        employeeEmail = "employee." + id.toString().substring(24) + "@taskhub.test";
        Map<String, Object> body = Map.of(
                "id", id.toString(),
                "name", "Пётр Петров",
                "email", employeeEmail,
                "position", "QA-инженер",
                "timezone", "Europe/Moscow",
                "temporaryPassword", password,
                "roles", List.of());
        api.post("/users", body, new TypeReference<Map<String, Object>>() {});
        return id;
    }

    private static void rename(Device device, UUID taskId, String title) {
        TaskView t = device.tasks.findViewById(taskId);
        device.tasks.update(new TaskEdit(taskId, title, t.description(), t.status(), t.priority(),
                t.deadline(), t.responsibleId(), t.tagNames()));
    }

    private static void changePriority(Device device, UUID taskId, TaskPriority priority) {
        TaskView t = device.tasks.findViewById(taskId);
        device.tasks.update(new TaskEdit(taskId, t.title(), t.description(), t.status(), priority,
                t.deadline(), t.responsibleId(), t.tagNames()));
    }

    /** Отдельное устройство: своя база H2, свой deviceId, свои токены. */
    private static final class Device {

        final UnitOfWork uow;
        final ApiClient api;
        final AuthService auth;
        final SyncService sync;
        final TaskServiceImpl tasks;
        final ProjectServiceImpl projects;

        Device() {
            String url = "jdbc:h2:mem:e2e_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
            Flyway.configure().dataSource(url, "sa", "").locations("classpath:migration").load().migrate();
            uow = new UnitOfWork(() -> DriverManager.getConnection(url, "sa", ""));
            Session session = new Session(UuidCreator.getTimeOrderedEpoch(), null);

            api = new ApiClient(URI.create(SERVER + "/api/v1"));
            TokenStore tokens = new TokenStore(api, uow, session);
            api.setAccessTokens(tokens);
            auth = new AuthService(api, tokens, uow, session);

            ChangeTracker tracker = new ChangeTracker(uow, session);
            H2TaskRepository taskRepository = new H2TaskRepository(uow);
            H2ProjectRepository projectRepository = new H2ProjectRepository(uow);
            tasks = new TaskServiceImpl(uow, taskRepository, new TagService(new H2TagRepository(uow), tracker, session), tracker, session);
            projects = new ProjectServiceImpl(uow, projectRepository, tracker, session);

            UserSyncAdapter users = new UserSyncAdapter(uow);
            sync = SyncService.create(uow, new HttpSyncApi(api), session, users, List.of(users, new TagSyncAdapter(uow),
                    new ProjectSyncAdapter(uow, projectRepository, users), new TaskSyncAdapter(uow, taskRepository, users)));
        }
    }
}
