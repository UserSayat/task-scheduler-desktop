package org.example.taskschedulerdesktop.controllers;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.Map;

public class MainController {
    @FXML
    private VBox navigationMenu;

    private Map<String, Runnable> navigationMenuActionMap = new HashMap<>();

    private GridPane contentArea;

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
                    case "Задачи":
                        button.setUserData("tasks");
                    case "Проекты":
                        button.setUserData("projects");
                    case "Команда":
                        button.setUserData("team");
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
    }




}