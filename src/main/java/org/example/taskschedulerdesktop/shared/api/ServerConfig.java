package org.example.taskschedulerdesktop.shared.api;

import java.net.URI;

/**
 * Адрес сервера. По умолчанию — локальный сервер разработки, другой задаётся флагом
 * {@code -Dtaskhub.server=https://taskhub.company.ru}.
 */
public final class ServerConfig {

    public static final String SERVER_PROPERTY = "taskhub.server";
    private static final String DEFAULT_SERVER = "http://localhost:8080";
    private static final String API_PATH = "/api/v1";

    private ServerConfig() {}

    public static String serverUrl() {
        String url = System.getProperty(SERVER_PROPERTY, DEFAULT_SERVER).trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static URI apiBase() {
        return URI.create(serverUrl() + API_PATH);
    }
}
