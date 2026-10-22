package org.example.taskschedulerdesktop.session;

import java.util.UUID;

/**
 * Кто работает с приложением и на каком устройстве.
 * <p>
 * Пользователь задаётся при входе (или при запуске, если вход уже был на этом устройстве),
 * в демо-режиме — демо-администратор из {@link org.example.taskschedulerdesktop.config.DevDataSeeder}.
 */
public class Session {

    private final UUID deviceId;
    private volatile UUID currentUserId;

    public Session(UUID deviceId, UUID currentUserId) {
        this.deviceId = deviceId;
        this.currentUserId = currentUserId;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public UUID getCurrentUserId() {
        UUID userId = currentUserId;
        if (userId == null) {
            throw new IllegalStateException("No user is signed in");
        }
        return userId;
    }

    public boolean isSignedIn() {
        return currentUserId != null;
    }

    public void signIn(UUID userId) {
        this.currentUserId = userId;
    }

    public void signOut() {
        this.currentUserId = null;
    }
}
