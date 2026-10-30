package org.example.taskschedulerdesktop.settings;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Label;
import org.example.taskschedulerdesktop.shared.api.OfflineException;
import org.example.taskschedulerdesktop.shared.navigation.NavigationManager;
import org.example.taskschedulerdesktop.user.Timezones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Экран «Настройки» для администратора (API.md, раздел 10). Сохраняются на сервере — нужна связь;
 * остальные сотрудники получат их при следующей синхронизации.
 */
public class SettingsController {

    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

    /** Те же форматы, что разрешает сервер. */
    private static final List<String> DATE_FORMATS = List.of("dd.MM.yyyy", "d MMMM yyyy", "yyyy-MM-dd", "MM/dd/yyyy");

    private final SettingsService service;

    @FXML private ComboBox<String> dateFormatComboBox;
    @FXML private Label dateExampleLabel;
    @FXML private ComboBox<String> timezoneComboBox;
    @FXML private Spinner<Integer> intervalSpinner;
    @FXML private Spinner<Integer> batchSpinner;
    @FXML private Button saveButton;

    public SettingsController(SettingsService service) {
        this.service = service;
    }

    @FXML
    public void initialize() {
        dateFormatComboBox.getItems().setAll(DATE_FORMATS);
        dateFormatComboBox.valueProperty().addListener((obs, old, pattern) -> showExample(pattern));
        timezoneComboBox.getItems().setAll(Timezones.COMMON);
        intervalSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(30, 3600, 120, 30));
        batchSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 500, 100, 10));
        intervalSpinner.setEditable(true);
        batchSpinner.setEditable(true);

        Map<String, String> current = service.current();
        dateFormatComboBox.setValue(current.getOrDefault(AppSettings.DATE_FORMAT, "dd.MM.yyyy"));
        timezoneComboBox.setValue(current.getOrDefault(AppSettings.TIMEZONE_DEFAULT, Timezones.defaultZone()));
        intervalSpinner.getValueFactory().setValue(number(current.get(AppSettings.SYNC_INTERVAL), 120));
        batchSpinner.getValueFactory().setValue(number(current.get(AppSettings.SYNC_BATCH), 100));

        saveButton.setOnAction(event -> save());
    }

    private void showExample(String pattern) {
        if (pattern == null) {
            return;
        }
        String example = pattern.contains("MMMM")
                ? LocalDate.now().getDayOfMonth() + " " + LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy",
                Locale.forLanguageTag("ru")))
                : LocalDate.now().format(DateTimeFormatter.ofPattern(pattern));
        dateExampleLabel.setText("Например: " + example);
    }

    private void save() {
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put(AppSettings.DATE_FORMAT, dateFormatComboBox.getValue());
        changes.put(AppSettings.TIMEZONE_DEFAULT, timezoneComboBox.getEditor().getText());
        changes.put(AppSettings.SYNC_INTERVAL, String.valueOf(intervalSpinner.getValue()));
        changes.put(AppSettings.SYNC_BATCH, String.valueOf(batchSpinner.getValue()));

        saveButton.setDisable(true);
        Task<Map<String, String>> task = new Task<>() {
            @Override
            protected Map<String, String> call() {
                return service.save(changes);
            }
        };
        task.setOnSucceeded(event -> {
            saveButton.setDisable(false);
            NavigationManager.showToast("Настройки сохранены. Сотрудники получат их при синхронизации", "success");
        });
        task.setOnFailed(event -> {
            saveButton.setDisable(false);
            Throwable error = task.getException();
            if (error instanceof IllegalArgumentException) {
                NavigationManager.showToast(error.getMessage(), "info");
            } else if (error instanceof OfflineException) {
                NavigationManager.showToast("Нет связи с сервером. Настройки сохраняются только онлайн", "info");
            } else {
                log.error("Settings saving failed", error);
                NavigationManager.showToast("Не удалось сохранить настройки", "error");
            }
        });
        Thread thread = new Thread(task, "settings");
        thread.setDaemon(true);
        thread.start();
    }

    private static int number(String value, int fallback) {
        try {
            return Integer.parseInt(value == null ? "" : value.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
