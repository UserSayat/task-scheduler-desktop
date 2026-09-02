package org.example.taskschedulerdesktop.controllers;

import javafx.fxml.FXML;
import org.example.taskschedulerdesktop.navigation.NavigationManager;

public class ReviewController {

    @FXML
    public void initialize() {
        NavigationManager.setCurrentTitle("Обзор");
    }
}
