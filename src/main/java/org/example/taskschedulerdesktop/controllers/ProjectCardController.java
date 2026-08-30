package org.example.taskschedulerdesktop.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.example.taskschedulerdesktop.models.ProjectCard;

import java.io.IOException;

public class ProjectCardController {

    @FXML
    private VBox rootVBox;

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

    private MainController mainController;

    public MainController getMainController() {
        return mainController;
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    @FXML
    public void initialize() {
        rootVBox.setOnMouseClicked(event -> {
            if (mainController != null) {
                try {
                    FXMLLoader loader = new FXMLLoader(
                            getClass().getResource("/org/example/taskschedulerdesktop/view/project_extended_page.fxml")
                    );

                    Parent projectExtendedPage = loader.load();
                    ProjectExtendedPageController projectExtendedPageController = loader.getController();
                    projectExtendedPageController.setMainController(mainController);

                    mainController.changeContentArea(projectExtendedPage);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

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
