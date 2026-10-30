package org.example.taskschedulerdesktop.user;

import org.example.taskschedulerdesktop.session.Session;
import org.example.taskschedulerdesktop.shared.api.ApiClient;
import org.example.taskschedulerdesktop.shared.api.ApiException;
import org.example.taskschedulerdesktop.shared.api.OfflineException;
import org.example.taskschedulerdesktop.shared.database.Jdbc;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.example.taskschedulerdesktop.sync.local.ChangeTracker;
import org.example.taskschedulerdesktop.sync.protocol.EntityType;
import org.example.taskschedulerdesktop.user.UserSyncAdapter.Fields;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Профиль текущего пользователя.
 * <ul>
 *   <li>имя, должность и часовой пояс меняются локально и уходят на сервер синхронизацией —
 *       так это работает и офлайн (API.md, 6.3);</li>
 *   <li>пароль меняется только онлайн: {@code POST /auth/change-password} (API.md, 5.5).</li>
 * </ul>
 */
public class ProfileService {

    /** Требования сервера к паролю (API.md, 5.5). */
    public static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_MAX_BYTES = 72;
    private static final int TEXT_MAX_LENGTH = 255;

    public record ChangePasswordRequest(String currentPassword, String newPassword) {}

    private final UnitOfWork uow;
    private final UserRepository users;
    private final ChangeTracker changeTracker;
    private final Session session;
    private final ApiClient api;

    public ProfileService(UnitOfWork uow, UserRepository users, ChangeTracker changeTracker, Session session, ApiClient api) {
        this.uow = uow;
        this.users = users;
        this.changeTracker = changeTracker;
        this.session = session;
        this.api = api;
    }

    public User current() {
        return uow.read(() -> users.findById(session.getCurrentUserId()))
                .orElseThrow(() -> new IllegalStateException("Current user is not in the local database"));
    }

    /**
     * @return изменился ли профиль
     * @throws IllegalArgumentException пустое имя, слишком длинный текст или неизвестный часовой пояс
     */
    public boolean update(String name, String position, String timezone) {
        String validName = required(name, "Укажите имя");
        String validPosition = position == null || position.isBlank() ? null : limited(position.strip(), "Должность");
        String validTimezone = timezone(timezone);
        limited(validName, "Имя");

        return uow.inTransaction(() -> {
            User user = users.findById(session.getCurrentUserId())
                    .orElseThrow(() -> new IllegalStateException("Current user is not in the local database"));
            List<String> changed = new ArrayList<>();
            if (!Objects.equals(user.getName(), validName)) {
                changed.add(Fields.NAME);
            }
            if (!Objects.equals(user.getPosition(), validPosition)) {
                changed.add(Fields.POSITION);
            }
            if (!Objects.equals(user.getTimezone(), validTimezone)) {
                changed.add(Fields.TIMEZONE);
            }
            if (changed.isEmpty()) {
                return false;
            }

            Instant now = Instant.now();
            try (PreparedStatement ps = uow.connection().prepareStatement(
                    "UPDATE users SET name = ?, position = ?, timezone = ?, updated_at = ?, updated_by = ? WHERE id = ?")) {
                ps.setString(1, validName);
                ps.setString(2, validPosition);
                ps.setString(3, validTimezone);
                Jdbc.setInstant(ps, 4, now);
                Jdbc.setUuid(ps, 5, user.getId());
                Jdbc.setUuid(ps, 6, user.getId());
                ps.executeUpdate();
            }
            changeTracker.updated(EntityType.USER, user.getId(), changed, now);
            return true;
        });
    }

    /**
     * Смена своего пароля на сервере. Остальные устройства пользователя будут разлогинены.
     *
     * @throws IllegalArgumentException новый пароль не подходит или текущий указан неверно
     * @throws OfflineException         нет связи с сервером
     */
    public void changePassword(String currentPassword, String newPassword) {
        if (currentPassword == null || currentPassword.isEmpty()) {
            throw new IllegalArgumentException("Введите текущий пароль");
        }
        validatePassword(newPassword);
        try {
            api.post("/auth/change-password", new ChangePasswordRequest(currentPassword, newPassword));
        } catch (ApiException e) {
            if (e.is("INVALID_CREDENTIALS")) {
                throw new IllegalArgumentException("Текущий пароль указан неверно", e);
            }
            if (e.fields() != null && !e.fields().isEmpty()) {
                throw new IllegalArgumentException(String.join("\n", e.fields().values().iterator().next()), e);
            }
            throw e;
        }
    }

    /** Те же требования, что на сервере: от 8 символов и не больше 72 байт в UTF-8. */
    public static void validatePassword(String password) {
        if (password == null || password.length() < PASSWORD_MIN_LENGTH) {
            throw new IllegalArgumentException("Пароль должен быть не короче " + PASSWORD_MIN_LENGTH + " символов");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            throw new IllegalArgumentException("Пароль слишком длинный");
        }
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.strip();
    }

    private static String limited(String value, String field) {
        if (value.length() > TEXT_MAX_LENGTH) {
            throw new IllegalArgumentException(field + " длиннее " + TEXT_MAX_LENGTH + " символов");
        }
        return value;
    }

    private static String timezone(String value) {
        String zone = required(value, "Укажите часовой пояс");
        try {
            return ZoneId.of(zone).getId();
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Неизвестный часовой пояс: " + zone, e);
        }
    }
}
