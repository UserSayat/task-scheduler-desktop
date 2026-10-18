package org.example.taskschedulerdesktop.task;

import java.util.UUID;

public class TaskDeleteEvent {

    private final UUID taskId;

    public TaskDeleteEvent(UUID taskId) {
        this.taskId = taskId;
    }

    public UUID getTaskId() {
        return taskId;
    }
}
