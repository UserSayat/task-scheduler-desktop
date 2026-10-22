package org.example.taskschedulerdesktop.shared.api;

import java.util.List;
import java.util.Map;

/**
 * Сервер ответил ошибкой (API.md, 2.6): HTTP-статус, код и сообщение для пользователя.
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final String code;
    private final Map<String, List<String>> fields;

    public ApiException(int status, String code, String message, Map<String, List<String>> fields) {
        super(message);
        this.status = status;
        this.code = code;
        this.fields = fields == null ? Map.of() : fields;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, List<String>> fields() {
        return fields;
    }

    public boolean is(String code) {
        return code.equals(this.code);
    }

    /** Ошибка на стороне сервера — запрос можно повторить позже. */
    public boolean isServerError() {
        return status >= 500;
    }
}
