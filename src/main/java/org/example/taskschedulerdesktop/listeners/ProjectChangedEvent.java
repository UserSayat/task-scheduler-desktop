package org.example.taskschedulerdesktop.listeners;

public class ProjectChangedEvent {

    private final Long projectId;
    private final ChangeType changeType;

    public ProjectChangedEvent(Long projectId, ChangeType changeType) {
        this.projectId = projectId;
        this.changeType = changeType;
    }

    public Long getProjectId() {
        return projectId;
    }

    public ChangeType getChangeType() {
        return changeType;
    }
}
