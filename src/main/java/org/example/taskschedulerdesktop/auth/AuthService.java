package org.example.taskschedulerdesktop.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import org.example.taskschedulerdesktop.session.Session;
import org.example.taskschedulerdesktop.shared.api.ApiClient;
import org.example.taskschedulerdesktop.shared.api.ApiException;
import org.example.taskschedulerdesktop.shared.api.OfflineException;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.example.taskschedulerdesktop.sync.local.LocalReplica;
import org.example.taskschedulerdesktop.sync.local.SyncStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

/**
 * Вход и выход (API.md, 5).
 * <ul>
 *   <li>Первый вход на устройстве — только онлайн; после него данные загружает snapshot.</li>
 *   <li>Если на устройстве входит другой пользователь, локальные данные предыдущего удаляются.</li>
 *   <li>При запуске без сети открывается последний вошедший пользователь — по сохранённому refresh-токену.</li>
 * </ul>
 */
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    record LoginRequest(String email, String password, String deviceId) {}

    record LogoutRequest(String refreshToken) {}

    private final ApiClient api;
    private final TokenStore tokens;
    private final UnitOfWork uow;
    private final SyncStateRepository syncState;
    private final LocalReplica replica;
    private final Session session;

    public AuthService(ApiClient api, TokenStore tokens, UnitOfWork uow, Session session) {
        this.api = api;
        this.tokens = tokens;
        this.uow = uow;
        this.syncState = new SyncStateRepository(uow);
        this.replica = new LocalReplica(uow);
        this.session = session;
    }

    /**
     * @throws ApiException   неверный пароль, пользователь заблокирован, слишком много попыток
     * @throws OfflineException сервер недоступен
     */
    public AuthUser login(String email, String password) {
        TokenResponse response = api.postAnonymous("/auth/login",
                new LoginRequest(email.trim(), password, session.getDeviceId().toString()), new TypeReference<>() {});
        UUID userId = response.user().id();

        uow.inTransaction(() -> {
            Optional<String> previous = syncState.get(SyncStateRepository.CURRENT_USER_ID);
            boolean otherUser = previous.isPresent() && !previous.get().equals(userId.toString());
            // Данные без владельца — демо-режим или незавершённый прошлый вход: на сервер они попасть не должны
            boolean orphanData = previous.isEmpty() && replica.hasData();
            if (otherUser || orphanData) {
                replica.wipe();
            }
            syncState.set(SyncStateRepository.CURRENT_USER_ID, userId.toString());
        });
        tokens.save(response);
        session.signIn(userId);
        log.info("Signed in as {}", userId);
        return response.user();
    }

    record ForgotPasswordRequest(String email) {}

    record ResetPasswordRequest(String token, String newPassword) {}

    /**
     * Запросить код сброса пароля (API.md, 5.6). Сервер отвечает одинаково, есть такой email или нет.
     */
    public void requestPasswordReset(String email) {
        api.postAnonymous("/auth/forgot-password", new ForgotPasswordRequest(email.trim()), new TypeReference<Object>() {});
    }

    /**
     * Задать новый пароль по коду из письма (API.md, 5.7). Неверный или устаревший код — 422.
     */
    public void resetPassword(String token, String newPassword) {
        api.postAnonymous("/auth/reset-password", new ResetPasswordRequest(token.trim(), newPassword),
                new TypeReference<Object>() {});
    }

    /**
     * Есть ли на устройстве вход, который можно продолжить без пароля. Если есть — пользователь становится текущим.
     */
    public boolean restoreSession() {
        Optional<String> userId = uow.read(() -> syncState.get(SyncStateRepository.CURRENT_USER_ID));
        if (userId.isEmpty() || tokens.refreshToken().isEmpty()) {
            return false;
        }
        session.signIn(UUID.fromString(userId.get()));
        log.info("Session restored for {}", userId.get());
        return true;
    }

    /**
     * Выход: refresh-токен отзывается на сервере (если он доступен) и удаляется локально.
     * Данные остаются — при повторном входе того же пользователя неотправленные изменения не потеряются.
     */
    public void logout() {
        try {
            // Сначала access-токен: его получение меняет refresh-токен (ротация), отзывать нужно уже новый
            if (tokens.refreshToken().isPresent() && tokens.current() == null) {
                tokens.refresh(null);
            }
            Optional<String> refreshToken = tokens.refreshToken();
            if (refreshToken.isPresent()) {
                api.post("/auth/logout", new LogoutRequest(refreshToken.get()));
            }
        } catch (OfflineException | ApiException e) {
            log.info("Logout request failed, token will expire on its own: {}", e.getMessage());
        }
        tokens.clear();
        session.signOut();
        log.info("Signed out");
    }
}
