package org.example.taskschedulerdesktop.models;

import org.example.taskschedulerdesktop.utils.AssigneeRole;

import java.util.UUID;

/**
 * Исполнитель задачи: ответственный (RESPONSIBLE) или соисполнитель (CO_EXECUTOR).
 */
public record TaskAssignee(UUID userId, AssigneeRole role) {
}
