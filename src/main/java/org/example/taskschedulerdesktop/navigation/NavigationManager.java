package org.example.taskschedulerdesktop.navigation;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.Duration;
import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.controllers.sidebar.RightSidebar;
import org.example.taskschedulerdesktop.controllers.Shutdownable;
import org.example.taskschedulerdesktop.listeners.EventBus;
import org.example.taskschedulerdesktop.listeners.PageTitleChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Центральный менеджер навигации.
 * Управляет сменой страниц, открытием диалогов и передачей контекста.
 */
public class NavigationManager {

    private static final Logger log = LoggerFactory.getLogger(NavigationManager.class);

    private static StackPane globalStackPane;
    private static StackPane contentArea;

    private static Callback<Class<?>, Object> controllerFactory;

    private static Parent rightSidebar = null;
    private static Object rightSidebarController = null;
    private static final double RIGHT_SIDEBAR_WIDTH = 400.0;

    // История переходов
    private static class PageInfo {
        final String fxmlPath;
        final String title;

        public PageInfo(String fxmlPath, String title) {
            this.fxmlPath = fxmlPath;
            this.title = title;
        }
    }
    private static final Stack<PageInfo> history = new Stack<>();
    private static String currentPage = null;
    private static String currentTitle = "";

    private static boolean isBackNavigation = false;

    //Уведомления
    private static Popup activeToast = null;

    // Пул потоков специально для асинхронной загрузки интерфейсов (хватит 2-х потоков)
    private static final ExecutorService navigationExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    });

    // Храним ссылку на контроллер ТЕКУЩЕЙ страницы, чтобы вовремя вызывать shutdown
    private static Object currentController = null;

    // Храним обработчик кликов вне сайдбара, чтобы потом от него отписаться
    private static EventHandler<MouseEvent> outsideClickFilter;
    // ============================================================
    // ИНИЦИАЛИЗАЦИЯ
    // ============================================================

    public static void init(StackPane globalStackPane, StackPane contentArea, Callback<Class<?>, Object> controllerFactory) {

        // Если init вызывается повторно — снять старый фильтр
        if (NavigationManager.globalStackPane != null && outsideClickFilter != null) {
            NavigationManager.globalStackPane.removeEventFilter(MouseEvent.MOUSE_CLICKED, outsideClickFilter);
            outsideClickFilter = null;
        }

        NavigationManager.globalStackPane = globalStackPane;
        NavigationManager.contentArea = contentArea;
        NavigationManager.controllerFactory = controllerFactory;

        outsideClickFilter = event -> {
            if (rightSidebar == null || !rightSidebar.isVisible()) {
                return;
            }
            Node clicked = (Node) event.getTarget();
            if (isChildOf(clicked, rightSidebar)) {
                return;
            }
            log.debug("Click outside sidebar, closing");
            closeRightSidebar();
        };

        globalStackPane.addEventFilter(MouseEvent.MOUSE_CLICKED, outsideClickFilter);
    }

    public static void navigateTo(String fxmlPath) {
        navigateTo(fxmlPath, null);
    }

    public static void navigateTo(String fxmlPath, String title) {
        navigateTo(fxmlPath, title, null);
    }

    public static void navigateTo(String fxmlPath, String title, Object context) {
        // Сохраняем текущую страницу в историю (если есть)
        if (!isBackNavigation && currentPage != null && !fxmlPath.equals(currentPage)) {
            history.push(new PageInfo(currentPage, currentTitle));
        }

        log.debug("Start async loading page: {}", fxmlPath);

        Task<FXMLLoader> loadTask = new Task<>() {
            @Override
            protected FXMLLoader call() throws Exception {
                FXMLLoader loader = new FXMLLoader(
                        NavigationManager.class.getResource(fxmlPath)
                );
                loader.setControllerFactory(controllerFactory);
                loader.load();
                return loader;
            }
        };

        loadTask.setOnSucceeded(event -> {
            try {
                FXMLLoader loader = loadTask.getValue();
                Parent page = loader.getRoot();
                Object newController = loader.getController();

                if (currentController instanceof Shutdownable shutdownable) {
                    shutdownable.shutdown();
                }

                currentController = newController;

                if (newController instanceof ContextAware aware) {
                    aware.setContext(context);
                }
                if (newController instanceof RightSidebar sidebarController) {
                    sidebarController.setContext(context);
                }

                contentArea.getChildren().setAll(page);
                currentPage = fxmlPath;

                if (title != null) {
                    currentTitle = title;
                }

                EventBus.getInstance().fire(new PageTitleChangedEvent(currentTitle));
                log.debug("Page displayed successfully: {}", fxmlPath);

            } catch (Exception e) {
                log.error("Error displaying page after load", e);
            }
        });

        loadTask.setOnFailed(event -> {
            Throwable e = loadTask.getException();
            log.error("Async load critical error");
            e.printStackTrace();
        });

        navigationExecutor.submit(loadTask);
    }

    // ============================================================
    // КНОПКА НАЗАД
    // ============================================================

    /**
     * Возврат на предыдущую страницу.
     */
    public static void goBack() {
        if (!history.isEmpty()) {
            PageInfo previousPage = history.pop();
            isBackNavigation = true;
            // При возврате контекст не передаем
            navigateTo(previousPage.fxmlPath, previousPage.title, null);
            isBackNavigation = false;
        } else {
            log.info("History is empty, nowhere to go");
        }
    }

    /**
     * Проверка: есть ли куда возвращаться.
     */
    public static boolean canGoBack() {
        return !history.isEmpty();
    }

    /**
     * Возвращает размер истории (количество сохраненных страниц).
     */
    public static int getHistorySize() {
        return history.size();
    }

    /**
     * Возвращает текущую страницу.
     */
    public static String getCurrentPage() {
        return currentPage;
    }

    public static void setCurrentTitle(String title) {
        currentTitle = title;
        EventBus.getInstance().fire(new PageTitleChangedEvent(title));
    }

    public static String getCurrentTitle() {
        return currentTitle;
    }

    // ============================================================
    // МОДАЛЬНЫЕ ДИАЛОГИ
    // ============================================================

    public static Stage openDialog(String fxmlPath, String title, Stage owner) {
        return openDialog(fxmlPath, title, owner, null);
    }

    public static Stage openDialog(String fxmlPath, String title, Stage owner, Object context) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    NavigationManager.class.getResource(fxmlPath)
            );
            loader.setControllerFactory(controllerFactory);

            Parent root = loader.load();

            if (loader.getController() instanceof ContextAware aware) {
                aware.setContext(context);
            }

            Stage dialogStage = new Stage();
            dialogStage.setTitle(title);
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(owner);

            Scene scene = new Scene(root);
            scene.getStylesheets().addAll(
                    NavigationManager.class.getResource("/org/example/taskschedulerdesktop/styles/style.css").toExternalForm()
            );

            dialogStage.setScene(scene);
            dialogStage.showAndWait();
            return dialogStage;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void closeDialog(Stage stage) {
        if (stage != null) {
            log.debug("Close Stage: {}", stage.getTitle());
            Platform.runLater(stage::close);
        } else {
            log.debug("Stage is null");
        }
    }

    // ============================================================
    // НОВЫЕ ОКНА
    // ============================================================

    public static void openWindow(String fxmlPath, String title) {
        openWindow(fxmlPath, title, null);
    }

    public static void openWindow(String fxmlPath, String title, Object context) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    NavigationManager.class.getResource(fxmlPath)
            );
            loader.setControllerFactory(controllerFactory);

            Parent root = loader.load();

            if (loader.getController() instanceof ContextAware aware) {
                aware.setContext(context);
            }

            Stage newStage = new Stage();
            newStage.setTitle(title);
            newStage.setScene(new Scene(root));
            newStage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void openRightSidebar(String path, Object context) {
        log.debug("openRightSidebar: path={}, context={}", path, context);

        if (rightSidebar != null) {
            globalStackPane.getChildren().remove(rightSidebar);
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    NavigationManager.class.getResource(path)
            );
            loader.setControllerFactory(AppConfig.getInstance().getControllerFactory());

            Parent sidebar = loader.load();

            Object controller = loader.getController();

            if (controller instanceof RightSidebar sidebarController) {
                log.debug("sidebarController found, calling setContext()");
                sidebarController.setContext(context);
            } else {
                log.warn("\n" +
                        "The controller does not implement RightSidebar: {}", controller);
            }

            rightSidebar = sidebar;
            rightSidebarController = controller;

            if (rightSidebar instanceof VBox sidebarVBox) {
                sidebarVBox.setPrefWidth(RIGHT_SIDEBAR_WIDTH);
                sidebarVBox.setMaxWidth(RIGHT_SIDEBAR_WIDTH);
            }

            StackPane.setAlignment(rightSidebar, Pos.CENTER_RIGHT);

            globalStackPane.getChildren().add(rightSidebar);

            TranslateTransition animate = new TranslateTransition(Duration.millis(200), rightSidebar);

            animate.setFromX(RIGHT_SIDEBAR_WIDTH);
            animate.setToX(0);
            animate.play();
        } catch (IOException e) {
            log.error("Sidebar loading error", e);
        }
    }

    public static void closeRightSidebar() {
        if (rightSidebar == null) {
            return;
        }

        if (rightSidebarController instanceof Shutdownable shutdownable) {
            shutdownable.shutdown();
        }

        rightSidebarController = null;

        TranslateTransition animate = new TranslateTransition(Duration.millis(200), rightSidebar);
        animate.setToX(RIGHT_SIDEBAR_WIDTH);
        animate.setOnFinished(event -> {
            globalStackPane.getChildren().remove(rightSidebar);
            rightSidebar = null;
        });
        animate.play();
    }

    private static boolean isChildOf(Node node, Node potentialParent) {
        while (node != null) {
            if (node == potentialParent) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    // ============================================================
    // УВЕДОМЛЕНИЯ
    // ============================================================

    /**
     * Показывает всплывающее уведомление внизу экрана.
     */
    public static void showToast(String message, String type) {
        if (globalStackPane == null) {
            log.warn("globalStackPane not initialized, Toast not shown");
            return;
        }

        // Удаляем старый Toast, если есть
        if (activeToast != null) {
            activeToast.hide();
            activeToast = null;
        }

        // Определяем цвет
        String color = switch (type) {
            case "success" -> "#2ecc71";
            case "error" -> "#e74c3c";
            case "info" -> "#3498db";
            default -> "#333333";
        };

        // Создаём контейнер
        Label label = new Label(message);
        label.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");

        StackPane container = new StackPane(label);
        container.setStyle(
                "-fx-background-color: " + color + "; " +
                        "-fx-padding: 12 24 12 24; " +
                        "-fx-border-radius: 8; " +
                        "-fx-background-radius: 8; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 2);"
        );
        container.setMaxWidth(400);

        // Создаём Popup
        Popup popup = new Popup();
        popup.getContent().add(container);
        popup.setAutoHide(true);
        popup.setAutoFix(true);

        // Позиционируем внизу по центру
        double popupX = (globalStackPane.getWidth() - container.getMaxWidth()) / 2;
        double popupY = globalStackPane.getHeight() - 80;

        // Если globalStackPane ещё не отрисовалась, ждём
        if (popupX < 0) popupX = 0;
        if (popupY < 0) popupY = 20;

        popup.setX(globalStackPane.localToScreen(popupX, 0).getX());
        popup.setY(globalStackPane.localToScreen(0, popupY).getY());

        // Анимация
        container.setOpacity(0);
        popup.show(globalStackPane.getScene().getWindow());
        activeToast = popup;

        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), container);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        PauseTransition stay = new PauseTransition(Duration.seconds(2.5));
        stay.setOnFinished(e -> {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), container);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(ev -> {
                popup.hide();
                activeToast = null;
            });
            fadeOut.play();
        });
        stay.play();

        log.debug("Toast is displayed: {}", message);
    }

    public static void dispose() {
        if (globalStackPane != null && outsideClickFilter != null) {
            globalStackPane.removeEventFilter(MouseEvent.MOUSE_CLICKED, outsideClickFilter);
            outsideClickFilter = null;
        }
        if (globalStackPane != null && rightSidebar != null) {
            globalStackPane.getChildren().remove(rightSidebar);
            rightSidebar = null;
        }
        rightSidebarController = null;
        history.clear();
        currentPage = null;
        currentController = null;
        log.debug("NavigationManager disposed");
    }
}