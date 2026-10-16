package org.example.taskschedulerdesktop.dto.tasks;

import org.example.taskschedulerdesktop.utils.TaskPriority;
import org.example.taskschedulerdesktop.utils.TaskStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Задача для экранов: вместе с названием проекта, ответственным и тегами.
 * Только для чтения — изменения идут через команды сервиса (NewTask, TaskEdit).
 *
 * @param synced false, если у задачи есть изменения, ещё не отправленные на сервер
 */
public record TaskView(
        UUID id,
        String title,
        String description,
        UUID projectId,
        String projectName,
        UUID responsibleId,
        String responsibleName,
        TaskStatus status,
        TaskPriority priority,
        LocalDate deadline,
        List<String> tagNames,
        boolean synced
) {

    public String tagsAsText() {
        return String.join(", ", tagNames);
    }

    public boolean isOverdue() {
        return deadline != null && deadline.isBefore(LocalDate.now()) && status != TaskStatus.DONE;
    }
}
