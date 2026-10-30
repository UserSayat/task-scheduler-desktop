package org.example.taskschedulerdesktop.admin;

import java.security.SecureRandom;

/**
 * Временный пароль для нового сотрудника или сброса. Без похожих символов (0/O, 1/l/I),
 * чтобы его было легко продиктовать.
 */
public final class TemporaryPasswords {

    /** Сервер требует не меньше 8 символов. */
    public static final int MIN_LENGTH = 8;

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TemporaryPasswords() {}

    public static String generate() {
        StringBuilder password = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            password.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return password.toString();
    }
}
