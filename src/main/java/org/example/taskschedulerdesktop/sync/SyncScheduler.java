package org.example.taskschedulerdesktop.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Когда запускать синхронизацию (API.md, 8.1): при старте, каждые 120 секунд, через 5 секунд после
 * локального изменения и по кнопке. Без сети сервер проверяется чаще — раз в 30 секунд, чтобы изменения
 * ушли вскоре после появления связи.
 * <p>
 * Синхронизация идёт в одном фоновом потоке, поэтому два цикла никогда не выполняются одновременно.
 */
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    private static final Duration INTERVAL = Duration.ofSeconds(120);
    private static final Duration OFFLINE_RETRY = Duration.ofSeconds(30);
    private static final Duration AFTER_CHANGE = Duration.ofSeconds(5);

    private final SyncService syncService;
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> next;

    public SyncScheduler(SyncService syncService) {
        this.syncService = syncService;
    }

    public synchronized void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sync");
            thread.setDaemon(true);
            return thread;
        });
        schedule(Duration.ZERO);
        log.info("Sync scheduler started");
    }

    public synchronized void stop() {
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        executor = null;
        next = null;
        log.info("Sync scheduler stopped");
    }

    /** Синхронизировать сейчас — кнопка индикатора. */
    public void syncNow() {
        schedule(Duration.ZERO);
    }

    /** Локальное изменение: отправить через несколько секунд, собрав соседние правки в одну отправку. */
    public void changeRecorded() {
        syncService.localChangeRecorded();
        schedule(AFTER_CHANGE);
    }

    /**
     * Запланировать запуск через {@code delay}, если ближайший запланированный запуск позже.
     */
    private synchronized void schedule(Duration delay) {
        if (executor == null) {
            return;
        }
        if (next != null && !next.isDone()) {
            if (next.getDelay(TimeUnit.MILLISECONDS) <= delay.toMillis()) {
                return;
            }
            next.cancel(false);
        }
        next = executor.schedule(this::run, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void run() {
        // Запрос, пришедший во время синхронизации, запланирует новый запуск, а не потеряется
        synchronized (this) {
            next = null;
        }
        SyncStatus result = syncService.synchronize();
        if (result.state() == SyncStatus.State.SIGNED_OUT) {
            return; // ждём повторного входа
        }
        schedule(result.state() == SyncStatus.State.OFFLINE ? OFFLINE_RETRY : INTERVAL);
    }
}
