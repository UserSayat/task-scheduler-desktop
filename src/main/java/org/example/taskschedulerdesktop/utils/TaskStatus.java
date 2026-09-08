package org.example.taskschedulerdesktop.utils;

public enum TaskStatus {
    NEW("Новая"),
    IN_PROGRESS("В работе"),
    UNDER_REVIEW("На проверке"),
    COMPLETED("Завершена");

    private final String displayName;

    TaskStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static TaskStatus fromString(String text) {
        for (TaskStatus status : TaskStatus.values()) {
            if (status.displayName.equals(text)) {
                return status;
            }
        }
        return NEW;
    }
}
