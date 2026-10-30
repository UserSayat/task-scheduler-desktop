package org.example.taskschedulerdesktop.project;

/**
 * Роль сотрудника в проекте (API.md, 2.3). Владелец в проекте ровно один — это руководитель проекта.
 */
public enum MemberRole {
    OWNER("Руководитель"),
    MANAGER("Менеджер"),
    MEMBER("Участник"),
    OBSERVER("Наблюдатель");

    private final String displayName;

    MemberRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
