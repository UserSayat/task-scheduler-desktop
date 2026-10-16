package org.example.taskschedulerdesktop.utils;

/**
 * Инициалы для аватара (аватары в MVP не загружаются — ТЗ, ред. 1.1).
 */
public final class Initials {

    private static final String UNKNOWN = "—";

    private Initials() {}

    /**
     * «Мария Волкова» → «МВ». Для пустого имени (например, у задачи нет ответственного) — «—».
     */
    public static String of(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return UNKNOWN;
        }

        String[] parts = fullName.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();
        for (int i = 0; i < Math.min(parts.length, 2); i++) {
            initials.append(parts[i].charAt(0));
        }
        return initials.toString().toUpperCase();
    }
}
