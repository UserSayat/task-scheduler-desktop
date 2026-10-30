package org.example.taskschedulerdesktop.tag;

import com.github.f4b6a3.uuid.UuidCreator;
import org.example.taskschedulerdesktop.access.PermissionService;
import org.example.taskschedulerdesktop.session.Session;
import org.example.taskschedulerdesktop.shared.database.Jdbc;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.example.taskschedulerdesktop.sync.local.ChangeTracker;
import org.example.taskschedulerdesktop.sync.protocol.EntityType;
import org.example.taskschedulerdesktop.tag.Tag.Fields;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Управление тегами — только для администратора (API.md, 8.7). Изменения уходят на сервер
 * синхронизацией, поэтому работают и офлайн.
 */
public class TagAdminService {

    private static final int NAME_MAX_LENGTH = 100;
    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    /** Тег в списке: сколько задач с ним связано. */
    public record TagRow(UUID id, String name, String color, int tasks) {}

    private final UnitOfWork uow;
    private final TagRepository repository;
    private final ChangeTracker changeTracker;
    private final Session session;
    private final PermissionService permissions;

    public TagAdminService(UnitOfWork uow, TagRepository repository, ChangeTracker changeTracker, Session session,
                           PermissionService permissions) {
        this.uow = uow;
        this.repository = repository;
        this.changeTracker = changeTracker;
        this.session = session;
        this.permissions = permissions;
    }

    /** Неудалённые теги по имени. */
    public List<TagRow> findAll() {
        String sql = """
                SELECT tg.id, tg.name, tg.color,
                       (SELECT COUNT(*) FROM task_tags tt JOIN tasks t ON t.id = tt.task_id
                         WHERE tt.tag_id = tg.id AND t.deleted_at IS NULL) AS tasks
                  FROM tags tg WHERE tg.deleted_at IS NULL ORDER BY LOWER(tg.name)
                """;
        return uow.read(() -> {
            List<TagRow> rows = new ArrayList<>();
            try (PreparedStatement ps = uow.connection().prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new TagRow(Jdbc.getUuid(rs, "id"), rs.getString("name"), rs.getString("color"), rs.getInt("tasks")));
                }
            }
            return rows;
        });
    }

    public void create(String name, String color) {
        String validName = validName(name);
        String validColor = validColor(color);
        permissions.requireCanCreateTag(validName);
        uow.inTransaction(() -> {
            requireFreeName(validName, null);
            Instant now = Instant.now();
            Tag tag = new Tag(UuidCreator.getTimeOrderedEpoch(), validName, validColor);
            repository.insert(tag, now, session.getCurrentUserId());
            changeTracker.created(EntityType.TAG, tag.id(), Fields.ON_CREATE, now);
        });
    }

    public void update(UUID id, String name, String color) {
        String validName = validName(name);
        String validColor = validColor(color);
        permissions.requireCanCreateTag(validName);
        uow.inTransaction(() -> {
            requireFreeName(validName, id);
            Tag current = find(id);
            List<String> changed = new ArrayList<>();
            if (!current.name().equals(validName)) {
                changed.add(Fields.NAME);
            }
            if (!validColor.equalsIgnoreCase(current.color() == null ? "" : current.color())) {
                changed.add(Fields.COLOR);
            }
            if (changed.isEmpty()) {
                return;
            }
            Instant now = Instant.now();
            try (PreparedStatement ps = uow.connection().prepareStatement(
                    "UPDATE tags SET name = ?, color = ?, updated_at = ?, updated_by = ? WHERE id = ?")) {
                ps.setString(1, validName);
                ps.setString(2, validColor);
                Jdbc.setInstant(ps, 3, now);
                Jdbc.setUuid(ps, 4, session.getCurrentUserId());
                Jdbc.setUuid(ps, 5, id);
                ps.executeUpdate();
            }
            changeTracker.updated(EntityType.TAG, id, changed, now);
        });
    }

    /** Мягкое удаление: тег пропадает у задач, но его имя остаётся занятым (API.md, 8.2). */
    public void delete(UUID id) {
        Tag tag = uow.read(() -> find(id));
        permissions.requireCanCreateTag(tag.name());
        uow.inTransaction(() -> {
            Instant now = Instant.now();
            try (PreparedStatement ps = uow.connection().prepareStatement(
                    "UPDATE tags SET deleted_at = ?, updated_at = ?, updated_by = ? WHERE id = ?")) {
                Jdbc.setInstant(ps, 1, now);
                Jdbc.setInstant(ps, 2, now);
                Jdbc.setUuid(ps, 3, session.getCurrentUserId());
                Jdbc.setUuid(ps, 4, id);
                ps.executeUpdate();
            }
            changeTracker.deleted(EntityType.TAG, id, now);
        });
    }

    private Tag find(UUID id) throws java.sql.SQLException {
        try (PreparedStatement ps = uow.connection().prepareStatement("SELECT id, name, color FROM tags WHERE id = ?")) {
            Jdbc.setUuid(ps, 1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Тег не найден");
                }
                return new Tag(Jdbc.getUuid(rs, "id"), rs.getString("name"), rs.getString("color"));
            }
        }
    }

    /** Имя уникально без учёта регистра, в том числе среди удалённых тегов (так проверяет сервер). */
    private void requireFreeName(String name, UUID ownId) throws java.sql.SQLException {
        Optional<Tag> existing = repository.findByNameIgnoreCase(name);
        if (existing.isPresent() && !existing.get().id().equals(ownId)) {
            throw new IllegalArgumentException("Тег «" + existing.get().name() + "» уже есть (возможно, удалённый)");
        }
    }

    private static String validName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Введите название тега");
        }
        String trimmed = name.strip();
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("Название тега длиннее " + NAME_MAX_LENGTH + " символов");
        }
        return trimmed;
    }

    private static String validColor(String color) {
        String value = color == null || color.isBlank() ? Tag.DEFAULT_COLOR : color.strip();
        if (!COLOR.matcher(value).matches()) {
            throw new IllegalArgumentException("Цвет должен быть в формате #RRGGBB");
        }
        return value.toUpperCase(Locale.ROOT);
    }
}
