package org.example.taskschedulerdesktop.settings;

import org.example.taskschedulerdesktop.shared.database.Jdbc;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Локальная копия системных настроек (таблица settings).
 */
public class SettingsRepository {

    private final UnitOfWork uow;

    public SettingsRepository(UnitOfWork uow) {
        this.uow = uow;
    }

    public Map<String, String> findAll() throws SQLException {
        Map<String, String> settings = new LinkedHashMap<>();
        try (PreparedStatement ps = uow.connection().prepareStatement("SELECT setting_key, setting_value FROM settings");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                settings.put(rs.getString(1), rs.getString(2));
            }
        }
        return settings;
    }

    public void saveAll(Map<String, String> settings) throws SQLException {
        String sql = "MERGE INTO settings (setting_key, setting_value, updated_at) KEY (setting_key) VALUES (?, ?, ?)";
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            for (Map.Entry<String, String> entry : settings.entrySet()) {
                ps.setString(1, entry.getKey());
                ps.setString(2, entry.getValue());
                Jdbc.setInstant(ps, 3, Instant.now());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
