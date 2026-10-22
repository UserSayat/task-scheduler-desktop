package org.example.taskschedulerdesktop.sync.protocol;

public enum Operation {
    CREATE,
    UPDATE,
    DELETE,
    /** Только в pull: пользователь потерял доступ к проекту (API.md, 8.4). */
    REVOKE
}
