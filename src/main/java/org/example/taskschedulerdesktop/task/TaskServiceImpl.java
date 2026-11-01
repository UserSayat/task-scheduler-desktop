package org.example.taskschedulerdesktop.task;

import com.github.f4b6a3.uuid.UuidCreator;
import org.example.taskschedulerdesktop.access.PermissionRepository;
import org.example.taskschedulerdesktop.access.ProjectTeam;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final PermissionRepository permissions;

    public TaskServiceImpl(UnitOfWork uow, TaskRepository repository, TagService tagService,
                           ChangeTracker changeTracker, Session session, PermissionRepository permissions) {
        this.uow = uow;
        this.repository = repository;
        this.tagService = tagService;
        this.changeTracker = changeTracker;
        this.session = session;
        this.permissions = permissions;
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
    public List<TaskView> findMyOpenViews() {
        UUID me = session.getCurrentUserId();
        return me == null ? List.of() : uow.read(() -> repository.findOpenViewsAssignedTo(me));
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
            // После вставки: задача в несуществующий проект падает на ограничении базы, а не здесь
            if (newTask.responsibleId() != null) {
                requireTeam(newTask.projectId(), List.of(newTask.responsibleId()), true);
            }
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
            TaskExtras extras = edit.extras();
            LocalDate startDate = extras != null ? extras.startDate() : task.getStartDate();
            validateDates(startDate, edit.deadline());
            if (!Objects.equals(task.getStartDate(), startDate)) {
                task.setStartDate(startDate);
                changed.add(Fields.START_DATE);
            }
            if (!Objects.equals(task.getDeadline(), edit.deadline())) {
                task.setDeadline(edit.deadline());
                changed.add(Fields.DEADLINE);
            }
            if (edit.status() != null && task.getStatus() != edit.status()) {
                changeStatus(task, edit.status(), now, changed);
            }

            List<TaskAssignee> assignees = extras != null
                    ? withCoExecutors(edit.responsibleId(), extras.coExecutorIds())
                    : withResponsible(task.getAssignees(), edit.responsibleId());
            if (!new HashSet<>(assignees).equals(new HashSet<>(task.getAssignees()))) {
                Set<UUID> before = task.getAssignees().stream().map(TaskAssignee::userId).collect(Collectors.toSet());
                requireTeam(task.getProjectId(), assignees.stream().map(TaskAssignee::userId)
                        .filter(id -> !before.contains(id)).toList(), true);
                task.setAssignees(assignees);
                changed.add(Fields.ASSIGNEES);
            }

            if (extras != null && !new HashSet<>(extras.observerIds()).equals(new HashSet<>(task.getObserverIds()))) {
                Set<UUID> before = Set.copyOf(task.getObserverIds());
                requireTeam(task.getProjectId(), extras.observerIds().stream().filter(id -> !before.contains(id)).toList(), false);
                task.setObserverIds(extras.observerIds());
                changed.add(Fields.OBSERVERS);
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
     * Новые исполнители — участники проекта, кроме наблюдателей; новые наблюдатели — любые участники
     * и администраторы.
     * Иначе сервер отклонит изменение (API.md, 8.7). Кто уже был в задаче, остаётся, даже если его потом
     * исключили из проекта.
     */
    private void requireTeam(UUID projectId, List<UUID> added, boolean assignees) throws SQLException {
        if (added.isEmpty()) {
            return;
        }
        Map<UUID, String> roles = permissions.projectMembers(projectId);
        ProjectTeam team = ProjectTeam.of(roles, assignees ? Set.of() : permissions.admins());
        for (UUID userId : added) {
            if (!(assignees ? team.assignable() : team.observers()).contains(userId)) {
                String name = permissions.userName(userId).orElse("Сотрудник");
                throw new IllegalArgumentException(roles.containsKey(userId)
                        ? "«" + name + "» — наблюдатель в проекте и не может исполнять задачи: смените ему роль "
                                + "или выберите другого сотрудника"
                        : "«" + name + "» не участвует в проекте: добавьте сотрудника в участники "
                                + "или выберите другого сотрудника");
            }
        }
    }

    /**
     * closedAt ставится при переходе в DONE и сбрасывается при выходе из него (API.md, 8.2).
     */
    @Override
    public TaskView take(UUID id) {
        return changeOwnAssignment(id, (task, me, changed, now) -> {
            if (task.getAssignees().isEmpty()) {
                task.setAssignees(List.of(new TaskAssignee(me, AssigneeRole.RESPONSIBLE)));
                changed.add(Fields.ASSIGNEES);
            }
            if (task.getStatus() == TaskStatus.NEW) {
                changeStatus(task, TaskStatus.IN_PROGRESS, now, changed);
            }
        });
    }

    @Override
    public TaskView join(UUID id) {
        return changeOwnAssignment(id, (task, me, changed, now) -> {
            if (task.getAssignees().stream().noneMatch(a -> a.userId().equals(me))) {
                List<TaskAssignee> assignees = new ArrayList<>(task.getAssignees());
                assignees.add(new TaskAssignee(me, AssigneeRole.CO_EXECUTOR));
                task.setAssignees(assignees);
                changed.add(Fields.ASSIGNEES);
            }
        });
    }

    @Override
    public TaskView leave(UUID id) {
        return changeOwnAssignment(id, (task, me, changed, now) -> {
            boolean wasResponsible = task.getAssignees().stream()
                    .anyMatch(a -> a.userId().equals(me) && a.role() == AssigneeRole.RESPONSIBLE);
            List<TaskAssignee> assignees = task.getAssignees().stream()
                    .filter(a -> !a.userId().equals(me)).toList();
            if (assignees.size() != task.getAssignees().size()) {
                task.setAssignees(assignees);
                changed.add(Fields.ASSIGNEES);
            }
            if (wasResponsible && task.getStatus() != TaskStatus.NEW && task.getStatus() != TaskStatus.DONE) {
                changeStatus(task, TaskStatus.NEW, now, changed);
            }
        });
    }

    private interface AssignmentChange {
        void apply(Task task, UUID me, List<String> changed, Instant now) throws SQLException;
    }

    private TaskView changeOwnAssignment(UUID id, AssignmentChange change) {
        return uow.inTransaction(() -> {
            Task task = repository.findById(id)
                    .orElseThrow(() -> new NotFoundException("Task not found: " + id));
            Instant now = Instant.now();
            List<String> changed = new ArrayList<>();
            change.apply(task, session.getCurrentUserId(), changed, now);
            if (!changed.isEmpty()) {
                task.setUpdatedAt(now);
                task.setUpdatedBy(session.getCurrentUserId());
                repository.update(task);
                changeTracker.updated(EntityType.TASK, task.getId(), changed, now);
            }
            return requireView(task.getId());
        });
    }

    /**
     * Дата начала по умолчанию — сегодня, но не позже дедлайна: иначе задача не сохранится.
     * {@code null}, если дедлайн уже прошёл.
     */
    public static LocalDate defaultStartDate(LocalDate deadline, LocalDate today) {
        return deadline == null || !deadline.isBefore(today) ? today : null;
    }

    private void changeStatus(Task task, TaskStatus status, Instant now, List<String> changed) {
        task.setStatus(status);
        changed.add(Fields.STATUS);

        // Задачу впервые берут в работу — это и есть её начало, если дату не указали сами
        if (status == TaskStatus.IN_PROGRESS && task.getStartDate() == null) {
            LocalDate start = defaultStartDate(task.getDeadline(), LocalDate.now());
            if (start != null) {
                task.setStartDate(start);
                if (!changed.contains(Fields.START_DATE)) {
                    changed.add(Fields.START_DATE);
                }
            }
        }

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

    /** Ответственный и соисполнители из формы. Ответственный соисполнителем не бывает. */
    private List<TaskAssignee> withCoExecutors(UUID responsibleId, List<UUID> coExecutorIds) {
        List<TaskAssignee> result = new ArrayList<>();
        if (responsibleId != null) {
            result.add(new TaskAssignee(responsibleId, AssigneeRole.RESPONSIBLE));
        }
        new LinkedHashSet<>(coExecutorIds).stream()
                .filter(userId -> !userId.equals(responsibleId))
                .forEach(userId -> result.add(new TaskAssignee(userId, AssigneeRole.CO_EXECUTOR)));
        return result;
    }

    @Override
    public TaskExtras findExtras(UUID id) {
        Task task = uow.read(() -> repository.findById(id))
                .orElseThrow(() -> new NotFoundException("Task not found: " + id));
        List<UUID> coExecutors = task.getAssignees().stream()
                .filter(a -> a.role() == AssigneeRole.CO_EXECUTOR)
                .map(TaskAssignee::userId)
                .toList();
        return new TaskExtras(task.getStartDate(), coExecutors, task.getObserverIds());
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
