package org.example.taskschedulerdesktop.sync.local;

import org.example.taskschedulerdesktop.shared.database.UnitOfWork;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Служебные значения синхронизации в таблице sync_state: устройство, курсор pull, вошедший пользователь
 * и его refresh-токен (API.md, 5).
 */
public class SyncStateRepository {

    public static final String DEVICE_ID = "device_id";
    public static final String PULL_CURSOR = "pull_cursor";
    public static final String CURRENT_USER_ID = "current_user_id";
    public static final String REFRESH_TOKEN = "refresh_token";

    private final UnitOfWork uow;

    public SyncStateRepository(UnitOfWork uow) {
        this.uow = uow;
    }

    public Optional<String> get(String key) throws SQLException {
        String sql = "SELECT state_value FROM sync_state WHERE state_key = ?";
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
            }
        }
    }

    public void set(String key, String value) throws SQLException {
        String sql = "MERGE INTO sync_state (state_key, state_value) KEY (state_key) VALUES (?, ?)";
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    public void remove(String key) throws SQLException {
        try (PreparedStatement ps = uow.connection().prepareStatement("DELETE FROM sync_state WHERE state_key = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
        }
    }
}
