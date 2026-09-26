package org.example.taskschedulerdesktop.listeners;

public class TaskDeleteEvent {

    private final long taskId;

    public TaskDeleteEvent(long taskId) {
        this.taskId = taskId;
    }

    public long getTaskId() {
        return taskId;
    }
}
