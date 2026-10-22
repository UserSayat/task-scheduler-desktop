package org.example.taskschedulerdesktop.sync.protocol;

import com.fasterxml.jackson.core.type.TypeReference;
import org.example.taskschedulerdesktop.shared.api.ApiClient;
import org.example.taskschedulerdesktop.sync.protocol.SyncMessages.Change;
import org.example.taskschedulerdesktop.sync.protocol.SyncMessages.PullResponse;
import org.example.taskschedulerdesktop.sync.protocol.SyncMessages.PushRequest;
import org.example.taskschedulerdesktop.sync.protocol.SyncMessages.PushResponse;
import org.example.taskschedulerdesktop.sync.protocol.SyncMessages.SnapshotResponse;

import java.util.List;
import java.util.UUID;

public class HttpSyncApi implements SyncApi {

    private final ApiClient api;

    public HttpSyncApi(ApiClient api) {
        this.api = api;
    }

    @Override
    public PushResponse push(List<Change> changes) {
        return api.post("/sync/push", new PushRequest(changes), new TypeReference<>() {});
    }

    @Override
    public PullResponse pull(String cursor) {
        return api.get("/sync/pull?cursor=" + ApiClient.encode(cursor), new TypeReference<>() {});
    }

    @Override
    public SnapshotResponse snapshot(UUID projectId, int page) {
        String query = "?page=" + page + (projectId != null ? "&projectId=" + projectId : "");
        return api.get("/sync/snapshot" + query, new TypeReference<>() {});
    }
}
