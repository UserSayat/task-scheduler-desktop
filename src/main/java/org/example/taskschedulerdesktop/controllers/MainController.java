package org.example.taskschedulerdesktop.controllers;

import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.models.Entity;
import org.example.taskschedulerdesktop.navigation.NavigationManager;
import org.example.taskschedulerdesktop.navigation.Routes;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MainController {

    private final AppConfig appConfig = AppConfig.getInstance();

    @FXML
    private StackPane contentArea;

    @FXML
    private VBox navigationMenu;
    @FXML
    private Button reviewButton;
    @FXML
    private Button tasksButton;
    @FXML
    private Button projectsButton;
    @FXML
    private Button teamButton;

    @FXML
    public void initialize() {

        NavigationManager.init(contentArea,
                appConfig.getControllerFactory());

        reviewButton.setOnAction(e -> NavigationManager.navigateTo(Routes.REVIEW));
        tasksButton.setOnAction(e -> NavigationManager.navigateTo(Routes.TASKS));
        projectsButton.setOnAction(e -> NavigationManager.navigateTo(Routes.PROJECTS));
        teamButton.setOnAction(e -> NavigationManager.navigateTo(Routes.TEAM));

        NavigationManager.navigateTo(Routes.REVIEW);
    }

}