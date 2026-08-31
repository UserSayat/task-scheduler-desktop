package org.example.taskschedulerdesktop.navigation;

/**
 * Интерфейс для контроллеров, которые могут принимать контекст при навигации.
 */
public interface ContextAware {

    /**
     * Передает контекст в контроллер.
     * @param context любой объект (Task, Project, User и т.д.)
     */
    void setContext(Object context);
}