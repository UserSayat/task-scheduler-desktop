package org.example.taskschedulerdesktop.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;

public class TaskRightSidebarController {
    @FXML
    private Button closeButton;

    private MainController mainController;

    public MainController getMainController() {
        return mainController;
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    @FXML
    public void initialize() {
        closeButton.setOnAction(event -> {
            if (mainController != null) {
                mainController.closeRightSidebar();
            }
        });
    }
}
