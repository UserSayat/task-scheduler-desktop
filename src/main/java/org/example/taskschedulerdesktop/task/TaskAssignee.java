package org.example.taskschedulerdesktop.task;


import java.util.UUID;

/**
 * Исполнитель задачи: ответственный (RESPONSIBLE) или соисполнитель (CO_EXECUTOR).
 */
public record TaskAssignee(UUID userId, AssigneeRole role) {
}
