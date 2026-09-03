package org.example.taskschedulerdesktop.service.task;

import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import org.example.taskschedulerdesktop.controllers.TaskDescriptionCardController;
import org.example.taskschedulerdesktop.models.Task;
import org.example.taskschedulerdesktop.navigation.NavigationManager;
import org.example.taskschedulerdesktop.navigation.Routes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TaskCardService {

    public HBox createCard(Task task, int sequenceNumber) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(Routes.TASK_DESCRIPTION_CARD_FXML_PATH)
            );

            HBox card = loader.load();
            TaskDescriptionCardController controller = loader.getController();

            // Заполняем карточку
            controller.setTaskSequenceNumberLabel(new Label(String.valueOf(sequenceNumber)));
            controller.setTaskNameLabel(new Label(task.getTaskName()));
            controller.setTaskTypeLabel(new Label(task.getStatus()));
            controller.setTaskDeadlineLabel(new Label(task.getDeadline()));
            controller.setExecutorInitialsLabel(new Label(task.getExecutor()));
            controller.setPriorityLabel(new Label(task.getPriority()));

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

    public List<HBox> createCards(List<Task> tasks) {
        List<HBox> cards = new ArrayList<>();
        int sequenceNumber = 1;

        for (Task task : tasks) {
            HBox card = createCard(task, sequenceNumber);
            cards.add(card);
            sequenceNumber++;
        }

        return cards;
    }
}
