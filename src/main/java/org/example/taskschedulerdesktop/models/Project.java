package org.example.taskschedulerdesktop.models;

import org.example.taskschedulerdesktop.utils.ProjectStatus;
import org.example.taskschedulerdesktop.utils.Visibility;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Проект — зеркало таблицы projects вместе с участниками.
 * Статистика по задачам здесь не хранится: она считается при чтении (см. ProjectView).
 */
public class Project {

    public static final class Fields {
        public static final String NAME = "name";
        public static final String DESCRIPTION = "description";
        public static final String OWNER_ID = "ownerId";
        public static final String STATUS = "status";
        public static final String VISIBILITY = "visibility";
        public static final String START_DATE = "startDate";
        public static final String DEADLINE = "deadline";
        public static final String DELETED_AT = "deletedAt";
        public static final String MEMBERS = "members";

        public static final List<String> ON_CREATE = List.of(
                NAME, DESCRIPTION, OWNER_ID, STATUS, VISIBILITY, START_DATE, DEADLINE, MEMBERS);

        private Fields() {}
    }

    private UUID id;
    private String name;
    private String description;
    private UUID ownerId;
    private ProjectStatus status = ProjectStatus.ACTIVE;
    private Visibility visibility = Visibility.PRIVATE;
    private LocalDate startDate;
    private LocalDate deadline;
    private Instant createdAt;
    private Instant updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
    private Instant deletedAt;

    private List<ProjectMember> members = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }

    public ProjectStatus getStatus() { return status; }
    public void setStatus(ProjectStatus status) { this.status = status; }

    public Visibility getVisibility() { return visibility; }
    public void setVisibility(Visibility visibility) { this.visibility = visibility; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }

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

    public List<ProjectMember> getMembers() { return members; }
    public void setMembers(List<ProjectMember> members) { this.members = new ArrayList<>(members); }
}
