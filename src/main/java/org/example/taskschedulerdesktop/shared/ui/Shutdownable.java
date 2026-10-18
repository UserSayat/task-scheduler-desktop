package org.example.taskschedulerdesktop.shared.ui;

public interface Shutdownable {
    /**
     * Вызывается автоматически перед уничтожением экрана
     * для очистки памяти и отписки от наблюдателей.
     */
    void shutdown();
}

