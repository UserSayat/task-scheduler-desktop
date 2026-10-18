package org.example.taskschedulerdesktop.user;


import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Сотрудники. Создаёт и меняет их сервер (API.md, раздел 6), клиент только читает.
 */
public interface UserRepository {

    List<User> findAllActive() throws SQLException;

    Optional<User> findById(UUID id) throws SQLException;
}
