package org.example.taskschedulerdesktop.task;

import javafx.scene.control.Label;

/**
 * Бейдж приоритета: текст и цвет по {@link TaskPriority}.
 */
public final class PriorityBadge {

    private PriorityBadge() {}

    public static void apply(Label label, TaskPriority priority) {
        label.setText(priority.getDisplayName());
        label.getStyleClass().removeAll(TaskPriority.badgeStyleClasses());
        label.getStyleClass().add(priority.badgeStyleClass());
    }
}
