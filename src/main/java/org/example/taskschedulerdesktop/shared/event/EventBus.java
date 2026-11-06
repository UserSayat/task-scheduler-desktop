package org.example.taskschedulerdesktop.shared.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Шина событий приложения. Подписываться можно из любого потока: экраны загружаются в фоне,
 * и их контроллеры подписываются ещё до показа, пока поток интерфейса рассылает события.
 */
public class EventBus {

    private static final EventBus instance = new EventBus();
    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    private final Map<Class<?>, List<Consumer<Object>>> subscribers = new ConcurrentHashMap<>();

    private EventBus() {}

    public static EventBus getInstance() {
        return instance;
    }

    /**
     * Подписаться на событие определённого типа.
     */
    @SuppressWarnings("unchecked")
    public <T> void subscribe(Class<T> eventType, Consumer<T> listener) {
        log.debug("SUBSCRIBE: {} -> {}", eventType.getSimpleName(), listener.hashCode());

        subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
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
            // Список копируется при каждом изменении, поэтому перебор не ломается, если обработчик отписывается
            for (Consumer<Object> listener : listeners) {
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
