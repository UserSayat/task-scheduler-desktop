package org.example.taskschedulerdesktop.notification;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Уведомления в локальной базе и очередь отметок «прочитано», сделанных офлайн.
 * Методы вызываются внутри единицы работы (UnitOfWork).
 */
public interface NotificationRepository {

    /** Новые сверху. */
    List<Notification> findAll(UUID userId) throws SQLException;

    int countUnread(UUID userId) throws SQLException;

    /** @return true, если уведомление было непрочитанным */
    boolean markRead(UUID id) throws SQLException;

    /** @return id уведомлений, которые были непрочитанными */
    List<UUID> markAllRead(UUID userId) throws SQLException;

    void addPendingRead(UUID id) throws SQLException;

    List<UUID> pendingReads() throws SQLException;

    void removePendingRead(UUID id) throws SQLException;

    /** Заменяет уведомления пользователя списком с сервера. */
    void replaceAll(UUID userId, List<Notification> notifications) throws SQLException;
}
