package org.example.taskschedulerdesktop.repository.tag;

import org.example.taskschedulerdesktop.models.Tag;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TagRepository {

    /**
     * Поиск по имени без учёта регистра, включая удалённые теги: имя уникально с учётом удалённых.
     */
    Optional<Tag> findByNameIgnoreCase(String name) throws SQLException;

    void insert(Tag tag, Instant createdAt, UUID createdBy) throws SQLException;
}
