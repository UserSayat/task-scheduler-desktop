package org.example.taskschedulerdesktop.shared.navigation;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
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
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.Duration;
import org.example.taskschedulerdesktop.shared.ui.Animations;
import org.example.taskschedulerdesktop.shared.ui.DialogFrame;
import org.example.taskschedulerdesktop.shared.ui.DateFormatter;
import org.example.taskschedulerdesktop.config.AppConfig;
import org.example.taskschedulerdesktop.shared.ui.RightSidebar;
import org.example.taskschedulerdesktop.shared.ui.Shutdownable;
import org.example.taskschedulerdesktop.shared.event.EventBus;
import org.example.taskschedulerdesktop.shared.event.PageTitleChangedEvent;
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
    private static Region sidebarOverlay = null;

    /**
     * Запись истории. При переходе вглубь страница сохраняется целиком ({@code node} и {@code controller}):
     * «Назад» показывает её мгновенно и в том же состоянии, как в macOS. После переключения
     * раздела меню хранится только путь — страница загрузится заново.
     */
    private static class PageInfo {
        final String fxmlPath;
        final String title;
        final Parent node;
        final Object controller;
        final Object context;

        PageInfo(String fxmlPath, String title, Parent node, Object controller, Object context) {
            this.fxmlPath = fxmlPath;
            this.title = title;
            this.node = node;
            this.controller = controller;
            this.context = context;
        }
    }
    private static final Stack<PageInfo> history = new Stack<>();
    private static String currentPage = null;
    private static String currentTitle = "";

    private static boolean isBackNavigation = false;

    //Уведомления
    private static StackPane activeToast = null;

    // Пул потоков специально для асинхронной загрузки интерфейсов (хватит 2-х потоков)
    private static final ExecutorService navigationExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    });

    // Храним ссылку на контроллер ТЕКУЩЕЙ страницы, чтобы вовремя вызывать shutdown
    private static Object currentController = null;
    private static Parent currentNode = null;
    /** С каким объектом открыта текущая страница (например, проект), или null. */
    private static Object currentContext = null;

    // ============================================================
    // ИНИЦИАЛИЗАЦИЯ
    // ============================================================

    public static void init(StackPane globalStackPane, StackPane contentArea, Callback<Class<?>, Object> controllerFactory) {

        NavigationManager.globalStackPane = globalStackPane;
        NavigationManager.contentArea = contentArea;
        NavigationManager.controllerFactory = controllerFactory;
        // Сдвигаемые при переходах экраны не должны заезжать на меню и шапку
        Animations.clipToBounds(contentArea);

    }

    public static void navigateTo(String fxmlPath) {
        navigateTo(fxmlPath, null);
    }

    public static void navigateTo(String fxmlPath, String title) {
        navigateTo(fxmlPath, title, null);
    }

    public static void navigateTo(String fxmlPath, String title, Object context) {
        navigate(fxmlPath, title, context, null);
    }

    /**
     * Открыть страницу из бокового меню: как переключение раздела (без перехода «вглубь»),
     * но с контекстом — например, проект из списка проектов в меню.
     */
    public static void navigateToSection(String fxmlPath, String title, Object context) {
        navigate(fxmlPath, title, context, Animations.Direction.SECTION);
    }

    private static void navigate(String fxmlPath, String title, Object context, Animations.Direction forced) {
        // Уже на этой странице (например, повторное нажатие на пункт меню) — не перезагружаем, без моргания
        if (!isBackNavigation && context == null && fxmlPath.equals(currentPage)) {
            return;
        }

        // Направление анимации определяем сразу: флаг «Назад» действует только во время этого вызова
        Animations.Direction direction = isBackNavigation ? Animations.Direction.BACK
                : forced != null ? forced
                : context != null ? Animations.Direction.FORWARD
                : Animations.Direction.SECTION;

        // Сохраняем текущую страницу в историю (если есть). При переходе вглубь — вместе с её состоянием
        boolean keepLeaving = false;
        if (!isBackNavigation && currentPage != null && !fxmlPath.equals(currentPage)) {
            keepLeaving = direction == Animations.Direction.FORWARD && currentNode != null;
            history.push(keepLeaving
                    ? new PageInfo(currentPage, currentTitle, currentNode, currentController, currentContext)
                    : new PageInfo(currentPage, currentTitle, null, null, null));
        }
        boolean leavingKept = keepLeaving;

        log.debug("Start async loading page: {}", fxmlPath);

        Task<FXMLLoader> loadTask = new Task<>() {
            @Override
            protected FXMLLoader call() throws Exception {
                FXMLLoader loader = new FXMLLoader(
                        NavigationManager.class.getResource(fxmlPath)
                );
                loader.setControllerFactory(controllerFactory);
                DateFormatter.applyToPickers(loader.load());
                return loader;
            }
        };

        loadTask.setOnSucceeded(event -> {
            try {
                FXMLLoader loader = loadTask.getValue();
                Parent page = loader.getRoot();
                Object newController = loader.getController();

                // Страница, сохранённая в истории целиком, продолжит жить — её не закрываем
                if (!leavingKept && currentController instanceof Shutdownable shutdownable) {
                    shutdownable.shutdown();
                }

                currentController = newController;
                currentNode = page;
                currentContext = context;

                if (newController instanceof ContextAware aware) {
                    aware.setContext(context);
                }
                if (newController instanceof RightSidebar sidebarController) {
                    sidebarController.setContext(context);
                }

                Animations.swapPage(contentArea, page, direction);
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
        if (!history.isEmpty() && history.peek().node != null) {
            // Страница сохранена целиком: показываем её сразу, текущую закрываем
            PageInfo previousPage = history.pop();
            if (currentController instanceof Shutdownable shutdownable) {
                shutdownable.shutdown();
            }
            Animations.swapPage(contentArea, previousPage.node, Animations.Direction.BACK);
            currentNode = previousPage.node;
            currentController = previousPage.controller;
            currentContext = previousPage.context;
            currentPage = previousPage.fxmlPath;
            currentTitle = previousPage.title;
            EventBus.getInstance().fire(new PageTitleChangedEvent(currentTitle));
            return;
        }
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

    /** С каким объектом открыта текущая страница (например, {@code ProjectView}), или null. */
    public static Object getCurrentContext() {
        return currentContext;
    }

    /** Эта страница сейчас на экране (а не лежит в истории для «Назад»). */
    public static boolean isCurrent(Object controller) {
        return controller != null && controller == currentController;
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
            DateFormatter.applyToPickers(root);

            if (loader.getController() instanceof ContextAware aware) {
                aware.setContext(context);
            }

            Stage dialogStage = new Stage();
            dialogStage.setTitle(title);
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(owner);

            Scene scene = DialogFrame.scene(dialogStage, root);
            // Esc закрывает окно (если клавишу не забрал, например, открытый выпадающий список)
            scene.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE && !event.isConsumed()) {
                    closeDialog(dialogStage);
                    event.consume();
                }
            });
            dialogStage.setScene(scene);
            dialogStage.setOnShown(event -> Animations.popIn(scene.getRoot()));
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
            Platform.runLater(() -> {
                if (stage.getScene() != null && stage.isShowing()) {
                    Animations.popOut(stage.getScene().getRoot(), stage::close);
                } else {
                    stage.close();
                }
            });
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
            DateFormatter.applyToPickers(root);

            if (loader.getController() instanceof ContextAware aware) {
                aware.setContext(context);
            }

            Stage newStage = new Stage();
            newStage.setTitle(title);
            newStage.setScene(DialogFrame.scene(newStage, root));
            newStage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void openRightSidebar(String path, Object context) {
        log.debug("openRightSidebar: path={}, context={}", path, context);

        if (rightSidebar != null) {
            globalStackPane.getChildren().remove(rightSidebar);
            rightSidebar = null;
        }
        if (sidebarOverlay != null) {
            globalStackPane.getChildren().remove(sidebarOverlay);
            sidebarOverlay = null;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    NavigationManager.class.getResource(path)
            );
            loader.setControllerFactory(AppConfig.getInstance().getControllerFactory());

            Parent sidebar = loader.load();
            DateFormatter.applyToPickers(sidebar);

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

            sidebarOverlay = new Region();
            sidebarOverlay.setBackground(new Background(
                    new BackgroundFill(Color.rgb(0, 0, 0, 0.4), null, null)
            ));
            sidebarOverlay.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);

            sidebarOverlay.setOnMouseClicked(e -> closeRightSidebar());

            StackPane.setAlignment(sidebarOverlay, Pos.CENTER);

            // Панель и затемнение — под верхней полосой окна, не поверх неё
            javafx.geometry.Insets belowHeader = new javafx.geometry.Insets(headerHeight(), 0, 0, 0);
            StackPane.setMargin(rightSidebar, belowHeader);
            StackPane.setMargin(sidebarOverlay, belowHeader);

            globalStackPane.getChildren().addAll(sidebarOverlay, rightSidebar);
            // Раскладка панели — до начала выезда, иначе первый кадр анимации запаздывает
            globalStackPane.applyCss();
            globalStackPane.layout();
            Animations.slidePanel(rightSidebar, sidebarOverlay, RIGHT_SIDEBAR_WIDTH, true).play();
        } catch (IOException e) {
            log.error("Sidebar loading error", e);
        }
    }

    /** Высота верхней полосы главного окна (логотип, синхронизация, профиль). */
    private static double headerHeight() {
        Node header = globalStackPane.lookup("#appHeader");
        return header instanceof Region region ? region.getHeight() : 0;
    }

    /** Открыта ли сейчас боковая панель (например, задача). */
    public static boolean isRightSidebarOpen() {
        return rightSidebar != null;
    }

    public static void closeRightSidebar() {
        if (rightSidebar == null) {
            return;
        }

        if (rightSidebarController instanceof Shutdownable shutdownable) {
            shutdownable.shutdown();
        }

        rightSidebarController = null;

        Parent closingSidebar = rightSidebar;
        Region closingOverlay = sidebarOverlay;
        rightSidebar = null;
        sidebarOverlay = null;

        // Пока панель уезжает, щелчки проходят сквозь неё и затемнение к странице
        closingSidebar.setMouseTransparent(true);
        closingOverlay.setMouseTransparent(true);
        var animateSidebar = Animations.slidePanel(closingSidebar, closingOverlay, RIGHT_SIDEBAR_WIDTH, false);
        animateSidebar.setOnFinished(event -> globalStackPane.getChildren().removeAll(closingSidebar, closingOverlay));
        animateSidebar.play();
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

        // Тост — слой внутри окна, а не отдельное всплывающее окно: у всплывающего окна на Windows
        // вокруг скруглённой плашки просвечивал прямоугольник (особенно поверх затемнения у боковой панели)
        if (activeToast != null) {
            globalStackPane.getChildren().remove(activeToast);
            activeToast = null;
        }

        String color = switch (type) {
            case "success" -> "#2ecc71";
            case "error" -> "#e74c3c";
            case "info" -> "#3498db";
            default -> "#333333";
        };

        Label label = new Label(message);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");

        StackPane toast = new StackPane(label);
        toast.setStyle(
                "-fx-background-color: " + color + "; " +
                        "-fx-padding: 12 24 12 24; " +
                        "-fx-background-radius: 8; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 12, 0, 0, 3);"
        );
        label.setMaxWidth(352); // длинный текст переносится, а плашка не шире 400
        toast.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE); // по размеру текста, не во всю ширину
        toast.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        toast.setMouseTransparent(true); // не мешает нажимать то, что под ним
        StackPane.setAlignment(toast, javafx.geometry.Pos.BOTTOM_CENTER);
        StackPane.setMargin(toast, new javafx.geometry.Insets(0, 0, 40, 0));

        StackPane shownToast = toast;
        globalStackPane.getChildren().add(toast); // поверх всего, в том числе боковой панели
        activeToast = toast;

        toast.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), toast);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        PauseTransition stay = new PauseTransition(Duration.seconds(2.5));
        stay.setOnFinished(e -> {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), shownToast);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(ev -> {
                globalStackPane.getChildren().remove(shownToast);
                if (activeToast == shownToast) {
                    activeToast = null;
                }
            });
            fadeOut.play();
        });
        stay.play();

        log.debug("Toast is displayed: {}", message);
    }

    public static void dispose() {
        if (globalStackPane != null && rightSidebar != null) {
            globalStackPane.getChildren().remove(rightSidebar);
            rightSidebar = null;
        }
        rightSidebarController = null;
        // Страницы, сохранённые в истории целиком, ещё подписаны на события — закрываем их
        for (PageInfo page : history) {
            if (page.controller instanceof Shutdownable shutdownable) {
                shutdownable.shutdown();
            }
        }
        history.clear();
        currentPage = null;
        currentController = null;
        currentNode = null;
        log.debug("NavigationManager disposed");
    }
}