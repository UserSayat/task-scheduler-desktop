package org.example.taskschedulerdesktop.access;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Факты для проверки прав из локальной базы: роли, участие в проектах, автор и исполнители задачи.
 * Методы вызываются внутри единицы работы (UnitOfWork).
 */
public interface PermissionRepository {

    /** Коды глобальных ролей: ADMIN, PROJECT_MANAGER. */
    Set<String> globalRoles(UUID userId) throws SQLException;

    /** Роль в проекте (OWNER, MANAGER, MEMBER, OBSERVER), если пользователь — участник. */
    Optional<String> memberRole(UUID projectId, UUID userId) throws SQLException;

    /** Проекты, где у пользователя одна из ролей. */
    Set<UUID> projectsWithRole(UUID userId, Collection<String> roles) throws SQLException;

    /** Проект и автор задачи. */
    Optional<TaskFacts> taskFacts(UUID taskId) throws SQLException;

    /** Исполнитель или соисполнитель задачи. */
    boolean isAssignee(UUID taskId, UUID userId) throws SQLException;

    record TaskFacts(UUID projectId, UUID creatorId) {}
}
