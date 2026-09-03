package org.example.taskschedulerdesktop.service.task;

import org.example.taskschedulerdesktop.models.Task;
import org.example.taskschedulerdesktop.repository.TaskRepository;

import java.util.List;

public class TaskServiceImpl implements TaskService {

    private final TaskRepository repository;

    public TaskServiceImpl(TaskRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Task> findAll() {
        return repository.findAll();
    }

    @Override
    public List<Task> findByStatus(String status) {
        return repository.findByStatus(status);
    }

    @Override
    public Task findById(int id) {
        return repository.findById(id);
    }

    @Override
    public void save(Task task) {
        // Бизнес-логика
        if (task.getTaskName() == null || task.getTaskName().isEmpty()) {
            throw new IllegalArgumentException("Название задачи не может быть пустым");
        }

        if (task.getStatus() == null || task.getStatus().isEmpty()) {
            task.setStatus("Новая");
        }

        repository.save(task);
    }

    @Override
    public void update(Task task) {
        if (task.getId() <= 0) {
            throw new IllegalArgumentException("ID задачи не может быть пустым");
        }
        repository.update(task);
    }

    @Override
    public void delete(int id) {
        repository.delete(id);
    }
}
