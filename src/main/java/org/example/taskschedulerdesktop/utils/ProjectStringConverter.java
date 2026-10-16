package org.example.taskschedulerdesktop.utils;

import javafx.util.StringConverter;
import org.example.taskschedulerdesktop.dto.projects.ProjectView;

public class ProjectStringConverter extends StringConverter<ProjectView> {

    @Override
    public String toString(ProjectView project) {
        return project != null ? project.name() : "";
    }

    @Override
    public ProjectView fromString(String s) {
        return null;
    }
}
