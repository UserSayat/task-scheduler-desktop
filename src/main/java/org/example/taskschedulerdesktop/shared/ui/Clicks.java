package org.example.taskschedulerdesktop.shared.ui;

import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * Надёжный одиночный щелчок по строке таблицы или карточке.
 * <p>
 * Стандартное событие щелчка JavaFX не приходит, если мышь между нажатием и отпусканием сдвинулась
 * хотя бы на несколько пикселей (обычное дело для тачпада или быстрого щелчка) — тогда срабатывал только
 * второй или «долгий» щелчок. Здесь щелчком считается нажатие и отпускание левой кнопки на том же элементе
 * со сдвигом не больше {@link #MAX_SHIFT} пикселей.
 */
public final class Clicks {

    /** Насколько можно сдвинуть мышь между нажатием и отпусканием, чтобы это ещё было щелчком. */
    private static final double MAX_SHIFT = 12;
    private static final String PRESS_KEY = "taskhub.clickPress";

    private Clicks() {}

    /**
     * Выполнить действие по одиночному щелчку левой кнопкой. Обработчики стоят фильтрами,
     * поэтому им не мешает, что таблица или список сами обрабатывают нажатия (выделение строки).
     */
    public static void onClick(Node node, Runnable action) {
        node.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
                node.getProperties().put(PRESS_KEY, new double[]{event.getScreenX(), event.getScreenY()});
            } else {
                node.getProperties().remove(PRESS_KEY);
            }
        });
        node.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            Object pressed = node.getProperties().remove(PRESS_KEY);
            if (!(pressed instanceof double[] start) || event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            boolean still = Math.abs(event.getScreenX() - start[0]) <= MAX_SHIFT
                    && Math.abs(event.getScreenY() - start[1]) <= MAX_SHIFT;
            boolean inside = node.contains(node.screenToLocal(event.getScreenX(), event.getScreenY()));
            if (still && inside) {
                action.run();
            }
        });
    }
}
