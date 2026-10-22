package org.example.taskschedulerdesktop.auth;

import java.time.Instant;

/**
 * Ответ /auth/login и /auth/refresh (API.md, 5.1–5.2). У refresh поля {@code user} нет.
 */
public record TokenResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        AuthUser user
) {
}
