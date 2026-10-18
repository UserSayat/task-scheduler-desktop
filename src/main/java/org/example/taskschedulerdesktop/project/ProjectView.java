package org.example.taskschedulerdesktop.project;

import java.util.UUID;

/**
 * Проект для экранов: владелец и статистика по задачам, посчитанная при чтении (ТЗ, 5.1).
 *
 * @param synced false, если у проекта есть изменения, ещё не отправленные на сервер
 */
public record ProjectView(
        UUID id,
        String name,
        UUID ownerId,
        String ownerName,
        int totalTasks,
        int doneTasks,
        boolean synced
) {

    public int remainingTasks() {
        return totalTasks - doneTasks;
    }

    public int percentDone() {
        return totalTasks > 0 ? doneTasks * 100 / totalTasks : 0;
    }
}
