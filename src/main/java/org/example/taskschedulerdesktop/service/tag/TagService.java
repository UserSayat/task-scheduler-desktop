package org.example.taskschedulerdesktop.service.tag;

import com.github.f4b6a3.uuid.UuidCreator;
import org.example.taskschedulerdesktop.models.Tag;
import org.example.taskschedulerdesktop.repository.tag.TagRepository;
import org.example.taskschedulerdesktop.session.Session;
import org.example.taskschedulerdesktop.sync.ChangeTracker;
import org.example.taskschedulerdesktop.sync.EntityType;

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

    public TagService(TagRepository repository, ChangeTracker changeTracker, Session session) {
        this.repository = repository;
        this.changeTracker = changeTracker;
        this.session = session;
    }

    /**
     * Находит теги по именам и создаёт недостающие. Порядок и дубликаты не важны.
     * <p>
     * На сервере создавать теги может только администратор (API.md, 8.7): у остальных
     * такое изменение вернётся с ошибкой FORBIDDEN при синхронизации.
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
            ids.add(existing.isPresent() ? existing.get().id() : create(name, now));
        }
        return new ArrayList<>(ids);
    }

    private UUID create(String name, Instant now) throws SQLException {
        Tag tag = new Tag(UuidCreator.getTimeOrderedEpoch(), name, Tag.DEFAULT_COLOR);
        repository.insert(tag, now, session.getCurrentUserId());
        changeTracker.created(EntityType.TAG, tag.id(), Tag.Fields.ON_CREATE, now);
        return tag.id();
    }
}
