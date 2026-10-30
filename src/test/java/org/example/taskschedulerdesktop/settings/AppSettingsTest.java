package org.example.taskschedulerdesktop.settings;

import org.example.taskschedulerdesktop.shared.database.UnitOfWork;
import org.example.taskschedulerdesktop.shared.ui.DateFormatter;
import org.example.taskschedulerdesktop.user.Timezones;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Системные настройки на клиенте: значения из локальной базы, их применение и защита от неверных значений.
 */
class AppSettingsTest {

    private final AppSettings settings = new AppSettings();

    @AfterEach
    void restoreDefaults() {
        settings.update(Map.of(AppSettings.DATE_FORMAT, "dd.MM.yyyy", AppSettings.TIMEZONE_DEFAULT, "Europe/Moscow"));
    }

    @Test
    void defaultsComeFromLocalDatabase() {
        String url = "jdbc:h2:mem:settings_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:migration").load().migrate();
        UnitOfWork uow = new UnitOfWork(() -> DriverManager.getConnection(url, "sa", ""));

        new SettingsService(null, uow, new SettingsRepository(uow), settings, () -> {}).loadLocal();

        assertEquals(Duration.ofSeconds(120), settings.syncInterval());
        assertEquals(100, settings.pushBatch());
        assertEquals("dd.MM.yyyy", DateFormatter.datePattern());
        assertEquals("Europe/Moscow", Timezones.defaultZone());
    }

    @Test
    void newValuesAreAppliedEverywhere() {
        settings.update(Map.of(
                AppSettings.SYNC_INTERVAL, "300",
                AppSettings.SYNC_BATCH, "50",
                AppSettings.DATE_FORMAT, "yyyy-MM-dd",
                AppSettings.TIMEZONE_DEFAULT, "Asia/Omsk"));

        assertEquals(Duration.ofSeconds(300), settings.syncInterval());
        assertEquals(50, settings.pushBatch());
        assertEquals("2026-11-05", DateFormatter.formatFull(LocalDate.of(2026, 11, 5)));
        assertEquals("Asia/Omsk", Timezones.defaultZone());

        settings.update(Map.of(AppSettings.DATE_FORMAT, "d MMMM yyyy"));
        assertEquals("5 ноября 2026", DateFormatter.formatFull(LocalDate.of(2026, 11, 5)), "месяц в родительном падеже");
    }

    @Test
    void outOfRangeValuesFallBackToDefaults() {
        settings.update(Map.of(AppSettings.SYNC_INTERVAL, "5", AppSettings.SYNC_BATCH, "много"));

        assertEquals(Duration.ofSeconds(120), settings.syncInterval());
        assertEquals(100, settings.pushBatch());
    }
}
