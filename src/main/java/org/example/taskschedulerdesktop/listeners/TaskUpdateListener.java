package org.example.taskschedulerdesktop.listeners;

import org.example.taskschedulerdesktop.models.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TaskUpdateListener {

    private static final List<Consumer<Task>> listeners = new ArrayList<>();

    /**
     * Подписаться на изменения задач.
     * Вызывать в методе initialize() контроллера.
     */
    public static void subscribe(Consumer<Task> listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Отписаться от изменения задач.
     * Вызывать в методе shutdown() контроллера.
     */
    public static void unsubscribe(Consumer<Task> listener) {
        listeners.remove(listener);
    }

    /**
     * Оповестить все экраны о том, что задача изменилась (создана, обновлена или удалена).
     */
    public static void notifyTaskChanged(Task changedTask) {
        for (var listener : listeners) {
            listener.accept(changedTask);
        }
    }
}
