package org.example.taskschedulerdesktop.sync.pull;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Что изменилось в локальной базе после применения данных с сервера: по этому экраны решают,
 * что перерисовать, а синхронизация — какие проекты догрузить через snapshot.
 */
public final class AppliedChanges {

    /** Проекты, у которых изменились поля или участники. */
    private final Set<UUID> projects = new LinkedHashSet<>();
    /** Проекты, в которых изменились задачи. */
    private final Set<UUID> taskProjects = new LinkedHashSet<>();
    /** Проекты, к которым пропал доступ (REVOKE): удалены локально. */
    private final Set<UUID> revokedProjects = new LinkedHashSet<>();
    /** Проекты, которых раньше не было локально: их старые задачи нужно догрузить. */
    private final Set<UUID> newProjects = new LinkedHashSet<>();
    /** Проекты, на которые сослались задачи, а локально их нет. */
    private final Set<UUID> missingProjects = new LinkedHashSet<>();
    private boolean usersChanged;
    private boolean tagsChanged;

    public void projectChanged(UUID id, boolean isNew) {
        projects.add(id);
        if (isNew) {
            newProjects.add(id);
        }
    }

    public void taskChanged(UUID projectId) {
        if (projectId != null) {
            taskProjects.add(projectId);
        }
    }

    public void projectRevoked(UUID id) {
        revokedProjects.add(id);
    }

    public void projectMissing(UUID id) {
        missingProjects.add(id);
    }

    public void userChanged() {
        usersChanged = true;
    }

    public void tagChanged() {
        tagsChanged = true;
    }

    public void addAll(AppliedChanges other) {
        projects.addAll(other.projects);
        taskProjects.addAll(other.taskProjects);
        revokedProjects.addAll(other.revokedProjects);
        newProjects.addAll(other.newProjects);
        missingProjects.addAll(other.missingProjects);
        usersChanged |= other.usersChanged;
        tagsChanged |= other.tagsChanged;
    }

    public boolean isEmpty() {
        return projects.isEmpty() && taskProjects.isEmpty() && revokedProjects.isEmpty()
                && !usersChanged && !tagsChanged;
    }

    public Set<UUID> projects() { return projects; }
    public Set<UUID> taskProjects() { return taskProjects; }
    public Set<UUID> revokedProjects() { return revokedProjects; }
    public Set<UUID> newProjects() { return newProjects; }
    public Set<UUID> missingProjects() { return missingProjects; }
    public boolean usersChanged() { return usersChanged; }
    public boolean tagsChanged() { return tagsChanged; }
}
