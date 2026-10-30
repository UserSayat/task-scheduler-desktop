package org.example.taskschedulerdesktop.shared.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * HTTP-клиент API сервера (API.md, 2): JSON, обёртка ответа {@code {"data": ...}}, ошибки {@code {"error": ...}}.
 * <p>
 * Запросы с авторизацией несут access-токен. Если его ещё нет (после перезапуска он хранится только в памяти)
 * или сервер ответил, что токен истёк, клиент один раз обновляет его через {@link AccessTokens} и повторяет запрос.
 * <p>
 * Методы блокирующие — вызывать только из фоновых потоков, не из потока JavaFX.
 */
public class ApiClient {

    private static final Logger log = LoggerFactory.getLogger(ApiClient.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Источник access-токенов. Реализация — {@code TokenStore}: токен в памяти, обновление по refresh-токену.
     */
    public interface AccessTokens {

        /** Текущий access-токен или null, если его ещё нет. */
        String current();

        /**
         * Получить новый access-токен взамен отклонённого. Если другой поток уже обновил токен,
         * возвращается новый без повторного обращения к серверу: refresh-токен одноразовый,
         * и двойное обновление сервер счёл бы кражей (API.md, 5).
         *
         * @param rejected токен, с которым запрос не прошёл (null, если токена не было)
         * @throws ApiException refresh-токен недействителен — нужен повторный вход
         */
        String refresh(String rejected);
    }

    private final HttpClient http;
    private final URI base;
    private final ObjectMapper json = Json.MAPPER;
    private AccessTokens tokens;

    public ApiClient(URI base) {
        this.base = base;
        this.http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    /** Задаётся после создания: TokenStore сам обновляет токены через этот же клиент. */
    public void setAccessTokens(AccessTokens tokens) {
        this.tokens = tokens;
    }

    // ===== Запросы с авторизацией =====

    public <T> T get(String path, TypeReference<T> type) {
        return read(send("GET", path, null, true), type);
    }

    public <T> T post(String path, Object body, TypeReference<T> type) {
        return read(send("POST", path, body, true), type);
    }

    public void post(String path, Object body) {
        send("POST", path, body, true);
    }

    public void put(String path, Object body) {
        send("PUT", path, body, true);
    }

    public void patch(String path, Object body) {
        send("PATCH", path, body, true);
    }

    public void delete(String path) {
        send("DELETE", path, null, true);
    }

    // ===== Запросы без авторизации (вход, обновление токена) =====

    public <T> T postAnonymous(String path, Object body, TypeReference<T> type) {
        return read(send("POST", path, body, false), type);
    }

    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // ===== Внутреннее =====

    private JsonNode send(String method, String path, Object body, boolean authorized) {
        if (!authorized) {
            return execute(method, path, body, null);
        }
        if (tokens == null) {
            throw new IllegalStateException("Access tokens are not configured");
        }

        String token = tokens.current();
        if (token == null) {
            token = tokens.refresh(null);
        }
        try {
            return execute(method, path, body, token);
        } catch (ApiException e) {
            // Токен истёк или отозван — обновляем один раз и повторяем
            if (e.status() == 401 && (e.is("TOKEN_EXPIRED") || e.is("UNAUTHORIZED"))) {
                log.debug("Access token rejected ({}), refreshing", e.code());
                return execute(method, path, body, tokens.refresh(token));
            }
            throw e;
        }
    }

    private JsonNode execute(String method, String path, Object body, String token) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.header("Content-Type", "application/json; charset=utf-8");
            request.method(method, HttpRequest.BodyPublishers.ofString(toJson(body), StandardCharsets.UTF_8));
        } else {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new OfflineException("Сервер недоступен", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OfflineException("Запрос прерван", e);
        }

        log.debug("{} {} -> {}", method, path, response.statusCode());
        JsonNode root = parse(response.body());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return root;
        }
        throw toException(response.statusCode(), root);
    }

    private <T> T read(JsonNode root, TypeReference<T> type) {
        if (root == null || root.get("data") == null) {
            return null;
        }
        try {
            return json.readerFor(type).readValue(root.get("data"));
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected server response: " + e.getMessage(), e);
        }
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return json.readTree(body);
        } catch (JsonProcessingException e) {
            return null; // не JSON (например, страница прокси) — разберёмся по статусу
        }
    }

    private ApiException toException(int status, JsonNode root) {
        JsonNode error = root == null ? null : root.get("error");
        if (error == null) {
            return new ApiException(status, "HTTP_" + status, "Сервер ответил ошибкой " + status, null);
        }
        Map<String, List<String>> fields = null;
        if (error.hasNonNull("fields")) {
            fields = json.convertValue(error.get("fields"), new TypeReference<>() {});
        }
        return new ApiException(status, error.path("code").asText("HTTP_" + status),
                error.path("message").asText("Ошибка сервера"), fields);
    }

    private String toJson(Object body) {
        try {
            return json.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize request body", e);
        }
    }
}
