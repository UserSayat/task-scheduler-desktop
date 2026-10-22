package org.example.taskschedulerdesktop.sync.adapter;

import java.sql.SQLException;
import java.util.Collection;
import java.util.UUID;

/**
 * Гарантирует, что в локальной базе есть строки пользователей, на которых ссылаются задачи, проекты
 * и field_versions (внешние ключи на users). Snapshot не присылает удалённых пользователей, а автором
 * старой задачи может быть уволенный сотрудник — для него создаётся заглушка, которую потом перезапишет pull.
 */
public interface KnownUsers {

    void ensureExist(Collection<UUID> userIds) throws SQLException;
}
