package org.example.taskschedulerdesktop.service.project;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import org.example.taskschedulerdesktop.controllers.projects.ProjectCardController;
import org.example.taskschedulerdesktop.models.Project;
import org.example.taskschedulerdesktop.models.ProjectCard;
import org.example.taskschedulerdesktop.navigation.NavigationManager;
import org.example.taskschedulerdesktop.navigation.Routes;
import org.example.taskschedulerdesktop.utils.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ProjectCardService {

    private static final Logger log = LoggerFactory.getLogger(ProjectCardService.class);

    private final ProjectService projectService;

    public ProjectCardService(ProjectService projectService) {
        this.projectService = projectService;
    }

    public Node createCard(Project project) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(Routes.PROJECT_CARD)
            );
            Node card = loader.load();
            ProjectCardController controller = loader.getController();

            controller.setProjectNameLabel(project.getName());
            controller.setProjectSupervisorLabel(project.getSupervisor());

            int numberOfTasks = projectService.countTasksByProjectName(project.getName());
            int completedTasks = projectService.countTasksByProjectNameAndStatus(project.getName(), TaskStatus.COMPLETED);
            int remainingTasks = numberOfTasks - completedTasks;
            int percentOfCompletion = (completedTasks / numberOfTasks) * 100;

            controller.setNumberOfTasksLabel(numberOfTasks);
            controller.setRemainingTasksLabel(remainingTasks);
            controller.setCompletedTasksLabel(completedTasks);
            controller.setPercentOfCompletionLabel(percentOfCompletion);

            card.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY) {
                    NavigationManager.navigateTo("/org/example/taskschedulerdesktop/view/project_extended_page.fxml");
                }
            });

            ProjectCard projectCard = new ProjectCard(project.getName(),
                    project.getSupervisor(),
                    percentOfCompletion,
                    numberOfTasks,
                    completedTasks,
                    remainingTasks);

            controller.setContext(projectCard);

            return card;

        } catch (IOException e) {
            log.warn("Failed to create a card for project: {}", project.getName());
            throw new RuntimeException("Failed to create a card for the project. ", e);
        }
    }

    public List<Node> createCards(List<Project> projects) {
        List<Node> cards = new ArrayList<>();

        for (Project project : projects) {
            Node card = createCard(project);
            cards.add(card);
        }

        return cards;
    }
}
