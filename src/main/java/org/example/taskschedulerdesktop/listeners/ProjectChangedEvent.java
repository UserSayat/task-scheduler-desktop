package org.example.taskschedulerdesktop.listeners;

public class ProjectChangedEvent {

    private final Long projectId;

    public ProjectChangedEvent(Long projectId) {
        this.projectId = projectId;
    }

    public Long getProjectId() {
        return projectId;
    }
}
