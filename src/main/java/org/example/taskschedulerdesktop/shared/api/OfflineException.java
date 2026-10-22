package org.example.taskschedulerdesktop.shared.api;

/**
 * Сервер недоступен: нет сети, сервер выключен или не ответил вовремя.
 * Для offline-first клиента это штатная ситуация — данные остаются в локальной базе.
 */
public class OfflineException extends RuntimeException {

    public OfflineException(String message, Throwable cause) {
        super(message, cause);
    }
}
