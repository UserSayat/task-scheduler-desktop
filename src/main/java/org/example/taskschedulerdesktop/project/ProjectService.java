package org.example.taskschedulerdesktop.project;


import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ProjectService {

    List<ProjectView> findAll();

    ProjectView findById(UUID id);

    /** Закрытый проект (видимость по умолчанию в модели). */
    ProjectView create(String name, UUID ownerId);

    ProjectView create(String name, UUID ownerId, Visibility visibility);

    /**
     * Проект сразу с описанием и участниками (кроме руководителя). Создатель, если он не руководитель
     * и не выбрал себе роль сам, становится менеджером — чтобы не потерять доступ к своему проекту.
     */
    ProjectView create(String name, UUID ownerId, Visibility visibility, String description,
                       Map<UUID, MemberRole> members);

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

    /**
     * То же, плюс описание, видимость и даты.
     *
     * @param info {@code null} — эти поля не меняются
     */
    ProjectView update(UUID id, String name, UUID ownerId, Map<UUID, MemberRole> members, ProjectInfo info);

    ProjectInfo findInfo(UUID id);

    /** В архив или обратно. Архивный проект виден, но в нём не создают задачи. */
    ProjectView setArchived(UUID id, boolean archived);

    List<ProjectMemberView> findMembers(UUID projectId);

    void delete(UUID id);
}
