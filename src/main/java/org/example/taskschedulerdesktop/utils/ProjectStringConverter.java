package org.example.taskschedulerdesktop.utils;

import javafx.util.StringConverter;
import org.example.taskschedulerdesktop.models.Project;

public class ProjectStringConverter extends StringConverter<Project> {

    @Override
    public String toString(Project project) {
        return project != null ? project.getName() : "";
    }

    @Override
    public Project fromString(String s) {
        return null;
    }
}
