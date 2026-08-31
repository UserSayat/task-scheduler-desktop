package org.example.taskschedulerdesktop.service;

import java.util.List;

public interface TaskService<T> {
    List<T> findAll();
}
