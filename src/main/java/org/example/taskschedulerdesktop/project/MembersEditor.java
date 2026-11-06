package org.example.taskschedulerdesktop.project;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.shared.navigation.NavigationManager;
import org.example.taskschedulerdesktop.user.User;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Состав участников проекта в форме (создание и изменение проекта): список с ролями и удалением,
 * строка добавления: сотрудник, роль и «+». Руководитель в списке не показывается — он выбирается отдельно.
 */
final class MembersEditor {

    /** Роли, которые можно выдать участнику (руководитель задаётся отдельно). */
    static final List<MemberRole> ASSIGNABLE_ROLES = List.of(MemberRole.MANAGER, MemberRole.MEMBER, MemberRole.OBSERVER);

    private final VBox list;
    private final ComboBox<User> newMember;
    private final ComboBox<User> owner;

    /** Активные сотрудники по id — для имён в списке и выпадающих списков. */
    private Map<UUID, User> users = Map.of();
    /** Участники, кроме руководителя, в порядке показа. */
    private final Map<UUID, MemberRole> members = new LinkedHashMap<>();

    /**
     * @param keepFormerOwner при смене руководителя прежний остаётся в проекте менеджером
     *                        (при изменении проекта — да, при создании — нет: там руководителя просто выбирают)
     */
    MembersEditor(VBox list, ComboBox<User> newMember, ComboBox<MemberRole> newRole, Button add,
                  ComboBox<User> owner, boolean keepFormerOwner) {
        this.list = list;
        this.newMember = newMember;
        this.owner = owner;
        newRole.getItems().setAll(ASSIGNABLE_ROLES);
        newRole.setValue(MemberRole.MEMBER);
        // Сброшенное значение — снова подсказка «Добавить сотрудника» (сам ComboBox её после сброса не показывает)
        newMember.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                boolean none = empty || user == null;
                setText(none ? newMember.getPromptText() : user.getName());
                getStyleClass().remove("combo-prompt");
                if (none) {
                    getStyleClass().add("combo-prompt");
                }
            }
        });

        // Новый руководитель уходит из списка участников
        owner.valueProperty().addListener((obs, oldOwner, newOwner) -> {
            if (keepFormerOwner && oldOwner != null && !members.containsKey(oldOwner.getId())) {
                members.put(oldOwner.getId(), MemberRole.MANAGER);
            }
            if (newOwner != null) {
                members.remove(newOwner.getId());
            }
            show();
        });

        add.setOnAction(event -> {
            User user = newMember.getValue();
            if (user == null) {
                NavigationManager.showToast("Выберите сотрудника", "info");
                return;
            }
            members.put(user.getId(), newRole.getValue());
            show();
        });
        show();
    }

    void setUsers(Collection<User> active) {
        Map<UUID, User> byId = new LinkedHashMap<>();
        active.forEach(user -> byId.put(user.getId(), user));
        users = byId;
        show();
    }

    /** Участники проекта, кроме руководителя. */
    void setMembers(Map<UUID, MemberRole> current) {
        members.clear();
        members.putAll(current);
        show();
    }

    Map<UUID, MemberRole> members() {
        return new LinkedHashMap<>(members);
    }

    private void show() {
        list.getChildren().clear();
        if (members.isEmpty()) {
            Label empty = new Label("Кроме руководителя, участников нет");
            empty.getStyleClass().add("members-empty");
            list.getChildren().add(empty);
        }
        members.forEach((userId, role) -> list.getChildren().add(row(userId, role)));

        // В списке «добавить» — только те, кого ещё нет в проекте
        User current = owner.getValue();
        newMember.getItems().setAll(users.values().stream()
                .filter(user -> !members.containsKey(user.getId()))
                .filter(user -> current == null || !user.getId().equals(current.getId()))
                .toList());
        newMember.setValue(null);
    }

    private HBox row(UUID userId, MemberRole role) {
        User user = users.get(userId);
        // Заблокированного сотрудника нет среди активных, но из проекта он не пропадает
        // Колонки строки совпадают со строкой добавления: имя в рамке — под «Сотрудник», роль — под ролью,
        // «×» — под «+»
        Label name = new Label(user != null ? user.getName() : "Неактивный сотрудник");
        name.getStyleClass().add("member-name-box");
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);

        ComboBox<MemberRole> roleBox = new ComboBox<>();
        roleBox.getItems().setAll(ASSIGNABLE_ROLES);
        roleBox.setValue(role);
        roleBox.getStyleClass().addAll("combo-box-styled", "compact-combo");
        roleBox.setPrefWidth(136);
        roleBox.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        roleBox.valueProperty().addListener((obs, oldRole, newRole) -> members.put(userId, newRole));

        Button remove = new Button("×");
        remove.getStyleClass().addAll("btn-secondary", "add-member-button", "member-remove-button");
        remove.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        remove.setOnAction(event -> {
            members.remove(userId);
            show();
        });

        HBox row = new HBox(6, name, roleBox, remove);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
