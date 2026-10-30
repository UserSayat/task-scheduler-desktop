package org.example.taskschedulerdesktop.sync;

/**
 * Дополнительный обмен с сервером в конце синхронизации — например, уведомления.
 * Выполняется после push и pull в потоке синхронизации; {@code OfflineException} и
 * {@code ApiException} обрабатываются так же, как ошибки push и pull.
 */
@FunctionalInterface
public interface SyncStep {

    void run();
}
