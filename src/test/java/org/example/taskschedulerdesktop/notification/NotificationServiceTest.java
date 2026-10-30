package org.example.taskschedulerdesktop.notification;

import org.example.taskschedulerdesktop.config.DevDataSeeder;
import org.example.taskschedulerdesktop.session.Session;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Отметки «прочитано» на клиенте: сразу видны на экране и встают в очередь для сервера.
 */
class NotificationServiceTest {

    private UnitOfWork uow;
    private H2NotificationRepository repository;
    private NotificationService service;
    private final AtomicInteger syncRequests = new AtomicInteger();

    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        String url = "jdbc:h2:mem:notif_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:migration").load().migrate();
        uow = new UnitOfWork(() -> DriverManager.getConnection(url, "sa", ""));
        DevDataSeeder.seed(uow);

        Session session = new Session(UUID.randomUUID(), DevDataSeeder.DEV_ADMIN_ID);
        repository = new H2NotificationRepository(uow);
        service = new NotificationService(uow, repository, session, syncRequests::incrementAndGet);

        Instant now = Instant.now();
        uow.inTransaction(() -> repository.replaceAll(DevDataSeeder.DEV_ADMIN_ID, List.of(
                new Notification(first, "TASK_ASSIGNED", "Вам назначена задача", "«А»", "TASK", UUID.randomUUID(), false, now.minusSeconds(60)),
                new Notification(second, "STATUS_CHANGED", "Изменён статус задачи", "«Б»", "TASK", UUID.randomUUID(), false, now))));
    }

    @Test
    void newestFirstAndUnreadCounted() {
        assertEquals(List.of(second, first), service.findAll().stream().map(Notification::id).toList());
        assertEquals(2, service.unreadCount());
    }

    @Test
    void readMarkIsLocalAndQueuedOnce() {
        service.markRead(first);
        service.markRead(first);

        assertEquals(1, service.unreadCount());
        assertTrue(service.findAll().stream().filter(n -> n.id().equals(first)).findFirst().orElseThrow().read());
        assertEquals(List.of(first), uow.read(repository::pendingReads));
        assertEquals(1, syncRequests.get(), "повторная отметка не запускает синхронизацию");
    }

    @Test
    void readAllQueuesEveryUnread() {
        service.markRead(first);
        service.markAllRead();

        assertEquals(0, service.unreadCount());
        assertEquals(2, uow.read(repository::pendingReads).size());
        uow.inTransaction(() -> repository.removePendingRead(first));
        assertFalse(uow.read(repository::pendingReads).contains(first));
    }
}
