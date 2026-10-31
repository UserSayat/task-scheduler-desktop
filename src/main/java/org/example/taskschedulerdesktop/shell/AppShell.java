package org.example.taskschedulerdesktop.shell;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.example.taskschedulerdesktop.sync.SyncStatus;
import org.example.taskschedulerdesktop.auth.LoginController;
import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.shared.navigation.NavigationManager;
import org.example.taskschedulerdesktop.shared.navigation.Routes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;

/**
 * Что показывает главное окно: экран входа или рабочее пространство. Переключение — заменой корня сцены.
 * <ul>
 *   <li>Демо-режим — сразу рабочее пространство, без сервера.</li>
 *   <li>Вход на устройстве уже был — рабочее пространство, синхронизация в фоне (работает и без сети).</li>
 *   <li>Иначе — экран входа.</li>
 * </ul>
 */
public class AppShell {

    private static final Logger log = LoggerFactory.getLogger(AppShell.class);

    private static final String[] STYLES = {
            "/org/example/taskschedulerdesktop/styles/style.css",
            "/org/example/taskschedulerdesktop/styles/row_task_table.css",
    };

    private final Stage stage;
    private final AppConfig config;
    private Scene scene;
    /** Своя рамка окна; null — системная ({@code -Dtaskhub.nativeFrame=true}). */
    private WindowFrame frame;
    private MainController mainController;
    private LoginController loginController;

    public AppShell(Stage stage, AppConfig config) {
        this.stage = stage;
        this.config = config;
    }

    public void start() {
        if (WindowFrame.enabled()) {
            frame = new WindowFrame(stage);
            scene = new Scene(frame.root(), WindowFrame.outerSize(1280), WindowFrame.outerSize(720));
            frame.install(scene);
        } else {
            scene = new Scene(new StackPane(), 1280, 720);
        }
        for (String cssPath : STYLES) {
            URL cssUrl = getClass().getResource(cssPath);
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            } else {
                log.error("Not found: {}", cssPath);
            }
        }
        // «Назад» кнопкой мыши, клавишами и свайпом — только на главном экране
        BackNavigation.install(scene, () -> mainController != null);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setTitle("Корпоративный планировщик");
        stage.setScene(scene);

        config.getTokenStore().setOnSessionExpired(() ->
                Platform.runLater(() -> showLogin("Сессия истекла. Войдите снова.")));
        // Заблокировали или требуется сменить временный пароль — сервер не отдаёт данные, нужен повторный вход
        config.getSyncService().addStatusListener(status -> {
            if (status.state() == SyncStatus.State.SIGNED_OUT && mainController != null) {
                Platform.runLater(() -> {
                    showLogin(status.message() != null ? status.message() : "Войдите снова.");
                    config.getTokenStore().clear();
                });
            }
        });

        if (config.isDemoMode() || config.getAuthService().restoreSession()) {
            showMain();
        } else {
            showLogin(null);
        }
        stage.show();
    }

    public void showMain() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(Routes.MAIN));
        loader.setControllerFactory(config.getControllerFactory());
        show(load(loader), SIDEBAR_WIDTH);
        mainController = loader.getController();
        if (frame != null) {
            // Кнопки окна — в общей верхней полосе, рядом с профилем, как в макете
            frame.attachHeader(mainController.header(), mainController.headerLogoCell(),
                    mainController.logoBox(), mainController.headerRight());
        }
        loginController = null;
        if (!config.isDemoMode()) {
            config.getSyncScheduler().start();
        }
    }

    /**
     * Экран входа — при первом запуске, после выхода и когда сервер отверг refresh-токен.
     * Локальные данные не удаляются.
     */
    public void showLogin(String message) {
        config.getSyncScheduler().stop();
        if (mainController != null) {
            mainController.shutdown();
            NavigationManager.dispose();
            mainController = null;
        }

        if (loginController == null) {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(Routes.LOGIN));
            loader.setControllerFactory(clazz -> clazz == LoginController.class
                    ? new LoginController(config.getAuthService(), config.getSyncService(), this::showMain)
                    : config.getControllerFactory().call(clazz));
            if (frame != null) {
                frame.detachHeader();
            }
            show(load(loader), 0);
            loginController = loader.getController();
        }
        if (message != null) {
            loginController.showMessage(message);
        }
    }

    public void stop() {
        config.getSyncScheduler().stop();
    }

    /** Ширина левого меню: над ним заголовок своей рамки тёмный. */
    private static final double SIDEBAR_WIDTH = 220;

    /** Показать экран: внутри своей рамки или корнем сцены. */
    private void show(Parent screen, double sidebarWidth) {
        if (frame != null) {
            frame.setContent(screen);
            frame.setSidebarWidth(sidebarWidth);
        } else {
            scene.setRoot(screen);
        }
    }

    private Parent load(FXMLLoader loader) {
        try {
            return loader.load();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load " + loader.getLocation(), e);
        }
    }
}
