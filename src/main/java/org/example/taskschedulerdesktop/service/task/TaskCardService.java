package org.example.taskschedulerdesktop.service.task;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import org.example.taskschedulerdesktop.controllers.tasks.TaskDescriptionCardController;
import org.example.taskschedulerdesktop.models.Task;
import org.example.taskschedulerdesktop.navigation.NavigationManager;
import org.example.taskschedulerdesktop.navigation.Routes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TaskCardService {

    public Node createCard(Task task, int sequenceNumber) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource( Routes.TASK_DESCRIPTION_CARD_FXML_PATH)
            );
            Node card = loader.load();
            TaskDescriptionCardController controller = loader.getController();

            // Заполняем карточку
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
                            "/org/example/taskschedulerdesktop/view/task_right_sidebar.fxml",
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

    public List<Node> createCards(List<Task> tasks) {
        List<Node> cards = new ArrayList<>();
        int sequenceNumber = 1;

        for (Task task : tasks) {
            Node card = createCard(task, sequenceNumber);
            cards.add(card);
            sequenceNumber++;
        }

        return cards;
    }
}
