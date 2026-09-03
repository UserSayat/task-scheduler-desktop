package org.example.taskschedulerdesktop.service.task;

import org.example.taskschedulerdesktop.models.Task;

import java.util.List;

public interface TaskService {
    List<Task> findAll();
    List<Task> findByStatus(String status);
    Task findById(int id);
    void save(Task task);
    void update(Task task);
    void delete(int id);
}
