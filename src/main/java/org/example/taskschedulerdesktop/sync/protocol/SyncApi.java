package org.example.taskschedulerdesktop.sync.protocol;

import org.example.taskschedulerdesktop.sync.protocol.SyncMessages.Change;
import org.example.taskschedulerdesktop.sync.protocol.SyncMessages.PullResponse;
import org.example.taskschedulerdesktop.sync.protocol.SyncMessages.PushResponse;
import org.example.taskschedulerdesktop.sync.protocol.SyncMessages.SnapshotResponse;

import java.util.List;
import java.util.UUID;

/**
 * Эндпоинты синхронизации сервера (API.md, 8.3–8.5). Отдельный интерфейс — чтобы в тестах
 * подставить сервер-заглушку.
 */
public interface SyncApi {

    PushResponse push(List<Change> changes);

    PullResponse pull(String cursor);

    /** @param projectId null — все доступные данные, иначе только проект и его задачи */
    SnapshotResponse snapshot(UUID projectId, int page);
}
