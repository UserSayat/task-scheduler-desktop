package org.example.taskschedulerdesktop.service.task;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import org.example.taskschedulerdesktop.controllers.tasks.TaskDescriptionCardController;
import org.example.taskschedulerdesktop.dto.TaskView;
import org.example.taskschedulerdesktop.navigation.NavigationManager;
import org.example.taskschedulerdesktop.navigation.Routes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TaskCardService {

    public Node createCardForProjectExtendedPage(TaskView task, int sequenceNumber) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(Routes.TASK_DESCRIPTION_CARD)
            );
            Node card = loader.load();
            TaskDescriptionCardController controller = loader.getController();

            controller.setTaskSequenceNumberLabel(sequenceNumber);
            controller.setTaskNameLabel(task.getTaskName());
            controller.setTaskTypeLabel(task.getType());
            controller.setTaskDeadlineLabel(task.getDeadline().toString());
            controller.setExecutorInitialsLabel(task.getExecutor());
            controller.setPriorityLabel(task.getPriority().getDisplayName());

            // Клик по карточке
            card.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY) {
                    NavigationManager.openRightSidebar(
                            "/org/example/taskschedulerdesktop/view/task-details.fxml",
                            task
                    );
                    event.consume();
                }
            });

            return card;

        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать карточку для задачи: " + task.getTaskName(), e);
        }
    }

    public List<Node> createCardsForProjectExtendedPage(List<TaskView> tasks) {
        List<Node> cards = new ArrayList<>();
        int sequenceNumber = 1;

        for (TaskView task : tasks) {
            Node card = createCardForProjectExtendedPage(task, sequenceNumber);
            cards.add(card);
            sequenceNumber++;
        }

        return cards;
    }

}
