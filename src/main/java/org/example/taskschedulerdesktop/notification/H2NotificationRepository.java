package org.example.taskschedulerdesktop.notification;

import org.example.taskschedulerdesktop.shared.database.Jdbc;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class H2NotificationRepository implements NotificationRepository {

    private final UnitOfWork uow;

    public H2NotificationRepository(UnitOfWork uow) {
        this.uow = uow;
    }

    @Override
    public List<Notification> findAll(UUID userId) throws SQLException {
        String sql = """
                SELECT id, type, title, body, entity_type, entity_id, is_read, created_at
                  FROM notifications WHERE user_id = ? ORDER BY created_at DESC
                """;
        List<Notification> notifications = new ArrayList<>();
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            Jdbc.setUuid(ps, 1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    notifications.add(new Notification(
                            Jdbc.getUuid(rs, "id"),
                            rs.getString("type"),
                            rs.getString("title"),
                            rs.getString("body"),
                            rs.getString("entity_type"),
                            Jdbc.getUuid(rs, "entity_id"),
                            rs.getBoolean("is_read"),
                            Jdbc.getInstant(rs, "created_at")));
                }
            }
        }
        return notifications;
    }

    @Override
    public int countUnread(UUID userId) throws SQLException {
        try (PreparedStatement ps = uow.connection().prepareStatement(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = FALSE")) {
            Jdbc.setUuid(ps, 1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    @Override
    public boolean markRead(UUID id) throws SQLException {
        try (PreparedStatement ps = uow.connection().prepareStatement(
                "UPDATE notifications SET is_read = TRUE, updated_at = ? WHERE id = ? AND is_read = FALSE")) {
            Jdbc.setInstant(ps, 1, Instant.now());
            Jdbc.setUuid(ps, 2, id);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public List<UUID> markAllRead(UUID userId) throws SQLException {
        List<UUID> unread = new ArrayList<>();
        try (PreparedStatement ps = uow.connection().prepareStatement(
                "SELECT id FROM notifications WHERE user_id = ? AND is_read = FALSE")) {
            Jdbc.setUuid(ps, 1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    unread.add(Jdbc.getUuid(rs, "id"));
                }
            }
        }
        for (UUID id : unread) {
            markRead(id);
        }
        return unread;
    }

    @Override
    public void addPendingRead(UUID id) throws SQLException {
        try (PreparedStatement ps = uow.connection().prepareStatement(
                "MERGE INTO pending_notification_reads (notification_id, created_at) KEY (notification_id) VALUES (?, ?)")) {
            Jdbc.setUuid(ps, 1, id);
            Jdbc.setInstant(ps, 2, Instant.now());
            ps.executeUpdate();
        }
    }

    @Override
    public List<UUID> pendingReads() throws SQLException {
        List<UUID> ids = new ArrayList<>();
        try (PreparedStatement ps = uow.connection().prepareStatement(
                "SELECT notification_id FROM pending_notification_reads ORDER BY created_at");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add(Jdbc.getUuid(rs, "notification_id"));
            }
        }
        return ids;
    }

    @Override
    public void removePendingRead(UUID id) throws SQLException {
        try (PreparedStatement ps = uow.connection().prepareStatement(
                "DELETE FROM pending_notification_reads WHERE notification_id = ?")) {
            Jdbc.setUuid(ps, 1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public void replaceAll(UUID userId, List<Notification> notifications) throws SQLException {
        try (PreparedStatement ps = uow.connection().prepareStatement("DELETE FROM notifications WHERE user_id = ?")) {
            Jdbc.setUuid(ps, 1, userId);
            ps.executeUpdate();
        }
        String sql = """
                INSERT INTO notifications (id, user_id, type, title, body, entity_type, entity_id, is_read,
                                           created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = uow.connection().prepareStatement(sql)) {
            for (Notification n : notifications) {
                Jdbc.setUuid(ps, 1, n.id());
                Jdbc.setUuid(ps, 2, userId);
                ps.setString(3, n.type());
                ps.setString(4, n.title());
                ps.setString(5, n.body());
                ps.setString(6, n.entityType());
                Jdbc.setUuid(ps, 7, n.entityId());
                ps.setBoolean(8, n.read());
                Jdbc.setInstant(ps, 9, n.createdAt());
                Jdbc.setInstant(ps, 10, n.createdAt());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
