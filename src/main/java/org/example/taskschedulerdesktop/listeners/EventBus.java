package org.example.taskschedulerdesktop.listeners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class EventBus {

    private static EventBus instance;
    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    private final Map<Class<?>, List<Consumer<Object>>> subscribers = new HashMap<>();

    private EventBus() {}

    public static EventBus getInstance() {
        if (instance == null) {
            instance = new EventBus();
        }
        return instance;
    }

    /**
     * Подписаться на событие определённого типа.
     */
    @SuppressWarnings("unchecked")
    public <T> void subscribe(Class<T> eventType, Consumer<T> listener) {
        log.debug("SUBSCRIBE: {} -> {}", eventType.getSimpleName(), listener.hashCode());

        subscribers.computeIfAbsent(eventType, k -> new ArrayList<>())
                .add((Consumer<Object>) listener);
    }

    /**
     * Отписаться от события.
     */
    @SuppressWarnings("unchecked")
    public <T> void unsubscribe(Class<T> eventType, Consumer<T> listener) {
        List<Consumer<Object>> listeners = subscribers.get(eventType);
        if (listeners != null) {
            listeners.remove((Consumer<Object>) listener);
        }
    }

    /**
     * Отправить событие всем подписчикам.
     */
    @SuppressWarnings("unchecked")
    public <T> void fire(T event) {
        log.debug("FIRE: {} (subscribers: {})",
                event.getClass().getSimpleName(),
                subscribers.getOrDefault(event.getClass(), List.of()).size());

        Class<?> eventType = event.getClass();
        List<Consumer<Object>> listeners = subscribers.get(eventType);

        if (listeners != null) {
            for (Consumer<Object> listener : new ArrayList<>(listeners)) {
                try {
                    ((Consumer<T>) listener).accept(event);
                } catch (Exception e) {
                    log.error("Ошибка в обработчике события: {}", eventType.getSimpleName());
                }
            }
        }
    }

    /**
     * Очистить всех подписчиков (для тестов).
     */
    public void clear() {
        subscribers.clear();
    }
}
