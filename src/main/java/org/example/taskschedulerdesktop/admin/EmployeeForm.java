package org.example.taskschedulerdesktop.admin;

/**
 * Данные из диалога «Сотрудник»: поля профиля и глобальные роли.
 */
public record EmployeeForm(
        String name,
        String email,
        String position,
        String timezone,
        boolean admin,
        boolean projectManager
) {
}
