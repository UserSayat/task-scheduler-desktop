package org.example.taskschedulerdesktop.tag;

import com.github.f4b6a3.uuid.UuidCreator;
import org.example.taskschedulerdesktop.access.PermissionService;
import org.example.taskschedulerdesktop.session.Session;
import org.example.taskschedulerdesktop.sync.local.ChangeTracker;
import org.example.taskschedulerdesktop.sync.protocol.EntityType;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Теги задач по именам из формы. Вызывается внутри транзакции сервиса задач.
 */
public class TagService {

    private static final int NAME_MAX_LENGTH = 100;

    private final TagRepository repository;
    private final ChangeTracker changeTracker;
    private final Session session;
    private final PermissionService permissions;

    public TagService(TagRepository repository, ChangeTracker changeTracker, Session session,
                      PermissionService permissions) {
        this.repository = repository;
        this.changeTracker = changeTracker;
        this.session = session;
        this.permissions = permissions;
    }

    /**
     * Находит теги по именам и создаёт недостающие. Порядок и дубликаты не важны.
     * <p>
     * Создавать теги может только администратор (API.md, 8.7). Остальным новый тег не создаётся:
     * сервер всё равно отклонил бы и тег, и задачу с ним.
     *
     * @throws IllegalArgumentException тега нет, а пользователь не администратор
     */
    public List<UUID> resolveTagIds(List<String> names, Instant now) throws SQLException {
        Set<UUID> ids = new LinkedHashSet<>();
        if (names == null) {
            return new ArrayList<>();
        }

        for (String raw : names) {
            String name = raw == null ? "" : raw.trim();
            if (name.isEmpty()) {
                continue;
            }
            if (name.length() > NAME_MAX_LENGTH) {
                throw new IllegalArgumentException("Тег длиннее " + NAME_MAX_LENGTH + " символов: " + name);
            }

            Optional<Tag> existing = repository.findByNameIgnoreCase(name);
            if (existing.isPresent() && repository.isDeleted(existing.get().id())) {
                throw new IllegalArgumentException("Тег «" + existing.get().name() + "» удалён администратором");
            }
            ids.add(existing.isPresent() ? existing.get().id() : create(name, now));
        }
        return new ArrayList<>(ids);
    }

    private UUID create(String name, Instant now) throws SQLException {
        permissions.requireCanCreateTag(name);
        Tag tag = new Tag(UuidCreator.getTimeOrderedEpoch(), name, Tag.DEFAULT_COLOR);
        repository.insert(tag, now, session.getCurrentUserId());
        changeTracker.created(EntityType.TAG, tag.id(), Tag.Fields.ON_CREATE, now);
        return tag.id();
    }
}
