package org.example.taskschedulerdesktop.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Сотрудник на экране администрирования.
 *
 * @param status ACTIVE или BLOCKED (удалённые на экран не попадают)
 * @param roles  все роли, в том числе в отдельных проектах
 */
public record Employee(
        UUID id,
        String name,
        String email,
        String position,
        String timezone,
        String status,
        List<RoleAssignment> roles
) {

    public static final String ACTIVE = "ACTIVE";
    public static final String BLOCKED = "BLOCKED";

    public boolean isBlocked() {
        return BLOCKED.equals(status);
    }

    public boolean hasGlobalRole(String roleCode) {
        return roles.stream().anyMatch(role -> role.isGlobal() && role.roleCode().equals(roleCode));
    }

    public String statusText() {
        return isBlocked() ? "Заблокирован" : "Активен";
    }

    /** Глобальные роли для таблицы: «Администратор, Руководитель проектов». */
    public String rolesText() {
        List<String> names = new ArrayList<>();
        if (hasGlobalRole(RoleAssignment.ADMIN)) {
            names.add("Администратор");
        }
        if (hasGlobalRole(RoleAssignment.PROJECT_MANAGER)) {
            names.add("Руководитель проектов");
        }
        return names.isEmpty() ? "Сотрудник" : String.join(", ", names);
    }
}
