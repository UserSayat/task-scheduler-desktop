package org.example.taskschedulerdesktop.service.task;

import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.dto.TaskView;
import org.example.taskschedulerdesktop.exeptions.NotFoundException;
import org.example.taskschedulerdesktop.listeners.EventBus;
import org.example.taskschedulerdesktop.listeners.TaskChangedEvent;
import org.example.taskschedulerdesktop.models.Project;
import org.example.taskschedulerdesktop.models.Task;
import org.example.taskschedulerdesktop.repository.task.TaskRepository;
import org.example.taskschedulerdesktop.service.project.ProjectService;
import org.example.taskschedulerdesktop.utils.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class TaskServiceImpl implements TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskServiceImpl.class);
    private final TaskRepository repository;

    public TaskServiceImpl(TaskRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<TaskView> findAllViews() {
        return repository.findAllViews();
    }

    @Override
    public List<TaskView> findViewByStatus(TaskStatus status) {
        return repository.findViewsByStatus(status);
    }

    @Override
    public TaskView findViewById(long id) {
        return repository.findViewById(id)
                .orElseThrow(() -> new NotFoundException("Task not found"));
    }

    @Override
    public void save(Task task) {
        log.debug("save({})", task);

        if (task.getTaskName() == null || task.getTaskName().isEmpty()) {
            log.warn("The name of the task should not be empty");
        }

        if (task.getStatus() == null || task.getStatus().getDisplayName().isEmpty()) {
            task.setStatus(TaskStatus.NEW);
        }

        repository.save(task);

        log.debug("TaskChangedEvent: projectId={}, taskName={}",
                task.getProjectId(), task.getTaskName());
        EventBus.getInstance().fire(new TaskChangedEvent(task.getProjectId(), task.getTaskName()));

        log.debug("Event TaskChangedEvent sent: projectName = {}", task.getProjectId());
    }

    @Override
    public void update(Task task) {
        if (task.getId() <= 0) {
            throw new IllegalArgumentException("ID задачи не может быть пустым");
        }
        repository.update(task);
    }

    @Override
    public void delete(long id) {
        repository.delete(id);
    }

    @Override
    public int countTasksByProjectIdAndStatus(long projectId, TaskStatus status) {
        return repository.countByProjectIdAndStatus(projectId, status);
    }

    @Override
    public int countTasksByProjectId(long projectId) {
        return repository.countByProjectId(projectId);
    }

    @Override
    public String getProjectNameById(long id) {
        TaskView view = repository.findViewById(id)
                .orElseThrow(() -> new IllegalArgumentException(""));
        return view.getProjectName();
    }
}
