package org.example.taskschedulerdesktop.database;

import org.example.taskschedulerdesktop.exeptions.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Единица работы: всё, что выполняется внутри {@link #inTransaction}, идёт через одно соединение
 * и фиксируется одной транзакцией. Репозитории берут соединение через {@link #connection()}.
 * <p>
 * Это гарантирует, что изменение сущности, запись в field_versions и запись в sync_log
 * либо сохраняются вместе, либо не сохраняются вовсе.
 * <p>
 * Вложенный вызов присоединяется к внешней транзакции.
 */
public class UnitOfWork {

    private static final Logger log = LoggerFactory.getLogger(UnitOfWork.class);

    private final ConnectionSource db;
    private final ThreadLocal<Connection> current = new ThreadLocal<>();

    public UnitOfWork(ConnectionSource db) {
        this.db = db;
    }

    @FunctionalInterface
    public interface Work<T> {
        T execute() throws SQLException;
    }

    @FunctionalInterface
    public interface VoidWork {
        void execute() throws SQLException;
    }

    public <T> T inTransaction(Work<T> work) {
        if (current.get() != null) {
            return run(work);
        }

        try (Connection connection = db.getConnection()) {
            connection.setAutoCommit(false);
            current.set(connection);
            try {
                T result = run(work);
                connection.commit();
                return result;
            } catch (RuntimeException e) {
                rollback(connection);
                throw e;
            } finally {
                current.remove();
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error", e);
        }
    }

    public void inTransaction(VoidWork work) {
        inTransaction(() -> {
            work.execute();
            return null;
        });
    }

    /**
     * Чтение. Пока это та же транзакция — отдельный метод нужен, чтобы в коде было видно намерение.
     */
    public <T> T read(Work<T> work) {
        return inTransaction(work);
    }

    /**
     * Соединение текущей единицы работы. Вне {@link #inTransaction} — ошибка программиста.
     */
    public Connection connection() {
        Connection connection = current.get();
        if (connection == null) {
            throw new IllegalStateException("Database access outside of a unit of work");
        }
        return connection;
    }

    private <T> T run(Work<T> work) {
        try {
            return work.execute();
        } catch (SQLException e) {
            throw new DataAccessException(e.getMessage(), e);
        }
    }

    private void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            log.error("Rollback failed", e);
        }
    }
}
