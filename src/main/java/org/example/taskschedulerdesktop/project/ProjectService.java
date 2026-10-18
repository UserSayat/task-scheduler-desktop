package org.example.taskschedulerdesktop.project;


import java.util.List;
import java.util.UUID;

public interface ProjectService {

    List<ProjectView> findAll();

    ProjectView findById(UUID id);

    ProjectView create(String name, UUID ownerId);

    ProjectView update(UUID id, String name, UUID ownerId);

    void delete(UUID id);
}
