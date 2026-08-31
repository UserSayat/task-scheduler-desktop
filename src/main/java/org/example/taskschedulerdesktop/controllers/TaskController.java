package org.example.taskschedulerdesktop.controllers;

import org.example.taskschedulerdesktop.service.TaskService;

public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }
}
