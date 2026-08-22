package org.example.taskschedulerdesktop.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.example.taskschedulerdesktop.models.ProjectCard;

public class ProjectCardController {

    @FXML
    private Label projectNameLabel;

    @FXML
    private Label projectSupervisorLabel;

    @FXML
    private Label percentOfCompletionLabel;

    @FXML
    private Label numberOfTasksLabel;

    @FXML
    private Label completedTasksLabel;

    @FXML
    private Label remainingTasksLabel;

    @FXML
    private Label firstTaskDescriptionLabel;

    @FXML
    private Label firstTaskDeadlineLabel;

    @FXML
    private Label secondTaskDescriptionLabel;

    @FXML
    private Label secondTaskDeadlineLabel;

    @FXML
    private Label thirdTaskDescriptionLabel;

    @FXML
    private Label thirdTaskDeadlineLabel;

    public void setProjectData(ProjectCard card) {
        projectNameLabel.setText(card.getProjectName());
        projectSupervisorLabel.setText(card.getProjectSupervisor());
        percentOfCompletionLabel.setText(String.valueOf(card.getPercentOfCompletion()));
        numberOfTasksLabel.setText(String.valueOf(card.getNumberOfTasks()));
        completedTasksLabel.setText(String.valueOf(card.getCompletedTasks()));
        remainingTasksLabel.setText(String.valueOf(card.getRemainingTasks()));

        if (card.getFirstTaskDescription() != null && !card.getFirstTaskDescription().isEmpty()) {
            firstTaskDescriptionLabel.setText(card.getFirstTaskDescription());
            firstTaskDeadlineLabel.setText(card.getFirstTaskDeadline());
        }

        if (card.getSecondTaskDescription() != null && !card.getSecondTaskDescription().isEmpty()) {
            secondTaskDescriptionLabel.setText(card.getSecondTaskDescription());
            secondTaskDeadlineLabel.setText(card.getSecondTaskDeadline());
        }

        if (card.getThirdTaskDescription() != null && !card.getThirdTaskDescription().isEmpty()) {
            thirdTaskDescriptionLabel.setText(card.getThirdTaskDescription());
            thirdTaskDeadlineLabel.setText(card.getThirdTaskDeadline());
        }
    }
}
