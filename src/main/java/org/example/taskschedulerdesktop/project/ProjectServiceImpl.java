package org.example.taskschedulerdesktop.project;

import com.github.f4b6a3.uuid.UuidCreator;
import org.example.taskschedulerdesktop.project.Project.Fields;
import org.example.taskschedulerdesktop.session.Session;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.example.taskschedulerdesktop.shared.exception.NotFoundException;
import org.example.taskschedulerdesktop.sync.local.ChangeTracker;
import org.example.taskschedulerdesktop.sync.protocol.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Проекты. Каждая запись — одна транзакция вместе с field_versions и sync_log.
 * Владелец проекта всегда есть в участниках с ролью OWNER (API.md, 8.2).
 */
public class ProjectServiceImpl implements ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectServiceImpl.class);
    private static final int NAME_MAX_LENGTH = 255;

    private final UnitOfWork uow;
    private final ProjectRepository repository;
    private final ChangeTracker changeTracker;
    private final Session session;

    public ProjectServiceImpl(UnitOfWork uow, ProjectRepository repository,
                              ChangeTracker changeTracker, Session session) {
        this.uow = uow;
        this.repository = repository;
        this.changeTracker = changeTracker;
        this.session = session;
    }

    @Override
    public List<ProjectView> findAll() {
        return uow.read(repository::findAllViews);
    }

    @Override
    public ProjectView findById(UUID id) {
        return uow.read(() -> repository.findViewById(id))
                .orElseThrow(() -> new NotFoundException("Project not found: " + id));
    }

    @Override
    public ProjectView create(String name, UUID ownerId) {
        return create(name, ownerId, Visibility.PRIVATE);
    }

    @Override
    public ProjectView create(String name, UUID ownerId, Visibility visibility) {
        String validName = validName(name);
        Objects.requireNonNull(ownerId, "ownerId");

        return uow.inTransaction(() -> {
            Instant now = Instant.now();
            UUID userId = session.getCurrentUserId();

            List<ProjectMember> members = new ArrayList<>();
            members.add(new ProjectMember(ownerId, MemberRole.OWNER, now));
            // Создатель должен сохранить доступ к проекту, даже если владельцем назначил другого
            if (!userId.equals(ownerId)) {
                members.add(new ProjectMember(userId, MemberRole.MANAGER, now));
            }

            Project project = new Project();
            project.setId(UuidCreator.getTimeOrderedEpoch());
            project.setName(validName);
            project.setOwnerId(ownerId);
            project.setVisibility(Objects.requireNonNull(visibility, "visibility"));
            project.setMembers(members);
            project.setCreatedAt(now);
            project.setUpdatedAt(now);
            project.setCreatedBy(userId);
            project.setUpdatedBy(userId);

            repository.insert(project);
            changeTracker.created(EntityType.PROJECT, project.getId(), Fields.ON_CREATE, now);

            log.debug("Project created: {}", project.getId());
            return requireView(project.getId());
        });
    }

    @Override
    public ProjectView update(UUID id, String name, UUID ownerId) {
        return save(id, name, ownerId, null,
                (project, now) -> withOwner(project.getMembers(), project.getOwnerId(), ownerId, now));
    }

    @Override
    public ProjectView update(UUID id, String name, UUID ownerId, Map<UUID, MemberRole> members) {
        return update(id, name, ownerId, members, null);
    }

    @Override
    public ProjectView update(UUID id, String name, UUID ownerId, Map<UUID, MemberRole> members, ProjectInfo info) {
        Objects.requireNonNull(members, "members");
        if (members.containsValue(MemberRole.OWNER)) {
            throw new IllegalArgumentException("Руководитель у проекта один — он задаётся отдельно");
        }
        if (info != null && info.startDate() != null && info.deadline() != null
                && info.deadline().isBefore(info.startDate())) {
            throw new IllegalArgumentException("Срок проекта раньше даты начала");
        }
        return save(id, name, ownerId, info, (project, now) -> {
            List<ProjectMember> result = new ArrayList<>();
            result.add(new ProjectMember(ownerId, MemberRole.OWNER, joinedAt(project.getMembers(), ownerId, now)));
            members.forEach((userId, role) -> {
                if (!userId.equals(ownerId)) {
                    result.add(new ProjectMember(userId, role, joinedAt(project.getMembers(), userId, now)));
                }
            });
            return result;
        });
    }

    @Override
    public List<ProjectMemberView> findMembers(UUID projectId) {
        return uow.read(() -> repository.findMemberViews(projectId));
    }

    /**
     * Общая часть изменения проекта: в журнал попадают только реально изменившиеся поля.
     *
     * @param newMembers новый состав по текущему проекту (ещё со старым руководителем) и моменту изменения
     */
    private ProjectView save(UUID id, String name, UUID ownerId, ProjectInfo info,
                             BiFunction<Project, Instant, List<ProjectMember>> newMembers) {
        String validName = validName(name);
        Objects.requireNonNull(ownerId, "ownerId");

        return uow.inTransaction(() -> {
            Project project = repository.findById(id)
                    .orElseThrow(() -> new NotFoundException("Project not found: " + id));
            Instant now = Instant.now();
            List<String> changed = new ArrayList<>();

            if (!Objects.equals(project.getName(), validName)) {
                project.setName(validName);
                changed.add(Fields.NAME);
            }
            List<ProjectMember> members = newMembers.apply(project, now);
            if (!Objects.equals(project.getOwnerId(), ownerId)) {
                project.setOwnerId(ownerId);
                changed.add(Fields.OWNER_ID);
            }
            if (!roles(project.getMembers()).equals(roles(members))) {
                project.setMembers(members);
                changed.add(Fields.MEMBERS);
            }
            if (info != null) {
                applyInfo(project, info, changed);
            }

            if (!changed.isEmpty()) {
                project.setUpdatedAt(now);
                project.setUpdatedBy(session.getCurrentUserId());
                repository.update(project);
                changeTracker.updated(EntityType.PROJECT, id, changed, now);
                log.debug("Project {} updated, fields: {}", id, changed);
            }

            return requireView(id);
        });
    }

    private static void applyInfo(Project project, ProjectInfo info, List<String> changed) {
        String description = info.description() == null || info.description().isBlank() ? null : info.description().strip();
        if (!Objects.equals(project.getDescription(), description)) {
            project.setDescription(description);
            changed.add(Fields.DESCRIPTION);
        }
        if (info.visibility() != null && project.getVisibility() != info.visibility()) {
            project.setVisibility(info.visibility());
            changed.add(Fields.VISIBILITY);
        }
        if (!Objects.equals(project.getStartDate(), info.startDate())) {
            project.setStartDate(info.startDate());
            changed.add(Fields.START_DATE);
        }
        if (!Objects.equals(project.getDeadline(), info.deadline())) {
            project.setDeadline(info.deadline());
            changed.add(Fields.DEADLINE);
        }
    }

    @Override
    public ProjectInfo findInfo(UUID id) {
        Project project = uow.read(() -> repository.findById(id))
                .orElseThrow(() -> new NotFoundException("Project not found: " + id));
        return new ProjectInfo(project.getDescription(), project.getVisibility(), project.getStartDate(), project.getDeadline());
    }

    @Override
    public ProjectView setArchived(UUID id, boolean archived) {
        return uow.inTransaction(() -> {
            Project project = repository.findById(id)
                    .orElseThrow(() -> new NotFoundException("Project not found: " + id));
            ProjectStatus status = archived ? ProjectStatus.ARCHIVED : ProjectStatus.ACTIVE;
            if (project.getStatus() != status) {
                Instant now = Instant.now();
                project.setStatus(status);
                project.setUpdatedAt(now);
                project.setUpdatedBy(session.getCurrentUserId());
                repository.update(project);
                changeTracker.updated(EntityType.PROJECT, id, List.of(Fields.STATUS), now);
            }
            return requireView(id);
        });
    }

    /** Состав для сравнения: кто и с какой ролью (порядок и дата вступления не важны). */
    private static Map<UUID, MemberRole> roles(List<ProjectMember> members) {
        return members.stream().collect(Collectors.toMap(ProjectMember::userId, ProjectMember::role));
    }

    @Override
    public void delete(UUID id) {
        uow.inTransaction(() -> {
            Project project = repository.findById(id)
                    .orElseThrow(() -> new NotFoundException("Project not found: " + id));
            Instant now = Instant.now();

            project.setDeletedAt(now);
            project.setUpdatedAt(now);
            project.setUpdatedBy(session.getCurrentUserId());
            repository.update(project);
            changeTracker.deleted(EntityType.PROJECT, id, now);
        });
    }

    /**
     * Новый владелец получает роль OWNER, прежний остаётся в проекте как MANAGER.
     */
    private List<ProjectMember> withOwner(List<ProjectMember> members, UUID oldOwnerId, UUID newOwnerId, Instant now) {
        List<ProjectMember> result = new ArrayList<>();
        result.add(new ProjectMember(newOwnerId, MemberRole.OWNER, joinedAt(members, newOwnerId, now)));

        for (ProjectMember member : members) {
            if (member.userId().equals(newOwnerId)) {
                continue;
            }
            if (member.userId().equals(oldOwnerId)) {
                result.add(new ProjectMember(oldOwnerId, MemberRole.MANAGER, member.joinedAt()));
            } else {
                result.add(member);
            }
        }
        return result;
    }

    private Instant joinedAt(List<ProjectMember> members, UUID userId, Instant fallback) {
        return members.stream()
                .filter(m -> m.userId().equals(userId))
                .map(ProjectMember::joinedAt)
                .findFirst()
                .orElse(fallback);
    }

    private ProjectView requireView(UUID id) throws SQLException {
        return repository.findViewById(id)
                .orElseThrow(() -> new NotFoundException("Project not found: " + id));
    }

    private String validName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Название проекта не может быть пустым");
        }
        String trimmed = name.trim();
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("Название проекта длиннее " + NAME_MAX_LENGTH + " символов");
        }
        return trimmed;
    }
}
