package org.example.taskschedulerdesktop.access;

import org.example.taskschedulerdesktop.session.Session;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Права текущего пользователя — те же правила, что проверяет сервер (API.md, 8.7).
 * <p>
 * Интерфейс по ним прячет кнопки, которые всё равно закончились бы отказом сервера
 * (изменение ушло бы в «Отклонено сервером»). Последнее слово остаётся за сервером:
 * если роли успели измениться, отказ придёт при синхронизации.
 */
public class PermissionService {

    public static final String ADMIN = "ADMIN";
    public static final String PROJECT_MANAGER = "PROJECT_MANAGER";

    /** Кто может менять проект и все задачи в нём. */
    private static final List<String> PROJECT_MANAGERS = List.of("OWNER", "MANAGER");
    /** Кто может создавать задачи в проекте. */
    private static final List<String> TASK_CREATORS = List.of("OWNER", "MANAGER", "MEMBER");

    /** Глобальные права. */
    public record GlobalRights(boolean admin, boolean canCreateProjects) {}

    /** Права в проекте. */
    public record ProjectRights(boolean canManage, boolean canCreateTasks) {}

    /**
     * Права на задачу.
     *
     * @param canEdit         менять любые поля и удалять
     * @param canChangeStatus менять только статус (исполнители и соисполнители)
     */
    public record TaskRights(boolean canEdit, boolean canChangeStatus) {}

    private final UnitOfWork uow;
    private final PermissionRepository repository;
    private final Session session;

    public PermissionService(UnitOfWork uow, PermissionRepository repository, Session session) {
        this.uow = uow;
        this.repository = repository;
        this.session = session;
    }

    public GlobalRights globalRights() {
        return uow.read(() -> {
            Set<String> roles = repository.globalRoles(userId());
            boolean admin = roles.contains(ADMIN);
            return new GlobalRights(admin, admin || roles.contains(PROJECT_MANAGER));
        });
    }

    public ProjectRights projectRights(UUID projectId) {
        return uow.read(() -> {
            if (isAdmin()) {
                return new ProjectRights(true, true);
            }
            Optional<String> role = repository.memberRole(projectId, userId());
            return new ProjectRights(
                    role.filter(PROJECT_MANAGERS::contains).isPresent(),
                    role.filter(TASK_CREATORS::contains).isPresent());
        });
    }

    public TaskRights taskRights(UUID taskId) {
        return uow.read(() -> {
            Optional<PermissionRepository.TaskFacts> task = repository.taskFacts(taskId);
            if (task.isEmpty()) {
                return new TaskRights(false, false);
            }
            UUID userId = userId();
            boolean canEdit = isAdmin()
                    || userId.equals(task.get().creatorId())
                    || repository.memberRole(task.get().projectId(), userId).filter(PROJECT_MANAGERS::contains).isPresent();
            return new TaskRights(canEdit, canEdit || repository.isAssignee(taskId, userId));
        });
    }

    /**
     * Проекты, в которых можно создавать задачи. {@code null} — любые (администратор).
     */
    public Set<UUID> projectsForNewTasks() {
        return uow.read(() -> isAdmin() ? null : repository.projectsWithRole(userId(), TASK_CREATORS));
    }

    /** Кого можно назначить исполнителем и наблюдателем задач проекта. */
    public ProjectTeam projectTeam(UUID projectId) {
        return uow.read(() -> ProjectTeam.of(repository.projectMembers(projectId), repository.admins()));
    }

    /**
     * Новые теги создаёт только администратор. Вызывается перед созданием тега,
     * чтобы задача не ушла на сервер со ссылкой на тег, который он отклонит.
     */
    public void requireCanCreateTag(String name) {
        if (!uow.read(this::isAdmin)) {
            throw new IllegalArgumentException("Тега «" + name + "» нет, а новые теги может создавать только администратор");
        }
    }

    private boolean isAdmin() throws SQLException {
        return repository.globalRoles(userId()).contains(ADMIN);
    }

    private UUID userId() {
        return session.getCurrentUserId();
    }
}
