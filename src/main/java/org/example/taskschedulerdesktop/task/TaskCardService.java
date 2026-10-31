package org.example.taskschedulerdesktop.task;

import org.example.taskschedulerdesktop.shared.ui.Clicks;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import org.example.taskschedulerdesktop.shared.navigation.NavigationManager;
import org.example.taskschedulerdesktop.shared.navigation.Routes;

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

            controller.setContext(task);

            // Клик по карточке
            Clicks.onClick(card, () -> NavigationManager.openRightSidebar(Routes.TASK_DETAILS, task));

            return card;

        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать карточку для задачи: " + task.title(), e);
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
