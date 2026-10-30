package org.example.taskschedulerdesktop.auth;

import org.example.taskschedulerdesktop.user.ProfileService;
import javafx.scene.layout.VBox;
import javafx.scene.control.Hyperlink;
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

    @FXML private Label formTitle;
    @FXML private VBox loginBox;
    @FXML private Hyperlink forgotLink;
    @FXML private Label infoLabel;

    @FXML private VBox resetBox;
    @FXML private VBox resetEmailBox;
    @FXML private TextField resetEmailField;
    @FXML private Button sendCodeButton;
    @FXML private Hyperlink haveCodeLink;
    @FXML private VBox resetCodeBox;
    @FXML private TextField resetCodeField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField repeatPasswordField;
    @FXML private Button resetButton;
    @FXML private Hyperlink backToLoginLink;

    @FXML private VBox changeBox;
    @FXML private PasswordField firstPasswordField;
    @FXML private PasswordField firstRepeatField;
    @FXML private Button firstChangeButton;

    /** Временный пароль, которым только что вошли, — нужен как «текущий» при смене. */
    private String temporaryPassword;

    public LoginController(AuthService authService, SyncService syncService, Runnable onSignedIn) {
        this.authService = authService;
        this.syncService = syncService;
        this.onSignedIn = onSignedIn;
    }

    @FXML
    public void initialize() {
        serverLabel.setText("Сервер: " + ServerConfig.serverUrl());
        loginButton.setOnAction(event -> login());

        forgotLink.setOnAction(event -> showReset());
        backToLoginLink.setOnAction(event -> showLogin(null));
        haveCodeLink.setOnAction(event -> showCodeStep());
        sendCodeButton.setOnAction(event -> sendCode());
        resetButton.setOnAction(event -> resetPassword());
        firstChangeButton.setOnAction(event -> changeTemporaryPassword());
    }

    // ===== Смена временного пароля =====

    private void showChangeStep() {
        hideMessages();
        formTitle.setText("Смените пароль");
        show(loginBox, false);
        show(resetBox, false);
        show(changeBox, true);
        firstPasswordField.requestFocus();
    }

    private void changeTemporaryPassword() {
        String next = firstPasswordField.getText();
        if (!next.equals(firstRepeatField.getText())) {
            showError("Пароли не совпадают");
            return;
        }
        try {
            ProfileService.validatePassword(next);
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
            return;
        }
        if (next.equals(temporaryPassword)) {
            showError("Новый пароль должен отличаться от временного");
            return;
        }
        firstChangeButton.setDisable(true);
        hideMessages();
        show(progressBox, true);
        progressLabel.setText("Сохранение пароля…");
        runInBackground(() -> {
            authService.changePassword(temporaryPassword, next);
            syncService.initialLoad();
        }, () -> {
            temporaryPassword = null;
            firstPasswordField.clear();
            firstRepeatField.clear();
            firstChangeButton.setDisable(false);
            show(progressBox, false);
            show(changeBox, false);
            show(loginBox, true);
            formTitle.setText("Вход в систему");
            onSignedIn.run();
        }, error -> {
            firstChangeButton.setDisable(false);
            show(progressBox, false);
            showError(describe(error));
        });
    }

    // ===== Сброс пароля =====

    private void showReset() {
        resetEmailField.setText(emailField.getText());
        hideMessages();
        formTitle.setText("Восстановление пароля");
        show(loginBox, false);
        show(resetBox, true);
        show(resetEmailBox, true);
        show(resetCodeBox, false);
    }

    private void showCodeStep() {
        hideMessages();
        show(resetEmailBox, false);
        show(resetCodeBox, true);
    }

    private void showLogin(String info) {
        hideMessages();
        formTitle.setText("Вход в систему");
        show(resetBox, false);
        show(loginBox, true);
        if (info != null) {
            infoLabel.setText(info);
            show(infoLabel, true);
        }
    }

    private void sendCode() {
        String email = resetEmailField.getText() == null ? "" : resetEmailField.getText().trim();
        if (!email.contains("@")) {
            showError("Введите email");
            return;
        }
        sendCodeButton.setDisable(true);
        runInBackground(() -> authService.requestPasswordReset(email), () -> {
            sendCodeButton.setDisable(false);
            emailField.setText(email);
            showCodeStep();
        }, error -> {
            sendCodeButton.setDisable(false);
            showError(describe(error));
        });
    }

    private void resetPassword() {
        String code = resetCodeField.getText() == null ? "" : resetCodeField.getText().trim();
        if (code.isEmpty()) {
            showError("Введите код из письма");
            return;
        }
        if (!newPasswordField.getText().equals(repeatPasswordField.getText())) {
            showError("Пароли не совпадают");
            return;
        }
        try {
            ProfileService.validatePassword(newPasswordField.getText());
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
            return;
        }
        String password = newPasswordField.getText();
        resetButton.setDisable(true);
        runInBackground(() -> authService.resetPassword(code, password), () -> {
            resetButton.setDisable(false);
            resetCodeField.clear();
            newPasswordField.clear();
            repeatPasswordField.clear();
            passwordField.clear();
            showLogin("Пароль изменён. Войдите с новым паролем.");
        }, error -> {
            resetButton.setDisable(false);
            showError(error instanceof ApiException api && api.is("VALIDATION_ERROR")
                    ? "Код неверный или устарел. Запросите новый." : describe(error));
        });
    }

    private void runInBackground(Runnable work, Runnable onSuccess, java.util.function.Consumer<Throwable> onError) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                work.run();
                return null;
            }
        };
        task.setOnSucceeded(event -> onSuccess.run());
        task.setOnFailed(event -> onError.accept(task.getException()));
        Thread thread = new Thread(task, "password-reset");
        thread.setDaemon(true);
        thread.start();
    }

    private void hideMessages() {
        show(errorLabel, false);
        show(infoLabel, false);
    }

    private static void show(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
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
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                updateMessage("Вход…");
                AuthUser user = authService.login(email, password);
                if (user.mustChangePassword()) {
                    return false; // данные загрузим после смены пароля
                }
                updateMessage("Загрузка данных…");
                syncService.initialLoad();
                return true;
            }
        };
        progressLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(event -> {
            progressLabel.textProperty().unbind();
            passwordField.clear();
            if (task.getValue()) {
                onSignedIn.run();
            } else {
                setBusy(false, null);
                temporaryPassword = password;
                showChangeStep();
            }
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
        show(infoLabel, false);
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
}
