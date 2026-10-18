package org.example.taskschedulerdesktop.shared.exception;

/**
 * Ошибка работы с локальной БД. Оборачивает SQLException, чтобы интерфейсы репозиториев
 * и сервисов не зависели от JDBC.
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
