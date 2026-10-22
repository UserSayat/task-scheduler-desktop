package org.example.taskschedulerdesktop.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import org.example.taskschedulerdesktop.session.Session;
import org.example.taskschedulerdesktop.shared.api.ApiClient;
import org.example.taskschedulerdesktop.shared.api.ApiException;
import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.example.taskschedulerdesktop.sync.local.SyncStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Токены доступа (API.md, 5): access-токен живёт только в памяти, refresh-токен хранится в sync_state,
 * чтобы после перезапуска не входить заново.
 * <p>
 * Если сервер отверг refresh-токен (истёк, отозван, пользователь заблокирован), токен удаляется
 * и вызывается {@code onSessionExpired} — приложение показывает экран входа.
 */
public class TokenStore implements ApiClient.AccessTokens {

    private static final Logger log = LoggerFactory.getLogger(TokenStore.class);

    record RefreshRequest(String refreshToken, String deviceId) {}

    private final ApiClient api;
    private final UnitOfWork uow;
    private final SyncStateRepository syncState;
    private final Session session;
    private volatile String accessToken;
    private volatile Runnable onSessionExpired = () -> {};

    public TokenStore(ApiClient api, UnitOfWork uow, Session session) {
        this.api = api;
        this.uow = uow;
        this.syncState = new SyncStateRepository(uow);
        this.session = session;
    }

    public void setOnSessionExpired(Runnable onSessionExpired) {
        this.onSessionExpired = onSessionExpired;
    }

    @Override
    public String current() {
        return accessToken;
    }

    @Override
    public synchronized String refresh(String rejected) {
        String current = accessToken;
        if (current != null && !current.equals(rejected)) {
            return current; // уже обновил другой поток
        }

        Optional<String> refreshToken = refreshToken();
        if (refreshToken.isEmpty()) {
            throw new ApiException(401, "INVALID_REFRESH_TOKEN", "Требуется вход", null);
        }
        try {
            TokenResponse tokens = api.postAnonymous("/auth/refresh",
                    new RefreshRequest(refreshToken.get(), session.getDeviceId().toString()), new TypeReference<>() {});
            save(tokens);
            log.debug("Access token refreshed");
            return tokens.accessToken();
        } catch (ApiException e) {
            if (e.status() == 401 || e.is("USER_BLOCKED")) {
                log.info("Session expired: {}", e.code());
                clear();
                onSessionExpired.run();
            }
            throw e;
        }
    }

    public Optional<String> refreshToken() {
        return uow.read(() -> syncState.get(SyncStateRepository.REFRESH_TOKEN));
    }

    public synchronized void save(TokenResponse tokens) {
        accessToken = tokens.accessToken();
        uow.inTransaction(() -> syncState.set(SyncStateRepository.REFRESH_TOKEN, tokens.refreshToken()));
    }

    public synchronized void clear() {
        accessToken = null;
        uow.inTransaction(() -> syncState.remove(SyncStateRepository.REFRESH_TOKEN));
    }
}
