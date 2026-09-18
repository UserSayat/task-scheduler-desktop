package org.example.taskschedulerdesktop.repository.project;

import org.example.taskschedulerdesktop.models.Project;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository {

    List<Project> findAll();
    void save(Project project);
    void update(Project project);
    void delete(long id);
    Optional<Project> findById(long id);
    List<Project> findUnsynced();
    void markAsSynced(long id);
}
