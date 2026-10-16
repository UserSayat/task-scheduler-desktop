package org.example.taskschedulerdesktop.service.task;

import org.example.taskschedulerdesktop.utils.TaskPriority;
import org.example.taskschedulerdesktop.utils.TaskStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Новые значения полей задачи из формы редактирования. В журнал синхронизации попадут
 * только поля, значения которых действительно изменились.
 */
public record TaskEdit(
        UUID id,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        LocalDate deadline,
        UUID responsibleId,
        List<String> tagNames
) {
}
