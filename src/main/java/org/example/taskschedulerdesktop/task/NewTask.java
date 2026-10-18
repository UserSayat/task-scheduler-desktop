package org.example.taskschedulerdesktop.task;


import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Данные для создания задачи из формы.
 */
public record NewTask(
        String title,
        UUID projectId,
        UUID responsibleId,
        TaskPriority priority,
        LocalDate deadline,
        List<String> tagNames
) {
}
