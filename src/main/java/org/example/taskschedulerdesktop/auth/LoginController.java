package org.example.taskschedulerdesktop.auth;

import org.example.taskschedulerdesktop.shared.api.ApiException;
import org.example.taskschedulerdesktop.shared.api.OfflineException;
import org.example.taskschedulerdesktop.shared.api.ServerConfig;
import org.example.taskschedulerdesktop.sync.SyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

/**
 * Экран входа. После успешного входа загружает данные (snapshot) и открывает главное окно.
 */
public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    private final AuthService authService;
    private final SyncService syncService;
    private final Runnable onSignedIn;

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;
    @FXML private HBox progressBox;
    @FXML private Label progressLabel;
    @FXML private Label serverLabel;

    public LoginController(AuthService authService, SyncService syncService, Runnable onSignedIn) {
        this.authService = authService;
        this.syncService = syncService;
        this.onSignedIn = onSignedIn;
    }

    @FXML
    public void initialize() {
        serverLabel.setText("Сервер: " + ServerConfig.serverUrl());
        loginButton.setOnAction(event -> login());
    }

    /** Сообщение над формой, например «Сессия истекла». */
    public void showMessage(String message) {
        showError(message);
    }

    private void login() {
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();
        if (email.isEmpty() || password.isEmpty()) {
            showError("Введите email и пароль");
            return;
        }

        setBusy(true, "Вход…");
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                updateMessage("Вход…");
                authService.login(email, password);
                updateMessage("Загрузка данных…");
                syncService.initialLoad();
                return null;
            }
        };
        progressLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(event -> {
            progressLabel.textProperty().unbind();
            passwordField.clear();
            onSignedIn.run();
        });
        task.setOnFailed(event -> {
            progressLabel.textProperty().unbind();
            setBusy(false, null);
            showError(describe(task.getException()));
        });

        Thread thread = new Thread(task, "login");
        thread.setDaemon(true);
        thread.start();
    }

    private String describe(Throwable error) {
        if (error instanceof OfflineException) {
            return "Нет связи с сервером. Первый вход возможен только онлайн.";
        }
        if (error instanceof ApiException api) {
            return switch (api.code()) {
                case "INVALID_CREDENTIALS" -> "Неверный email или пароль";
                case "USER_BLOCKED" -> "Пользователь заблокирован. Обратитесь к администратору.";
                case "RATE_LIMITED" -> "Слишком много попыток входа. Попробуйте через 15 минут.";
                default -> api.getMessage();
            };
        }
        log.error("Login failed", error);
        return "Не удалось войти: " + error.getMessage();
    }

    private void setBusy(boolean busy, String message) {
        loginButton.setDisable(busy);
        emailField.setDisable(busy);
        passwordField.setDisable(busy);
        progressBox.setVisible(busy);
        progressBox.setManaged(busy);
        if (busy) {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
            progressLabel.setText(message);
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
}
