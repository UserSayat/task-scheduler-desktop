package org.example.taskschedulerdesktop.project;

import org.example.taskschedulerdesktop.shared.event.ChangeType;
import java.util.UUID;

public class ProjectChangedEvent {

    private final UUID projectId;
    private final ChangeType changeType;

    public ProjectChangedEvent(UUID projectId, ChangeType changeType) {
        this.projectId = projectId;
        this.changeType = changeType;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public ChangeType getChangeType() {
        return changeType;
    }
}
