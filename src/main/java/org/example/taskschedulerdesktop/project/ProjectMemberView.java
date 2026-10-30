package org.example.taskschedulerdesktop.project;

import java.util.UUID;

/**
 * Участник проекта для экранов.
 *
 * @param openTasks открытые задачи участника в этом проекте (исполнитель или соисполнитель)
 */
public record ProjectMemberView(UUID userId, String name, String position, MemberRole role, int openTasks) {
}
