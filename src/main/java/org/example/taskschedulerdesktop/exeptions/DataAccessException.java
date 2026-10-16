package org.example.taskschedulerdesktop.exeptions;

/**
 * Ошибка работы с локальной БД. Оборачивает SQLException, чтобы интерфейсы репозиториев
 * и сервисов не зависели от JDBC.
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
