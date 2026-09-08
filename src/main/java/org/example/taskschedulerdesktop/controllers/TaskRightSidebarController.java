package org.example.taskschedulerdesktop.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import org.example.taskschedulerdesktop.models.Task;
import org.example.taskschedulerdesktop.navigation.NavigationManager;
import org.example.taskschedulerdesktop.navigation.Routes;
import org.example.taskschedulerdesktop.service.task.AsyncTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskRightSidebarController implements RightSidebarController {

    private static final Logger log = LoggerFactory.getLogger(TaskRightSidebarController.class);
    private final AsyncTaskService taskService;

    @FXML private Button closeButton;
    @FXML private Label projectNameLabel;
    @FXML private Label taskNameLabel;
    @FXML private Label statusLabel;
    @FXML private Label priorityLabel;
    @FXML private FlowPane executorsFlowPane;
    @FXML private Label deadlineLabel;
    @FXML private FlowPane tagsFlowPane;
    @FXML private Label taskDescriptionLabel;
    @FXML private Button acceptTheTaskButton;
    @FXML private Button editButton;

    private Task contextTask;

    public TaskRightSidebarController(AsyncTaskService taskService) {
        this.taskService = taskService;
    }

    @FXML
    public void initialize() {
        log.info("initialize()");

        closeButton.setOnAction(event -> {
            log.debug("Close right sidebar");
            NavigationManager.closeRightSidebar();
        });

        acceptTheTaskButton.setOnAction(event -> {
            log.debug("Click on button accept");

            //taskService.assignExecutor();


        });

        editButton.setOnAction(event -> {
            log.debug("Click on button edit");

            if (contextTask == null) {
                log.warn("contextTask = null");
                return;
            }
            log.info("Open edit sidebar for task: {}", contextTask.getTaskName());
            NavigationManager.openRightSidebar(Routes.EDIT_TASK_RIGHT_SIDEBAR, contextTask);
        });
    }

    @Override
    public void setContext(Object context) {
        log.info("setContext()");
        log.debug("context = {}", context);

        if (!(context instanceof Task)) {
            log.error("Expected Task, received: {}", context.getClass().getSimpleName());
            throw new IllegalArgumentException();
        }

        this.contextTask = (Task) context;
        log.info("contextTask saved: {}", contextTask.getTaskName());

        updateUI();
    }

    @Override
    public void updateUI() {
        log.debug("updateUI()");

        if (contextTask == null) {
            log.warn("contextTask = null, skip");
            return;
        }

        log.debug("projectName: {}", contextTask.getProjectName());
        log.debug("taskName: {}", contextTask.getTaskName());
        log.debug("status: {}", contextTask.getStatus());
        projectNameLabel.setText(contextTask.getProjectName());
        taskNameLabel.setText(contextTask.getTaskName());
        statusLabel.setText(contextTask.getStatus().getDisplayName());
        priorityLabel.setText(contextTask.getPriority().getDisplayName());
        //executorsFlowPane = null;
        deadlineLabel.setText(contextTask.getDeadline().toString());
        //tagsFlowPane = null;
        taskDescriptionLabel.setText(contextTask.getDescription());

        log.info("UI updated");
    }
}
