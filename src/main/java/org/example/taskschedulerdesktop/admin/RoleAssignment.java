package org.example.taskschedulerdesktop.admin;

import java.util.UUID;

/**
 * Роль сотрудника: глобальная ({@code scopeId = null}) или в конкретном проекте (API.md, 6.6).
 */
public record RoleAssignment(String roleCode, String scopeType, UUID scopeId) {

    public static final String ADMIN = "ADMIN";
    public static final String PROJECT_MANAGER = "PROJECT_MANAGER";
    public static final String GLOBAL = "GLOBAL";

    public static RoleAssignment global(String roleCode) {
        return new RoleAssignment(roleCode, GLOBAL, null);
    }

    public boolean isGlobal() {
        return GLOBAL.equals(scopeType);
    }
}
