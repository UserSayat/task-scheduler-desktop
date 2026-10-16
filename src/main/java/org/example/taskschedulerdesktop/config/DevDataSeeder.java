package org.example.taskschedulerdesktop.config;

import com.github.f4b6a3.uuid.UuidCreator;
import org.example.taskschedulerdesktop.database.Jdbc;
import org.example.taskschedulerdesktop.database.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Демо-сотрудники для режима разработки, пока нет входа и синхронизации с сервером (этапы 6–7).
 * <p>
 * Режим включён по умолчанию; выключается флагом {@code -Dtaskhub.dev=false}.
 * Данные пишутся напрямую, без журнала синхронизации: на сервер они попадать не должны.
 */
public final class DevDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    public static final String DEV_PROPERTY = "taskhub.dev";

    /** Демо-администратор — текущий пользователь в режиме разработки. */
    public static final UUID DEV_ADMIN_ID = UUID.fromString("00000000-0000-7000-8000-0000000000a1");

    private static final UUID ADMIN_ROLE_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final UUID PROJECT_MANAGER_ROLE_ID = UUID.fromString("00000000-0000-7000-8000-000000000002");

    private record DemoUser(UUID id, String name, String email, String position, UUID globalRoleId) {}

    private static final List<DemoUser> USERS = List.of(
            new DemoUser(DEV_ADMIN_ID, "Алексей Козлов", "kozlov@taskhub.test", "Frontend-разработчик", ADMIN_ROLE_ID),
            new DemoUser(UUID.fromString("00000000-0000-7000-8000-0000000000a2"), "Мария Волкова", "volkova@taskhub.test", "Backend-разработчик", PROJECT_MANAGER_ROLE_ID),
            new DemoUser(UUID.fromString("00000000-0000-7000-8000-0000000000a3"), "Елена Никитина", "nikitina@taskhub.test", "QA-инженер", null),
            new DemoUser(UUID.fromString("00000000-0000-7000-8000-0000000000a4"), "Павел Сорокин", "sorokin@taskhub.test", "Аналитик данных", null),
            new DemoUser(UUID.fromString("00000000-0000-7000-8000-0000000000a5"), "Дмитрий Лебедев", "lebedev@taskhub.test", "Backend-разработчик", null),
            new DemoUser(UUID.fromString("00000000-0000-7000-8000-0000000000a6"), "Ирина Фёдорова", "fedorova@taskhub.test", "UI/UX дизайнер", null)
    );

    private DevDataSeeder() {}

    public static boolean isEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty(DEV_PROPERTY));
    }

    public static void seed(UnitOfWork uow) {
        uow.inTransaction(() -> {
            Instant now = Instant.now();
            String userSql = """
                    MERGE INTO users (id, name, email, position, timezone, status, created_at, updated_at)
                    KEY (id) VALUES (?, ?, ?, ?, 'Europe/Moscow', 'ACTIVE', ?, ?)
                    """;
            String roleSql = """
                    MERGE INTO user_roles (id, user_id, role_id, scope_type, scope_id, created_at, updated_at)
                    KEY (user_id, role_id, scope_type) VALUES (?, ?, ?, 'GLOBAL', NULL, ?, ?)
                    """;

            try (PreparedStatement users = uow.connection().prepareStatement(userSql);
                 PreparedStatement roles = uow.connection().prepareStatement(roleSql)) {
                for (DemoUser user : USERS) {
                    Jdbc.setUuid(users, 1, user.id());
                    users.setString(2, user.name());
                    users.setString(3, user.email());
                    users.setString(4, user.position());
                    Jdbc.setInstant(users, 5, now);
                    Jdbc.setInstant(users, 6, now);
                    users.addBatch();

                    if (user.globalRoleId() != null) {
                        Jdbc.setUuid(roles, 1, UuidCreator.getTimeOrderedEpoch());
                        Jdbc.setUuid(roles, 2, user.id());
                        Jdbc.setUuid(roles, 3, user.globalRoleId());
                        Jdbc.setInstant(roles, 4, now);
                        Jdbc.setInstant(roles, 5, now);
                        roles.addBatch();
                    }
                }
                users.executeBatch();
                roles.executeBatch();
            }
        });
        log.info("Dev mode: {} demo users seeded", USERS.size());
    }
}
