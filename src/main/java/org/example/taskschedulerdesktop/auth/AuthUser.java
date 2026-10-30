package org.example.taskschedulerdesktop.auth;

import java.util.List;
import java.util.UUID;

/**
 * Вошедший пользователь — часть UserDto (API.md, 6.1), нужная клиенту при входе.
 * Полный профиль приходит вместе с остальными данными через snapshot.
 */
public record AuthUser(UUID id, String name, String email, String position, List<Role> roles,
                       boolean mustChangePassword) {

    public record Role(String roleCode, String scopeType, UUID scopeId) {}
}
