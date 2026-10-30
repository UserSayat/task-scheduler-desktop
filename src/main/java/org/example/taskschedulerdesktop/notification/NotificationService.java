package org.example.taskschedulerdesktop.notification;

import org.example.taskschedulerdesktop.session.Session;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;

import java.util.List;
import java.util.UUID;

/**
 * Уведомления для экранов — из локальной базы, поэтому работают и офлайн.
 * Отметка «прочитано» сохраняется сразу и уходит на сервер при ближайшей синхронизации ({@code afterRead}).
 */
public class NotificationService {

    private final UnitOfWork uow;
    private final NotificationRepository repository;
    private final Session session;
    private final Runnable afterRead;

    public NotificationService(UnitOfWork uow, NotificationRepository repository, Session session, Runnable afterRead) {
        this.uow = uow;
        this.repository = repository;
        this.session = session;
        this.afterRead = afterRead;
    }

    public List<Notification> findAll() {
        return uow.read(() -> repository.findAll(session.getCurrentUserId()));
    }

    public int unreadCount() {
        return uow.read(() -> repository.countUnread(session.getCurrentUserId()));
    }

    public void markRead(UUID id) {
        boolean changed = uow.inTransaction(() -> {
            if (!repository.markRead(id)) {
                return false;
            }
            repository.addPendingRead(id);
            return true;
        });
        if (changed) {
            afterRead.run();
        }
    }

    public void markAllRead() {
        List<UUID> marked = uow.inTransaction(() -> {
            List<UUID> ids = repository.markAllRead(session.getCurrentUserId());
            for (UUID id : ids) {
                repository.addPendingRead(id);
            }
            return ids;
        });
        if (!marked.isEmpty()) {
            afterRead.run();
        }
    }
}
