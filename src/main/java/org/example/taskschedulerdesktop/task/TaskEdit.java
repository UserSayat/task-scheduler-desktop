package org.example.taskschedulerdesktop.task;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Новые значения полей задачи из формы редактирования. В журнал синхронизации попадут
 * только поля, значения которых действительно изменились.
 *
 * @param extras дата начала, соисполнители и наблюдатели; {@code null} — эти поля не меняются
 */
public record TaskEdit(
        UUID id,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        LocalDate deadline,
        UUID responsibleId,
        List<String> tagNames,
        TaskExtras extras
) {

    /** Изменение без даты начала, соисполнителей и наблюдателей. */
    public TaskEdit(UUID id, String title, String description, TaskStatus status, TaskPriority priority,
                    LocalDate deadline, UUID responsibleId, List<String> tagNames) {
        this(id, title, description, status, priority, deadline, responsibleId, tagNames, null);
    }
}
