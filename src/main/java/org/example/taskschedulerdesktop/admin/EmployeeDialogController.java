package org.example.taskschedulerdesktop.admin;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.example.taskschedulerdesktop.shared.navigation.ContextAware;
import org.example.taskschedulerdesktop.shared.navigation.NavigationManager;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Диалог «Сотрудник»: создание (контекст {@code null}) или изменение ({@link Employee}).
 * При создании задаётся временный пароль — сотрудник сменит его после входа.
 */
public class EmployeeDialogController implements ContextAware {

    private static final String DEFAULT_TIMEZONE = "Europe/Moscow";

    /** Часовые пояса для выбора; можно ввести и любой другой из базы IANA. */
    private static final List<String> TIMEZONES = List.of(
            "Europe/Kaliningrad", "Europe/Moscow", "Europe/Samara", "Asia/Yekaterinburg", "Asia/Omsk",
            "Asia/Novosibirsk", "Asia/Krasnoyarsk", "Asia/Irkutsk", "Asia/Yakutsk", "Asia/Vladivostok",
            "Asia/Magadan", "Asia/Kamchatka", "Asia/Almaty", "Asia/Tashkent");

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final EmployeeAdminService service;

    @FXML private Label titleLabel;
    @FXML private TextField nameField;
    @FXML private TextField emailField;
    @FXML private TextField positionField;
    @FXML private ComboBox<String> timezoneComboBox;
    @FXML private CheckBox adminCheckBox;
    @FXML private CheckBox projectManagerCheckBox;

    @FXML private Label passwordLabel;
    @FXML private HBox passwordBox;
    @FXML private TextField passwordField;
    @FXML private Button generatePasswordButton;

    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    /** {@code null} — создаём нового сотрудника. */
    private Employee employee;

    public EmployeeDialogController(EmployeeAdminService service) {
        this.service = service;
    }

    @FXML
    public void initialize() {
        timezoneComboBox.getItems().setAll(TIMEZONES);
        timezoneComboBox.setValue(DEFAULT_TIMEZONE);
        passwordField.setText(TemporaryPasswords.generate());

        generatePasswordButton.setOnAction(event -> passwordField.setText(TemporaryPasswords.generate()));
        cancelButton.setOnAction(event -> close());
        saveButton.setOnAction(event -> save());
    }

    @Override
    public void setContext(Object context) {
        if (!(context instanceof Employee existing)) {
            return; // новый сотрудник
        }
        this.employee = existing;
        titleLabel.setText("Сотрудник");
        nameField.setText(existing.name());
        emailField.setText(existing.email());
        positionField.setText(existing.position());
        timezoneComboBox.setValue(existing.timezone());
        adminCheckBox.setSelected(existing.hasGlobalRole(RoleAssignment.ADMIN));
        projectManagerCheckBox.setSelected(existing.hasGlobalRole(RoleAssignment.PROJECT_MANAGER));

        // Пароль существующего сотрудника меняется кнопкой «Сбросить пароль» на экране
        passwordLabel.setVisible(false);
        passwordLabel.setManaged(false);
        passwordBox.setVisible(false);
        passwordBox.setManaged(false);
        saveButton.setText("Сохранить");
    }

    private void save() {
        String error = validate();
        if (error != null) {
            NavigationManager.showToast(error, "info");
            return;
        }

        EmployeeForm form = new EmployeeForm(nameField.getText(), emailField.getText(), positionField.getText(),
                timezone(), adminCheckBox.isSelected(), projectManagerCheckBox.isSelected());
        saveButton.setDisable(true);

        if (employee == null) {
            String password = passwordField.getText();
            service.create(form, password, () -> {
                close();
                NavigationManager.showToast("Сотрудник создан", "success");
                PasswordNotice.show(null, form.name().trim(), password);
            }, this::showError);
        } else {
            service.update(employee, form, () -> {
                close();
                NavigationManager.showToast("Сотрудник сохранён", "success");
            }, this::showError);
        }
    }

    /** Те же правила, что на сервере (API.md, 6.2–6.3), чтобы не ждать ответа ради очевидной ошибки. */
    private String validate() {
        if (nameField.getText().isBlank()) {
            return "Укажите имя";
        }
        if (nameField.getText().trim().length() > 255) {
            return "Имя длиннее 255 символов";
        }
        if (!EMAIL.matcher(emailField.getText().trim()).matches()) {
            return "Некорректный email";
        }
        if (positionField.getText() != null && positionField.getText().trim().length() > 255) {
            return "Должность длиннее 255 символов";
        }
        try {
            ZoneId.of(timezone());
        } catch (DateTimeException e) {
            return "Неизвестный часовой пояс: " + timezone();
        }
        if (employee == null && passwordField.getText().length() < TemporaryPasswords.MIN_LENGTH) {
            return "Временный пароль должен быть не короче " + TemporaryPasswords.MIN_LENGTH + " символов";
        }
        return null;
    }

    private String timezone() {
        String value = timezoneComboBox.getEditor().getText();
        return value == null || value.isBlank() ? DEFAULT_TIMEZONE : value.trim();
    }

    private void showError(String message) {
        saveButton.setDisable(false);
        NavigationManager.showToast(message, "error");
    }

    private void close() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        if (stage != null) {
            NavigationManager.closeDialog(stage);
        }
    }
}
