package org.example.taskschedulerdesktop.task;


import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Задача — зеркало таблицы tasks вместе с коллекциями (исполнители, наблюдатели, теги).
 * Имена синхронизируемых полей — в {@link Fields}, они совпадают с контрактом API (API.md, 8.2).
 */
public class Task {

    public static final class Fields {
        public static final String TITLE = "title";
        public static final String DESCRIPTION = "description";
        public static final String PROJECT_ID = "projectId";
        public static final String CREATOR_ID = "creatorId";
        public static final String STATUS = "status";
        public static final String PRIORITY = "priority";
        public static final String START_DATE = "startDate";
        public static final String DEADLINE = "deadline";
        public static final String CLOSED_AT = "closedAt";
        public static final String DELETED_AT = "deletedAt";
        public static final String ASSIGNEES = "assignees";
        public static final String OBSERVERS = "observers";
        public static final String TAGS = "tags";

        public static final List<String> ON_CREATE = List.of(
                TITLE, DESCRIPTION, PROJECT_ID, CREATOR_ID, STATUS, PRIORITY,
                START_DATE, DEADLINE, CLOSED_AT, ASSIGNEES, OBSERVERS, TAGS);

        private Fields() {}
    }

    private UUID id;
    private String title;
    private String description;
    private UUID projectId;
    private UUID creatorId;
    private TaskStatus status = TaskStatus.NEW;
    private TaskPriority priority = TaskPriority.MEDIUM;
    private LocalDate startDate;
    private LocalDate deadline;
    private Instant closedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
    private Instant deletedAt;

    private List<TaskAssignee> assignees = new ArrayList<>();
    private List<UUID> observerIds = new ArrayList<>();
    private List<UUID> tagIds = new ArrayList<>();

    public Optional<UUID> getResponsibleId() {
        return assignees.stream()
                .filter(a -> a.role() == AssigneeRole.RESPONSIBLE)
                .map(TaskAssignee::userId)
                .findFirst();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public UUID getCreatorId() { return creatorId; }
    public void setCreatorId(UUID creatorId) { this.creatorId = creatorId; }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }

    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }

    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public List<TaskAssignee> getAssignees() { return assignees; }
    public void setAssignees(List<TaskAssignee> assignees) { this.assignees = new ArrayList<>(assignees); }

    public List<UUID> getObserverIds() { return observerIds; }
    public void setObserverIds(List<UUID> observerIds) { this.observerIds = new ArrayList<>(observerIds); }

    public List<UUID> getTagIds() { return tagIds; }
    public void setTagIds(List<UUID> tagIds) { this.tagIds = new ArrayList<>(tagIds); }
}
