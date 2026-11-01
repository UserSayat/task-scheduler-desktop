package org.example.taskschedulerdesktop.access;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Кого можно назначить на задачи проекта (API.md, 8.7): исполнитель и соисполнители — участники
 * с ролью «Владелец», «Менеджер» или «Участник»; наблюдатель — любой участник и администратор.
 *
 * @param assignable исполнители и соисполнители
 * @param observers  наблюдатели
 */
public record ProjectTeam(Set<UUID> assignable, Set<UUID> observers) {

    public static final ProjectTeam EMPTY = new ProjectTeam(Set.of(), Set.of());

    private static final Set<String> ASSIGNABLE_ROLES = Set.of("OWNER", "MANAGER", "MEMBER");

    /**
     * @param roles  участники проекта: сотрудник → роль в проекте
     * @param admins администраторы
     */
    public static ProjectTeam of(Map<UUID, String> roles, Set<UUID> admins) {
        Set<UUID> assignable = roles.entrySet().stream()
                .filter(entry -> ASSIGNABLE_ROLES.contains(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
        Set<UUID> observers = new HashSet<>(roles.keySet());
        observers.addAll(admins);
        return new ProjectTeam(assignable, Set.copyOf(observers));
    }
}
