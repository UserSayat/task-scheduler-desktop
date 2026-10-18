package org.example.taskschedulerdesktop.shared.database;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Откуда {@link UnitOfWork} берёт соединения. В приложении — пул {@link DatabaseConnection},
 * в тестах — отдельная временная база.
 */
@FunctionalInterface
public interface ConnectionSource {

    Connection getConnection() throws SQLException;
}
