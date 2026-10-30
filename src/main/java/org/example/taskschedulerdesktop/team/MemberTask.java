package org.example.taskschedulerdesktop.team;

import org.example.taskschedulerdesktop.task.TaskPriority;

import java.time.LocalDate;

/**
 * Открытая задача сотрудника в его карточке.
 */
public record MemberTask(String title, TaskPriority priority, LocalDate deadline) {
}
