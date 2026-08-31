package org.example.taskschedulerdesktop.repository;

import org.example.taskschedulerdesktop.models.Task;
import java.util.List;

public interface TaskRepository {

    List<Task> findAll();
    void save(Task task);
    void update(Task task);
    void delete(long id);
    Task findById(long id);
    List<Task> findUnsynced();
    void markAsSynced(long id);
}