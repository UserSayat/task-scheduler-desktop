package org.example.taskschedulerdesktop.admin;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Сотрудники и их роли из локальной базы.
 */
public interface EmployeeRepository {

    /** Активные и заблокированные сотрудники по имени. */
    List<Employee> findAll() throws SQLException;

    boolean hasGlobalRole(UUID userId, String roleCode) throws SQLException;
}
