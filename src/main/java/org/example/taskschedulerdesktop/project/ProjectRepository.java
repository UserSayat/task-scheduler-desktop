package org.example.taskschedulerdesktop.project;


import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Хранилище проектов. Методы вызываются только внутри единицы работы (UnitOfWork).
 * Удалённые проекты (deleted_at) в выборки для экранов не попадают.
 */
public interface ProjectRepository {

    void insert(Project project) throws SQLException;

    /**
     * Сохраняет строку проекта и полностью заменяет список участников.
     */
    void update(Project project) throws SQLException;

    Optional<Project> findById(UUID id) throws SQLException;

    List<ProjectView> findAllViews() throws SQLException;

    Optional<ProjectView> findViewById(UUID id) throws SQLException;

    /**
     * Участники проекта: сначала руководитель, потом по роли и имени.
     */
    List<ProjectMemberView> findMemberViews(UUID projectId) throws SQLException;
}
