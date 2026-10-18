package org.example.taskschedulerdesktop.project;

import com.github.f4b6a3.uuid.UuidCreator;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.example.taskschedulerdesktop.shared.exception.NotFoundException;
import org.example.taskschedulerdesktop.project.Project.Fields;
import org.example.taskschedulerdesktop.session.Session;
import org.example.taskschedulerdesktop.sync.ChangeTracker;
import org.example.taskschedulerdesktop.sync.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
            if (!Objects.equals(project.getOwnerId(), ownerId)) {
                project.setMembers(withOwner(project.getMembers(), project.getOwnerId(), ownerId, now));
                project.setOwnerId(ownerId);
                changed.add(Fields.OWNER_ID);
                changed.add(Fields.MEMBERS);
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
