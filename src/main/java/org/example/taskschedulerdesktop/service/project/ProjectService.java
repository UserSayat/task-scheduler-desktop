package org.example.taskschedulerdesktop.service.project;

import org.example.taskschedulerdesktop.models.Project;
import org.example.taskschedulerdesktop.utils.TaskStatus;

import java.util.List;

public interface ProjectService {

    List<Project> findAll();
    void save(Project project);
    void update(Project project);
    void delete(long id);
    Project findById(long id);
    int countTasksByProjectNameAndStatus(String projectName, TaskStatus status);
    int countTasksByProjectName(String projectName);
}
