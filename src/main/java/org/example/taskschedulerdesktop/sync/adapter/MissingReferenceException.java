package org.example.taskschedulerdesktop.sync.adapter;

import java.util.UUID;

/**
 * Пришедшая с сервера сущность ссылается на проект, которого у клиента ещё нет
 * (например, задачу перенесли в проект, куда пользователя добавили позже). Изменение откладывается,
 * а проект загружается через snapshot (API.md, 8.4).
 */
public class MissingReferenceException extends RuntimeException {

    private final UUID projectId;

    public MissingReferenceException(UUID projectId) {
        super("Project is not available locally: " + projectId);
        this.projectId = projectId;
    }

    public UUID projectId() {
        return projectId;
    }
}
