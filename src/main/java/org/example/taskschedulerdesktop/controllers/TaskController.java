package org.example.taskschedulerdesktop.controllers;

import javafx.fxml.FXML;
import org.example.taskschedulerdesktop.navigation.NavigationManager;
import org.example.taskschedulerdesktop.service.task.TaskService;

public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @FXML
    public void initialize() {
        NavigationManager.setCurrentTitle("Задачи");
    }
}
