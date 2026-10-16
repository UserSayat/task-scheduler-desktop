package org.example.taskschedulerdesktop.session;

import java.util.UUID;

/**
 * Кто работает с приложением и на каком устройстве.
 * <p>
 * Пока входа нет (этап 7), текущий пользователь задаётся при запуске — в режиме разработки
 * это демо-администратор из {@link org.example.taskschedulerdesktop.config.DevDataSeeder}.
 */
public class Session {

    private final UUID deviceId;
    private UUID currentUserId;

    public Session(UUID deviceId, UUID currentUserId) {
        this.deviceId = deviceId;
        this.currentUserId = currentUserId;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public UUID getCurrentUserId() {
        if (currentUserId == null) {
            throw new IllegalStateException("No user is signed in");
        }
        return currentUserId;
    }

    public void setCurrentUserId(UUID currentUserId) {
        this.currentUserId = currentUserId;
    }
}
