package org.example.taskschedulerdesktop.service;

import org.example.taskschedulerdesktop.models.Task;

import java.util.List;

public interface TaskService {
    void save(Task task);
    List<Task> findAll();
}
