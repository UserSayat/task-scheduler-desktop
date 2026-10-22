package org.example.taskschedulerdesktop.sync;

import java.time.Instant;

/**
 * Состояние синхронизации для индикатора в шапке окна.
 *
 * @param pending      неотправленные записи журнала
 * @param errors       изменения, которые сервер отклонил
 * @param lastSyncedAt когда синхронизация последний раз прошла успешно
 * @param message      подробность для подсказки (текст ошибки)
 */
public record SyncStatus(State state, int pending, int errors, Instant lastSyncedAt, String message) {

    public enum State {
        /** Синхронизация ещё не запускалась. */
        IDLE,
        /** Есть локальные изменения, push скоро начнётся. */
        PENDING,
        SYNCING,
        SYNCED,
        /** Сервер недоступен — работаем с локальными данными. */
        OFFLINE,
        ERROR,
        /** Refresh-токен недействителен или пользователь заблокирован — нужен повторный вход. */
        SIGNED_OUT
    }

    public static SyncStatus idle() {
        return new SyncStatus(State.IDLE, 0, 0, null, null);
    }

    public SyncStatus with(State state, String message) {
        return new SyncStatus(state, pending, errors, lastSyncedAt, message);
    }
}
