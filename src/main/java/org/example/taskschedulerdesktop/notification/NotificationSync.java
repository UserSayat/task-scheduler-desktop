package org.example.taskschedulerdesktop.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import org.example.taskschedulerdesktop.session.Session;
import org.example.taskschedulerdesktop.shared.api.ApiClient;
import org.example.taskschedulerdesktop.shared.api.ApiException;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.example.taskschedulerdesktop.sync.SyncStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Обмен уведомлениями в конце каждой синхронизации (API.md, раздел 9):
 * <ol>
 *   <li>отметки «прочитано», сделанные офлайн, уходят на сервер;</li>
 *   <li>последние уведомления с сервера заменяют локальную копию.</li>
 * </ol>
 * Пока хоть одна отметка не отправлена, локальная копия не заменяется — иначе отметка потерялась бы.
 */
public class NotificationSync implements SyncStep {

    private static final Logger log = LoggerFactory.getLogger(NotificationSync.class);

    /** Сколько последних уведомлений держать на клиенте. */
    static final int KEEP = 50;

    /** Уведомление в ответе сервера (API.md, 9.1). */
    record NotificationDto(UUID id, String type, String title, String body, String entityType, UUID entityId,
                           boolean isRead, Instant createdAt) {

        Notification toNotification() {
            return new Notification(id, type, title, body, entityType, entityId, isRead, createdAt);
        }
    }

    private final ApiClient api;
    private final UnitOfWork uow;
    private final NotificationRepository repository;
    private final Session session;
    private final Runnable onChanged;

    /**
     * @param onChanged локальные уведомления обновились (вызывается в потоке синхронизации)
     */
    public NotificationSync(ApiClient api, UnitOfWork uow, NotificationRepository repository, Session session,
                            Runnable onChanged) {
        this.api = api;
        this.uow = uow;
        this.repository = repository;
        this.session = session;
        this.onChanged = onChanged;
    }

    @Override
    public void run() {
        sendPendingReads();
        List<Notification> fresh = api.get("/notifications?perPage=" + KEEP,
                        new TypeReference<List<NotificationDto>>() {})
                .stream()
                .map(NotificationDto::toNotification)
                .toList();
        UUID userId = session.getCurrentUserId();
        boolean replaced = uow.inTransaction(() -> {
            // Пока шёл запрос, пользователь мог отметить что-то прочитанным — тогда обновим в следующий раз
            if (!repository.pendingReads().isEmpty()) {
                return false;
            }
            repository.replaceAll(userId, fresh);
            return true;
        });
        if (replaced) {
            onChanged.run();
        }
    }

    private void sendPendingReads() {
        for (UUID id : uow.read(repository::pendingReads)) {
            try {
                api.patch("/notifications/" + id + "/read", null);
            } catch (ApiException e) {
                if (!e.is("NOT_FOUND")) {
                    throw e;
                }
                log.debug("Notification {} no longer exists on the server", id);
            }
            uow.inTransaction(() -> repository.removePendingRead(id));
        }
    }
}
