package org.example.taskschedulerdesktop.utils;

public enum TaskStatus {
    NEW("К выполнению"),
    IN_PROGRESS("В работе"),
    ON_REVIEW("На проверке"),
    DONE("Завершена");

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
