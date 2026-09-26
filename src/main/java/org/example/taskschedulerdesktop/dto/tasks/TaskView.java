package org.example.taskschedulerdesktop.dto.tasks;

import org.example.taskschedulerdesktop.utils.TaskPriority;
import org.example.taskschedulerdesktop.utils.TaskStatus;

import java.time.LocalDate;

public class TaskView {

    private Long id;
    private String taskName;
    private Long projectId;
    private String projectName;  // 👈 из JOIN
    private String executor;
    private String type;
    private TaskStatus status;
    private TaskPriority priority;
    private LocalDate deadline;
    private String description;
    private boolean synced;

    public TaskView(Long id, String taskName, long projectId, String projectName, String executor, String type, TaskStatus status, TaskPriority priority, LocalDate deadline, String description, boolean synced) {
        this.id = id;
        this.taskName = taskName;
        this.projectId = projectId;
        this.projectName = projectName;
        this.executor = executor;
        this.type = type;
        this.status = status;
        this.priority = priority;
        this.deadline = deadline;
        this.description = description;
        this.synced = synced;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getExecutor() {
        return executor;
    }

    public void setExecutor(String executor) {
        this.executor = executor;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDate deadline) {
        this.deadline = deadline;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isSynced() {
        return synced;
    }

    public void setSynced(boolean synced) {
        this.synced = synced;
    }
}
