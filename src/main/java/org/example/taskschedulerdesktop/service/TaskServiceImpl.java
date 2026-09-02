package org.example.taskschedulerdesktop.service;

import org.example.taskschedulerdesktop.models.Task;
import org.example.taskschedulerdesktop.repository.TaskRepository;

import java.util.List;

public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;

    public TaskServiceImpl(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public void save(Task task) {
        taskRepository.save(task);
    }

    @Override
    public List<Task> findAll() {
        return taskRepository.findAll();
    }
}
