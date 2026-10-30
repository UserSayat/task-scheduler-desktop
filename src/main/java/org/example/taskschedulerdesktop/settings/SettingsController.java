package org.example.taskschedulerdesktop.settings;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
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
import java.util.Objects;

/**
 * Экран «Настройки» для администратора (API.md, раздел 10). Сохраняются на сервере — нужна связь;
 * остальные сотрудники получат их при следующей синхронизации. «Сохранить» и «Отменить»
 * активны, только когда что-то изменено.
 */
public class SettingsController {

    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

    /** Те же форматы, что разрешает сервер. */
    private static final List<String> DATE_FORMATS = List.of("dd.MM.yyyy", "d MMMM yyyy", "yyyy-MM-dd", "MM/dd/yyyy");
    private static final String DEFAULT_HINT = "Настройки действуют для всех сотрудников. Сохраняются на сервере — нужна связь.";

    private final SettingsService service;

    @FXML private ComboBox<String> dateFormatComboBox;
    @FXML private Label dateExampleLabel;
    @FXML private ComboBox<String> timezoneComboBox;
    @FXML private Spinner<Integer> intervalSpinner;
    @FXML private Spinner<Integer> batchSpinner;
    @FXML private Label statusLabel;
    @FXML private Button revertButton;
    @FXML private Button saveButton;

    /** Значения, которые сейчас действуют, — с ними сравниваются поля формы. */
    private Map<String, String> saved = Map.of();

    public SettingsController(SettingsService service) {
        this.service = service;
    }

    @FXML
    public void initialize() {
        dateFormatComboBox.getItems().setAll(DATE_FORMATS);
        timezoneComboBox.getItems().setAll(Timezones.COMMON);
        intervalSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(30, 3600, 120, 30));
        batchSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 500, 100, 10));
        intervalSpinner.setEditable(true);
        batchSpinner.setEditable(true);

        dateFormatComboBox.valueProperty().addListener((obs, old, pattern) -> {
            showExample(pattern);
            updateButtons();
        });
        timezoneComboBox.valueProperty().addListener((obs, old, zone) -> updateButtons());
        timezoneComboBox.getEditor().textProperty().addListener((obs, old, zone) -> updateButtons());
        intervalSpinner.valueProperty().addListener((obs, old, value) -> updateButtons());
        batchSpinner.valueProperty().addListener((obs, old, value) -> updateButtons());

        revertButton.setOnAction(event -> fill(saved));
        saveButton.setOnAction(event -> save());

        saved = service.current();
        fill(saved);
    }

    /** Поля формы — по действующим значениям. */
    private void fill(Map<String, String> values) {
        dateFormatComboBox.setValue(values.getOrDefault(AppSettings.DATE_FORMAT, "dd.MM.yyyy"));
        timezoneComboBox.setValue(values.getOrDefault(AppSettings.TIMEZONE_DEFAULT, Timezones.defaultZone()));
        timezoneComboBox.getEditor().setText(timezoneComboBox.getValue());
        intervalSpinner.getValueFactory().setValue(number(values.get(AppSettings.SYNC_INTERVAL), 120));
        batchSpinner.getValueFactory().setValue(number(values.get(AppSettings.SYNC_BATCH), 100));
        statusLabel.setText(DEFAULT_HINT);
        updateButtons();
    }

    private Map<String, String> form() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(AppSettings.DATE_FORMAT, dateFormatComboBox.getValue());
        values.put(AppSettings.TIMEZONE_DEFAULT, timezoneComboBox.getEditor().getText().strip());
        values.put(AppSettings.SYNC_INTERVAL, String.valueOf(intervalSpinner.getValue()));
        values.put(AppSettings.SYNC_BATCH, String.valueOf(batchSpinner.getValue()));
        return values;
    }

    /** Только изменённые значения: их и отправляем на сервер. */
    private Map<String, String> changes() {
        Map<String, String> changes = new LinkedHashMap<>();
        form().forEach((key, value) -> {
            if (!Objects.equals(value, saved.get(key))) {
                changes.put(key, value);
            }
        });
        return changes;
    }

    private void updateButtons() {
        boolean dirty = !changes().isEmpty();
        saveButton.setDisable(!dirty);
        revertButton.setDisable(!dirty);
    }

    private void showExample(String pattern) {
        if (pattern == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        String example = pattern.contains("MMMM")
                ? today.getDayOfMonth() + " " + today.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("ru")))
                : today.format(DateTimeFormatter.ofPattern(pattern));
        dateExampleLabel.setText("Например: " + example);
    }

    private void save() {
        Map<String, String> changes = changes();
        if (changes.isEmpty()) {
            return;
        }
        saveButton.setDisable(true);
        revertButton.setDisable(true);
        statusLabel.setText("Сохранение…");
        Task<Map<String, String>> task = new Task<>() {
            @Override
            protected Map<String, String> call() {
                return service.save(changes);
            }
        };
        task.setOnSucceeded(event -> {
            saved = task.getValue();
            fill(saved);
            statusLabel.setText("Сохранено. Сотрудники получат настройки при следующей синхронизации.");
        });
        task.setOnFailed(event -> {
            updateButtons();
            Throwable error = task.getException();
            if (error instanceof IllegalArgumentException) {
                statusLabel.setText(error.getMessage());
            } else if (error instanceof OfflineException) {
                statusLabel.setText("Нет связи с сервером. Настройки сохраняются только онлайн.");
            } else {
                log.error("Settings saving failed", error);
                statusLabel.setText(DEFAULT_HINT);
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
