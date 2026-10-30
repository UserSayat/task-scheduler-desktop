package org.example.taskschedulerdesktop.sync;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.f4b6a3.uuid.UuidCreator;
import org.example.taskschedulerdesktop.settings.SettingsService;
import org.example.taskschedulerdesktop.settings.SettingsRepository;
import org.example.taskschedulerdesktop.settings.AppSettings;
import org.example.taskschedulerdesktop.user.ProfileService;
import org.example.taskschedulerdesktop.user.H2UserRepository;
import org.example.taskschedulerdesktop.task.TaskExtras;
import org.example.taskschedulerdesktop.tag.TagAdminService;
import org.example.taskschedulerdesktop.sync.protocol.EntityType;
import org.example.taskschedulerdesktop.project.Visibility;
import org.example.taskschedulerdesktop.project.ProjectInfo;
import org.example.taskschedulerdesktop.access.H2PermissionRepository;
import org.example.taskschedulerdesktop.access.PermissionService;
import org.example.taskschedulerdesktop.admin.Employee;
import org.example.taskschedulerdesktop.admin.EmployeeAdminApi;
import org.example.taskschedulerdesktop.admin.EmployeeAdminService;
import org.example.taskschedulerdesktop.admin.EmployeeForm;
import org.example.taskschedulerdesktop.admin.H2EmployeeRepository;
import org.example.taskschedulerdesktop.admin.RoleAssignment;
import org.example.taskschedulerdesktop.auth.AuthService;
import org.example.taskschedulerdesktop.auth.AuthUser;
import org.example.taskschedulerdesktop.auth.TokenStore;
import org.example.taskschedulerdesktop.notification.H2NotificationRepository;
import org.example.taskschedulerdesktop.notification.Notification;
import org.example.taskschedulerdesktop.notification.NotificationService;
import org.example.taskschedulerdesktop.notification.NotificationSync;
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
import org.example.taskschedulerdesktop.task.TaskStatus;
import org.example.taskschedulerdesktop.task.TaskSyncAdapter;
import org.example.taskschedulerdesktop.task.TaskView;
import org.example.taskschedulerdesktop.user.UserSyncAdapter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
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
        firstLogin(employeeDevice, employeeEmail, employeePassword);
        employeeDevice.sync.initialLoad();
        assertEquals("Подготовить релиз 2.0", employeeDevice.tasks.findViewById(task.id()).title());

        // Новая задача приходит сотруднику обычным pull
        TaskView second = laptop.tasks.create(new NewTask("Проверить сборку", project.id(), employeeId,
                TaskPriority.HIGH, null, List.of()));
        laptop.sync.synchronize();
        employeeDevice.sync.synchronize();
        assertEquals("Проверить сборку", employeeDevice.tasks.findViewById(second.id()).title());

        // Уведомления приходят вместе с синхронизацией; отметка «прочитано» уходит на сервер при следующей
        List<Notification> received = employeeDevice.notifications.findAll();
        assertTrue(received.stream().anyMatch(n -> n.type().equals("TASK_ASSIGNED") && second.id().equals(n.entityId())),
                "назначение задачи");
        int unread = employeeDevice.notifications.unreadCount();
        assertTrue(unread > 0);
        employeeDevice.notifications.markAllRead();
        assertEquals(0, employeeDevice.notifications.unreadCount());
        employeeDevice.sync.synchronize();
        Map<String, Object> serverCount = employeeDevice.api.get("/notifications/unread-count",
                new TypeReference<Map<String, Object>>() {});
        assertEquals(0, ((Number) serverCount.get("count")).intValue(), "отметки дошли до сервера");

        // Сотрудник меняет статус — автор задачи (администратор) получает уведомление
        TaskView fromEmployee = employeeDevice.tasks.findViewById(second.id());
        employeeDevice.tasks.update(new TaskEdit(second.id(), fromEmployee.title(), fromEmployee.description(),
                TaskStatus.IN_PROGRESS, fromEmployee.priority(), fromEmployee.deadline(),
                fromEmployee.responsibleId(), fromEmployee.tagNames()));
        employeeDevice.sync.synchronize();
        laptop.sync.synchronize();
        assertTrue(laptop.notifications.findAll().stream().anyMatch(n -> n.type().equals("STATUS_CHANGED")
                && second.id().equals(n.entityId()) && n.body().contains("К выполнению → В работе")));

        // Тег может создать только администратор: клиент не создаёт ни тег, ни задачу с ним,
        // поэтому на сервер не уходит изменение, которое он бы отклонил
        assertThrows(IllegalArgumentException.class, () -> employeeDevice.tasks.create(new NewTask(
                "Задача с новым тегом", project.id(), employeeId, TaskPriority.LOW, null,
                List.of("e2e-" + UUID.randomUUID().toString().substring(0, 6)))));
        SyncStatus afterRefusal = employeeDevice.sync.synchronize();
        assertEquals(SyncStatus.State.SYNCED, afterRefusal.state());
        assertEquals(0, afterRefusal.pending());
        assertEquals(0, afterRefusal.errors());

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
        String ownPassword = firstLogin(staffDevice, email, password);
        staffDevice.sync.initialLoad();
        List<ProjectMemberView> members = staffDevice.projects.findMembers(project.id());
        assertEquals(List.of(admin.id(), id), members.stream().map(ProjectMemberView::userId).toList());
        assertEquals(MemberRole.MEMBER, members.get(1).role());

        // Блокировка действует сразу, даже с неистёкшим access-токеном (API.md, 6.4).
        // Разблокировка и сброс пароля возвращают доступ.
        adminApi.setStatus(id, Employee.BLOCKED);
        assertEquals(SyncStatus.State.SIGNED_OUT, staffDevice.sync.synchronize().state());
        ApiException blocked = assertThrows(ApiException.class, () -> new Device().auth.login(email, ownPassword));
        assertTrue(blocked.is("USER_BLOCKED"), blocked.code());
        adminApi.setStatus(id, Employee.ACTIVE);
        String newPassword = "Reset-" + UUID.randomUUID().toString().substring(0, 8);
        adminApi.resetPassword(id, newPassword);
        assertThrows(ApiException.class, () -> new Device().auth.login(email, ownPassword), "старый пароль больше не подходит");
        assertTrue(new Device().auth.login(email, newPassword).mustChangePassword(), "после сброса пароль снова временный");

        // Удалённый сотрудник пропадает с экрана администратора
        adminApi.delete(id);
        adminDevice.sync.synchronize();
        assertTrue(employees(adminDevice).stream().noneMatch(e -> e.id().equals(id)));

        adminDevice.auth.logout();
    }

    @Test
    void cardFieldsProfileTagsAndRejectedChangesGoThroughServer() {
        Device adminDevice = new Device();
        AuthUser admin = adminDevice.auth.login(ADMIN_EMAIL, ADMIN_PASSWORD);
        adminDevice.sync.initialLoad();
        EmployeeAdminApi adminApi = new EmployeeAdminApi(adminDevice.api);
        UUID staffId = UuidCreator.getTimeOrderedEpoch();
        String email = "card." + staffId.toString().substring(24) + "@taskhub.test";
        String password = "Temp-" + UUID.randomUUID().toString().substring(0, 8);
        adminApi.create(new EmployeeAdminApi.CreateRequest(staffId, "Ольга Белова", email, null, "Europe/Moscow",
                password, List.of()));
        adminDevice.sync.synchronize();

        // Тег администратора
        String tagName = "e2e-" + UUID.randomUUID().toString().substring(0, 6);
        adminDevice.tags.create(tagName, "#1A3A8F");

        // Проект с описанием, видимостью и датами; сотрудник — наблюдатель
        ProjectView project = adminDevice.projects.create("Карточки " + UUID.randomUUID().toString().substring(0, 6), admin.id());
        LocalDate start = LocalDate.now();
        adminDevice.projects.update(project.id(), project.name(), admin.id(), Map.of(staffId, MemberRole.OBSERVER),
                new ProjectInfo("Цели проекта", Visibility.PRIVATE, start, start.plusMonths(1)));

        // Задача: описание, дата начала, соисполнитель и наблюдатель
        TaskView task = adminDevice.tasks.create(new NewTask("Подготовить отчёт", project.id(), admin.id(),
                TaskPriority.MEDIUM, start.plusDays(7), List.of(tagName)));
        adminDevice.tasks.update(new TaskEdit(task.id(), task.title(), "Подробности", task.status(), task.priority(),
                task.deadline(), admin.id(), task.tagNames(), new TaskExtras(start, List.of(staffId), List.of())));
        SyncStatus pushed = adminDevice.sync.synchronize();
        assertEquals(0, pushed.errors(), "сервер принял тег, проект и задачу");
        assertEquals(0, pushed.pending());

        Device staffDevice = new Device();
        String ownPassword = firstLogin(staffDevice, email, password);
        staffDevice.sync.initialLoad();
        TaskExtras received = staffDevice.tasks.findExtras(task.id());
        assertEquals(start, received.startDate());
        assertEquals(List.of(staffId), received.coExecutorIds());
        assertEquals("Подробности", staffDevice.tasks.findViewById(task.id()).description());
        assertEquals(List.of(tagName), staffDevice.tasks.findViewById(task.id()).tagNames());
        assertEquals("Цели проекта", staffDevice.projects.findInfo(project.id()).description());

        // Профиль: должность и часовой пояс уходят синхронизацией, пустая должность допустима
        staffDevice.profile.update("Ольга Белова", "Аналитик", "Asia/Novosibirsk");
        assertEquals(0, staffDevice.sync.synchronize().errors());
        adminDevice.sync.synchronize();
        assertTrue(employees(adminDevice).stream().anyMatch(e -> e.id().equals(staffId)
                && "Аналитик".equals(e.position()) && "Asia/Novosibirsk".equals(e.timezone())));

        // Наблюдатель меняет название — сервер отклоняет; «отменить мою правку» возвращает серверное
        TaskView seen = staffDevice.tasks.findViewById(task.id());
        staffDevice.tasks.update(new TaskEdit(task.id(), "Чужая правка", seen.description(), seen.status(), seen.priority(),
                seen.deadline(), seen.responsibleId(), seen.tagNames()));
        assertEquals(1, staffDevice.sync.synchronize().errors());
        staffDevice.sync.discardRejected(EntityType.TASK, task.id(), false);
        assertEquals("Подготовить отчёт", staffDevice.tasks.findViewById(task.id()).title());
        assertEquals(0, staffDevice.sync.status().errors());
        assertEquals(0, staffDevice.sync.status().pending());

        // Смена пароля: неверный текущий — понятная ошибка, верный — новый пароль работает
        assertThrows(IllegalArgumentException.class, () -> staffDevice.profile.changePassword("wrong-password", "NewPass-123"));
        staffDevice.profile.changePassword(ownPassword, "NewPass-123");
        new Device().auth.login(email, "NewPass-123");

        adminDevice.auth.logout();
    }

    @Test
    void adminSettingsReachOtherDevices() {
        Device adminDevice = new Device();
        adminDevice.auth.login(ADMIN_EMAIL, ADMIN_PASSWORD);
        adminDevice.sync.initialLoad();
        Device secondDevice = new Device();
        secondDevice.auth.login(ADMIN_EMAIL, ADMIN_PASSWORD);
        secondDevice.sync.initialLoad();

        try {
            assertThrows(IllegalArgumentException.class,
                    () -> adminDevice.settings.save(Map.of("sync.interval_seconds", "5")), "сервер проверяет значения");
            adminDevice.settings.save(Map.of("sync.interval_seconds", "300", "date.format", "yyyy-MM-dd"));

            assertEquals(SyncStatus.State.SYNCED, secondDevice.sync.synchronize().state());
            assertEquals(Duration.ofSeconds(300), secondDevice.appSettings.syncInterval());
            assertEquals("yyyy-MM-dd", secondDevice.appSettings.all().get("date.format"));
        } finally {
            adminDevice.settings.save(Map.of("sync.interval_seconds", "120", "date.format", "dd.MM.yyyy"));
            adminDevice.auth.logout();
            secondDevice.auth.logout();
        }
    }

    /**
     * Первый вход сотрудника: временный пароль от администратора нужно сменить, иначе сервер не отдаст данные.
     *
     * @return новый пароль
     */
    private static String firstLogin(Device device, String email, String temporaryPassword) {
        AuthUser user = device.auth.login(email, temporaryPassword);
        assertTrue(user.mustChangePassword(), "пароль от администратора — временный");
        ApiException blocked = assertThrows(ApiException.class, () -> device.sync.initialLoad());
        assertTrue(blocked.is("PASSWORD_CHANGE_REQUIRED"), blocked.code());
        String ownPassword = temporaryPassword + "-own";
        device.auth.changePassword(temporaryPassword, ownPassword);
        return ownPassword;
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
        final NotificationService notifications;
        final AuthService auth;
        final SyncService sync;
        final TaskServiceImpl tasks;
        final ProjectServiceImpl projects;
        final ProfileService profile;
        final AppSettings appSettings = new AppSettings();
        final SettingsService settings;
        final TagAdminService tags;

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
            tasks = new TaskServiceImpl(uow, taskRepository, new TagService(new H2TagRepository(uow), tracker, session,
                    new PermissionService(uow, new H2PermissionRepository(uow), session)), tracker, session);
            projects = new ProjectServiceImpl(uow, projectRepository, tracker, session);
            profile = new ProfileService(uow, new H2UserRepository(uow), tracker, session, api);
            tags = new TagAdminService(uow, new H2TagRepository(uow), tracker, session,
                    new PermissionService(uow, new H2PermissionRepository(uow), session));

            UserSyncAdapter users = new UserSyncAdapter(uow);
            H2NotificationRepository notificationRepository = new H2NotificationRepository(uow);
            notifications = new NotificationService(uow, notificationRepository, session, () -> {});
            sync = SyncService.create(uow, new HttpSyncApi(api), session, users, List.of(users, new TagSyncAdapter(uow),
                    new ProjectSyncAdapter(uow, projectRepository, users), new TaskSyncAdapter(uow, taskRepository, users)));
            settings = new SettingsService(api, uow, new SettingsRepository(uow), appSettings, () -> {});
            sync.addStep(settings);
            sync.addStep(new NotificationSync(api, uow, notificationRepository, session, () -> {}));
        }
    }
}
