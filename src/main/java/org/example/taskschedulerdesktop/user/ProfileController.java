package org.example.taskschedulerdesktop.user;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.example.taskschedulerdesktop.shared.api.OfflineException;
import org.example.taskschedulerdesktop.shared.api.ServerConfig;
import org.example.taskschedulerdesktop.shared.event.EventBus;
import org.example.taskschedulerdesktop.shared.navigation.NavigationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Диалог «Профиль»: имя, должность, часовой пояс (работает и офлайн) и смена пароля (только онлайн).
 */
public class ProfileController {

    private static final Logger log = LoggerFactory.getLogger(ProfileController.class);

    private final ProfileService profile;

    @FXML private Label emailLabel;
    @FXML private Label serverLabel;
    @FXML private TextField nameField;
    @FXML private TextField positionField;
    @FXML private ComboBox<String> timezoneComboBox;
    @FXML private Button saveProfileButton;

    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField repeatPasswordField;
    @FXML private Button changePasswordButton;

    @FXML private Button closeButton;

    public ProfileController(ProfileService profile) {
        this.profile = profile;
    }

    @FXML
    public void initialize() {
        timezoneComboBox.getItems().setAll(Timezones.COMMON);
        serverLabel.setText("Сервер: " + ServerConfig.serverUrl());
        closeButton.setOnAction(event -> close());
        saveProfileButton.setOnAction(event -> saveProfile());
        changePasswordButton.setOnAction(event -> changePassword());

        run(profile::current, user -> {
            emailLabel.setText(user.getEmail());
            nameField.setText(user.getName());
            positionField.setText(user.getPosition());
            timezoneComboBox.setValue(user.getTimezone() != null ? user.getTimezone() : Timezones.defaultZone());
        }, error -> NavigationManager.showToast("Не удалось загрузить профиль", "error"));
    }

    private void saveProfile() {
        String timezone = timezoneComboBox.getEditor().getText();
        saveProfileButton.setDisable(true);
        run(() -> profile.update(nameField.getText(), positionField.getText(), timezone),
                changed -> {
                    saveProfileButton.setDisable(false);
                    if (changed) {
                        EventBus.getInstance().fire(new UsersChangedEvent());
                        NavigationManager.showToast("Профиль сохранён", "success");
                    }
                },
                error -> {
                    saveProfileButton.setDisable(false);
                    NavigationManager.showToast(message(error, "Не удалось сохранить профиль"), "error");
                });
    }

    private void changePassword() {
        if (!newPasswordField.getText().equals(repeatPasswordField.getText())) {
            NavigationManager.showToast("Новые пароли не совпадают", "info");
            return;
        }
        try {
            ProfileService.validatePassword(newPasswordField.getText());
        } catch (IllegalArgumentException e) {
            NavigationManager.showToast(e.getMessage(), "info");
            return;
        }
        String current = currentPasswordField.getText();
        String next = newPasswordField.getText();
        changePasswordButton.setDisable(true);
        run(() -> {
                    profile.changePassword(current, next);
                    return null;
                },
                ignored -> {
                    changePasswordButton.setDisable(false);
                    currentPasswordField.clear();
                    newPasswordField.clear();
                    repeatPasswordField.clear();
                    NavigationManager.showToast("Пароль изменён. На других устройствах нужно войти заново", "success");
                },
                error -> {
                    changePasswordButton.setDisable(false);
                    NavigationManager.showToast(message(error, "Не удалось сменить пароль"), "error");
                });
    }

    private static String message(Throwable error, String fallback) {
        if (error instanceof IllegalArgumentException) {
            return error.getMessage();
        }
        if (error instanceof OfflineException) {
            return "Нет связи с сервером. Пароль можно сменить только онлайн";
        }
        return fallback;
    }

    private <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> {
            log.warn("Profile action failed: {}", task.getException().toString());
            onError.accept(task.getException());
        });
        Thread thread = new Thread(task, "profile");
        thread.setDaemon(true);
        thread.start();
    }

    private void close() {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        if (stage != null) {
            NavigationManager.closeDialog(stage);
        }
    }
}
