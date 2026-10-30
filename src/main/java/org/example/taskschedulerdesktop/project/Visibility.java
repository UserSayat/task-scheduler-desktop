package org.example.taskschedulerdesktop.project;

/**
 * Кто видит проект (API.md, 8.7): закрытый — только участники и администратор, открытый — все сотрудники.
 */
public enum Visibility {
    PRIVATE("Закрытый — только участники"),
    PUBLIC("Открытый — все сотрудники");

    private final String displayName;

    Visibility(String displayName) {
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
