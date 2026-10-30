package org.example.taskschedulerdesktop.task;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Поля задачи, которые редко нужны в списках, но есть в карточке: дата начала,
 * соисполнители и наблюдатели.
 *
 * @param coExecutorIds соисполнители (ответственный сюда не входит)
 */
public record TaskExtras(LocalDate startDate, List<UUID> coExecutorIds, List<UUID> observerIds) {

    public TaskExtras {
        coExecutorIds = List.copyOf(coExecutorIds);
        observerIds = List.copyOf(observerIds);
    }
}
