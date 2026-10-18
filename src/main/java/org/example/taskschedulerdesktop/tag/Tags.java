package org.example.taskschedulerdesktop.tag;

import java.util.Arrays;
import java.util.List;

/**
 * Теги в формах вводятся одной строкой через запятую.
 */
public final class Tags {

    private Tags() {}

    public static List<String> parse(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }
}
