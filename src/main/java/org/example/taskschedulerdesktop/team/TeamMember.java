package org.example.taskschedulerdesktop.team;

import java.util.List;
import java.util.UUID;

/**
 * Карточка сотрудника на экране «Команда».
 *
 * @param openTasks задачи, где сотрудник исполнитель или соисполнитель, кроме выполненных
 * @param doneTasks выполненные задачи
 * @param topTasks  самые важные открытые задачи: сначала по приоритету, потом по сроку
 */
public record TeamMember(
        UUID id,
        String name,
        String position,
        int openTasks,
        int doneTasks,
        List<MemberTask> topTasks
) {
}
