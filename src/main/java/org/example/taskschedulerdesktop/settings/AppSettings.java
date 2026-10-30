package org.example.taskschedulerdesktop.settings;

import org.example.taskschedulerdesktop.shared.ui.DateFormatter;
import org.example.taskschedulerdesktop.user.Timezones;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Системные настройки (API.md, раздел 10) в памяти клиента. Значения приходят с сервера при синхронизации
 * и хранятся в локальной таблице settings, поэтому действуют и офлайн. Формат даты и часовой пояс
 * по умолчанию сразу передаются туда, где они используются.
 */
public class AppSettings {

    public static final String TIMEZONE_DEFAULT = "timezone.default";
    public static final String DATE_FORMAT = "date.format";
    public static final String SYNC_INTERVAL = "sync.interval_seconds";
    public static final String SYNC_BATCH = "sync.batch_size";

    private static final int DEFAULT_INTERVAL = 120;
    private static final int DEFAULT_BATCH = 100;

    private volatile Map<String, String> values = Map.of();

    /** Новые значения (с сервера или из локальной базы при запуске). */
    public void update(Map<String, String> settings) {
        Map<String, String> copy = new LinkedHashMap<>(values);
        copy.putAll(settings);
        values = Map.copyOf(copy);
        DateFormatter.setDatePattern(values.get(DATE_FORMAT));
        Timezones.setDefaultZone(values.get(TIMEZONE_DEFAULT));
    }

    public Map<String, String> all() {
        return values;
    }

    /** Интервал автоматической синхронизации; значения вне 30–3600 секунд не применяются. */
    public Duration syncInterval() {
        return Duration.ofSeconds(number(SYNC_INTERVAL, DEFAULT_INTERVAL, 30, 3600));
    }

    /** Сколько изменений отправлять за один push; значения вне 10–500 не применяются. */
    public int pushBatch() {
        return number(SYNC_BATCH, DEFAULT_BATCH, 10, 500);
    }

    private int number(String key, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(values.getOrDefault(key, "").strip());
            return value >= min && value <= max ? value : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
