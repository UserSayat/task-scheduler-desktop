package org.example.taskschedulerdesktop.tag;

import java.util.List;
import java.util.UUID;

/**
 * Глобальный тег.
 */
public record Tag(UUID id, String name, String color) {

    public static final String DEFAULT_COLOR = "#CCCCCC";

    public static final class Fields {
        public static final String NAME = "name";
        public static final String COLOR = "color";
        public static final String DELETED_AT = "deletedAt";

        public static final List<String> ON_CREATE = List.of(NAME, COLOR);

        private Fields() {}
    }
}
