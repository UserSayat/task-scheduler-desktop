package org.example.taskschedulerdesktop.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import org.example.taskschedulerdesktop.models.Entity;
import org.example.taskschedulerdesktop.models.Task;
import org.example.taskschedulerdesktop.navigation.NavigationManager;

public class TaskRightSidebarController implements RightSidebarController{
    @FXML
    private Button closeButton;

    @FXML
    private Label projectNameLabel;

    @FXML
    private Label taskNameLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label priorityLabel;

    @FXML
    private FlowPane executorsFlowPane;

    @FXML
    private Label deadlineLabel;

    @FXML
    private FlowPane tagsFlowPane;

    @FXML
    private Label taskDescriptionLabel;

    @FXML
    private Button acceptTheTaskButton;

    @FXML
    private Button editButton;

    @Override
    public void setData(Entity object) {
        if (!(object instanceof Task)) {
            throw new IllegalArgumentException();
        }

        Task task = (Task) object;

        projectNameLabel.setText(task.getProjectName());
        taskNameLabel.setText(task.getTaskName());
        statusLabel.setText(task.getStatus());
        priorityLabel.setText(task.getPriority());
        executorsFlowPane = null;
        deadlineLabel.setText(task.getDeadline());
        tagsFlowPane = null;
        taskDescriptionLabel.setText(task.getDescription());
    }

    @FXML
    public void initialize() {
        closeButton.setOnAction(event -> {
            NavigationManager.closeRightSidebar();
        });
    }
}
