package org.example.taskschedulerdesktop.task;

import com.github.f4b6a3.uuid.UuidCreator;
import org.example.taskschedulerdesktop.session.Session;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.example.taskschedulerdesktop.shared.exception.NotFoundException;
import org.example.taskschedulerdesktop.sync.local.ChangeTracker;
import org.example.taskschedulerdesktop.sync.protocol.EntityType;
import org.example.taskschedulerdesktop.tag.TagService;
import org.example.taskschedulerdesktop.task.Task.Fields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Задачи. Каждая запись — одна транзакция: строка задачи, её коллекции,
 * field_versions и sync_log сохраняются вместе.
 */
public class TaskServiceImpl implements TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskServiceImpl.class);
    private static final int TITLE_MAX_LENGTH = 500;

    private final UnitOfWork uow;
    private final TaskRepository repository;
    private final TagService tagService;
    private final ChangeTracker changeTracker;
    private final Session session;

    public TaskServiceImpl(UnitOfWork uow, TaskRepository repository, TagService tagService,
                           ChangeTracker changeTracker, Session session) {
        this.uow = uow;
        this.repository = repository;
        this.tagService = tagService;
        this.changeTracker = changeTracker;
        this.session = session;
    }

    // ===== ЧТЕНИЕ =====

    @Override
    public List<TaskView> findAllViews() {
        return uow.read(repository::findAllViews);
    }

    @Override
    public List<TaskView> findViewsByProjectIdAndStatus(UUID projectId, TaskStatus status) {
        return uow.read(() -> repository.findViewsByProjectIdAndStatus(projectId, status));
    }

    @Override
    public TaskView findViewById(UUID id) {
        return uow.read(() -> repository.findViewById(id))
                .orElseThrow(() -> new NotFoundException("Task not found: " + id));
    }

    @Override
    public List<TaskView> findUpcomingDeadlines(int maxDaysUntilDeadline) {
        return uow.read(() -> repository.findUpcomingDeadlines(maxDaysUntilDeadline));
    }

    @Override
    public List<TaskView> findTopOpenViews(UUID projectId, int limit) {
        return uow.read(() -> repository.findTopOpenViews(projectId, limit));
    }

    @Override
    public int countByProjectIdAndStatus(UUID projectId, TaskStatus status) {
        return uow.read(() -> repository.countByProjectIdAndStatus(projectId, status));
    }

    @Override
    public int countByStatus(TaskStatus status) {
        return uow.read(() -> repository.countByStatus(status));
    }

    @Override
    public int countByProjectIdAndPriority(UUID projectId, TaskPriority priority) {
        return uow.read(() -> repository.countByProjectIdAndPriority(projectId, priority));
    }

    // ===== ЗАПИСЬ =====

    @Override
    public TaskView create(NewTask newTask) {
        String title = validTitle(newTask.title());
        Objects.requireNonNull(newTask.projectId(), "projectId");

        return uow.inTransaction(() -> {
            Instant now = Instant.now();
            UUID userId = session.getCurrentUserId();

            Task task = new Task();
            task.setId(UuidCreator.getTimeOrderedEpoch());
            task.setTitle(title);
            task.setProjectId(newTask.projectId());
            task.setCreatorId(userId);
            task.setStatus(TaskStatus.NEW);
            task.setPriority(newTask.priority() != null ? newTask.priority() : TaskPriority.MEDIUM);
            task.setDeadline(newTask.deadline());
            if (newTask.responsibleId() != null) {
                task.setAssignees(List.of(new TaskAssignee(newTask.responsibleId(), AssigneeRole.RESPONSIBLE)));
            }
            task.setTagIds(tagService.resolveTagIds(newTask.tagNames(), now));
            task.setCreatedAt(now);
            task.setUpdatedAt(now);
            task.setCreatedBy(userId);
            task.setUpdatedBy(userId);

            repository.insert(task);
            changeTracker.created(EntityType.TASK, task.getId(), Fields.ON_CREATE, now);

            log.debug("Task created: {}", task.getId());
            return requireView(task.getId());
        });
    }

    @Override
    public TaskView update(TaskEdit edit) {
        String title = validTitle(edit.title());

        return uow.inTransaction(() -> {
            Task task = repository.findById(edit.id())
                    .orElseThrow(() -> new NotFoundException("Task not found: " + edit.id()));
            Instant now = Instant.now();
            List<String> changed = new ArrayList<>();

            if (!Objects.equals(task.getTitle(), title)) {
                task.setTitle(title);
                changed.add(Fields.TITLE);
            }
            if (!Objects.equals(task.getDescription(), edit.description())) {
                task.setDescription(edit.description());
                changed.add(Fields.DESCRIPTION);
            }
            if (edit.priority() != null && task.getPriority() != edit.priority()) {
                task.setPriority(edit.priority());
                changed.add(Fields.PRIORITY);
            }
            if (!Objects.equals(task.getDeadline(), edit.deadline())) {
                validateDates(task.getStartDate(), edit.deadline());
                task.setDeadline(edit.deadline());
                changed.add(Fields.DEADLINE);
            }
            if (edit.status() != null && task.getStatus() != edit.status()) {
                changeStatus(task, edit.status(), now, changed);
            }

            List<TaskAssignee> assignees = withResponsible(task.getAssignees(), edit.responsibleId());
            if (!new HashSet<>(assignees).equals(new HashSet<>(task.getAssignees()))) {
                task.setAssignees(assignees);
                changed.add(Fields.ASSIGNEES);
            }

            List<UUID> tagIds = tagService.resolveTagIds(edit.tagNames(), now);
            if (!new HashSet<>(tagIds).equals(new HashSet<>(task.getTagIds()))) {
                task.setTagIds(tagIds);
                changed.add(Fields.TAGS);
            }

            if (!changed.isEmpty()) {
                task.setUpdatedAt(now);
                task.setUpdatedBy(session.getCurrentUserId());
                repository.update(task);
                changeTracker.updated(EntityType.TASK, task.getId(), changed, now);
                log.debug("Task {} updated, fields: {}", task.getId(), changed);
            }

            return requireView(task.getId());
        });
    }

    @Override
    public void delete(UUID id) {
        uow.inTransaction(() -> {
            Task task = repository.findById(id)
                    .orElseThrow(() -> new NotFoundException("Task not found: " + id));
            Instant now = Instant.now();

            task.setDeletedAt(now);
            task.setUpdatedAt(now);
            task.setUpdatedBy(session.getCurrentUserId());
            repository.update(task);
            changeTracker.deleted(EntityType.TASK, id, now);
        });
    }

    /**
     * closedAt ставится при переходе в DONE и сбрасывается при выходе из него (API.md, 8.2).
     */
    private void changeStatus(Task task, TaskStatus status, Instant now, List<String> changed) {
        task.setStatus(status);
        changed.add(Fields.STATUS);

        Instant closedAt = status == TaskStatus.DONE ? now : null;
        if (!Objects.equals(task.getClosedAt(), closedAt)) {
            task.setClosedAt(closedAt);
            changed.add(Fields.CLOSED_AT);
        }
    }

    /**
     * Заменяет ответственного, соисполнителей не трогает. Ответственный не может одновременно
     * быть соисполнителем (уникальность task_id + user_id).
     */
    private List<TaskAssignee> withResponsible(List<TaskAssignee> current, UUID responsibleId) {
        List<TaskAssignee> result = new ArrayList<>();
        if (responsibleId != null) {
            result.add(new TaskAssignee(responsibleId, AssigneeRole.RESPONSIBLE));
        }
        for (TaskAssignee assignee : current) {
            if (assignee.role() == AssigneeRole.CO_EXECUTOR && !assignee.userId().equals(responsibleId)) {
                result.add(assignee);
            }
        }
        return result;
    }

    private TaskView requireView(UUID id) throws SQLException {
        return repository.findViewById(id)
                .orElseThrow(() -> new NotFoundException("Task not found: " + id));
    }

    private String validTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Название задачи не может быть пустым");
        }
        String trimmed = title.trim();
        if (trimmed.length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("Название задачи длиннее " + TITLE_MAX_LENGTH + " символов");
        }
        return trimmed;
    }

    private void validateDates(LocalDate startDate, LocalDate deadline) {
        if (startDate != null && deadline != null && deadline.isBefore(startDate)) {
            throw new IllegalArgumentException("Дедлайн раньше даты начала");
        }
    }
}
