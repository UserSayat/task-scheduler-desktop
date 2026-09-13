package org.example.taskschedulerdesktop.controllers;

public interface Shutdownable {
    /**
     * Вызывается автоматически перед уничтожением экрана
     * для очистки памяти и отписки от наблюдателей.
     */
    void shutdown();
}

