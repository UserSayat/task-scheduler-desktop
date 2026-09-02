package org.example.taskschedulerdesktop.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.navigation.NavigationManager;
import org.example.taskschedulerdesktop.navigation.Routes;

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
    private Button backButton;

    @FXML
    private Label pageTitleLabel;

    @FXML
    public void initialize() {

        NavigationManager.init(contentArea,
                appConfig.getControllerFactory());

        NavigationManager.addListener(() -> {
            pageTitleLabel.setText(NavigationManager.getCurrentTitle());
            updateBackButtonState();
        });

        backButton.setOnAction(event -> NavigationManager.goBack());
        updateBackButtonState(); // начальное состояние

        reviewButton.setOnAction(e -> NavigationManager.navigateTo(Routes.REVIEW));
        tasksButton.setOnAction(e -> NavigationManager.navigateTo(Routes.TASKS));
        projectsButton.setOnAction(e -> NavigationManager.navigateTo(Routes.PROJECTS));
        teamButton.setOnAction(e -> NavigationManager.navigateTo(Routes.TEAM));

        NavigationManager.navigateTo(Routes.REVIEW);
    }

    public void updateBackButtonState() {
        boolean canGoBack = NavigationManager.canGoBack();
        backButton.setVisible(canGoBack);
        backButton.setManaged(canGoBack);
    }
}