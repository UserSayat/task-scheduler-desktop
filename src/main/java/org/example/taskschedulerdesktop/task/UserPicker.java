package org.example.taskschedulerdesktop.task;

import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuButton;
import org.example.taskschedulerdesktop.user.User;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Выбор нескольких сотрудников в выпадающем списке с галочками (соисполнители, наблюдатели).
 */
final class UserPicker {

    private final MenuButton button;
    private final String emptyText;

    UserPicker(MenuButton button, String emptyText) {
        this.button = button;
        this.emptyText = emptyText;
    }

    /** Показать сотрудников, отметив выбранных. Выбранные, которых нет среди активных, сохраняются. */
    void show(Collection<User> users, Set<UUID> selected, Map<UUID, String> names) {
        button.getItems().clear();
        for (User user : users) {
            button.getItems().add(item(user.getId(), user.getName(), selected.contains(user.getId())));
        }
        for (UUID id : selected) {
            if (users.stream().noneMatch(user -> user.getId().equals(id))) {
                button.getItems().add(item(id, names.getOrDefault(id, "Неактивный сотрудник"), true));
            }
        }
        updateText();
    }

    List<UUID> selected() {
        return button.getItems().stream()
                .map(CheckMenuItem.class::cast)
                .filter(CheckMenuItem::isSelected)
                .map(item -> (UUID) item.getUserData())
                .toList();
    }

    private CheckMenuItem item(UUID id, String name, boolean selected) {
        CheckMenuItem item = new CheckMenuItem(name);
        item.setUserData(id);
        item.setSelected(selected);
        item.selectedProperty().addListener((obs, was, now) -> updateText());
        return item;
    }

    private void updateText() {
        List<String> names = button.getItems().stream()
                .map(CheckMenuItem.class::cast)
                .filter(CheckMenuItem::isSelected)
                .map(CheckMenuItem::getText)
                .toList();
        button.setText(names.isEmpty() ? emptyText : String.join(", ", names));
    }
}
