package org.example.taskschedulerdesktop.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MainController {
    @FXML
    private VBox navigationMenu;

    private Map<String, Runnable> navigationMenuActionMap = new HashMap<>();

    @FXML
    private StackPane contentArea;

    @FXML
    public void initialize() {
        navigationMenuActionMap.put("review", this::showReview);
        navigationMenuActionMap.put("tasks", this::showTasks);
        navigationMenuActionMap.put("projects", this::showProjects);
        navigationMenuActionMap.put("team", this::showTeam);

        for (Node node : navigationMenu.getChildren()) {
            if (node instanceof Button) {
                Button button = (Button) node;

                String text = button.getText();
                switch (text) {
                    case "Обзор":
                        button.setUserData("review");
                        break;
                    case "Задачи":
                        button.setUserData("tasks");
                        break;
                    case "Проекты":
                        button.setUserData("projects");
                        break;
                    case "Команда":
                        button.setUserData("team");
                        break;
                    default:
                        button.setUserData("");
                }

                button.setOnAction(event -> {
                    String command = (String) button.getUserData();
                    Runnable action = navigationMenuActionMap.get(command);

                    try {
                        action.run();
                    } catch (Exception e) {
                        System.out.println("Команда не установлена: " + command);
                    }
                });
            }
        }
        showReview();
    }

    public void changeContentArea(Parent newContent) {
        contentArea.getChildren().clear();
        contentArea.getChildren().add(newContent);
    }

    @FXML
    private void showReview() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/example/taskschedulerdesktop/view/review.fxml")
            );

            Parent review = loader.load();
            ReviewController reviewController = loader.getController();
            reviewController.setMainController(this);

            changeContentArea(review);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void showTasks() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/example/taskschedulerdesktop/view/tasks.fxml")
            );

            Parent tasks = loader.load();
            TasksController tasksController = loader.getController();
            tasksController.setMainController(this);

            changeContentArea(tasks);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void showProjects() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/org/example/taskschedulerdesktop/view/projects.fxml")
            );

            Parent projects = loader.load();
            ProjectsController projectsController = loader.getController();
            projectsController.setMainController(this);

            changeContentArea(projects);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void showTeam() {
        try {
            FXMLLoader loader  = new FXMLLoader(
                    getClass().getResource("/org/example/taskschedulerdesktop/view/team.fxml")
            );

            Parent team = loader.load();
            TeamController teamController = loader.getController();
            teamController.setMainController(this);

            changeContentArea(team);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}