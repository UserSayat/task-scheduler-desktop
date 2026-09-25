package org.example.taskschedulerdesktop.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.navigation.NavigationManager;
import org.example.taskschedulerdesktop.navigation.Routes;
import org.example.taskschedulerdesktop.utils.DateFormatter;

import java.time.LocalDate;

public class MainController {

    private final AppConfig appConfig = AppConfig.getInstance();

    @FXML private StackPane globalStackPane;
    @FXML private StackPane contentArea;

    @FXML private Button reviewButton;
    @FXML private Button tasksButton;
    @FXML private Button projectsButton;
    @FXML private Button teamButton;

    @FXML private Button backButton;

    @FXML private Label pageTitleLabel;
    @FXML private Label pageDate;

    @FXML private Button createTaskButton;

    private final Runnable titleUpdateListener = () -> {
        pageTitleLabel.setText(NavigationManager.getCurrentTitle());
        updateBackButtonState();
    };

    @FXML
    public void initialize() {

        NavigationManager.init(globalStackPane,
                contentArea,
                appConfig.getControllerFactory());

        NavigationManager.addListener(titleUpdateListener);

        backButton.setOnAction(event -> NavigationManager.goBack());
        updateBackButtonState(); // начальное состояние

        pageDate.setText(DateFormatter.formatFullWithDay(LocalDate.now()));

        createTaskButton.setOnAction(event -> NavigationManager.openDialog(
                Routes.CREATE_TASK,
                "Новая задача",
                AppConfig.getInstance().getPrimaryStage()));

        reviewButton.setOnAction(event -> NavigationManager.navigateTo(Routes.DASHBOARD, "Обзор"));
        tasksButton.setOnAction(event -> NavigationManager.navigateTo(Routes.TASKS, "Задачи"));
        projectsButton.setOnAction(event -> NavigationManager.navigateTo(Routes.PROJECTS, "Проекты"));
        teamButton.setOnAction(event -> NavigationManager.navigateTo(Routes.TEAM, "Команда"));

        NavigationManager.navigateTo(Routes.DASHBOARD);
    }

    public void updateBackButtonState() {
        boolean canGoBack = NavigationManager.canGoBack();
        backButton.setVisible(canGoBack);
        backButton.setManaged(canGoBack);
    }

    public void shutdown() {
        NavigationManager.removeListener(titleUpdateListener);
    }
}