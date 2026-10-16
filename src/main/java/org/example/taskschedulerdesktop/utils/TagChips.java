package org.example.taskschedulerdesktop.utils;

import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;

import java.util.List;

/**
 * Отрисовка тегов задачи «чипсами». Пустой список скрывает панель.
 */
public final class TagChips {

    private TagChips() {}

    public static void fill(FlowPane pane, List<String> tagNames) {
        pane.getChildren().clear();
        for (String name : tagNames) {
            Label chip = new Label(name);
            chip.getStyleClass().add("tag-chip");
            pane.getChildren().add(chip);
        }

        boolean hasTags = !tagNames.isEmpty();
        pane.setVisible(hasTags);
        pane.setManaged(hasTags);
    }
}
