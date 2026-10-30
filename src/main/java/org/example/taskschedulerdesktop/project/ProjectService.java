package org.example.taskschedulerdesktop.project;


import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ProjectService {

    List<ProjectView> findAll();

    ProjectView findById(UUID id);

    ProjectView create(String name, UUID ownerId);

    /**
     * Название и руководитель. Остальные участники остаются как были, прежний руководитель становится менеджером.
     */
    ProjectView update(UUID id, String name, UUID ownerId);

    /**
     * Название, руководитель и полный состав участников.
     *
     * @param members участники, кроме руководителя, и их роли (OWNER здесь не допускается)
     */
    ProjectView update(UUID id, String name, UUID ownerId, Map<UUID, MemberRole> members);

    List<ProjectMemberView> findMembers(UUID projectId);

    void delete(UUID id);
}
