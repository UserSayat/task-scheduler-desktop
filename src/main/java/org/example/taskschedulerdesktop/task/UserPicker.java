package org.example.taskschedulerdesktop.task;

import org.example.taskschedulerdesktop.shared.ui.MultiSelectField;
import org.example.taskschedulerdesktop.user.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Выбор нескольких сотрудников (соисполнители, наблюдатели): поле с курсором и список с галочками под ним.
 */
final class UserPicker {

    private final MultiSelectField<UUID> field;

    UserPicker(MultiSelectField<UUID> field, String emptyText) {
        this.field = field;
        field.setPromptText(emptyText);
    }

    /** Показать сотрудников, отметив выбранных. Выбранные, которых нет среди активных, сохраняются. */
    void show(Collection<User> users, Set<UUID> selected, Map<UUID, String> names) {
        Map<UUID, String> options = new LinkedHashMap<>();
        for (User user : users) {
            options.put(user.getId(), user.getName());
        }
        for (UUID id : selected) {
            options.putIfAbsent(id, names.getOrDefault(id, "Неактивный сотрудник"));
        }
        field.setOptions(options);
        field.setSelected(selected);
    }

    List<UUID> selected() {
        return new ArrayList<>(field.selected());
    }
}
