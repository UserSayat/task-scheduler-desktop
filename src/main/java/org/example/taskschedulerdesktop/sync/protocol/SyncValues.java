package org.example.taskschedulerdesktop.sync.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Значения полей протокола синхронизации: JSON ↔ Java (API.md, 8.2). Зеркало SyncValues на сервере:
 * UUID, даты, моменты времени и перечисления — строки, коллекции — массивы.
 */
public final class SyncValues {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private SyncValues() {}

    // ===== Java → JSON =====

    public static JsonNode node(Object value) {
        if (value == null) {
            return JSON.nullNode();
        }
        return JSON.textNode(value instanceof Enum<?> e ? e.name() : value.toString());
    }

    public static JsonNode uuidArray(Collection<UUID> ids) {
        ArrayNode array = JSON.arrayNode();
        ids.forEach(id -> array.add(id.toString()));
        return array;
    }

    /** {@code [{"userId": "...", "role": "..."}]} — исполнители задачи и участники проекта. */
    public static JsonNode usersWithRoles(Collection<UserWithRole> items) {
        ArrayNode array = JSON.arrayNode();
        for (UserWithRole item : items) {
            ObjectNode node = JSON.objectNode();
            node.put("userId", item.userId().toString());
            node.put("role", item.role());
            array.add(node);
        }
        return array;
    }

    // ===== JSON → Java =====

    public static String text(JsonNode value) {
        return value == null || value.isNull() ? null : value.asText();
    }

    public static UUID uuid(JsonNode value) {
        return parse(value, UUID::fromString);
    }

    public static LocalDate date(JsonNode value) {
        return parse(value, LocalDate::parse);
    }

    public static Instant instant(JsonNode value) {
        return parse(value, Instant::parse);
    }

    public static <E extends Enum<E>> E enumValue(JsonNode value, Class<E> type) {
        return parse(value, s -> Enum.valueOf(type, s));
    }

    public static List<UUID> uuidList(JsonNode value) {
        List<UUID> result = new ArrayList<>();
        if (value != null && value.isArray()) {
            value.forEach(item -> result.add(UUID.fromString(item.asText())));
        }
        return result;
    }

    public static List<UserWithRole> usersWithRoles(JsonNode value) {
        List<UserWithRole> result = new ArrayList<>();
        if (value != null && value.isArray()) {
            for (JsonNode item : value) {
                result.add(new UserWithRole(UUID.fromString(item.path("userId").asText()), item.path("role").asText()));
            }
        }
        return result;
    }

    public record UserWithRole(UUID userId, String role) {}

    private static <T> T parse(JsonNode value, Function<String, T> parser) {
        String text = text(value);
        return text == null ? null : parser.apply(text);
    }
}
