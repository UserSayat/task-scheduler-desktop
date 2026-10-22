package org.example.taskschedulerdesktop.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.f4b6a3.uuid.UuidCreator;
import org.example.taskschedulerdesktop.shared.database.Jdbc;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.example.taskschedulerdesktop.sync.adapter.KnownUsers;
import org.example.taskschedulerdesktop.sync.adapter.SyncEntityAdapter;
import org.example.taskschedulerdesktop.sync.protocol.EntityType;
import org.example.taskschedulerdesktop.sync.protocol.SyncValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Синхронизация пользователей (API.md, 8.2). Профиль, email, статус и глобальные роли приходят с сервера;
 * через push можно отправить только свой профиль (имя, должность, аватар, часовой пояс).
 */
public class UserSyncAdapter implements SyncEntityAdapter, KnownUsers {

    private static final Logger log = LoggerFactory.getLogger(UserSyncAdapter.class);

    public static final class Fields {
        public static final String NAME = "name";
        public static final String EMAIL = "email";
        public static final String POSITION = "position";
        public static final String AVATAR_URL = "avatarUrl";
        public static final String TIMEZONE = "timezone";
        public static final String STATUS = "status";
        public static final String DELETED_AT = "deletedAt";
        public static final String ROLES = "roles";

        private Fields() {}
    }

    private static final String PLACEHOLDER_NAME = "Неизвестный сотрудник";
    private static final String PLACEHOLDER_DOMAIN = "@placeholder.invalid";

    private final UnitOfWork uow;

    public UserSyncAdapter(UnitOfWork uow) {
        this.uow = uow;
    }

    @Override
    public EntityType type() {
        return EntityType.USER;
    }

    @Override
    public boolean exists(UUID id) throws SQLException {
        try (PreparedStatement ps = uow.connection().prepareStatement("SELECT 1 FROM users WHERE id = ?")) {
            Jdbc.setUuid(ps, 1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public Optional<Map<String, JsonNode>> values(UUID id) throws SQLException {
        String sql = "SELECT name, email, position, avatar_url, timezone, status, deleted_at FROM users WHERE id = ?";
        Map<String, JsonNode> fields = new LinkedHashMap<>();
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            Jdbc.setUuid(ps, 1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                fields.put(Fields.NAME, SyncValues.node(rs.getString("name")));
                fields.put(Fields.EMAIL, SyncValues.node(rs.getString("email")));
                fields.put(Fields.POSITION, SyncValues.node(rs.getString("position")));
                fields.put(Fields.AVATAR_URL, SyncValues.node(rs.getString("avatar_url")));
                fields.put(Fields.TIMEZONE, SyncValues.node(rs.getString("timezone")));
                fields.put(Fields.STATUS, SyncValues.node(rs.getString("status")));
                fields.put(Fields.DELETED_AT, SyncValues.node(Jdbc.getInstant(rs, "deleted_at")));
            }
        }
        fields.put(Fields.ROLES, roles(id));
        return Optional.of(fields);
    }

    @Override
    public void apply(UUID id, Map<String, JsonNode> fields, Instant at) throws SQLException {
        Map<String, JsonNode> merged = new LinkedHashMap<>(values(id).orElse(Map.of()));
        boolean isNew = merged.isEmpty();
        merged.putAll(fields);

        String name = Objects.requireNonNull(SyncValues.text(merged.get(Fields.NAME)), "name");
        String email = Objects.requireNonNull(SyncValues.text(merged.get(Fields.EMAIL)), "email");
        String timezone = SyncValues.text(merged.get(Fields.TIMEZONE));
        String status = SyncValues.text(merged.get(Fields.STATUS));
        releaseEmail(email, id);

        String sql = isNew
                ? """
                  INSERT INTO users (name, email, position, avatar_url, timezone, status, deleted_at, updated_at, created_at, id)
                  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                  """
                : """
                  UPDATE users SET name = ?, email = ?, position = ?, avatar_url = ?, timezone = ?, status = ?,
                                   deleted_at = ?, updated_at = ?
                   WHERE id = ?
                  """;
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, SyncValues.text(merged.get(Fields.POSITION)));
            ps.setString(4, SyncValues.text(merged.get(Fields.AVATAR_URL)));
            ps.setString(5, timezone != null ? timezone : "Europe/Moscow");
            ps.setString(6, status != null ? status : "ACTIVE");
            Jdbc.setInstant(ps, 7, SyncValues.instant(merged.get(Fields.DELETED_AT)));
            Jdbc.setInstant(ps, 8, at);
            if (isNew) {
                Jdbc.setInstant(ps, 9, at);
                Jdbc.setUuid(ps, 10, id);
            } else {
                Jdbc.setUuid(ps, 9, id);
            }
            ps.executeUpdate();
        }

        if (fields.containsKey(Fields.ROLES)) {
            replaceRoles(id, fields.get(Fields.ROLES), at);
        }
    }

    /**
     * Заглушки для пользователей, на которых ссылаются данные, но которых нет локально
     * (удалённые сотрудники в snapshot не приходят). Без field_versions — первый же pull с настоящими
     * данными перезапишет их целиком.
     */
    @Override
    public void ensureExist(Collection<UUID> userIds) throws SQLException {
        String sql = """
                INSERT INTO users (id, name, email, timezone, status, created_at, updated_at)
                VALUES (?, ?, ?, 'Europe/Moscow', 'DELETED', ?, ?)
                """;
        Instant now = Instant.now();
        for (UUID userId : new LinkedHashSet<>(userIds)) {
            if (userId == null || exists(userId)) {
                continue;
            }
            try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
                Jdbc.setUuid(ps, 1, userId);
                ps.setString(2, PLACEHOLDER_NAME);
                ps.setString(3, userId + PLACEHOLDER_DOMAIN);
                Jdbc.setInstant(ps, 4, now);
                Jdbc.setInstant(ps, 5, now);
                ps.executeUpdate();
            }
            log.debug("Placeholder for user {}", userId);
        }
    }

    /**
     * Email уникален. Если его занимает другой сотрудник (адреса поменялись местами, а его запись ещё не дошла),
     * тому временно ставится служебный адрес — настоящий придёт следующим pull.
     */
    private void releaseEmail(String email, UUID id) throws SQLException {
        String sql = "UPDATE users SET email = CONCAT(CAST(id AS VARCHAR), '" + PLACEHOLDER_DOMAIN + "') WHERE email = ? AND id <> ?";
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            ps.setString(1, email);
            Jdbc.setUuid(ps, 2, id);
            ps.executeUpdate();
        }
    }

    private JsonNode roles(UUID userId) throws SQLException {
        String sql = """
                SELECT r.code, ur.scope_type, ur.scope_id FROM user_roles ur JOIN roles r ON r.id = ur.role_id
                 WHERE ur.user_id = ? ORDER BY r.code
                """;
        ArrayNode roles = JsonNodeFactory.instance.arrayNode();
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            Jdbc.setUuid(ps, 1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ObjectNode role = roles.addObject();
                    role.put("roleCode", rs.getString("code"));
                    role.put("scopeType", rs.getString("scope_type"));
                    role.set("scopeId", SyncValues.node(Jdbc.getUuid(rs, "scope_id")));
                }
            }
        }
        return roles;
    }

    /** Роли целиком заменяются пришедшим списком; неизвестные клиенту коды ролей пропускаются. */
    private void replaceRoles(UUID userId, JsonNode roles, Instant at) throws SQLException {
        try (PreparedStatement ps = uow.connection().prepareStatement("DELETE FROM user_roles WHERE user_id = ?")) {
            Jdbc.setUuid(ps, 1, userId);
            ps.executeUpdate();
        }
        if (roles == null || !roles.isArray()) {
            return;
        }
        String sql = """
                INSERT INTO user_roles (id, user_id, role_id, scope_type, scope_id, created_at, updated_at)
                SELECT ?, ?, r.id, ?, ?, ?, ? FROM roles r WHERE r.code = ?
                """;
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            for (JsonNode role : roles) {
                Jdbc.setUuid(ps, 1, UuidCreator.getTimeOrderedEpoch());
                Jdbc.setUuid(ps, 2, userId);
                ps.setString(3, role.path("scopeType").asText("GLOBAL"));
                Jdbc.setUuid(ps, 4, SyncValues.uuid(role.get("scopeId")));
                Jdbc.setInstant(ps, 5, at);
                Jdbc.setInstant(ps, 6, at);
                ps.setString(7, role.path("roleCode").asText());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
