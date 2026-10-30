package org.example.taskschedulerdesktop.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * Уведомление текущего пользователя (API.md, 9.1). Создаёт их сервер, клиент хранит копию,
 * чтобы колокольчик работал и без сети.
 *
 * @param entityType {@code TASK} — уведомление о задаче, {@code entityId} — её id
 */
public record Notification(
        UUID id,
        String type,
        String title,
        String body,
        String entityType,
        UUID entityId,
        boolean read,
        Instant createdAt
) {

    public boolean isAboutTask() {
        return "TASK".equals(entityType) && entityId != null;
    }
}
