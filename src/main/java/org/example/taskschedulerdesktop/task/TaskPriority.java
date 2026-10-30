package org.example.taskschedulerdesktop.task;

import java.util.Arrays;
import java.util.List;

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

    /**
     * Окончание CSS-классов приоритета: {@code badge-critical}, {@code priority-high}, {@code toggle-low}...
     */
    public String styleName() {
        return name().toLowerCase();
    }

    /** Стиль бейджа приоритета (карточки и детали задачи). */
    public String badgeStyleClass() {
        return "badge-" + styleName();
    }

    /** Все стили бейджей — чтобы снять старый перед установкой нового. */
    public static List<String> badgeStyleClasses() {
        return Arrays.stream(values()).map(TaskPriority::badgeStyleClass).toList();
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
