package org.example.taskschedulerdesktop.listeners;

public class TaskChangedEvent {
    private final Long projectId;
    private final String taskName;

    public TaskChangedEvent(Long projectId, String taskName) {
        this.projectId = projectId;
        this.taskName = taskName;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getTaskName() {
        return taskName;
    }
}
