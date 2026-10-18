package org.example.taskschedulerdesktop.project;

import javafx.util.StringConverter;

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
