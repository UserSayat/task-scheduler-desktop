package org.example.taskschedulerdesktop.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.example.taskschedulerdesktop.models.EmployeeCard;

public class EmployeeCardController {

    @FXML
    private Label employeeNameLabel;

    @FXML
    private Label employeePositionLabel;

    @FXML
    private Label numberOfTasksLabel;

    @FXML
    private Label completedTasksLabel;

    @FXML
    private Label firstTaskDescriptionLabel;

    @FXML
    private Label secondTaskDescriptionLabel;

    public void setEmployeeData(EmployeeCard card) {
        employeeNameLabel.setText(card.getName());
        employeePositionLabel.setText(card.getPosition());
        numberOfTasksLabel.setText(String.valueOf(card.getNumberOfTasks()));
        completedTasksLabel.setText(String.valueOf(card.getCompletedTasks()));

        if (card.getFirstTaskDescription() != null && !card.getFirstTaskDescription().isEmpty())
            firstTaskDescriptionLabel.setText(card.getFirstTaskDescription());

        if (card.getSecondTaskDescription() != null && !card.getSecondTaskDescription().isEmpty())
            secondTaskDescriptionLabel.setText(card.getSecondTaskDescription());
    }

}
