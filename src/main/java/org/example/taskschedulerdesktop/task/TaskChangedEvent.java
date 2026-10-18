package org.example.taskschedulerdesktop.task;

import java.util.UUID;

public class TaskChangedEvent {
    private final UUID projectId;
    private final String taskName;

    public TaskChangedEvent(UUID projectId, String taskName) {
        this.projectId = projectId;
        this.taskName = taskName;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getTaskName() {
        return taskName;
    }
}
