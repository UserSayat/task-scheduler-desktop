package org.example.taskschedulerdesktop.controllers.tasks;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TasksController {

    private static final Logger log = LoggerFactory.getLogger(TasksController.class);

    @FXML private VBox taskTableContainerVBox;


    @FXML
    public void initialize() {
        loadTaskTable();
    }

    private void loadTaskTable() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/example/taskschedulerdesktop/view/task_table.fxml")
            );

            loader.setControllerFactory(AppConfig.getInstance().getControllerFactory());

            Node taskTable = loader.load();
            taskTableContainerVBox.getChildren().add(taskTable);

            log.debug("Task table loaded successfully");
        } catch (IOException e) {
            log.error("Error loading task table", e);
        }
    }
}
