package org.example.taskschedulerdesktop.utils;

public enum TaskPriority {
    LOW("Низкий"),
    MEDIUM("Средний"),
    HIGH("Высокий"),
    CRITICAL("Критический");

    private final String displayName;

    TaskPriority(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static TaskPriority fromString(String text) {
        for (TaskPriority priority : TaskPriority.values()) {
            if (priority.displayName.equals(text)) {
                return priority;
            }
        }
        return LOW;
    }
}
